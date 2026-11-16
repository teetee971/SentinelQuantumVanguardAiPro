import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const WORKFLOWS = Object.freeze({
  'arcep-numbering-refresh.yml': 'automation/arcep-numbering-refresh',
  'ofcom-numbering-refresh.yml': 'automation/ofcom-numbering-refresh',
  'acm-numbering-refresh.yml': 'automation/acm-numbering-refresh',
  'ctu-numbering-refresh.yml': 'automation/ctu-numbering-refresh',
  'cisa-kev-refresh.yml': 'automation/cisa-kev-refresh',
  'scheduled-vulnerability-watch.yml': 'automation/vulnerability-watch-refresh'
});

for (const [filename, stableBranch] of Object.entries(WORKFLOWS)) {
  test(`${filename} reuses one stable refresh PR branch`, () => {
    const source = fs.readFileSync(path.join('.github', 'workflows', filename), 'utf8');
    assert.ok(source.includes(`BRANCH: ${stableBranch}`));
    assert.equal(/BRANCH:.*github\.run_id/.test(source), false);
    assert.ok(source.includes('git ls-remote --exit-code --heads origin "$BRANCH"'));
    assert.ok(source.includes('git fetch origin "$BRANCH:refs/remotes/origin/$BRANCH"'));
    assert.ok(source.includes('git checkout -B "$BRANCH"'));
    assert.ok(source.includes('git push --force-with-lease origin "$BRANCH"'));
    assert.ok(source.includes('gh pr list --base main --head "$BRANCH" --state open'));
    assert.ok(source.includes('if [ -z "$existing_pr" ]; then'));
    assert.ok(source.includes('Updated existing refresh PR #$existing_pr.'));
  });
}
