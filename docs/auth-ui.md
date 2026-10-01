# Authentication screens

The entry screen uses the supplied DevWell logo and landscape. Get Started opens
registration; I have an account opens sign-in. Android Back returns to the entry
screen. Authenticated users continue through the existing account-status routing.

Registration collects name, email, password and confirmation, then uses the existing
authentication service. After authentication, onboarding proceeds through company,
team and confirmation screens. Changing company clears the team selection. Send
Request submits the selected IDs through the existing repository. Pending and
rejected accounts remain outside the dashboard.

Company and team names come from the directory. Department is omitted because the
current Team model has no department field. The sign-in screen describes the existing
secure session persistence; it does not present an unsupported Remember me toggle
or password-reset action.

For sample data, open I have an account, expand Student preview (debug only), and
select Preview new engineer. Production builds omit these preview controls.

The updated Member1UiTest covers the entry screen, login validation, company changes,
the three onboarding steps, approval gates, sign-out and password clearing on rotation.

## Verification — 27 September 2026

- Debug APK built successfully with JDK 17.
- All 23 JVM unit tests passed. The first test-process launch failed; retry passed.
- The remaining check run was stopped after lint analysis produced no progress for
  several minutes. Lint and the UI test APK build are not verified.
- On-device UI and visual checks remain unverified: the local test emulator stayed
  offline and was stopped.
