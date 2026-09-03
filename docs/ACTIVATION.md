# Phase F — Activation Documentation

## 🎯 Overview

This document provides precise instructions for each possible activation in Sentinel Quantum Vanguard AI Pro Phase F.

**Current State:** All features PREPARED but DISABLED  
**Mode:** Controlled Activation Ready  
**Version:** 2.0.0-pro

---

## 📊 Feature Activation Matrix

| Feature | Default State | Risk Level | Prerequisites | Rollback Time |
|---------|--------------|------------|---------------|---------------|
| Backend (Read-Only) | ✅ ON | 🟢 LOW | None | Instant |
| Backend (Write) | ❌ OFF | 🟠 HIGH | Security audit | Instant |
| Agents (Sandbox) | ❌ OFF | 🟢 LOW | None | Instant |
| Agents (Monitor) | ❌ OFF | 🟡 MEDIUM | Sandbox validation | Instant |
| Agents (Armed) | ❌ OFF | 🔴 HIGH | Monitor validation | Instant |
| Live Logs | ❌ OFF | 🟢 LOW | Backend active | Instant |
| Android Release | ❌ OFF | 🟡 MEDIUM | Code signing | Manual |
| Auto-Updates | ❌ OFF | 🟡 MEDIUM | Release mode | Manual |

---

## 1️⃣ Backend Activation

### Read-Only Mode (Active by Default)

**File:** `/config/feature-flags.js`

```javascript
export const FEATURE_FLAGS = {
  FEATURE_BACKEND: false,              // Keep OFF
  FEATURE_BACKEND_READ_ONLY: true,    // Already ON ✅
  FEATURE_BACKEND_WRITE: false,       // Keep OFF
  // ...
};
```

**Endpoints Available:**
- `GET /api/v1/health` - System health check
- `GET /api/v1/system/status` - System status
- `GET /api/v1/agents` - List all agents
- `GET /api/v1/agents/:id` - Get agent details
- `GET /api/v1/monitoring/metrics` - System metrics

**Risk:** 🟢 LOW - No data modification possible

**Testing:**
```javascript
// Browser console
const response = await window.SENTINEL_sentinelFetch('/api/v1/health');
const data = await response.json();
console.log(data);
```

**Rollback:**
```javascript
FEATURE_BACKEND_READ_ONLY: false
```

---

### Write Mode (Disabled)

**Activation (Requires Authorization):**

```javascript
export const FEATURE_FLAGS = {
  FEATURE_BACKEND: true,               // Enable backend
  FEATURE_BACKEND_READ_ONLY: false,   // Disable read-only
  FEATURE_BACKEND_WRITE: true,        // Enable write operations
  // ...
};
```

**Additional Endpoints:**
- `POST /api/v1/agents/:id/status` - Change agent status
- `PUT /api/v1/agents/:id` - Update agent configuration
- `POST /api/v1/logs` - Write logs to backend

**Risk:** 🔴 HIGH - Can modify system state

**Prerequisites:**
1. ✅ Security audit completed
2. ✅ Database configured
3. ✅ Authentication implemented
4. ✅ Rate limiting active
5. ✅ Authorization granted

**Rollback:**
```javascript
FEATURE_BACKEND_WRITE: false
// Or emergency:
window.SENTINEL_emergencyShutdown()
```

---

## 2️⃣ AI Agents Activation

### Agent State Progression

**DORMANT → SANDBOX → MONITOR → ARMED**

Each agent must progress through states sequentially.

### Phase 1: SANDBOX (Safe Simulation)

**Activation:**

```javascript
export const FEATURE_FLAGS = {
  FEATURE_AGENTS: true,
  AGENT_NETWORK_GUARDIAN: 'SANDBOX',
  AGENT_PEGASUS_SCAN: 'DORMANT',     // Keep others dormant initially
  AGENT_ANTI_FRAUD: 'DORMANT',
  AGENT_PRIVACY_GUARDIAN: 'DORMANT',
  AGENT_ROOTKIT_SCANNER: 'DORMANT',
  AGENT_CLOUD_SYNC: 'DORMANT',
  // ...
};
```

