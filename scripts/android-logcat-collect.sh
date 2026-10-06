#!/usr/bin/env bash
# Retry only the read-only ADB collection. Never clear buffers or replay product actions.
set -euo pipefail
OUTPUT="${1:?Logcat output file required}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$(dirname -- "$OUTPUT")"
for attempt in 1 2 3; do
  partial="$OUTPUT.attempt-$attempt.txt"
  errors="$OUTPUT.attempt-$attempt.stderr"
  status=0
  capture_status=0
  pull_status=0
  if [[ "$attempt" == 1 ]]; then
    printf 'direct-adb-stream\n' > "$OUTPUT.attempt-$attempt.mode"
    timeout --signal=INT --kill-after=5s 30s adb logcat -d -v time > "$partial" 2> "$errors" || status=$?
  else
    # Booted shell probes do not prove that a large stdout stream will survive.
    # Snapshot the same unfiltered buffer on the device, then use ADB's file-sync
    # transport. Keep both exit codes; a failed capture cannot pass via an old file.
    printf 'device-file-sync\n' > "$OUTPUT.attempt-$attempt.mode"
    remote_log=/data/local/tmp/sentinel-qualification-logcat.txt
    timeout --signal=INT --kill-after=5s 30s adb shell "logcat -d -v time > $remote_log" \
      > "$partial.capture.stdout" 2> "$partial.capture.stderr" || capture_status=$?
    printf '%s\n' "$capture_status" > "$partial.capture.status"
    : > "$partial"
    timeout --signal=INT --kill-after=5s 30s adb pull "$remote_log" "$partial" \
      > "$partial.pull.stdout" 2> "$partial.pull.stderr" || pull_status=$?
    printf '%s\n' "$pull_status" > "$partial.pull.status"
    if [[ ! -f "$partial" ]]; then : > "$partial"; fi
    cat "$partial.capture.stderr" "$partial.pull.stderr" > "$errors"
    if [[ "$capture_status" != 0 ]]; then status=$capture_status; else status=$pull_status; fi
  fi
  printf '%s\n' "$status" > "$OUTPUT.attempt-$attempt.status"
  cp "$partial" "$OUTPUT"
  # Analyze even failed partial reads. A later read cannot erase observed product
  # crashes/ANRs; an empty partial read is infrastructure, never health evidence.
  analysis_status=0
  node "$SCRIPT_DIR/android-logcat-analysis.cjs" "$partial" "$partial.analysis.json" > "$partial.analysis.stdout" 2> "$partial.analysis.stderr" || analysis_status=$?
  if [[ "$analysis_status" -ne 0 ]] && [[ ! -s "$partial.analysis.json" ]]; then
    cat "$partial.analysis.stderr" >&2
    exit "$analysis_status"
  fi
  node - "$partial.analysis.json" <<'NODE'
const report = JSON.parse(require('node:fs').readFileSync(process.argv[2], 'utf8'));
if (report.failures.length) {
  console.error(`Observed product/unattributed failure in partial logcat: ${JSON.stringify(report.failures)}`);
  process.exit(1);
}
NODE
  if [[ "$status" -eq 0 ]] && [[ -s "$partial" ]]; then
    printf 'logcat collection succeeded on attempt %s\n' "$attempt"
    exit 0
  fi
  printf 'logcat collection attempt %s failed (exit %s); partial output and stderr retained\n' "$attempt" "$status" >&2
  # Non-transient command failures must remain failures. 255 is the observed
  # aborted ADB stream; 124/137 are watchdog expiry. No product action is retried.
  transient=0
  if [[ "$status" == 255 || "$status" == 124 || "$status" == 137 ]]; then transient=1; fi
  if [[ "$status" == 1 ]] && grep -Eq 'device offline|no devices/emulators found|device .* not found' "$errors"; then transient=1; fi
  # Emulator 37.2.x has also returned exit 1 with no diagnostic from `adb pull`
  # immediately after a successful on-device snapshot. The API-36 CI evidence from
  # 2026-10-06 captured exactly that signature after a prior aborted direct stream,
  # while a subsequent ADB read succeeded. Retry that transport-only condition once;
  # missing files, permission errors and any diagnostic failure still fail closed.
  if [[ "$attempt" -ge 2 && "$status" == 1 && "$capture_status" == 0 && "$pull_status" == 1 && ! -s "$errors" ]]; then
    transient=1
  fi
  if [[ "$transient" != 1 || "$attempt" == 3 ]]; then exit 1; fi
  timeout 15s adb wait-for-device
  # wait-for-device can return during a brief reconnect before the next stream
  # is usable. Require two actual shell round trips and boot readiness; backoff
  # applies only to the aborted infrastructure read, never to a product action.
  sleep "$attempt"
  stable=0
  ready=0
  readiness_deadline=$((SECONDS + 15))
  for observation in $(seq 1 20); do
    if [[ "$SECONDS" -ge "$readiness_deadline" ]]; then break; fi
    boot_status=0
    timeout 5s adb shell getprop sys.boot_completed > "$OUTPUT.readiness-$attempt.txt" 2> "$OUTPUT.readiness-$attempt.stderr" || boot_status=$?
    if [[ "$boot_status" == 0 ]] && [[ "$(tr -d '\r\n' < "$OUTPUT.readiness-$attempt.txt")" == 1 ]]; then
      stable=$((stable + 1))
      if [[ "$stable" -ge 2 ]]; then ready=1; break; fi
    else stable=0; fi
    sleep 0.5
  done
  if [[ "$ready" != 1 ]]; then
    echo 'ADB did not regain stable booted shell communication for logcat collection.' >&2
    exit 1
  fi
done
exit 1
