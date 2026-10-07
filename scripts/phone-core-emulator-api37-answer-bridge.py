#!/usr/bin/env python3
"""Synchronize Android 17 emulator GSM transport inside a real Telecom answer request.

This helper is host-only qualification infrastructure. It keeps the emulator console
connection open before the UI tap, observes one causal Telecom answer transaction, then
synchronizes the synthetic modem only when REQUEST_ACCEPT belongs to that same transaction.
The later ANSWERED and ACTIVE evidence must belong to the same Telecom call id. Android 17
emulation can transiently map that same accepted call from ANSWERED to ON_HOLD; in that one
observed state the helper permits one additional emulator-console accept and still requires
the same call to reach ACTIVE. The shell harness separately requires Sentinel's private
INCALL_ACTIVE evidence.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import selectors
import socket
import subprocess
import sys
import time

ANSWER_REQUEST_MARKER = (
    "CallSequencingController: answerCall: Beginning call sequencing transaction for answering incoming call."
)
REQUEST_ACCEPT_RE = re.compile(r"RecordEntry (TC@\d+): REQUEST_ACCEPT\b")
ANSWERED_RE = re.compile(
    r"CallsManager: setCallState RINGING(?:\(RINGING\))? -> ANSWERED, call: \[Call id=(TC@\d+),"
)
HELD_RE = re.compile(
    r"CallsManager: setCallState ANSWERED(?:\(ANSWERED\))? -> ON_HOLD, call: \[Call id=(TC@\d+),"
)
ACTIVE_RE = re.compile(
    r"CallsManager: setCallState (?:ANSWERED(?:\(ANSWERED\))?|ON_HOLD(?:\(ON_HOLD\))?) -> ACTIVE, call: \[Call id=(TC@\d+),"
)
TRANSACTION_TOKEN_RE = re.compile(r"@([A-Za-z0-9]+)[^A-Za-z0-9]*$")
SERIAL_RE = re.compile(r"^emulator-(\d+)$")


def append_line(path: Path, line: str) -> None:
    with path.open("a", encoding="utf-8") as handle:
        handle.write(line.rstrip("\r\n") + "\n")


def transaction_token(line: str) -> str | None:
    match = TRANSACTION_TOKEN_RE.search(line.rstrip())
    return match.group(1) if match else None


def recv_until(sock: socket.socket, needles: tuple[str, ...], timeout_s: float) -> str:
    deadline = time.monotonic() + timeout_s
    received = ""
    while time.monotonic() < deadline:
        sock.settimeout(max(0.01, deadline - time.monotonic()))
        try:
            chunk = sock.recv(4096)
        except socket.timeout:
            continue
        if not chunk:
            break
        received += chunk.decode("utf-8", errors="replace")
        if any(needle in received for needle in needles):
            break
    return received


def authenticate_console(sock: socket.socket, evidence: Path, token_file: Path) -> None:
    greeting = recv_until(sock, ("OK", "KO"), 1.5)
    if greeting:
        append_line(evidence, "console_greeting=" + greeting.replace("\r", " ").replace("\n", " | ").strip())
    if "KO" in greeting:
        raise RuntimeError("emulator console rejected connection")
    if "Authentication required" not in greeting:
        if "OK" not in greeting:
            raise RuntimeError("emulator console greeting was unreadable")
        return

    try:
        token = token_file.read_text(encoding="utf-8").strip()
    except OSError as exc:
        raise RuntimeError(f"emulator console auth token unavailable: {exc}") from exc
    if not token:
        raise RuntimeError("emulator console auth token is empty")

    sock.sendall(f"auth {token}\n".encode("utf-8"))
    response = recv_until(sock, ("OK", "KO"), 1.5)
    append_line(evidence, "console_auth_response=" + response.replace("\r", " ").replace("\n", " | ").strip())
    if "OK" not in response or "KO" in response:
        raise RuntimeError("emulator console authentication failed")


def discover_console_port() -> int:
    result = subprocess.run(
        ["adb", "get-serialno"],
        text=True,
        capture_output=True,
        timeout=3,
        check=False,
    )
    serial = result.stdout.strip().replace("\r", "")
    if result.returncode != 0:
        raise RuntimeError(f"adb get-serialno failed with status {result.returncode}")
    match = SERIAL_RE.fullmatch(serial)
    if not match:
        raise RuntimeError(f"refusing non-emulator adb target: {serial or '<empty>'}")
    return int(match.group(1))


def run_bridge(number: str, evidence: Path, marker_file: Path, timeout_s: float) -> None:
    evidence.parent.mkdir(parents=True, exist_ok=True)
    evidence.write_text("", encoding="utf-8")
    marker_file.write_text("", encoding="utf-8")

    port = discover_console_port()
    host = "127.0.0.1"
    token_path = Path(
        os.environ.get(
            "SENTINEL_EMULATOR_CONSOLE_TOKEN_FILE",
            str(Path.home() / ".emulator_console_auth_token"),
        )
    )
    append_line(evidence, f"console_target={host}:{port}")

    with socket.create_connection((host, port), timeout=2.0) as console:
        authenticate_console(console, evidence, token_path)
        logcat = subprocess.Popen(
            ["adb", "logcat", "-v", "brief", "-T", "1"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=1,
        )
        assert logcat.stdout is not None
        selector = selectors.DefaultSelector()
        selector.register(logcat.stdout, selectors.EVENT_READ)
        deadline = time.monotonic() + timeout_s
        answer_transaction: str | None = None
        call_id: str | None = None
        accept_requested = False
        synchronized = False
        answered = False
        recovery_attempted = False
        active = False
        try:
            while time.monotonic() < deadline:
                events = selector.select(timeout=min(0.1, max(0.0, deadline - time.monotonic())))
                if not events:
                    if logcat.poll() is not None:
                        break
                    continue
                for key, _ in events:
                    line = key.fileobj.readline()
                    if not line:
                        continue

                    if answer_transaction is None and ANSWER_REQUEST_MARKER in line:
                        observed_transaction = transaction_token(line)
                        append_line(evidence, line)
                        if observed_transaction is None:
                            append_line(evidence, "answer_request_missing_transaction_token=1")
                            continue
                        answer_transaction = observed_transaction
                        marker_file.write_text(line, encoding="utf-8")
                        continue

                    if answer_transaction is not None and not accept_requested:
                        accept_match = REQUEST_ACCEPT_RE.search(line)
                        if accept_match and transaction_token(line) == answer_transaction:
                            call_id = accept_match.group(1)
                            append_line(evidence, line)
                            with marker_file.open("a", encoding="utf-8") as marker_handle:
                                marker_handle.write(line)
                            accept_requested = True
                            console.sendall(f"gsm accept {number}\n".encode("utf-8"))
                            response = recv_until(console, ("OK", "KO"), 1.0)
                            append_line(
                                evidence,
                                "console_gsm_accept_response="
                                + response.replace("\r", " ").replace("\n", " | ").strip(),
                            )
                            if "OK" not in response or "KO" in response:
                                raise RuntimeError("emulator console gsm accept failed")
                            append_line(
                                evidence,
                                f"transport_sync=emulator_console_gsm_accept api=37 number={number} call_id={call_id} transaction={answer_transaction}",
                            )
                            synchronized = True
                            continue

                    if synchronized and not answered and call_id is not None:
                        answered_match = ANSWERED_RE.search(line)
                        if (
                            answered_match
                            and answered_match.group(1) == call_id
                            and transaction_token(line) == answer_transaction
                        ):
                            append_line(evidence, line)
                            answered = True
                            continue

                    if synchronized and answered and not active and call_id is not None:
                        held_match = HELD_RE.search(line)
                        if held_match and held_match.group(1) == call_id and not recovery_attempted:
                            append_line(evidence, line)
                            console.sendall(f"gsm accept {number}\n".encode("utf-8"))
                            response = recv_until(console, ("OK", "KO"), 1.0)
                            append_line(
                                evidence,
                                "console_gsm_accept_recovery_response="
                                + response.replace("\r", " ").replace("\n", " | ").strip(),
                            )
                            if "OK" not in response or "KO" in response:
                                raise RuntimeError("emulator console gsm accept recovery failed")
                            recovery_attempted = True
                            append_line(
                                evidence,
                                f"transport_recovery=emulator_console_gsm_accept_from_hold api=37 number={number} call_id={call_id}",
                            )
                            continue

                        active_match = ACTIVE_RE.search(line)
                        if active_match and active_match.group(1) == call_id:
                            append_line(evidence, line)
                            active = True

                    if answer_transaction and accept_requested and synchronized and answered and active:
                        return

            missing = []
            if answer_transaction is None:
                missing.append("answer_request")
            if not accept_requested:
                missing.append("request_accept")
            if not synchronized:
                missing.append("transport_sync")
            if not answered:
                missing.append("answered_same_call")
            if not active:
                missing.append("active_same_call")
            raise RuntimeError("missing Telecom bridge evidence: " + ",".join(missing))
        finally:
            selector.close()
            if logcat.poll() is None:
                logcat.terminate()
                try:
                    logcat.wait(timeout=1)
                except subprocess.TimeoutExpired:
                    logcat.kill()
                    logcat.wait(timeout=1)
            if logcat.stderr is not None:
                stderr = logcat.stderr.read().strip()
                if stderr:
                    append_line(evidence, "logcat_stderr=" + stderr.replace("\n", " | "))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--number", required=True)
    parser.add_argument("--evidence", required=True)
    parser.add_argument("--marker-file", required=True)
    parser.add_argument("--timeout", type=float, default=5.0)
    args = parser.parse_args()

    evidence = Path(args.evidence)
    marker_file = Path(args.marker_file)
    try:
        run_bridge(args.number, evidence, marker_file, args.timeout)
        return 0
    except Exception as exc:
        evidence.parent.mkdir(parents=True, exist_ok=True)
        append_line(evidence, f"bridge_failure={type(exc).__name__}:{exc}")
        print(f"API 37 incoming-answer transport bridge failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())