import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const voice = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/VoiceStudioActivity.kt',
  'utf8'
);

test('voice studio microphone prompt contains launch failures', () => {
  assert.match(
    voice,
    /fun requestMicrophonePermission\(\)[\s\S]*?microphoneLauncher\.launch\(Manifest\.permission\.RECORD_AUDIO\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'microphone permission prompts must contain OEM/framework failures'
  );
  assert.match(
    voice,
    /requestMicrophonePermission\(\)/,
    'recording must use the guarded microphone prompt'
  );
  assert.match(
    voice,
    /status = "Android n’a pas pu ouvrir la demande de microphone/,
    'microphone prompt failures must remain visible'
  );
});
