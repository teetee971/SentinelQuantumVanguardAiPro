# Privacy Policy - Sentinel Quantum Vanguard AI Pro

**Effective Date:** December 15, 2025  
**Last Updated:** December 15, 2025

## Overview

Sentinel Quantum Vanguard AI Pro ("the App") is committed to protecting your privacy. This privacy policy explains our data collection, usage, and storage practices.

---

## Data Collection

### What We Collect

**The App does NOT collect any personal data.**

Specifically, we do NOT collect:
- ❌ Personal information (name, email, address)
- ❌ Phone numbers or call logs from the cloud
- ❌ SMS messages or content
- ❌ Contact information
- ❌ Location data
- ❌ Device identifiers
- ❌ Usage analytics
- ❌ Crash reports to external servers

### Local Data Storage ONLY

All app data is stored **exclusively on your device**:
- ✅ Call screening preferences (local SQLite database)
- ✅ Blocked numbers list (local storage)
- ✅ App settings and configuration (local SharedPreferences)

**This data never leaves your device.**

---

## Data Usage

### How We Use Data

The App uses locally stored data solely for:
1. **Call Screening** - Identifying and blocking spam calls
2. **User Preferences** - Remembering your settings
3. **App Functionality** - Providing the requested features

### What We DON'T Do

- ❌ We do NOT send your data to any server
- ❌ We do NOT share data with third parties
- ❌ We do NOT use analytics or tracking
- ❌ We do NOT display advertisements
- ❌ We do NOT sell your information

---

## Permissions

### Permissions We Request (Public Build)

| Permission | Purpose | Usage |
|------------|---------|-------|
| `READ_PHONE_STATE` | Detect incoming calls | Required for call screening |
| `READ_CONTACTS` | Display caller name | Optional, enhances caller ID |
| `FOREGROUND_SERVICE` | Background call monitoring | Required for persistent protection |
| `FOREGROUND_SERVICE_PHONE_CALL` | Phone call service type | Required by Android 14+ |
| `INTERNET` | Standard network access | App updates only (optional) |
| `RECEIVE_BOOT_COMPLETED` | Start at device boot | Ensures continuous protection |

**Important:** All permissions are requested at runtime. You can deny any permission, though some features may be limited.

### What We DON'T Request (Public Build)

The Play Store version does NOT request:
- ❌ READ_CALL_LOG (uses CallScreeningService instead)
- ❌ READ_SMS
- ❌ RECORD_AUDIO
- ❌ ACCESS_FINE_LOCATION
- ❌ CAMERA

---

## CallScreeningService

The App uses Android's official **CallScreeningService** API to:
- Identify incoming calls
- Display caller information
- Block spam/scam calls

This API:
- ✅ Is officially supported by Google
- ✅ Does NOT require READ_CALL_LOG permission
- ✅ Respects Android 13+ privacy restrictions
- ✅ Keeps data on your device

---

## Data Storage

### Local Storage Details

**Storage Location:** Device internal storage only

**Database Type:** SQLite (encrypted, local)

**Data Retention:** Data is retained until you:
- Manually delete it within the app
- Clear app data in Android settings
- Uninstall the app

**Backup:** 
- `android:allowBackup="false"` - We disable Android automatic backup
- Your data is NOT backed up to cloud services
- You maintain full control

---

## Data Security

### Security Measures

- ✅ **Code Obfuscation** - ProGuard/R8 enabled
- ✅ **HTTPS Enforcement** - Network security config active
- ✅ **No Cloud Sync** - Zero external data transmission
- ✅ **Local Encryption** - SQLite database protection
- ✅ **No Third-Party SDKs** - No analytics or tracking libraries

---

## Third-Party Services

**We do NOT use any third-party services**, including:
- ❌ No analytics (Google Analytics, Firebase, etc.)
- ❌ No crash reporting (Crashlytics, Sentry, etc.)
- ❌ No advertising networks
- ❌ No social media integrations
- ❌ No cloud storage providers

**100% offline functionality.**

---

## Children's Privacy

The App does not knowingly collect information from children under 13 years of age. The App is not directed at children.

---

## Changes to This Policy

We may update this privacy policy from time to time. Changes will be posted:
- In the app
- On our GitHub repository
- On the Google Play Store listing

**You will be notified** of any material changes.

---

## Your Rights

You have the right to:
- ✅ Access all data (it's on your device)
- ✅ Delete all data (via app settings or Android)
- ✅ Export data (future feature)
- ✅ Control all permissions

### How to Delete Your Data

**Option 1: Within the App**
1. Open Sentinel Quantum Vanguard AI Pro
2. Go to Settings
3. Tap "Clear All Data"
4. Confirm deletion

**Option 2: Android Settings**
1. Go to Settings → Apps
2. Find "Sentinel Quantum Vanguard AI Pro"
3. Tap "Storage"
4. Tap "Clear Data"

**Option 3: Uninstall**
- Uninstalling the app permanently deletes all local data

---

## Compliance

### GDPR (European Union)

We are **fully compliant** with GDPR because:
- We don't collect personal data
- All data is local (no processing outside device)
- No data transfers occur
- Users have full control

### CCPA (California)

We are **fully compliant** with CCPA because:
- We don't sell personal information
- We don't share personal information
- We don't collect personal information

### Other Regulations

The App complies with privacy regulations worldwide because it does not collect or transmit any personal data.

---

## Contact Information

**Questions or concerns about privacy?**

- **GitHub Issues:** https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues
- **Repository:** https://github.com/teetee971/SentinelQuantumVanguardAiPro
- **Documentation:** See PRODUCTION_SECURITY_AUDIT.md

**Response Time:** We aim to respond within 48 hours.

---

## Transparency

### Open Source

This app's source code is available on GitHub for review:
https://github.com/teetee971/SentinelQuantumVanguardAiPro

You can verify:
- ✅ No data collection code
- ✅ No network transmission of personal data
- ✅ All data storage is local
- ✅ No third-party libraries for tracking

---

## Summary

**In Plain English:**

> **Sentinel Quantum Vanguard AI Pro does not collect any personal data.**
> 
> Everything stays on your phone. Nothing is sent to the cloud or any server. You have complete control over your data.
> 
> We respect your privacy 100%.

---

**Application ID (Play Store):** com.sentinel.quantum.public  
**Developer:** Sentinel Security Team  
**Version:** 1.0  
**Platform:** Android 6.0+

---

**Last Reviewed:** December 15, 2025  
**Document Version:** 1.0  
**Status:** Active
