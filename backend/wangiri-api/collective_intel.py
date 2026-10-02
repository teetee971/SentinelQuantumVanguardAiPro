"""Sentinel Collective Defense intelligence API.

This module centralizes technical indicators without persisting raw values in the
community reputation store. Public reports are always low-trust and remain pending
until an explicit moderation decision promotes one signal.

A reputation count is not a proof of identity or maliciousness. The API never grants
automatic enforcement from community reputation alone.
"""

from __future__ import annotations

import hashlib
import hmac
import os
import re
import time
from enum import StrEnum
from typing import Annotated, Any
from urllib.parse import urlsplit, urlunsplit

from fastapi import APIRouter, Header, HTTPException, Request, status
from pydantic import BaseModel, ConfigDict, Field
from redis.exceptions import RedisError


class IndicatorType(StrEnum):
    DOMAIN = "DOMAIN"
    URL = "URL"
    EMAIL = "EMAIL"
    SHA256 = "SHA256"


class IntelReportCategory(StrEnum):
    PHISHING = "PHISHING"
    MALWARE = "MALWARE"
    CREDENTIAL_THEFT = "CREDENTIAL_THEFT"
    BANK_IMPERSONATION = "BANK_IMPERSONATION"
    DELIVERY_SCAM = "DELIVERY_SCAM"
    TECH_SUPPORT_SCAM = "TECH_SUPPORT_SCAM"
    ACCOUNT_TAKEOVER = "ACCOUNT_TAKEOVER"
    INVESTMENT_SCAM = "INVESTMENT_SCAM"
    ROMANCE_SCAM = "ROMANCE_SCAM"
    OTHER = "OTHER"


class IntelModerationDecision(StrEnum):
    APPROVE = "APPROVE"
    REJECT = "REJECT"


class RelationshipType(StrEnum):
    REFERENCES = "REFERENCES"
    REDIRECTS_TO = "REDIRECTS_TO"
    DELIVERS_FILE = "DELIVERS_FILE"
    SHARES_INFRASTRUCTURE = "SHARES_INFRASTRUCTURE"
    SAME_CAMPAIGN_CANDIDATE = "SAME_CAMPAIGN_CANDIDATE"


class EvidenceStrength(StrEnum):
    E1 = "E1"
    E2 = "E2"
    E3 = "E3"
    E4 = "E4"


