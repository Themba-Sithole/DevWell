# Android verification

Date: 23 September 2026. Manager registration added and re-verified on 21 October 2026.
Manager team administration added on 22 October 2026.

## Manager registration verification (21 October 2026)

- 174 JVM unit tests passed, including the new role-picker, manager onboarding, team
  switch and create-team cases. Android lint completed with 0 errors and 17 warnings.
- The migration `20261021120000_member1_manager_registration.sql` was applied to a
  local Supabase stack first. 35 live HTTP checks passed against the local database:
  the signup role request, the manager's company and first team, the engineer join
  request, the manager's approval, a second team, and the refusal cases (an engineer
  cannot create a company or team, an anonymous caller is rejected, a manager cannot
  create a second company, a duplicate or blank team name is refused, extra body
  fields are ignored and a suspended manager is refused).
- The same 31 checks then passed against the live project
  `whtsjwiqcesngltfghyb`. The live project rate-limits public signups, so the test
  accounts were created through the admin API; the profile trigger still ran, so the
  role handling under test was identical.
- Every account, team and company created by the live checks was removed afterwards.
  The live database was confirmed to contain no leftover test rows.
- The debug APK was installed on the emulator and driven against the live project.
  The signup screen offers the Engineer and Manager cards, the helper text changes with
  the selection, a manager signs in and lands on the two-step company wizard, the
  company and first team are created, the dashboard then shows the team name, the Team
  tab lists the team, a second team can be created with the New team action, and
  Team settings opens the selected team. Switching teams reloads the roster and the
  settings of the newly selected team.
- The live project's public signup endpoint answers 429 Too Many Requests from this
  network, so a new engineer account could not be created from the app during the
  check. The engineer's join request and the manager's approval were covered by the
  HTTP checks above instead.

## Manager team administration verification (22 October 2026)

- `supabase/tests/manager_team_administration.sql` covers ownership rules, denial for
  engineers, admins, same-company colleagues and cross-company managers, member removal,
  deletion of teams with active/pending/rejected engineers, request cleanup, and the
  manager's move to a remaining team (or none). All SQL test files pass on a fresh
  `supabase db reset`.
- 189 JVM unit tests across 22 suites passed, including roster user IDs, remove-member
  and delete-team repository calls, user-facing server errors, a failed reload after
  deletion, and the new view-model states.
- The migration was applied to the live project with a scoped `supabase db push`
  (only `20261022140000_member1_manager_team_administration.sql`); the other pending
  migrations remain pending.
- On the emulator against the live project, a temporary manager saw both engineers in
  Team settings, removing one showed the confirmation and the "must choose a company and
  team again" message, and the removed engineer's live profile became pending/onboarding.
  Deleting the team cleared it for the remaining engineer, kept the manager's company,
  and showed the Team Data empty state with Create team. Signing in as the removed
  engineer then opened the 3-step company/team onboarding instead of the dashboard.
  Temporary accounts and company were removed afterwards.

## Environment

Windows, JDK 17, Android SDK 35, Android Gradle Plugin 8.7.3,
Gradle wrapper 8.14.3 and Kotlin 2.1.0. Device checks used the
DevWellTest Android 16 / API 36 x86_64 emulator at 720 x 1600.

## Verified checks

- Debug APK built, installed and launched on the emulator.
- Unsigned release APK built successfully; release vital lint also passed.
  A Windows Gradle cache move failed on the first attempt; a single-worker retry passed.
- All 23 JVM unit tests passed: role/status routing, validation, repeated taps,
  session loss, repository responses, offline sign-out and existing privacy/tips rules.

- All 7 emulator tests passed: validation, onboarding selection and approval gates,
  manager/admin navigation and sign-out, rotation, encrypted session round-trip and corruption.
- Android lint completed with 0 errors and 11 warnings. Warnings cover dependency/SDK
  updates, the starter's missing launcher icon, backup configuration, and a synchronous
  preference write deliberately performed on the IO dispatcher.

## Scope and remaining integration

The Android tests used controlled responses or the isolated student preview. Device
session tests used real Android encrypted storage. The backend was subsequently deployed
on 25 September 2026: 10 function tests, SQL assertions and all 28 live API checks passed,
including concurrent submission, RLS, approval and rejection. The Security Advisor found
no issues. Temporary accounts and data were removed. See supabase/README.md for details.
Real email confirmation/public signup and the Android UI with live accounts still need
manual verification. The actual company/team directory remains intentionally empty.

Other members' dashboards still use sample repositories. This is Member 1's app-side
implementation, not a claim that the whole team application is connected to live data.
Android Studio's underlying Gradle build and an Android emulator were exercised;
opening and syncing through the Android Studio editor UI was not automated.

## Local Windows build note

This machine required JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/devwell-unused-socket-directory
for Gradle's local communication startup. The path must remain nonexistent to use the
TCP fallback. This is a process environment workaround for this machine, not an app
setting. If the same startup error occurs in Android Studio, set that environment
variable before launching the editor. Normal installations may not need it.

Use JDK 17 and the checked-in Gradle wrapper. The release APK is unsigned; use the debug
APK for local installation or configure your own release signing outside Git.
