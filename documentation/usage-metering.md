# Usage Metering & Pricing System Documentation

## Overview

The Sentinel Quantum Vanguard AI Pro usage metering system tracks and manages consumption across multiple pricing tiers (Freemium, Starter, Pro, Business, Enterprise).

## Table of Contents

1. [Usage Metrics](#usage-metrics)
2. [Pricing Tiers & Quotas](#pricing-tiers--quotas)
3. [API Endpoints](#api-endpoints)
4. [Frontend Hooks](#frontend-hooks)
5. [Monthly Reset System](#monthly-reset-system)
6. [Overage Billing](#overage-billing)
7. [Integration Guide](#integration-guide)

---

## Usage Metrics

The system tracks the following metrics per user per month:

| Metric | Description | Unit |
|--------|-------------|------|
| `events` | Security events detected and processed | Count |
| `api_calls` | API requests made to Sentinel services | Count |
| `scans` | Security scans performed (Pegasus, threat detection, etc.) | Count |
| `stt_minutes` | Speech-to-Text processing time | Minutes |
| `tts_minutes` | Text-to-Speech generation time | Minutes |

---

## Pricing Tiers & Quotas

### Individual Plans

#### Freemium
- **Price**: €0/month
- **Quotas**:
  - Events: 1,000/month
  - API Calls: 500/month
  - Scans: 50/month
  - STT Minutes: 10/month
  - TTS Minutes: 10/month
  - Data Retention: 7 days
- **Overage**: Service suspended until monthly reset

#### Starter
- **Price**: €4.99/month (€47.90/year)
- **Quotas**:
  - Events: 10,000/month
  - API Calls: 5,000/month
  - Scans: 500/month
  - STT Minutes: 60/month
  - TTS Minutes: 60/month
  - Data Retention: 30 days
- **Overage**: Billed at standard rates

#### Pro
- **Price**: €9.99/month (€95.90/year)
- **Quotas**:
  - Events: 50,000/month
  - API Calls: 25,000/month
  - Scans: 2,500/month
  - STT Minutes: 300/month
  - TTS Minutes: 300/month
  - Data Retention: 60 days
- **Overage**: Billed at standard rates

### Professional Plans

#### Developer (Freemium)
- **Price**: €0/user/month
- **Quotas**: Similar to individual Freemium
- **API Access**: 1,000 requests/month

#### Pro (Team)
- **Price**: €19/user/month (€182.40/user/year)
- **Enhanced Features**: RBAC, organizations, advanced exports

#### Business
- **Price**: €49/user/month (€470.40/user/year)
- **Quotas**:
  - Events: 500,000/month
  - API Calls: 250,000/month
  - Scans: 25,000/month
  - STT Minutes: 1,000/month
  - TTS Minutes: 1,000/month
  - Data Retention: 90 days
- **Features**: SIEM integration, SSO, 99.9% SLA

#### Enterprise
- **Price**: Custom
- **Quotas**: Unlimited/Custom
- **Features**: Dedicated environment, 24/7 support, custom features

---

## API Endpoints

### Base URL
```
http://localhost:3333/api/usage  (development)
https://api.sentinel.example.com/api/usage  (production)
```

### Authentication
All endpoints require Bearer token authentication:
```
Authorization: Bearer YOUR_API_KEY
```

### Endpoints

#### 1. Get User Usage
**GET** `/api/usage/:userId`

Returns current usage statistics for a user.

**Response:**
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

#### 2. Track Usage
**POST** `/api/usage/track`

Increment usage for a specific metric.

**Request Body:**
```json
{
  "userId": "user-123",
  "metric": "events",
  "amount": 1
}
```

**Response:**
```json
{
  "userId": "user-123",
  "metric": "events",
  "amount": 1,
  "timestamp": "2025-11-06T16:30:00.000Z",
  "success": true
}
```

#### 3. Check Quota
**GET** `/api/usage/:userId/check/:metric`

Check quota status for a specific metric.

**Response:**
```json
{
  "userId": "user-123",
  "metric": "events",
  "plan": "freemium",
  "current": 750,
  "limit": 1000,
  "remaining": 250,
  "exceeded": false,
  "percentage": 75
}
```

#### 4. Export Usage Data
**GET** `/api/usage/:userId/export?format=json|csv`

Export usage data for analytics and billing.

**Query Parameters:**
- `format`: `json` (default) or `csv`

**Response (JSON):**
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
    { "date": "2025-11-02", "metric": "events", "value": 200 }
  ]
}
```

**Response (CSV):**
```csv
Date,Metric,Value
2025-11-01,events,150
2025-11-01,api_calls,75
2025-11-02,events,200
```

#### 5. Get Overage Charges
**GET** `/api/usage/:userId/overage`

Calculate overage charges for the current period.

**Response:**
```json
{
  "userId": "user-123",
  "period": "2025-11",
  "overageCharges": {
    "events": {
      "units": 0,
      "pricePerUnit": 0.00001,
      "totalCharge": 0
    },
    "api_calls": {
      "units": 0,
      "pricePerUnit": 0.00002,
      "totalCharge": 0
    }
  },
  "totalCharge": 0,
  "currency": "EUR"
}
```

#### 6. Reset Usage (Admin/Scheduler Only)
**POST** `/api/usage/:userId/reset`

Reset usage counters for a user. Typically called by monthly scheduler.

**Response:**
```json
{
  "userId": "user-123",
  "resetDate": "2025-12-01T00:00:00.000Z",
  "success": true,
  "message": "Usage counters reset successfully"
}
```

---

## Frontend Hooks

### useUsageMetering

Monitor usage data in real-time.

```javascript
import { useUsageMetering } from '../hooks/useUsageMetering';

function Dashboard() {
  const { usage, loading, error } = useUsageMetering('user-123');

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;

  return (
    <div>
      <h2>Current Usage</h2>
      <p>Events: {usage.usage.events} / {usage.quotas.events}</p>
      <p>API Calls: {usage.usage.api_calls} / {usage.quotas.api_calls}</p>
    </div>
  );
}
```

### useQuotaCheck

Check quota status for a specific metric.

```javascript
import { useQuotaCheck } from '../hooks/useUsageMetering';

function QuotaWarning({ usage, plan }) {
  const quotaStatus = useQuotaCheck(usage, plan, 'events');

  if (quotaStatus.warning) {
    return (
      <div className="alert alert-warning">
        Warning: You've used {quotaStatus.percentage}% of your events quota
      </div>
    );
  }

  if (quotaStatus.exceeded) {
    return (
      <div className="alert alert-danger">
        Quota exceeded! Upgrade your plan to continue.
      </div>
    );
  }

  return null;
}
```

### useTrackUsage

Track usage when performing actions.

```javascript
import { useTrackUsage } from '../hooks/useUsageMetering';

function SecurityScan() {
  const trackUsage = useTrackUsage('user-123', 'scans');

  const performScan = async () => {
    // Perform scan logic
    await scanForThreats();
    
    // Track usage
    await trackUsage(1);
  };

  return <button onClick={performScan}>Run Scan</button>;
}
```

---

## Monthly Reset System

### Automatic Reset

Usage counters are automatically reset on the 1st of each month at 00:00 UTC via GitHub Actions workflow.

**Workflow File:** `.github/workflows/monthly-usage-reset.yml`

**Schedule:**
```yaml
schedule:
  - cron: '0 0 1 * *'  # Monthly on the 1st at 00:00 UTC
```

### Manual Reset

To manually trigger a reset:

1. Go to GitHub Actions
2. Select "Monthly Usage Reset" workflow
3. Click "Run workflow"

Or run the script directly:

```bash
cd backend
API_URL=http://localhost:3333 API_KEY=your_key node scripts/reset-usage.js
```

### Reset Script

The reset script (`backend/scripts/reset-usage.js`):
- Fetches all users from the database
- Calls the reset endpoint for each user
- Logs results and creates a summary
- Sends notifications on completion/failure

---

## Overage Billing

### Pricing Structure

Overage charges apply to paid plans (Starter, Pro, Business) when monthly quotas are exceeded:

| Metric | Price per Unit |
|--------|----------------|
| Events | €0.01 per 1,000 events |
| API Calls | €0.02 per 1,000 calls |
| Scans | €0.05 per scan |
| STT Minutes | €0.15 per minute |
| TTS Minutes | €0.12 per minute |

### Freemium Limitations

Freemium users **cannot** exceed quotas. When a quota is reached:
1. The service is suspended for that metric
2. User receives an upgrade prompt
3. Service resumes after monthly reset or upgrade

### Billing Cycle

- Overage charges are calculated at the end of each month
- Charges appear on the next month's invoice
- Pro-rated billing applies for mid-month upgrades

---

## Integration Guide

### Step 1: Initialize i18n

Add to your `main.jsx`:

```javascript
import './i18n';
```

### Step 2: Use Pricing Page

The pricing page is available at `/pricing` and includes:
- All pricing tiers with features
- Usage quotas table
- FAQ section
- Overage pricing
- Language toggle (FR/EN)

### Step 3: Track Usage in Your App

```javascript
import { useTrackUsage } from './hooks/useUsageMetering';

function MyComponent() {
  const trackApiCall = useTrackUsage(userId, 'api_calls');
  const trackEvent = useTrackUsage(userId, 'events');

  const handleAction = async () => {
    // Your business logic
    await performAction();
    
    // Track usage
    await trackApiCall(1);
    await trackEvent(1);
  };
}
```

### Step 4: Display Usage Warnings

```javascript
import { useUsageMetering, useQuotaCheck } from './hooks/useUsageMetering';
import { useTranslation } from 'react-i18next';

function UsageMonitor({ userId, plan }) {
  const { t } = useTranslation();
  const { usage } = useUsageMetering(userId);
  const quotaStatus = useQuotaCheck(usage, plan, 'events');

  if (quotaStatus.warning) {
    return (
      <div className="quota-warning">
        {t('usage_metering.quota_warning', { 
          percent: quotaStatus.percentage.toFixed(0),
          metric: 'events' 
        })}
      </div>
    );
  }

  return null;
}
```

### Step 5: Configure Environment Variables

Create `.env` file in backend:

```env
# API Configuration
API_URL=http://localhost:3333
API_KEY=your_secret_api_key

# Database Configuration
DB_CONNECTION=mysql
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=sentinel
DB_PASSWORD=your_password
DB_DATABASE=sentinel_usage
```

Create `.env` file in frontend:

```env
VITE_API_BASE_URL=http://localhost:3333
```

---

## Example Usage Export

### cURL Examples

**Export as JSON:**
```bash
curl -H "Authorization: Bearer YOUR_API_KEY" \
  "http://localhost:3333/api/usage/user-123/export?format=json"
```

**Export as CSV:**
```bash
curl -H "Authorization: Bearer YOUR_API_KEY" \
  "http://localhost:3333/api/usage/user-123/export?format=csv" \
  -o usage-export.csv
```

### JavaScript Example

```javascript
async function exportUsage(userId, format = 'json') {
  const response = await fetch(
    `${API_BASE_URL}/api/usage/${userId}/export?format=${format}`,
    {
      headers: {
        'Authorization': `Bearer ${API_KEY}`,
      },
    }
  );

  if (format === 'csv') {
    const csv = await response.text();
    // Download or process CSV
    return csv;
  }

  const data = await response.json();
  return data;
}
```

---

## Monitoring & Alerts

### Quota Warnings

Set up automatic alerts when users reach:
- 80% of quota: Warning notification
- 90% of quota: Urgent notification
- 100% of quota: Upgrade prompt (Freemium) or overage notification (Paid)

### Reset Monitoring

The monthly reset workflow:
- Creates GitHub issues on failure
- Can be integrated with external monitoring tools
- Logs all actions for audit purposes

---

## Support & Contact

For questions or issues related to usage metering:
- Documentation: `/documentation`
- Support: support@sentinel.example.com
- API Status: https://status.sentinel.example.com

---

## Changelog

### Version 1.0.0 (2025-11-06)
- Initial implementation of usage metering system
- Multi-tier pricing (Freemium → Enterprise)
- i18n support (FR/EN)
- Automatic monthly reset via GitHub Actions
- Usage export API (JSON/CSV)
- Overage billing structure
- React hooks for frontend integration
