/** Source-contract checks complement Android runtime tests; they never replace them. */
export function validateAndroidStartupContracts(application, service) {
  const errors = [];
  if (!/override fun onCreate\(\)\s*\{\s*super\.onCreate\(\)\s*\}/.test(application)) {
    errors.push('Application.onCreate must remain free of storage, Keystore, worker and network startup work.');
  }
  const response = service.indexOf('respondToCall(callDetails, response.build())');
  const worker = service.indexOf('responseBoundary.submitPostResponse', response);
  const maintenance = service.indexOf('runPostResponseMaintenance()', worker);
  const callback = service.indexOf('override fun onScreenCall(');
  const beforeResponse = callback >= 0 && response > callback ? service.slice(callback, response) : '';
  if (response < 0 || worker <= response || maintenance <= worker ||
      !beforeResponse.includes('cachedSnapshotForScreening()') ||
      !beforeResponse.includes('SCREENING_FINGERPRINTER::cachedCandidates') ||
      /getSharedPreferences\s*\(|KeyStore\s*\.\s*getInstance\s*\(|CallBlocklistStore\s*\(/.test(beforeResponse)) {
    errors.push('Call screening must use memory-only caches and schedule maintenance only after the Telecom response.');
  }
  if (!application.includes('store.prepareScreeningSnapshot()') ||
      !application.includes('blockedNumberHashes.isNotEmpty()') ||
      !application.includes('store.prepareFingerprintKeys()') ||
      !application.includes('internal fun runPostResponseMaintenance()')) {
    errors.push('Post-response cache/HMAC maintenance must remain implemented.');
  }
  return errors;
}

export function validateWifiLocationContract(declarations, scanner) {
  const errors = [];
  for (const name of ['ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION']) {
    const declaration = declarations.find(item => item.name === name);
    if (!declaration || declaration.attributes['android:maxSdkVersion'] !== undefined) {
      errors.push(`${name} must cover the paired Android 12+ scan permission request on every supported SDK.`);
    }
  }
  if (!/val requiredPermissions:[\s\S]*?arrayOf\(\s*Manifest\.permission\.ACCESS_FINE_LOCATION,\s*Manifest\.permission\.ACCESS_COARSE_LOCATION\s*\)/.test(scanner) ||
      !scanner.includes('mandatoryPermissions.all') ||
      !scanner.includes('ContextCompat.checkSelfPermission') ||
      !scanner.includes('PackageManager.PERMISSION_GRANTED')) {
    errors.push('Wi-Fi scan access requires the explicit fine/coarse permission pair and effective grant checks.');
  }
  return errors;
}
