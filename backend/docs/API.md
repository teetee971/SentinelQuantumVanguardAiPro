# Backend API Documentation

## ⚠️ PHASE F - APIs READY (READ-ONLY ACTIVE)

**READ-ONLY APIs are ACTIVE. Write APIs are PREPARED but NOT DEPLOYED.**

Status: **READ-ONLY ACTIVE** | Phase: **F - PRO Mode** | Write Mode: **OFF**

---

## API Overview

### Base URL
```
https://api.sentinel-quantum-vanguard.example.com/v1
```
*Note: This URL is not active - it's a placeholder for future deployment*

### Authentication
All API requests will require authentication when deployed:
- Bearer token authentication
- API key authentication (for service accounts)

**Current State**: No authentication required because APIs are not deployed.

---

## Agent Management API

### List All Agents
```http
GET /api/v1/agents
```

**Response (when deployed):**
```json
{
  "agents": [
    {
      "id": "network-guardian",
      "name": "Network Guardian",
      "status": "DISARMED",
      "type": "network-protection",
      "lastActivity": "2024-01-15T10:30:00Z"
    },
    {
      "id": "pegasus-scan",
      "name": "Pegasus Scanner",
      "status": "DISARMED",
      "type": "threat-detection",
      "lastActivity": "2024-01-15T10:30:00Z"
    }
  ]
}
```

### Get Agent Details
```http
GET /api/v1/agents/:id
```

### Update Agent Configuration
```http
PUT /api/v1/agents/:id
Content-Type: application/json

{
  "configuration": {
    "scanInterval": 60,
    "sensitivity": "high"
  }
}
```

### Arm/Disarm Agent
```http
POST /api/v1/agents/:id/status
Content-Type: application/json

{
  "status": "ARMED"
}
```

---

## Monitoring API

### System Health
```http
GET /api/v1/monitoring/health
```

**Response (when deployed):**
```json
{
  "status": "HEALTHY",
  "components": {
    "backend": "HEALTHY",
    "agents": "HEALTHY",
    "database": "HEALTHY"
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### System Metrics
```http
GET /api/v1/monitoring/metrics
```

**Response (when deployed):**
```json
{
  "cpu": 45.2,
  "memory": 62.8,
  "disk": 35.1,
  "network": {
    "incoming": 1024,
    "outgoing": 2048
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## Logs API

### Query Logs
```http
GET /api/v1/logs?level=INFO&from=2024-01-15T00:00:00Z&to=2024-01-15T23:59:59Z&limit=100
```

**Response (when deployed):**
```json
{
  "logs": [
    {
      "timestamp": "2024-01-15T10:30:00Z",
      "level": "INFO",
      "source": "network-guardian",
      "message": "Agent initialized successfully",
      "metadata": {
        "agentId": "network-guardian",
        "version": "1.0.0"
      }
    }
  ],
  "total": 1
}
```

### Live Log Stream (WebSocket)
```
ws://api.sentinel-quantum-vanguard.example.com/v1/logs/stream
```

**Message Format (when deployed):**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "level": "INFO",
  "source": "system",
  "message": "Real-time log message",
  "metadata": {}
}
```

---

## System Status API

### Get System Status
```http
GET /api/v1/system/status
```

**Response (when deployed):**
```json
{
  "phase": "E",
  "status": "ARMABLE_NOT_ARMED",
  "activationReady": true,
  "activationEnabled": false,
  "features": {
    "backend": false,
    "agents": false,
    "logsLive": false
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## Error Responses

All APIs will use standard HTTP status codes when deployed:

- `200 OK` - Success
- `201 Created` - Resource created
- `400 Bad Request` - Invalid request
- `401 Unauthorized` - Authentication required
- `403 Forbidden` - Insufficient permissions
- `404 Not Found` - Resource not found
- `500 Internal Server Error` - Server error

**Error Response Format:**
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": {}
  }
}
```

---

## Rate Limiting

When deployed, APIs will be rate-limited to:
- 1000 requests per hour (authenticated)
- 100 requests per hour (unauthenticated)

---

## Deployment Checklist

Before deploying these APIs:

- [ ] Set `FEATURE_BACKEND` to `true` in feature flags
- [ ] Configure production environment
- [ ] Set up authentication system
- [ ] Configure rate limiting
- [ ] Set up monitoring and logging
- [ ] Complete security audit
- [ ] Configure CORS policies
- [ ] Set up SSL/TLS certificates
- [ ] Configure database connections
- [ ] Set up backup systems
- [ ] Obtain deployment authorization

---

**Last Updated**: Phase E - December 2024  
**Status**: NOT DEPLOYED - PREPARATION ONLY
