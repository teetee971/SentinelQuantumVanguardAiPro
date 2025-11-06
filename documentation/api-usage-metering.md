# Sentinel API - Usage Metering Endpoints

## Authentication

All API requests require authentication using a Bearer token:

```http
Authorization: Bearer YOUR_API_KEY
```

## Base URL

```
Development: http://localhost:3333
Production: https://api.sentinel-quantum-vanguard.com
```

---

## Endpoints

### 1. Get User Usage Statistics

Retrieve current usage statistics for a user.

**Endpoint:** `GET /api/usage/:userId`

**Parameters:**
- `userId` (path, required): User identifier

**Response Example:**

```json
{
  "userId": "user-123",
  "plan": "freemium",
  "period": {
    "start": "2025-11-01T00:00:00.000Z",
    "end": "2025-11-30T23:59:59.999Z"
  },
  "usage": {
    "events": 750,
    "api_calls": 320,
    "scans": 45,
    "stt_minutes": 8,
    "tts_minutes": 6
  },
  "quotas": {
    "events": 1000,
    "api_calls": 500,
    "scans": 50,
    "stt_minutes": 10,
    "tts_minutes": 10
  },
  "overage": {
    "events": 0,
    "api_calls": 0,
    "scans": 0,
    "stt_minutes": 0,
    "tts_minutes": 0
  }
}
```

**cURL Example:**

```bash
curl -X GET \
  'http://localhost:3333/api/usage/user-123' \
  -H 'Authorization: Bearer YOUR_API_KEY'
```

---

### 2. Track Usage Increment

Increment usage counter for a specific metric.

**Endpoint:** `POST /api/usage/track`

**Request Body:**

```json
{
  "userId": "user-123",
  "metric": "events",
  "amount": 1
}
```

**Valid Metrics:**
- `events` - Security events
- `api_calls` - API requests
- `scans` - Security scans
- `stt_minutes` - Speech-to-Text minutes
- `tts_minutes` - Text-to-Speech minutes

**Response Example:**

```json
{
  "userId": "user-123",
  "metric": "events",
  "amount": 1,
  "timestamp": "2025-11-06T16:30:00.000Z",
  "success": true
}
```

**cURL Example:**

```bash
curl -X POST \
  'http://localhost:3333/api/usage/track' \
  -H 'Authorization: Bearer YOUR_API_KEY' \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": "user-123",
    "metric": "events",
    "amount": 1
  }'
```

**JavaScript Example:**

```javascript
async function trackUsage(userId, metric, amount = 1) {
  const response = await fetch('http://localhost:3333/api/usage/track', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ userId, metric, amount }),
  });
  return response.json();
}

// Usage
await trackUsage('user-123', 'api_calls', 1);
```

---

### 3. Check Quota Status

Check if a user has exceeded or is approaching their quota for a specific metric.

**Endpoint:** `GET /api/usage/:userId/check/:metric`

**Parameters:**
- `userId` (path, required): User identifier
- `metric` (path, required): Metric to check

**Response Example:**

```json
{
  "userId": "user-123",
  "metric": "events",
  "plan": "freemium",
  "current": 750,
  "limit": 1000,
  "remaining": 250,
  "exceeded": false,
  "percentage": 75.0
}
```

**cURL Example:**

```bash
curl -X GET \
  'http://localhost:3333/api/usage/user-123/check/events' \
  -H 'Authorization: Bearer YOUR_API_KEY'
```

**Python Example:**

```python
import requests

def check_quota(user_id, metric, api_key):
    url = f'http://localhost:3333/api/usage/{user_id}/check/{metric}'
    headers = {'Authorization': f'Bearer {api_key}'}
    response = requests.get(url, headers=headers)
    return response.json()

# Usage
quota = check_quota('user-123', 'events', 'YOUR_API_KEY')
if quota['exceeded']:
    print('Quota exceeded!')
elif quota['percentage'] >= 80:
    print(f"Warning: {quota['percentage']}% of quota used")
```

---

### 4. Export Usage Data

Export detailed usage data for analytics and billing.

**Endpoint:** `GET /api/usage/:userId/export`