**Behavior:**
- ✅ Runs in isolated simulation mode
- ✅ Simulates detections
- ✅ Logs all activities
- ❌ Takes NO real actions
- ❌ Modifies NO system state

**Risk:** 🟢 LOW - Completely safe, simulation only

**Validation:**
```javascript
const agent = window.SENTINEL_AgentSystem.getAgent('network-guardian');
const status = agent.getStatus();
console.log(status); // Should show state: 'SANDBOX'

// Execute agent
const result = await agent.execute();
console.log(result); // action: 'SIMULATE'
```

**Rollback:**
```javascript
AGENT_NETWORK_GUARDIAN: 'DORMANT'
```

---

### Phase 2: MONITOR (Observe Only)

**Activation (After SANDBOX validation):**

```javascript
export const FEATURE_FLAGS = {
  FEATURE_AGENTS: true,
  AGENT_NETWORK_GUARDIAN: 'MONITOR',
  // ...
};
```

**Behavior:**
- ✅ Performs real detection
- ✅ Logs all observations
- ✅ Alerts on threats
- ❌ Takes NO actions
- ❌ Blocks NO traffic

**Risk:** 🟡 MEDIUM - Real detection, passive observation

**Prerequisites:**
1. ✅ SANDBOX mode tested successfully
2. ✅ No errors in sandbox logs
3. ✅ Monitoring infrastructure ready

**Validation:**
```javascript
// Should see real detections but no actions
const result = await agent.execute();
console.log(result); // action: 'OBSERVE'
```

**Rollback:**
```javascript
AGENT_NETWORK_GUARDIAN: 'SANDBOX'  // Step down
// Or
AGENT_NETWORK_GUARDIAN: 'DORMANT'  // Full stop
```

---

### Phase 3: ARMED (Full Autonomy)

**Activation (After MONITOR validation):**

```javascript
export const FEATURE_FLAGS = {
  FEATURE_AGENTS: true,
  AGENT_NETWORK_GUARDIAN: 'ARMED',
  // ...
};
```

**Behavior:**
- ✅ Performs real detection
- ✅ Takes autonomous actions
- ✅ Blocks threats
- ✅ Modifies system state
- ⚠️ Fully autonomous

**Risk:** 🔴 HIGH - Autonomous actions, can impact operations

**Prerequisites:**
1. ✅ MONITOR mode validated (minimum 24 hours)
2. ✅ No false positives in monitor logs
3. ✅ Response procedures documented
4. ✅ 24/7 monitoring available
5. ✅ Authorization granted

**Validation:**
```javascript
const result = await agent.execute();
console.log(result); 
// action: 'ACTIVE' or 'STANDBY'
// If detection: actionTaken will be present
```

**Rollback:**
```javascript
AGENT_NETWORK_GUARDIAN: 'MONITOR'  // Step down
// Or
AGENT_NETWORK_GUARDIAN: 'DORMANT'  // Full stop
// Or emergency:
window.SENTINEL_emergencyShutdown()
```

---

## 3️⃣ Live Logging Activation

### Read-Only Live Logs

**Activation:**

```javascript
export const FEATURE_FLAGS = {
  FEATURE_LOGS_LIVE: true,
  FEATURE_LOGS_READ_ONLY: true,
  FEATURE_LOGS_EXPORT: false,
  // ...
};
```

**Behavior:**
- ✅ Real-time log streaming
- ✅ WebSocket connection
- ✅ Read-only access
- ❌ No log modification
- ❌ No log deletion

**Risk:** 🟢 LOW - Read-only, no data modification

**Prerequisites:**
1. ✅ Backend active (READ_ONLY or WRITE)
2. ✅ WebSocket support available

**Testing:**
```javascript
// Listen for log events
window.addEventListener('sentinel:log', (event) => {
  console.log('Live log:', event.detail);
});
```

**Rollback:**
```javascript
FEATURE_LOGS_LIVE: false
```

---

### Log Export (Disabled)

**Activation (Requires Authorization):**

```javascript
export const FEATURE_FLAGS = {
  FEATURE_LOGS_LIVE: true,
  FEATURE_LOGS_READ_ONLY: true,
  FEATURE_LOGS_EXPORT: true,          // Enable export
  // ...
};
```

