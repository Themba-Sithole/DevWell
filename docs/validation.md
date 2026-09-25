# Android verification

Date: 23 September 2026.

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

The tests use controlled responses or the isolated student preview. The device session
tests use Android's real encrypted storage. Live registration, email confirmation,
join-request submission, RLS and manager approval must still be tested once the backend
owner deploys the tables and Edge Functions described in the handoff contract.
No database tables or Edge Functions were created or changed by this app task.

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
