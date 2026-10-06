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
  timeout --signal=INT --kill-after=5s 30s adb logcat -d -v time > "$partial" 2> "$errors" || status=$?
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
  if [[ "$transient" != 1 || "$attempt" == 3 ]]; then exit 1; fi
  timeout 15s adb wait-for-device
done
exit 1