**Risk:** 🟡 MEDIUM - Can export sensitive data

**Prerequisites:**
1. ✅ Data protection policies reviewed
2. ✅ Export audit trail implemented
3. ✅ Authorization granted

---

## 4️⃣ Android Release Mode

### Debug Mode (Current)

**Current State:**
```javascript
export const FEATURE_FLAGS = {
  FEATURE_ANDROID_RELEASE: false,     // Debug builds
  FEATURE_ANDROID_AUTO_UPDATE: false,
  // ...
};
```

**Characteristics:**
- ✅ Debug APK builds
- ✅ Development certificates
- ✅ Debug logging enabled
- ❌ Not for production

---

### Release Mode

**Activation:**

```javascript
export const FEATURE_FLAGS = {
  FEATURE_ANDROID_RELEASE: true,      // Production builds
  FEATURE_ANDROID_AUTO_UPDATE: false, // Keep manual initially
  // ...
};
```

**Risk:** 🟡 MEDIUM - Production deployment

**Prerequisites:**
1. ✅ Release certificates configured
2. ✅ ProGuard/R8 rules tested
3. ✅ Play Store configuration ready
4. ✅ Version signing validated
5. ✅ Beta testing completed

**Impact:**
- APK builds use release configuration
- Code obfuscation enabled
- Production certificates
- Debug logging disabled

**Note:** This does NOT automatically trigger builds. GitHub Actions workflows remain unchanged.

**Rollback:**
```javascript
FEATURE_ANDROID_RELEASE: false
```

---

### Auto-Update

**Activation (After Release Mode validated):**

```javascript
export const FEATURE_FLAGS = {
  FEATURE_ANDROID_RELEASE: true,
  FEATURE_ANDROID_AUTO_UPDATE: true,  // Enable auto-updates
  // ...
};
```

**Risk:** 🟡 MEDIUM - Automatic distribution

**Prerequisites:**
1. ✅ Release mode active and validated
2. ✅ Update server configured
3. ✅ Update verification implemented
4. ✅ Rollback mechanism tested

---

## 🆘 Emergency Procedures

### Instant Kill Switch

**When to Use:**
- Critical security incident
- Severe bug discovered
- Unauthorized access detected
- System behaving unexpectedly

**Execution:**

```javascript
// Method 1: Browser console (immediate)
window.SENTINEL_emergencyShutdown()

// Method 2: Feature flags file
EMERGENCY_SHUTDOWN: true
KILL_SWITCH_ACTIVE: true

// Method 3: Git rollback
git revert HEAD --no-edit && git push
```

**Effect:**
- ✅ ALL features disabled immediately
- ✅ Audit logging remains active
- ✅ Read-only mode preserved
- ✅ All agents set to DORMANT
- ✅ Event logged with timestamp

**Recovery:**
```javascript
window.SENTINEL_restoreFromEmergency()
// Then review and selectively re-enable features
```

---

## 📋 Activation Checklist

### Before ANY Activation

- [ ] Review this documentation completely
- [ ] Verify current system status
- [ ] Check audit logs for anomalies
- [ ] Ensure monitoring is active
- [ ] Have rollback plan ready
- [ ] Notify relevant stakeholders
- [ ] Document the activation in audit trail

### During Activation

- [ ] Make ONE change at a time
- [ ] Test thoroughly before proceeding
- [ ] Monitor logs continuously
- [ ] Document any issues
- [ ] Be ready to rollback

### After Activation

- [ ] Verify feature working as expected
- [ ] Check for errors in logs
- [ ] Monitor system metrics
- [ ] Update audit trail
- [ ] Document lessons learned
- [ ] Plan next activation step

---

## 📞 Support & Escalation

### Normal Support
- Documentation: `/backend/docs/`
- Audit logs: Automatic (FEATURE_AUDIT_LOG always ON)
- System status: `/public/security-audit.html`

### Emergency Escalation
1. Execute kill switch immediately
2. Document the issue
3. Review audit logs
4. Contact security team
5. Do NOT re-enable until investigated

---

**Last Updated:** Phase F - December 2024  
**Version:** 2.0.0-pro  
**Status:** CONTROLLED ACTIVATION READY
