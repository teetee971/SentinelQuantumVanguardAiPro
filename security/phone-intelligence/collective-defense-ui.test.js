import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const screen = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CollectiveDefenseScreen.kt',
  'utf8'
);

test('collective-defense status is presentation-only and never a dead action', () => {
  assert.doesNotMatch(
    screen,
    /status\?\.let \{[\s\S]*AssistChip\([\s\S]*onClick\s*=\s*\{\s*\}/,
    'a status message must not expose an empty interactive callback'
  );
  assert.match(
    screen,
    /status\?\.let \{[\s\S]*Surface\([\s\S]*Text\(it\)/,
    'the status must remain visible as a non-interactive surface'
  );
});

test('collective-defense customer heading is localized in French', () => {
  assert.match(screen, /Réseau de défense collective/);
  assert.doesNotMatch(screen, /Collective Defense Network/);
});

test('collective-defense watch mutations only refresh after durable persistence', () => {
  assert.match(
    screen,
    /val saved = if \(refreshed\.communityIntelligence == "available"\) \{[\s\S]*store\.upsert\(refreshed\)[\s\S]*\} else \{[\s\S]*true[\s\S]*\}/,
    'recheck must observe whether the refreshed reputation was persisted'
  );
  assert.match(
    screen,
    /if \(saved\) \{[\s\S]*refreshWatch\(\)[\s\S]*networkStatusText\(refreshed\.communityIntelligence\)[\s\S]*\} else \{[\s\S]*Impossible d’enregistrer le nouveau résultat/s,
    'a failed recheck must remain visible instead of being overwritten by a success status'
  );
  assert.match(
    screen,
    /if \(store\.remove\(item\.indicatorType, item\.fingerprint\)\) \{[\s\S]*refreshWatch\(\)[\s\S]*\} else \{[\s\S]*Impossible de supprimer l’indicateur/s,
    'removal must expose a failed durable mutation'
  );
});