class IndicatorLookup(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    indicator_type: IndicatorType
    value: Annotated[str, Field(min_length=1, max_length=4096)]


class IndicatorFingerprintLookup(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    indicator_type: IndicatorType
    indicator_fingerprint: Annotated[
        str,
        Field(pattern=r"^[a-fA-F0-9]{64}$"),
    ]


class IndicatorReport(IndicatorLookup):
    category: IntelReportCategory
    client_nonce: Annotated[str, Field(min_length=16, max_length=128)]


class IndicatorRef(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    indicator_type: IndicatorType
    value: Annotated[str, Field(min_length=1, max_length=4096)]


class RelationshipReport(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    source: IndicatorRef
    target: IndicatorRef
    relationship_type: RelationshipType
    evidence_strength: EvidenceStrength
    client_nonce: Annotated[str, Field(min_length=16, max_length=128)]


class GraphLookup(IndicatorLookup):
    max_neighbors: Annotated[int, Field(ge=1, le=25)] = 25


class IndicatorModerationAction(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    indicator_type: IndicatorType
    indicator_fingerprint: Annotated[str, Field(pattern=r"^[a-fA-F0-9]{64}$")]
    category: IntelReportCategory
    decision: IntelModerationDecision


_REPUTATION_TTL_SECONDS = 180 * 86_400
_PENDING_TTL_SECONDS = 30 * 86_400
_NONCE_TTL_SECONDS = 86_400
_REPORTER_DEDUPE_TTL_SECONDS = 7 * 86_400
_GRAPH_TTL_SECONDS = 90 * 86_400

_EVIDENCE_RANK = {
    EvidenceStrength.E1.value: 1,
    EvidenceStrength.E2.value: 2,
    EvidenceStrength.E3.value: 3,
    EvidenceStrength.E4.value: 4,
}
_SYMMETRIC_RELATIONSHIPS = {
    RelationshipType.SHARES_INFRASTRUCTURE,
    RelationshipType.SAME_CAMPAIGN_CANDIDATE,
}

_DOMAIN_LABEL_RE = re.compile(r"^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")


def _positive_int_env(name: str, default: int) -> int:
    try:
        return max(0, int(os.getenv(name, str(default))))
    except ValueError:
        return default


def _normalize_domain(value: str) -> str:
    raw = value.strip().rstrip(".")
    if not raw or len(raw) > 253 or any(ch.isspace() for ch in raw):
        raise ValueError("invalid_domain")
    try:
        ascii_domain = raw.encode("idna").decode("ascii").lower()
    except UnicodeError as exc:
        raise ValueError("invalid_domain") from exc
    labels = ascii_domain.split(".")
    if len(labels) < 2 or any(not _DOMAIN_LABEL_RE.fullmatch(label) for label in labels):
        raise ValueError("invalid_domain")
    return ascii_domain


def _normalize_email(value: str) -> str:
    raw = value.strip()
    if raw.count("@") != 1:
        raise ValueError("invalid_email")
    local, domain = raw.rsplit("@", 1)
    if not local or len(local.encode("utf-8")) > 64:
        raise ValueError("invalid_email")
    if any(ord(ch) < 33 or ch.isspace() for ch in local):
        raise ValueError("invalid_email")
    return f"{local}@{_normalize_domain(domain)}"


def _normalize_url(value: str) -> str:
    raw = value.strip()
    try:
        parsed = urlsplit(raw)
    except ValueError as exc:
        raise ValueError("invalid_url") from exc
    scheme = parsed.scheme.lower()
    if scheme not in {"http", "https"} or not parsed.hostname:
        raise ValueError("invalid_url")
    if parsed.username is not None or parsed.password is not None:
        raise ValueError("userinfo_forbidden")
    host = _normalize_domain(parsed.hostname)
    try:
        port = parsed.port
    except ValueError as exc:
        raise ValueError("invalid_url_port") from exc
    if port is not None and not 1 <= port <= 65535:
        raise ValueError("invalid_url_port")
    default_port = (scheme == "http" and port == 80) or (scheme == "https" and port == 443)
    netloc = host if port is None or default_port else f"{host}:{port}"
    path = parsed.path or "/"
    return urlunsplit((scheme, netloc, path, parsed.query, ""))


def _normalize_sha256(value: str) -> str:
    normalized = value.strip().lower()
    if not re.fullmatch(r"[a-f0-9]{64}", normalized):
        raise ValueError("invalid_sha256")
    return normalized


def normalize_indicator(indicator_type: IndicatorType, value: str) -> str:
    if indicator_type is IndicatorType.DOMAIN:
        return _normalize_domain(value)
    if indicator_type is IndicatorType.URL:
        return _normalize_url(value)
    if indicator_type is IndicatorType.EMAIL:
        return _normalize_email(value)
    if indicator_type is IndicatorType.SHA256:
        return _normalize_sha256(value)
    raise ValueError("unsupported_indicator_type")


def _indicator_secret() -> str | None:
    value = os.getenv("INDICATOR_HASH_PEPPER")
    return value if value else None


def _public_report_secret() -> str | None:
    base = _indicator_secret()
    if not base:
        return None
    return hmac.new(
        base.encode(),
        b"sentinel-collective-public-report-v1",
        hashlib.sha256,
    ).hexdigest()


def indicator_fingerprint(indicator_type: IndicatorType, normalized_value: str) -> str | None:
    secret = _indicator_secret()
    if not secret:
        return None
    material = f"{indicator_type.value}\0{normalized_value}".encode()
    return hmac.new(secret.encode(), material, hashlib.sha256).hexdigest()


def _client_rate_fingerprint(request: Request) -> str:
    host = request.client.host if request.client else "unknown"
    pepper = os.getenv("RATE_LIMIT_PEPPER") or _indicator_secret() or "ephemeral"
    return hmac.new(pepper.encode(), host.encode(), hashlib.sha256).hexdigest()[:24]


def _node_id(indicator_type: IndicatorType, fingerprint: str) -> str:
    return f"{indicator_type.value}:{fingerprint}"


def _relationship_edge_id(
    source_type: IndicatorType,
    source_fingerprint: str,
    target_type: IndicatorType,
    target_fingerprint: str,
    relationship_type: RelationshipType,
) -> str | None:
    secret = _indicator_secret()
    if not secret:
        return None

    source_node = _node_id(source_type, source_fingerprint)
    target_node = _node_id(target_type, target_fingerprint)
    if relationship_type in _SYMMETRIC_RELATIONSHIPS and target_node < source_node:
        source_node, target_node = target_node, source_node

    material = (
        f"sentinel-graph-edge-v1\0{relationship_type.value}\0"
        f"{source_node}\0{target_node}"
    ).encode()
    return hmac.new(secret.encode(), material, hashlib.sha256).hexdigest()


def _campaign_candidate_fingerprint(nodes: set[str]) -> str | None:
    if len(nodes) < 2:
        return None
    secret = _indicator_secret()
    if not secret:
        return None
    material = (
        "sentinel-campaign-candidate-v1\0" + "\0".join(sorted(nodes))
    ).encode()
    return hmac.new(secret.encode(), material, hashlib.sha256).hexdigest()


def _relationship_reporter_hash(
    request: Request,
    *,
    edge_id: str,
    secret: str,
) -> str:
    material = f"{_client_rate_fingerprint(request)}:{edge_id}"
    return hmac.new(secret.encode(), material.encode(), hashlib.sha256).hexdigest()


async def _rate_limit(
    request: Request,
    *,
    endpoint: str,
    per_client_env: str,
    per_client_default: int,
) -> None:
    client = getattr(request.app.state, "redis", None)
    per_client_limit = _positive_int_env(per_client_env, per_client_default)
    global_limit = _positive_int_env("GLOBAL_RATE_LIMIT_PER_MINUTE", 120)
    if client is None or (per_client_limit <= 0 and global_limit <= 0):
        return

    now = int(time.time())
    window = now // 60
    retry_after = 60 - (now % 60)
    fingerprint = _client_rate_fingerprint(request)
    counters: list[tuple[str, int]] = []
    if per_client_limit > 0:
        counters.append((f"api:rate:v2:{endpoint}:client:{fingerprint}:{window}", per_client_limit))
    if global_limit > 0:
        counters.append((f"api:rate:v2:{endpoint}:global:{window}", global_limit))

    try:
        pipe = client.pipeline(transaction=True)
        for key, _ in counters:
            pipe.incr(key)
            pipe.expire(key, 120)
        results = await pipe.execute()
        counts = [int(results[index * 2]) for index in range(len(counters))]
        if any(count > limit for count, (_, limit) in zip(counts, counters, strict=True)):
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Limite de requêtes atteinte. Réessayez plus tard.",
                headers={"Retry-After": str(retry_after)},
            )
    except HTTPException:
        raise
    except (RedisError, TimeoutError, ValueError):
        return


def _category_codes(data: dict[str, Any]) -> list[str]:
    ranked: list[tuple[int, str]] = []
    for category in IntelReportCategory:
        count = int(data.get(f"category:{category.value}", 0) or 0)
        if count > 0:
            ranked.append((count, category.value))
    ranked.sort(key=lambda item: (-item[0], item[1]))
    return [name for _, name in ranked[:8]]


def _confidence_tier(signals: int) -> str:
    if signals >= 10:
        return "HIGH_CONFIDENCE"
    if signals >= 3:
        return "SUSPICIOUS"
    if signals >= 1:
        return "OBSERVED"
    return "UNKNOWN"


async def _read_reputation(
    app: Any,
    indicator_type: IndicatorType,
    fingerprint: str | None,
) -> tuple[int, str, list[str], int | None, int | None]:
    client = getattr(app.state, "redis", None)
    if client is None or fingerprint is None:
        return 0, "disabled", [], None, None
    key = f"intel:reputation:v1:{indicator_type.value}:{fingerprint}"
    try:
        data = await client.hgetall(key)
        ttl_seconds = int(await client.ttl(key))
        if not data:
            if ttl_seconds == -1:
                return 0, "degraded", [], None, None
            return 0, "available", [], None, None

        signals = int(data.get("signals", 0) or 0)
        last_seen = int(data.get("last_seen", 0) or 0)
        if (
            ttl_seconds <= 0
            or signals <= 0
            or last_seen <= 0
            or ttl_seconds > _REPUTATION_TTL_SECONDS + 1
        ):
            return 0, "degraded", [], None, None

        observed_at_ms = last_seen * 1_000
        ttl_ms = ttl_seconds * 1_000
        return signals, "available", _category_codes(data), observed_at_ms, ttl_ms
    except (RedisError, TimeoutError, ValueError, TypeError):
        return 0, "degraded", [], None, None


_TRUSTED_REPORT_LUA = """
if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end
if redis.call('EXISTS', KEYS[2]) == 1 then
  redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
  return 0
end
redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
redis.call('HSETNX', KEYS[3], 'first_seen', ARGV[3])
redis.call('HSET', KEYS[3], 'last_seen', ARGV[3])
redis.call('HINCRBY', KEYS[3], 'signals', 1)
redis.call('HINCRBY', KEYS[3], ARGV[4], 1)
redis.call('EXPIRE', KEYS[3], ARGV[5])
return 1
"""


_PENDING_REPORT_LUA = """
if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end
if redis.call('EXISTS', KEYS[2]) == 1 then
  redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
  return 0
end
redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
redis.call('HSETNX', KEYS[3], 'first_seen', ARGV[3])
redis.call('HSET', KEYS[3], 'last_seen', ARGV[3])
redis.call('HINCRBY', KEYS[3], 'signals', 1)
redis.call('HINCRBY', KEYS[3], ARGV[4], 1)
redis.call('EXPIRE', KEYS[3], ARGV[5])
redis.call('ZREMRANGEBYSCORE', KEYS[4], '-inf', ARGV[6])
redis.call('ZADD', KEYS[4], ARGV[3], ARGV[7])
return 1
"""


_TRUSTED_RELATIONSHIP_LUA = """
if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end
if redis.call('EXISTS', KEYS[2]) == 1 then
  redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
  return 0
end

redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])

redis.call('HSETNX', KEYS[3], 'first_seen', ARGV[3])
redis.call('HSET', KEYS[3],
  'last_seen', ARGV[3],
  'source_type', ARGV[4],
  'source_fingerprint', ARGV[5],
  'target_type', ARGV[6],
  'target_fingerprint', ARGV[7],
  'relationship_type', ARGV[8]
)
redis.call('HINCRBY', KEYS[3], 'signals', 1)

local old_rank = tonumber(redis.call('HGET', KEYS[3], 'evidence_rank') or '0')
local new_rank = tonumber(ARGV[9])
if new_rank > old_rank then
  redis.call('HSET', KEYS[3], 'evidence_rank', ARGV[9], 'evidence_strength', ARGV[10])
end

redis.call('EXPIRE', KEYS[3], ARGV[11])
local expires_at = tonumber(ARGV[3]) + tonumber(ARGV[11])
redis.call('ZREMRANGEBYSCORE', KEYS[4], '-inf', ARGV[3])
redis.call('ZREMRANGEBYSCORE', KEYS[5], '-inf', ARGV[3])
redis.call('ZADD', KEYS[4], expires_at, ARGV[12])
redis.call('ZADD', KEYS[5], expires_at, ARGV[12])
redis.call('EXPIRE', KEYS[4], ARGV[11])
redis.call('EXPIRE', KEYS[5], ARGV[11])
return 1
"""


_MODERATE_LUA = """
local pending_count = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
local total_signals = tonumber(redis.call('HGET', KEYS[1], 'signals') or '0')
if pending_count <= 0 or total_signals <= 0 then
  return 0
end

redis.call('HINCRBY', KEYS[1], ARGV[1], -1)
local remaining = redis.call('HINCRBY', KEYS[1], 'signals', -1)

if ARGV[2] == 'APPROVE' then
  redis.call('HSETNX', KEYS[3], 'first_seen', ARGV[3])
  redis.call('HSET', KEYS[3], 'last_seen', ARGV[3])
  redis.call('HINCRBY', KEYS[3], 'signals', 1)
  redis.call('HINCRBY', KEYS[3], ARGV[1], 1)
  redis.call('EXPIRE', KEYS[3], ARGV[4])
end

if remaining <= 0 then
  redis.call('DEL', KEYS[1])
  redis.call('ZREM', KEYS[2], ARGV[5])
end

if ARGV[2] == 'APPROVE' then
  return 1
end
return 2
"""


def _reporter_hash(
    request: Request,
    *,
    indicator_type: IndicatorType,
    fingerprint: str,
    category: IntelReportCategory,
    secret: str,
) -> str:
    material = (
        f"{_client_rate_fingerprint(request)}:{indicator_type.value}:"
        f"{fingerprint}:{category.value}"
    )
    return hmac.new(secret.encode(), material.encode(), hashlib.sha256).hexdigest()


async def _store_trusted_report(
    client: Any,
    *,
    indicator_type: IndicatorType,
    nonce_hash: str,
    reporter_hash: str,
    fingerprint: str,
    category: IntelReportCategory,
    now: int,
) -> bool:
    result = await client.eval(
        _TRUSTED_REPORT_LUA,
        3,
        f"intel:report:dedupe:v1:{nonce_hash}",
        f"intel:report:reporter:v1:{reporter_hash}",
        f"intel:reputation:v1:{indicator_type.value}:{fingerprint}",
        str(_NONCE_TTL_SECONDS),
        str(_REPORTER_DEDUPE_TTL_SECONDS),
        str(now),
        f"category:{category.value}",
        str(_REPUTATION_TTL_SECONDS),
    )
    return int(result) == 1


async def _store_pending_report(
    client: Any,
    *,
    indicator_type: IndicatorType,
    nonce_hash: str,
    reporter_hash: str,
    fingerprint: str,
    category: IntelReportCategory,
    now: int,
) -> bool:
    member = f"{indicator_type.value}:{fingerprint}"
    cutoff = now - _PENDING_TTL_SECONDS
    result = await client.eval(
        _PENDING_REPORT_LUA,
        4,
        f"intel:community:pending:dedupe:v1:{nonce_hash}",
        f"intel:community:pending:reporter:v1:{reporter_hash}",
        f"intel:community:pending:v1:{indicator_type.value}:{fingerprint}",
        "intel:community:moderation:v1",
        str(_NONCE_TTL_SECONDS),
        str(_REPORTER_DEDUPE_TTL_SECONDS),
        str(now),
        f"category:{category.value}",
        str(_PENDING_TTL_SECONDS),
        str(cutoff),
        member,
    )
    return int(result) == 1


async def _store_trusted_relationship(
    client: Any,
    *,
    indicator_source_type: IndicatorType,
    source_fingerprint: str,
    indicator_target_type: IndicatorType,
    target_fingerprint: str,
    relationship_type: RelationshipType,
    evidence_strength: EvidenceStrength,
    nonce_hash: str,
    reporter_hash: str,
    edge_id: str,
    now: int,
) -> bool:
    source_node = _node_id(indicator_source_type, source_fingerprint)
    target_node = _node_id(indicator_target_type, target_fingerprint)
    result = await client.eval(
        _TRUSTED_RELATIONSHIP_LUA,
        5,
        f"intel:graph:dedupe:v1:{nonce_hash}",
        f"intel:graph:reporter:v1:{reporter_hash}",
        f"intel:graph:edge:v1:{edge_id}",
        f"intel:graph:adj:v1:{source_node}",
        f"intel:graph:adj:v1:{target_node}",
        str(_NONCE_TTL_SECONDS),
        str(_REPORTER_DEDUPE_TTL_SECONDS),
        str(now),
        indicator_source_type.value,
        source_fingerprint,
        indicator_target_type.value,
        target_fingerprint,
        relationship_type.value,
        str(_EVIDENCE_RANK[evidence_strength.value]),
        evidence_strength.value,
        str(_GRAPH_TTL_SECONDS),
        edge_id,
    )
    return int(result) == 1


async def _read_graph(
    app: Any,
    *,
    indicator_type: IndicatorType,
    fingerprint: str,
    max_neighbors: int,
) -> tuple[str, list[dict[str, Any]], str | None]:
    client = getattr(app.state, "redis", None)
    if client is None:
        return "disabled", [], None

    node = _node_id(indicator_type, fingerprint)
    adjacency_key = f"intel:graph:adj:v1:{node}"
    try:
        now = int(time.time())
        await client.zremrangebyscore(adjacency_key, "-inf", now)
        edge_ids = await client.zrevrange(adjacency_key, 0, max_neighbors - 1)
        neighbors: list[dict[str, Any]] = []
        candidate_nodes = {node}
        for edge_id in edge_ids:
            data = await client.hgetall(f"intel:graph:edge:v1:{edge_id}")
            if not data:
                continue

            source_node = _node_id(
                IndicatorType(data["source_type"]),
                data["source_fingerprint"],
            )
            target_node = _node_id(
                IndicatorType(data["target_type"]),
                data["target_fingerprint"],
            )
            if source_node == node:
                neighbor_type = data["target_type"]
                neighbor_fingerprint = data["target_fingerprint"]
                direction = "OUTBOUND"
                neighbor_node = target_node
            elif target_node == node:
                neighbor_type = data["source_type"]
                neighbor_fingerprint = data["source_fingerprint"]
                direction = "INBOUND"
                neighbor_node = source_node
            else:
                continue

            relationship_type = data.get("relationship_type", "")
            evidence_strength = data.get("evidence_strength", "E1")
            evidence_rank = int(data.get("evidence_rank", 1) or 1)
            if (
                relationship_type == RelationshipType.SAME_CAMPAIGN_CANDIDATE.value
                and evidence_rank >= _EVIDENCE_RANK[EvidenceStrength.E2.value]
            ):
                candidate_nodes.add(neighbor_node)

            neighbors.append(
                {
                    "indicator_type": neighbor_type,
                    "indicator_fingerprint": neighbor_fingerprint,
                    "relationship_type": relationship_type,
                    "evidence_strength": evidence_strength,
                    "signals": int(data.get("signals", 0) or 0),
                    "first_seen": int(data.get("first_seen", 0) or 0),
                    "last_seen": int(data.get("last_seen", 0) or 0),
                    "direction": (
                        "UNDIRECTED"
                        if RelationshipType(relationship_type) in _SYMMETRIC_RELATIONSHIPS
                        else direction
                    ),
                }
            )

        candidate = _campaign_candidate_fingerprint(candidate_nodes)
        return "available", neighbors, candidate
    except (RedisError, TimeoutError, ValueError, KeyError):
        return "degraded", [], None


async def _moderate_pending(
    client: Any,
    *,
    indicator_type: IndicatorType,
    fingerprint: str,
    category: IntelReportCategory,
    decision: IntelModerationDecision,
    now: int,
) -> str:
    member = f"{indicator_type.value}:{fingerprint}"
    result = await client.eval(
        _MODERATE_LUA,
        3,
        f"intel:community:pending:v1:{indicator_type.value}:{fingerprint}",
        "intel:community:moderation:v1",
        f"intel:reputation:v1:{indicator_type.value}:{fingerprint}",
        f"category:{category.value}",
        decision.value,
        str(now),
        str(_REPUTATION_TTL_SECONDS),
        member,
    )
    numeric = int(result)
    if numeric == 1:
        return "approved"
    if numeric == 2:
        return "rejected"
    return "not_found"


def create_collective_intel_router() -> APIRouter:
    router = APIRouter(prefix="/v1/intelligence", tags=["Collective Defense"])

    @router.post("/lookup")
    async def lookup_indicator(payload: IndicatorLookup, request: Request) -> dict[str, Any]:
        await _rate_limit(
            request,
            endpoint="intel-lookup",
            per_client_env="INTEL_LOOKUP_RATE_LIMIT_PER_MINUTE",
            per_client_default=60,
        )
        try:
            normalized = normalize_indicator(payload.indicator_type, payload.value)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

        fingerprint = indicator_fingerprint(payload.indicator_type, normalized)
        signals, intel_status, categories, observed_at_ms, ttl_ms = await _read_reputation(
            request.app,
            payload.indicator_type,
            fingerprint,
        )
        return {
            "indicator_type": payload.indicator_type,
            "indicator_fingerprint": fingerprint,
            "risk_state": _confidence_tier(signals),
            "signals": signals,
            "categories": categories,
            "community_intelligence": intel_status,
            "reputation_observed_at_ms": observed_at_ms,
            "reputation_ttl_ms": ttl_ms,
            "enforcement_allowed": False,
            "warning": (
                "La réputation communautaire est un signal technique. "
                "Elle ne prouve ni l'identité d'une personne ni une fraude."
            ),
        }

    @router.post("/lookup-fingerprint")
    async def lookup_indicator_fingerprint(
        payload: IndicatorFingerprintLookup,
        request: Request,
    ) -> dict[str, Any]:
        await _rate_limit(
            request,
            endpoint="intel-fingerprint-lookup",
            per_client_env="INTEL_FINGERPRINT_LOOKUP_RATE_LIMIT_PER_MINUTE",
            per_client_default=60,
        )
        fingerprint = payload.indicator_fingerprint.lower()
        signals, intel_status, categories, observed_at_ms, ttl_ms = await _read_reputation(
            request.app,
            payload.indicator_type,
            fingerprint,
        )
        return {
            "indicator_type": payload.indicator_type,
            "indicator_fingerprint": fingerprint,
            "risk_state": _confidence_tier(signals),
            "signals": signals,
            "categories": categories,
            "community_intelligence": intel_status,
            "reputation_observed_at_ms": observed_at_ms,
            "reputation_ttl_ms": ttl_ms,
            "enforcement_allowed": False,
            "warning": (
                "Le fingerprint est un identifiant technique pseudonymisé. "
                "La réputation ne prouve ni l'identité d'une personne ni une fraude."
            ),
        }

    @router.post("/report-public", status_code=status.HTTP_202_ACCEPTED)
    async def report_indicator_public(
        payload: IndicatorReport,
        request: Request,
    ) -> dict[str, str]:
        await _rate_limit(
            request,
            endpoint="intel-report-public",
            per_client_env="INTEL_PUBLIC_REPORT_RATE_LIMIT_PER_MINUTE",
            per_client_default=5,
        )
        secret = _public_report_secret()
        if not secret:
            raise HTTPException(status_code=503, detail="Signalement intelligence non configuré")
        try:
            normalized = normalize_indicator(payload.indicator_type, payload.value)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        fingerprint = indicator_fingerprint(payload.indicator_type, normalized)
        client = getattr(request.app.state, "redis", None)
        if not fingerprint or client is None:
            raise HTTPException(status_code=503, detail="File de modération intelligence indisponible")

        nonce_hash = hmac.new(
            secret.encode(), payload.client_nonce.encode(), hashlib.sha256
        ).hexdigest()
        reporter_hash = _reporter_hash(
            request,
            indicator_type=payload.indicator_type,
            fingerprint=fingerprint,
            category=payload.category,
            secret=secret,
        )
        try:
            accepted = await _store_pending_report(
                client,
                indicator_type=payload.indicator_type,
                nonce_hash=nonce_hash,
                reporter_hash=reporter_hash,
                fingerprint=fingerprint,
                category=payload.category,
                now=int(time.time()),
            )
        except (RedisError, TimeoutError, ValueError) as exc:
            raise HTTPException(status_code=503, detail="File de modération intelligence indisponible") from exc

        return {
            "status": "pending" if accepted else "duplicate",
            "indicator_fingerprint": fingerprint,
            "effect_on_reputation": "none_pending_moderation",
        }

    @router.post("/report", status_code=status.HTTP_202_ACCEPTED)
    async def report_indicator_trusted(
        payload: IndicatorReport,
        request: Request,
        x_report_key: Annotated[str | None, Header()] = None,
    ) -> dict[str, str]:
        await _rate_limit(
            request,
            endpoint="intel-report",
            per_client_env="INTEL_REPORT_RATE_LIMIT_PER_MINUTE",
            per_client_default=20,
        )
        expected = os.getenv("REPORT_API_KEY")
        if not expected or not x_report_key or not hmac.compare_digest(expected, x_report_key):
            raise HTTPException(status_code=401, detail="Signalement intelligence non autorisé")
        try:
            normalized = normalize_indicator(payload.indicator_type, payload.value)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        fingerprint = indicator_fingerprint(payload.indicator_type, normalized)
        client = getattr(request.app.state, "redis", None)
        if not fingerprint or client is None:
            raise HTTPException(status_code=503, detail="Réputation intelligence indisponible")

        nonce_hash = hmac.new(
            expected.encode(), payload.client_nonce.encode(), hashlib.sha256
        ).hexdigest()
        reporter_hash = _reporter_hash(
            request,
            indicator_type=payload.indicator_type,
            fingerprint=fingerprint,
            category=payload.category,
            secret=expected,
        )
        try:
            accepted = await _store_trusted_report(
                client,
                indicator_type=payload.indicator_type,
                nonce_hash=nonce_hash,
                reporter_hash=reporter_hash,
                fingerprint=fingerprint,
                category=payload.category,
                now=int(time.time()),
            )
        except (RedisError, TimeoutError, ValueError) as exc:
            raise HTTPException(status_code=503, detail="Réputation intelligence indisponible") from exc

        return {
            "status": "accepted" if accepted else "duplicate",
            "effect_on_reputation": "one_observation_if_accepted",
        }

    @router.post("/relationships/report", status_code=status.HTTP_202_ACCEPTED)
    async def report_relationship_trusted(
        payload: RelationshipReport,
        request: Request,
        x_report_key: Annotated[str | None, Header()] = None,
    ) -> dict[str, Any]:
        await _rate_limit(
            request,
            endpoint="intel-relationship-report",
            per_client_env="INTEL_RELATIONSHIP_RATE_LIMIT_PER_MINUTE",
            per_client_default=30,
        )
        expected = os.getenv("REPORT_API_KEY")
        if not expected or not x_report_key or not hmac.compare_digest(expected, x_report_key):
            raise HTTPException(status_code=401, detail="Relation intelligence non autorisée")

        try:
            source_normalized = normalize_indicator(
                payload.source.indicator_type,
                payload.source.value,
            )
            target_normalized = normalize_indicator(
                payload.target.indicator_type,
                payload.target.value,
            )
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

        source_fingerprint = indicator_fingerprint(
            payload.source.indicator_type,
            source_normalized,
        )
        target_fingerprint = indicator_fingerprint(
            payload.target.indicator_type,
            target_normalized,
        )
        if not source_fingerprint or not target_fingerprint:
            raise HTTPException(status_code=503, detail="Threat Graph non configuré")
        if (
            payload.source.indicator_type == payload.target.indicator_type
            and source_fingerprint == target_fingerprint
        ):
            raise HTTPException(status_code=422, detail="self_relationship_forbidden")

        edge_id = _relationship_edge_id(
            payload.source.indicator_type,
            source_fingerprint,
            payload.target.indicator_type,
            target_fingerprint,
            payload.relationship_type,
        )
        client = getattr(request.app.state, "redis", None)
        if not edge_id or client is None:
            raise HTTPException(status_code=503, detail="Threat Graph indisponible")

        nonce_hash = hmac.new(
            expected.encode(),
            payload.client_nonce.encode(),
            hashlib.sha256,
        ).hexdigest()
        reporter_hash = _relationship_reporter_hash(
            request,
            edge_id=edge_id,
            secret=expected,
        )
        try:
            accepted = await _store_trusted_relationship(
                client,
                indicator_source_type=payload.source.indicator_type,
                source_fingerprint=source_fingerprint,
                indicator_target_type=payload.target.indicator_type,
                target_fingerprint=target_fingerprint,
                relationship_type=payload.relationship_type,
                evidence_strength=payload.evidence_strength,
                nonce_hash=nonce_hash,
                reporter_hash=reporter_hash,
                edge_id=edge_id,
                now=int(time.time()),
            )
        except (RedisError, TimeoutError, ValueError) as exc:
            raise HTTPException(status_code=503, detail="Threat Graph indisponible") from exc

        return {
            "status": "accepted" if accepted else "duplicate",
            "edge_id": edge_id,
            "storage": "fingerprints_only",
            "campaign_effect": "candidate_only",
            "enforcement_allowed": False,
        }

    @router.post("/graph/lookup")
    async def graph_lookup(
        payload: GraphLookup,
        request: Request,
        x_report_key: Annotated[str | None, Header()] = None,
    ) -> dict[str, Any]:
        await _rate_limit(
            request,
            endpoint="intel-graph-lookup",
            per_client_env="INTEL_GRAPH_LOOKUP_RATE_LIMIT_PER_MINUTE",
            per_client_default=30,
        )
        expected = os.getenv("REPORT_API_KEY")
        if not expected or not x_report_key or not hmac.compare_digest(expected, x_report_key):
            raise HTTPException(status_code=401, detail="Threat Graph non autorisé")

        try:
            normalized = normalize_indicator(payload.indicator_type, payload.value)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

        fingerprint = indicator_fingerprint(payload.indicator_type, normalized)
        if not fingerprint:
            raise HTTPException(status_code=503, detail="Threat Graph non configuré")

        graph_status, neighbors, candidate = await _read_graph(
            request.app,
            indicator_type=payload.indicator_type,
            fingerprint=fingerprint,
            max_neighbors=payload.max_neighbors,
        )
        return {
            "indicator_type": payload.indicator_type,
            "indicator_fingerprint": fingerprint,
            "graph_intelligence": graph_status,
            "neighbors": neighbors,
            "candidate_cluster_fingerprint": candidate,
            "candidate_cluster_state": (
                "UNAVAILABLE"
                if graph_status != "available"
                else ("CANDIDATE" if candidate else "NONE")
            ),
            "enforcement_allowed": False,
            "warning": (
                "Une relation de graphe ou un cluster candidat n'est pas une attribution "
                "d'identité et ne confirme pas une campagne malveillante."
            ),
        }

    @router.get("/moderation/pending")
    async def list_pending(
        request: Request,
        x_moderation_key: Annotated[str | None, Header()] = None,
    ) -> dict[str, Any]:
        expected = os.getenv("MODERATION_API_KEY")
        if not expected or not x_moderation_key or not hmac.compare_digest(
            expected, x_moderation_key
        ):
            raise HTTPException(status_code=401, detail="Modération intelligence non autorisée")
        client = getattr(request.app.state, "redis", None)
        if client is None:
            raise HTTPException(status_code=503, detail="File de modération intelligence indisponible")
        try:
            members = await client.zrevrange("intel:community:moderation:v1", 0, 99)
            items: list[dict[str, Any]] = []
            for member in members:
                try:
                    raw_type, fingerprint = member.split(":", 1)
                    indicator_type = IndicatorType(raw_type)
                except (ValueError, AttributeError):
                    continue
                data = await client.hgetall(
                    f"intel:community:pending:v1:{indicator_type.value}:{fingerprint}"
                )
                if not data:
                    continue
                items.append(
                    {
                        "indicator_type": indicator_type,
                        "indicator_fingerprint": fingerprint,
                        "signals": int(data.get("signals", 0) or 0),
                        "first_seen": int(data.get("first_seen", 0) or 0),
                        "last_seen": int(data.get("last_seen", 0) or 0),
                        "categories": {
                            category.value: int(
                                data.get(f"category:{category.value}", 0) or 0
                            )
                            for category in IntelReportCategory
                            if int(data.get(f"category:{category.value}", 0) or 0) > 0
                        },
                    }
                )
        except (RedisError, TimeoutError, ValueError) as exc:
            raise HTTPException(status_code=503, detail="File de modération intelligence indisponible") from exc
        return {"items": items, "count": len(items)}

    @router.post("/moderation/decision")
    async def moderate(
        payload: IndicatorModerationAction,
        request: Request,
        x_moderation_key: Annotated[str | None, Header()] = None,
    ) -> dict[str, str]:
        await _rate_limit(
            request,
            endpoint="intel-moderation-decision",
            per_client_env="INTEL_MODERATION_RATE_LIMIT_PER_MINUTE",
            per_client_default=30,
        )
        expected = os.getenv("MODERATION_API_KEY")
        if not expected or not x_moderation_key or not hmac.compare_digest(
            expected, x_moderation_key
        ):
            raise HTTPException(status_code=401, detail="Modération intelligence non autorisée")
        client = getattr(request.app.state, "redis", None)
        if client is None:
            raise HTTPException(status_code=503, detail="File de modération intelligence indisponible")
        try:
            result = await _moderate_pending(
                client,
                indicator_type=payload.indicator_type,
                fingerprint=payload.indicator_fingerprint.lower(),
                category=payload.category,
                decision=payload.decision,
                now=int(time.time()),
            )
        except (RedisError, TimeoutError, ValueError) as exc:
            raise HTTPException(status_code=503, detail="File de modération intelligence indisponible") from exc
        if result == "not_found":
            raise HTTPException(status_code=404, detail="Signalement intelligence pending introuvable")
        return {
            "status": result,
            "effect_on_reputation": "incremented_once" if result == "approved" else "none",
        }

    return router
