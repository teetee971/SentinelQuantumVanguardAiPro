import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const service = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt',
  'utf8'
);

test('slow local screening fails open before the Telecom response boundary', () => {
  assert.match(
    service,
    /const val MAX_PRE_RESPONSE_MS = 450L/,
    'screening must reserve margin below the 500 ms physical gate'
  );
  assert.match(
    service,
    /if \(responseBudgetExceeded\(startedAtElapsedMs\)\) \{[\s\S]*?respondOpen\(callDetails, startedAtElapsedMs\)[\s\S]*?return/s,
    'an over-budget rule evaluation must fail open before a block/silence response'
  );
  assert.match(
    service,
    /private fun responseBudgetExceeded\(startedAtElapsedMs: Long\): Boolean =\s*\(SystemClock\.elapsedRealtime\(\) - startedAtElapsedMs\)\.coerceAtLeast\(0L\) >= MAX_PRE_RESPONSE_MS/s,
    'the budget must use elapsed monotonic time and fail at the configured boundary'
  );
});

test('Telecom screening responses are contained at one fail-open boundary', () => {
  const responseCalls = [...service.matchAll(/\brespondToCall\(/g)];
  assert.equal(
    responseCalls.length,
    1,
    'all screening response paths must use one guarded responder'
  );
  assert.match(
    service,
    /private fun respondAndLog\([\s\S]*?runCatching \{\s*respondToCall\(/,
    'a Telecom response exception must not crash the screening service'
  );
});

test('screening latency evidence must distinguish a submitted response from an exception', () => {
  assert.match(
    service,
    /const val RESPONSE_SENT_MARKER = "CallScreeningService:response_sent="/,
    'the lifecycle evidence needs an explicit response-submission marker'
  );
  assert.match(
    service,
    /responseSent = runCatching\s*\{[\s\S]*?respondToCall\(/,
    'response submission must be captured as a success boolean'
  );
  assert.match(
    service,
    /Log\.i\(LIFECYCLE_TAG, RESPONSE_SENT_MARKER \+ responseSent\)/,
    'latency evidence must record whether Telecom accepted the response call'
  );
});