**Query Parameters:**
- `format` (optional): `json` (default) or `csv`

**Response Example (JSON):**

```json
{
  "userId": "user-123",
  "exportDate": "2025-11-06T16:30:00.000Z",
  "period": {
    "start": "2025-11-01T00:00:00.000Z",
    "end": "2025-11-06T16:30:00.000Z"
  },
  "metrics": [
    { "date": "2025-11-01", "metric": "events", "value": 150 },
    { "date": "2025-11-01", "metric": "api_calls", "value": 75 },
    { "date": "2025-11-02", "metric": "events", "value": 200 },
    { "date": "2025-11-02", "metric": "api_calls", "value": 100 },
    { "date": "2025-11-03", "metric": "scans", "value": 10 }
  ]
}
```

**Response Example (CSV):**

```csv
Date,Metric,Value
2025-11-01,events,150
2025-11-01,api_calls,75
2025-11-02,events,200
2025-11-02,api_calls,100
2025-11-03,scans,10
```

**cURL Examples:**

```bash
# Export as JSON
curl -X GET \
  'http://localhost:3333/api/usage/user-123/export?format=json' \
  -H 'Authorization: Bearer YOUR_API_KEY'

# Export as CSV
curl -X GET \
  'http://localhost:3333/api/usage/user-123/export?format=csv' \
  -H 'Authorization: Bearer YOUR_API_KEY' \
  -o usage-export.csv
```

**Node.js Example:**

```javascript
const fs = require('fs');
const https = require('https');

async function exportUsage(userId, format = 'json') {
  const url = `http://localhost:3333/api/usage/${userId}/export?format=${format}`;
  
  const response = await fetch(url, {
    headers: { 'Authorization': `Bearer ${process.env.API_KEY}` }
  });

  if (format === 'csv') {
    const csv = await response.text();
    fs.writeFileSync(`usage-${userId}.csv`, csv);
    console.log(`Exported to usage-${userId}.csv`);
  } else {
    const json = await response.json();
    fs.writeFileSync(`usage-${userId}.json`, JSON.stringify(json, null, 2));
    console.log(`Exported to usage-${userId}.json`);
  }
}

// Usage
exportUsage('user-123', 'csv');
```

---

### 5. Get Overage Charges

Calculate overage charges for the current billing period.

**Endpoint:** `GET /api/usage/:userId/overage`

**Parameters:**
- `userId` (path, required): User identifier

**Response Example:**

```json
{
  "userId": "user-123",
  "period": "2025-11",
  "overageCharges": {
    "events": {
      "units": 500,
      "pricePerUnit": 0.00001,
      "totalCharge": 0.005
    },
    "api_calls": {
      "units": 100,
      "pricePerUnit": 0.00002,
      "totalCharge": 0.002
    },
    "scans": {
      "units": 0,
      "pricePerUnit": 0.05,
      "totalCharge": 0
    },
    "stt_minutes": {
      "units": 0,
      "pricePerUnit": 0.15,
      "totalCharge": 0
    },
    "tts_minutes": {
      "units": 0,
      "pricePerUnit": 0.12,
      "totalCharge": 0
    }
  },
  "totalCharge": 0.007,
  "currency": "EUR"
}
```

**cURL Example:**

```bash
curl -X GET \
  'http://localhost:3333/api/usage/user-123/overage' \
  -H 'Authorization: Bearer YOUR_API_KEY'
```

---

### 6. Reset Usage Counters (Admin/Scheduler Only)

Reset usage counters for a user. This endpoint is typically called by the monthly scheduler.

**Endpoint:** `POST /api/usage/:userId/reset`

**Parameters:**
- `userId` (path, required): User identifier

**Response Example:**

```json
{
  "userId": "user-123",
  "resetDate": "2025-12-01T00:00:00.000Z",
  "success": true,
  "message": "Usage counters reset successfully"
}
```

**cURL Example:**

```bash
curl -X POST \
  'http://localhost:3333/api/usage/user-123/reset' \
  -H 'Authorization: Bearer YOUR_API_KEY'
```

---

## Integration Examples

### Express.js Middleware

```javascript
const axios = require('axios');

