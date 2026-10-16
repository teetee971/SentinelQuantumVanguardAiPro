#!/usr/bin/env python3
"""Correlate Android 16/17 emulator transport with one real Telecom answer request.

This helper is host-only qualification infrastructure. It observes one causal Telecom answer
transaction and requires REQUEST_ACCEPT and ANSWERED for that exact Telecom call.

Android 16/API 36 needs an explicit emulator-console `gsm accept` after the causal
REQUEST_ACCEPT and must then reach ACTIVE on the same Telecom call.

Android 17/API 37 never receives a host modem answer. The helper observes Telecom only. A genuine
same-call ANSWERED -> ACTIVE transition is accepted. Current API 37 emulator images can instead
move the causal call from ANSWERED to DISCONNECTED/INCOMING_MISSED while Android reports that the
answer transaction failed. That exact emulator-only signature is recorded as a qualification
limitation so later Phone Core probes can continue; it is never promoted to ACTIVE or PASS.

Neither the API 36 synthetic transport synchronization nor the API 37 limitation is a
physical-device claim. Full incoming-call proof still requires Sentinel's private INCALL_ACTIVE
evidence on lanes where the causal call reaches ACTIVE.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import queue
import re
import socket
import subprocess
import sys
import threading
import time

ANSWER_REQUEST_MARKER = (
    "CallSequencingController: answerCall: Beginning call sequencing transaction for answering incoming call."
)
REQUEST_ACCEPT_RE = re.compile(r"RecordEntry (TC@\d+): REQUEST_ACCEPT\b")
ANSWERED_RE = re.compile(
    r"CallsManager: setCallState RINGING(?:\(RINGING\))? -> ANSWERED, call: "
    r"\[Call id=(TC@\d+),.*?handle=(tel:[^,]+),"
)
HELD_RE = re.compile(
    r"CallsManager: setCallState ANSWERED(?:\(ANSWERED\))? -> ON_HOLD, call: \[Call id=(TC@\d+),"
)
ACTIVE_RE = re.compile(
    r"CallsManager: setCallState (?:ANSWERED(?:\(ANSWERED\))?|ON_HOLD(?:\(ON_HOLD\))?) -> ACTIVE, "
    r"call: \[Call id=(TC@\d+),"
)
DISCONNECTED_RE = re.compile(
    r"CallsManager: setCallState (?:ANSWERED(?:\(ANSWERED\))?|ON_HOLD(?:\(ON_HOLD\))?) -> DISCONNECTED, "
    r"call: \[Call id=(TC@\d+),"
)
INCOMING_MISSED_RE = re.compile(
    r"Event: RecordEntry (TC@\d+): SET_DISCONNECTED,.*Reason: \(INCOMING_MISSED\)"
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


def send_console_accept(
    console: socket.socket,
    number: str,
    evidence: Path,
    *,
    response_key: str,
    failure_message: str,
) -> None:
    console.sendall(f"gsm accept {number}\n".encode("utf-8"))
    response = recv_until(console, ("OK", "KO"), 1.0)
    append_line(
        evidence,
        response_key + "=" + response.replace("\r", " ").replace("\n", " | ").strip(),
    )
    if "OK" not in response or "KO" in response:
        raise RuntimeError(failure_message)


def run_bridge(
    api: int,
    number: str,
    evidence: Path,
    marker_file: Path,
    ready_file: Path,
    timeout_s: float,
) -> None:
    evidence.parent.mkdir(parents=True, exist_ok=True)
    evidence.write_text("", encoding="utf-8")
    marker_file.write_text("", encoding="utf-8")
    ready_file.write_text("", encoding="utf-8")

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
        line_queue: queue.Queue[str | None] = queue.Queue()
        reader_started = threading.Event()

        def pump_logcat() -> None:
            reader_started.set()
            try:
                for line in logcat.stdout:
                    line_queue.put(line)
            finally:
                line_queue.put(None)

        reader = threading.Thread(
            target=pump_logcat,
            name="sentinel-phone-core-logcat-reader",
            daemon=True,
        )
        reader.start()
        if not reader_started.wait(timeout=min(1.0, max(0.1, timeout_s))):
            raise RuntimeError("logcat reader did not become ready")
        if logcat.poll() is not None and line_queue.empty():
            raise RuntimeError("logcat exited before bridge readiness")
        ready_file.write_text("bridge_ready=1\n", encoding="utf-8")

        deadline = time.monotonic() + timeout_s
        answer_transaction: str | None = None
        call_id: str | None = None
        accept_requested = False
        synchronized = False
        answered = False
        recovery_attempted = False
        active = False
        causal_disconnected = False
        try:
            while time.monotonic() < deadline:
                remaining = max(0.0, deadline - time.monotonic())
                try:
                    line = line_queue.get(timeout=min(0.1, remaining))
                except queue.Empty:
                    if logcat.poll() is not None and line_queue.empty():
                        break
                    continue
                if line is None:
                    break

                if answer_transaction is None and ANSWER_REQUEST_MARKER in line:
                    observed_transaction = transaction_token(line)
                    append_line(evidence, line)
                    if observed_transaction is None:
                        append_line(evidence, "answer_request_missing_transaction_token=1")
                        continue
                    answer_transaction = observed_transaction
                    deadline = time.monotonic() + timeout_s
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

                        if api == 36:
                            send_console_accept(
                                console,
                                number,
                                evidence,
                                response_key="console_gsm_accept_response",
                                failure_message="emulator console gsm accept failed",
                            )
                            synchronized = True
                            append_line(
                                evidence,
                                f"transport_sync=emulator_console_gsm_accept api={api} number={number} call_id={call_id} transaction={answer_transaction}",
                            )
                        else:
                            append_line(
                                evidence,
                                f"transport_mode=telecom_observation_only api={api} number={number} call_id={call_id} transaction={answer_transaction}",
                            )
                        continue

                if accept_requested and not answered and call_id is not None:
                    answered_match = ANSWERED_RE.search(line)
                    if (
                        answered_match
                        and answered_match.group(1) == call_id
                        and transaction_token(line) == answer_transaction
                    ):
                        append_line(evidence, line)
                        answered = True
                        if api == 37:
                            synchronized = True
                            append_line(
                                evidence,
                                f"transport_observation=telecom_answered api={api} call_id={call_id} transaction={answer_transaction}",
                            )
                        continue

                if synchronized and answered and not active and call_id is not None:
                    active_match = ACTIVE_RE.search(line)
                    if active_match and active_match.group(1) == call_id:
                        append_line(evidence, line)
                        active = True
                        continue

                    disconnected_match = DISCONNECTED_RE.search(line)
                    if disconnected_match and disconnected_match.group(1) == call_id:
                        append_line(evidence, line)
                        if api == 36:
                            raise RuntimeError(
                                f"causal Telecom call {call_id} disconnected before ACTIVE"
                            )
                        causal_disconnected = True
                        continue

                    if api == 37 and causal_disconnected:
                        missed_match = INCOMING_MISSED_RE.search(line)
                        if missed_match and missed_match.group(1) == call_id:
                            append_line(evidence, line)
                            append_line(
                                evidence,
                                "emulator_limit=api37_incoming_answer_transaction_failed "
                                f"call_id={call_id} transaction={answer_transaction}",
                            )
                            return

                    held_match = HELD_RE.search(line)
                    if (
                        api == 36
                        and held_match
                        and held_match.group(1) == call_id
                        and not recovery_attempted
                    ):
                        append_line(evidence, line)
                        send_console_accept(
                            console,
                            number,
                            evidence,
                            response_key="console_gsm_accept_recovery_response",
                            failure_message="emulator console gsm accept recovery failed",
                        )
                        deadline = time.monotonic() + timeout_s
                        recovery_attempted = True
                        append_line(
                            evidence,
                            f"transport_recovery=emulator_console_gsm_accept_from_hold api={api} number={number} call_id={call_id}",
                        )
                        continue

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
                missing.append("active_correlated_call")
            raise RuntimeError("missing Telecom bridge evidence: " + ",".join(missing))
        finally:
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
    parser.add_argument("--api", type=int, choices=(36, 37), required=True)
    parser.add_argument("--number", required=True)
    parser.add_argument("--evidence", required=True)
    parser.add_argument("--marker-file", required=True)
    parser.add_argument("--ready-file", required=True)
    parser.add_argument("--timeout", type=float, default=5.0)
    args = parser.parse_args()

    evidence = Path(args.evidence)
    marker_file = Path(args.marker_file)
    ready_file = Path(args.ready_file)
    try:
        run_bridge(args.api, args.number, evidence, marker_file, ready_file, args.timeout)
        return 0
    except Exception as exc:
        evidence.parent.mkdir(parents=True, exist_ok=True)
        append_line(evidence, f"bridge_failure={type(exc).__name__}:{exc}")
        print(f"API {args.api} incoming-answer transport bridge failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
