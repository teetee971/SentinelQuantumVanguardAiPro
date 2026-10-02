import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const WORKFLOWS = Object.freeze({
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


for (const target of ['arcep', 'ofcom', 'acm', 'ctu']) {
  test(`${target} publishes only validated numbering data autonomously`, () => {
    const source = fs.readFileSync(path.join('.github', 'workflows', `${target}-numbering-refresh.yml`), 'utf8');
    assert.ok(source.includes(`group: ${target}-numbering-refresh`));
    assert.ok(source.includes('ref: main'));
    assert.ok(source.includes('npm run test:phone-intelligence'));
    assert.ok(source.includes('npm run build'));
    assert.ok(source.includes(`node scripts/publish-numbering-refresh.js ${target}`));
    assert.equal(source.includes('gh pr create'), false);
  });
}

test('daily orchestration serializes countries and continues after a failed country', () => {
  const source = fs.readFileSync('.github/workflows/international-numbering-refresh.yml', 'utf8');
  assert.ok(source.includes("cron: '19 6 * * *'"));
  assert.ok(source.includes('group: numbering-autonomous-publication-main'));
  for (const target of ['arcep', 'ofcom', 'acm', 'ctu']) {
    assert.ok(source.includes(`uses: ./.github/workflows/${target}-numbering-refresh.yml`));
  }
  for (const previous of ['arcep', 'ofcom', 'acm']) assert.ok(source.includes(`needs: ${previous}`));
  assert.equal((source.match(/if: always\(\)/g) ?? []).length, 3);
});
