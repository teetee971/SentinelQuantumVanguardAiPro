#!/usr/bin/env python3
"""Synchronize Android 17 emulator GSM transport after a real Telecom answer request.

This helper is host-only qualification infrastructure. It keeps the emulator console
connection open before the UI tap, waits for Telecom's causal answer-transaction marker,
then sends the synthetic modem accept command without paying a second adb startup cost.
It still requires Telecom ANSWERED -> ACTIVE after synchronization; the shell harness
separately requires Sentinel's private INCALL_ACTIVE evidence.
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
ANSWERED_MARKER = "CallsManager: setCallState RINGING(RINGING) -> ANSWERED"
ACTIVE_MARKER = "CallsManager: setCallState ANSWERED(ANSWERED) -> ACTIVE"
SERIAL_RE = re.compile(r"^emulator-(\d+)$")


def append_line(path: Path, line: str) -> None:
    with path.open("a", encoding="utf-8") as handle:
        handle.write(line.rstrip("\r\n") + "\n")


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
    # Android's documented console banner ends with an initial OK even when authentication is
    # required. Consume that complete banner first; otherwise its stale OK could be mistaken for
    # the response to the later auth or gsm command.
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
    # The Android emulator console is a host-local qualification channel. Do not permit an
    # environment override here: a remote socket would violate the emulator-only trust boundary.
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
        synchronized = False
        answered = False
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
                    if not synchronized and ANSWER_REQUEST_MARKER in line:
                        marker_file.write_text(line, encoding="utf-8")
                        append_line(evidence, line)
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
                            f"transport_sync=emulator_console_gsm_accept api=37 number={number}",
                        )
                        synchronized = True
                    if ANSWERED_MARKER in line:
                        append_line(evidence, line)
                        answered = True
                    if ACTIVE_MARKER in line:
                        append_line(evidence, line)
                        active = True
                    if synchronized and answered and active:
                        return
            missing = []
            if not synchronized:
                missing.append("answer_request")
            if not answered:
                missing.append("answered")
            if not active:
                missing.append("active")
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
    except Exception as exc:  # qualification infrastructure must fail closed
        evidence.parent.mkdir(parents=True, exist_ok=True)
        append_line(evidence, f"bridge_failure={type(exc).__name__}:{exc}")
        print(f"API 37 incoming-answer transport bridge failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
