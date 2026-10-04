import test from 'node:test';
import assert from 'node:assert/strict';
import { validateApiOriginSources } from './check-sentinel-api-origin-contract.js';

test('canonical origin file may contain the legacy compatibility field', () => {
  const errors = validateApiOriginSources([
    {
      name: 'SentinelApiOrigin.kt',
      content: 'val baseUrl get() = BuildConfig.WANGIRI_API_BASE_URL'
    },
    {
      name: 'CommunityReportClient.kt',
      content: 'private val baseUrl = SentinelApiOrigin.baseUrl'
    }
  ]);

  assert.deepEqual(errors, []);
});

test('business client cannot read legacy API BuildConfig directly', () => {
  const errors = validateApiOriginSources([
    {
      name: 'SentinelApiOrigin.kt',
      content: 'val baseUrl get() = BuildConfig.WANGIRI_API_BASE_URL'
    },
    {
      name: 'CallerReputationClient.kt',
      content: 'val url = BuildConfig.WANGIRI_API_BASE_URL'
    }
  ]);

  assert.ok(errors.some((error) => error.includes('direct legacy API BuildConfig access')));
});

test('provider-specific hostname cannot be scattered into Android security source', () => {
  const errors = validateApiOriginSources([
    {
      name: 'SentinelApiOrigin.kt',
      content: 'val baseUrl get() = BuildConfig.WANGIRI_API_BASE_URL'
    },
    {
      name: 'CollectiveDefenseClient.kt',
      content: 'const val HOST = "service.onrender.com"'
    }
  ]);

  assert.ok(errors.some((error) => error.includes('provider-specific API hostname marker')));
});

test('missing canonical origin fails closed', () => {
  const errors = validateApiOriginSources([
    {
      name: 'CallerReputationClient.kt',
      content: 'private val baseUrl = SentinelApiOrigin.baseUrl'
    }
  ]);

  assert.ok(errors.some((error) => error.includes('canonical API origin source is missing')));
});
