/**
 * Auth0 Post Login Action — example only.
 * Configure the Action in Auth0's Login flow and test the resulting access token.
 * The namespace must exactly match VOICE_EMAIL_VERIFIED_CLAIM in the voice API.
 * Do not infer verification from an email address or client-supplied request field.
 */
exports.onExecutePostLogin = async (event, api) => {
  const namespace = "https://sentinelquantumvanguardaipro.pages.dev/claims/";
  // Add this claim only to tokens for the intended Sentinel voice API.
  // Set this exact audience in Auth0 Action secrets (SENTINEL_VOICE_API_AUDIENCE).
  const expectedAudience = event.secrets.SENTINEL_VOICE_API_AUDIENCE;
  if (!expectedAudience || event.resource_server?.identifier !== expectedAudience) {
    return;
  }
  api.accessToken.setCustomClaim(
    namespace + "email_verified",
    event.user.email_verified === true
  );
};
