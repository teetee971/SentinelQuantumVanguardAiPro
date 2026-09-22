# SonarQube CI integration

Sentinel runs SonarQube as an observational, non-blocking analysis layer alongside the existing CodeQL and repository security gates.

## Required GitHub configuration

- Secret `SONAR_TOKEN`: authentication token issued by SonarQube Cloud or SonarQube Server.
- Variable `SONAR_PROJECT_KEY`: project key configured in SonarQube.
- Variable `SONAR_ORGANIZATION`: SonarQube Cloud organization key. For SonarQube Server, leave this unset only after adapting the workflow to the server configuration.
- Variable `SONAR_HOST_URL`: required for SonarQube Server; for SonarQube Cloud it may be left unset.

The workflow intentionally skips when `SONAR_TOKEN` is unavailable and remains non-blocking while the integration is being calibrated. It does not replace CodeQL and must not be added to required branch-protection checks until repeated runs are stable and the quality gate is explicitly approved.

The official scan action is pinned to the immutable commit for v8.2.2. Signature verification remains enabled by default.
