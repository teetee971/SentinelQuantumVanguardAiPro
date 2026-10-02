import test from 'node:test';
import assert from 'node:assert/strict';
import { inspectWorkflow } from './check-actions-permissions.js';

test('rejects write-all',()=>assert.equal(inspectWorkflow('permissions: write-all\n','x.yml').length,1));
test('rejects unapproved workflow write',()=>assert.equal(inspectWorkflow('permissions:\n  contents: write\n','x.yml').length,1));
test('rejects forbidden job-level OIDC write',()=>assert.equal(inspectWorkflow('permissions:\n  contents: read\njobs:\n  x:\n    permissions:\n      id-token: write\n','x.yml').length,1));
test('allows exact refresh scopes',()=>assert.deepEqual(inspectWorkflow('permissions:\n  contents: write\n  pull-requests: write\n','cisa-kev-refresh.yml'),[]));
test('allows exact Ofcom refresh scopes',()=>assert.deepEqual(inspectWorkflow('permissions:\n  contents: write\n  pull-requests: write\n','ofcom-numbering-refresh.yml'),[]));
test('allows exact ACM refresh scopes',()=>assert.deepEqual(inspectWorkflow('permissions:\n  contents: write\n  pull-requests: write\n','acm-numbering-refresh.yml'),[]));
test('allows exact CTU refresh scopes',()=>assert.deepEqual(inspectWorkflow('permissions:\n  contents: write\n  pull-requests: write\n','ctu-numbering-refresh.yml'),[]));
test('rejects privilege expansion in allowlisted workflow',()=>assert.equal(inspectWorkflow('permissions:\n  contents: write\n  pull-requests: write\n  packages: write\n','cisa-kev-refresh.yml').length,1));
test('allows CodeQL security event upload',()=>assert.deepEqual(inspectWorkflow('permissions:\n  contents: read\njobs:\n  x:\n    permissions:\n      actions: read\n      contents: read\n      security-events: write\n','codeql-analysis.yml'),[]));

test('rejects inline permission maps',()=>assert.equal(inspectWorkflow('permissions: { contents: write }\n','x.yml').length,1));

for (const filename of ['rtr-numbering-refresh.yml', 'international-numbering-watch.yml']) {
  test(`${filename} permits dataset publication but rejects additional write scopes`, () => {
    assert.deepEqual(inspectWorkflow('permissions:\n  contents: write\n  pull-requests: write\n', filename), []);
    for (const scope of ['packages', 'id-token', 'actions']) {
      assert.equal(inspectWorkflow(`permissions:\n  contents: write\n  ${scope}: write\n`, filename).length, 1);
    }
  });
}