// Middleware to track API usage
const trackApiUsage = async (req, res, next) => {
  const userId = req.user?.id;
  
  if (!userId) {
    return next();
  }

  try {
    // Check quota before processing request
    const quota = await axios.get(
      `http://localhost:3333/api/usage/${userId}/check/api_calls`,
      { headers: { Authorization: `Bearer ${process.env.API_KEY}` } }
    );

    if (quota.data.exceeded && req.user.plan === 'freemium') {
      return res.status(429).json({
        error: 'Quota exceeded',
        message: 'Please upgrade your plan to continue using the API',
        upgradeUrl: '/pricing',
      });
    }

    // Track usage after successful request
    res.on('finish', async () => {
      if (res.statusCode < 400) {
        await axios.post(
          'http://localhost:3333/api/usage/track',
          { userId, metric: 'api_calls', amount: 1 },
          { headers: { Authorization: `Bearer ${process.env.API_KEY}` } }
        );
      }
    });

    next();
  } catch (error) {
    console.error('Error checking quota:', error);
    next();
  }
};

// Apply middleware to routes
app.use('/api/*', trackApiUsage);
```

### React Component

```javascript
import { useUsageMetering, useQuotaCheck } from './hooks/useUsageMetering';

function Dashboard() {
  const userId = 'user-123';
  const { usage, loading } = useUsageMetering(userId);
  const eventQuota = useQuotaCheck(usage, 'freemium', 'events');

  if (loading) return <div>Loading...</div>;

  return (
    <div>
      <h2>Usage Dashboard</h2>
      
      {/* Progress bars for each metric */}
      <div>
        <h3>Events</h3>
        <progress value={eventQuota.percentage} max="100" />
        <p>{eventQuota.current} / {eventQuota.limit}</p>
        
        {eventQuota.warning && (
          <div className="alert-warning">
            You've used {eventQuota.percentage.toFixed(0)}% of your quota
          </div>
        )}
        
        {eventQuota.exceeded && (
          <div className="alert-danger">
            Quota exceeded! 
            <a href="/pricing">Upgrade now</a>
          </div>
        )}
      </div>
    </div>
  );
}
```

---

## Error Handling

### Error Response Format

```json
{
  "error": "Error type",
  "message": "Detailed error message",
  "statusCode": 400
}
```

### Common Error Codes

| Status Code | Description |
|-------------|-------------|
| 400 | Bad Request - Invalid parameters |
| 401 | Unauthorized - Missing or invalid API key |
| 404 | Not Found - User or resource not found |
| 429 | Too Many Requests - Quota exceeded (Freemium) |
| 500 | Internal Server Error |

---

## Rate Limiting

API endpoints are subject to rate limiting:

- **Freemium**: 60 requests/minute
- **Starter**: 120 requests/minute
- **Pro**: 300 requests/minute
- **Business**: 1000 requests/minute
- **Enterprise**: Unlimited

Rate limit headers are included in responses:

```http
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 45
X-RateLimit-Reset: 1699276800
```

---

## Webhooks (Coming Soon)

Configure webhooks to receive notifications for:
- Quota warnings (80%, 90%)
- Quota exceeded
- Monthly reset completed
- Overage charges calculated

Example webhook payload:

```json
{
  "event": "quota.warning",
  "userId": "user-123",
  "metric": "events",
  "percentage": 85,
  "timestamp": "2025-11-06T16:30:00.000Z"
}
```

---

## Best Practices

1. **Cache quota checks** - Don't check on every request, cache for 30-60 seconds
2. **Async tracking** - Track usage asynchronously to avoid blocking requests
3. **Error handling** - Always handle API errors gracefully
4. **Monitor usage** - Set up alerts for unusual usage patterns
5. **Export regularly** - Export usage data monthly for audit/billing

---

## Support

For API support:
- Documentation: https://docs.sentinel-quantum-vanguard.com
- Email: api-support@sentinel.com
- Status Page: https://status.sentinel.com

---

## Changelog

### v1.0.0 (2025-11-06)
- Initial release
- Usage tracking endpoints
- Quota checking
- Usage export (JSON/CSV)
- Overage calculation
