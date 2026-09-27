import test from 'node:test';
import assert from 'node:assert/strict';
import { inspectWorkflow } from './check-actions-permissions.js';

test('rejects write-all',()=>assert.equal(inspectWorkflow('permissions: write-all\n','x.yml').length,1));
test('rejects unapproved workflow write',()=>assert.equal(inspectWorkflow('permissions:\n  contents: write\n','x.yml').length,1));
test('rejects forbidden job-level OIDC write',()=>assert.equal(inspectWorkflow('permissions:\n  contents: read\njobs:\n  x:\n    permissions:\n      id-token: write\n','x.yml').length,1));
test('allows exact refresh scopes',()=>assert.deepEqual(inspectWorkflow('permissions:\n  contents: write\n  pull-requests: write\n','cisa-kev-refresh.yml'),[]));
test('rejects privilege expansion in allowlisted workflow',()=>assert.equal(inspectWorkflow('permissions:\n  contents: write\n  pull-requests: write\n  packages: write\n','cisa-kev-refresh.yml').length,1));
test('allows CodeQL security event upload',()=>assert.deepEqual(inspectWorkflow('permissions:\n  contents: read\njobs:\n  x:\n    permissions:\n      actions: read\n      contents: read\n      security-events: write\n','codeql-analysis.yml'),[]));

test('rejects inline permission maps',()=>assert.equal(inspectWorkflow('permissions: { contents: write }\n','x.yml').length,1));
