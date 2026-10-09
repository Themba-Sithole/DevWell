# Member 1: Android setup and team handoff

Owner: Themba / Member 1. Course: ITDMA3-34, Team 3.

## What changed

Real email/password sign-in and engineer registration replace role-based login.
The account profile supplies the role and approval state. New engineers choose an
active company and matching team, submit a join request, and wait for approval.
Pending/rejected accounts do not get a protected navigation graph. The graph is
recreated for each signed-in user and role, so sign-out removes previous navigation.
Passwords are not put into saved UI state. Supabase sessions use device-key encryption;
Supabase handles refresh tokens. Application backup is disabled to avoid restoring tokens.

The SDK versions are pinned: Supabase Kotlin 3.0.3, Ktor 3.0.2, Kotlin/Compose compiler
2.1.0. These match the existing Android Gradle Plugin 8.7.3 and SDK 35 project.

## Run in Android Studio

1. Open this repository root (the folder containing settings.gradle.kts).
2. Select JDK 17 for Gradle and install Android SDK 35 when prompted.
3. Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties. Use a publishable key,
   never a service-role/secret key. This file is excluded from Git.
4. Sync Gradle, choose the app debug configuration and an API 26+ emulator/device.
5. Run the app. Register, confirm your email if required, and sign in.
6. To explore without real company/team data, expand Student preview (debug only). Preview
   an existing role or a new engineer. The pending preview can simulate approval
   and rejection. These actions use memory only. Restarting the process ends preview.
7. Release builds hide preview and also reject preview actions in the ViewModel.

The project URL is https://whtsjwiqcesngltfghyb.supabase.co. Obtain the public key
from the project Connect/API Keys panel. The key is embedded in the installed app;
row-level security, not hiding a publishable key, protects the database.

## Simple code map

- ui/auth: form fields, loading, errors, company/team selection, approval screens.
- viewmodel/AuthViewModel.kt: actions and UI state; blocks repeated taps.
- data/auth/SupabaseIdentityRepository.kt: real account and onboarding requests.
- data/auth/IdentityRows.kt: JSON column names mapped to the team's domain models.
- data/auth/DeviceSessionManager.kt: saves SDK sessions encrypted on the phone.
- data/auth/PreviewIdentityRepository.kt: separate in-memory student preview.
- model/EntryDestination.kt: readable role/status rules and form validation.
- ui/navigation/DevWellApp.kt: auth gate and role-specific navigation graphs.

Screens call ViewModel functions; ViewModels call repositories. Other feature
repositories still use the starter's sample data and must be connected by their owners.
The app labels those dashboards as sample data. Do not treat preview approval as a
real manager approval, or mocked manager reviews as database updates.

## Database contract (backend owner)

Deployed on 25 September 2026: the four tables below, profile creation/email-sync
triggers, RLS and both submit-join-request and review-join-request Edge Functions.
No companies or teams have been seeded because real names are not available yet.
The original app task left the database untouched; this backend follow-up implemented it.
See supabase/README.md for deployment and setup instructions.
All IDs below are UUIDs in production, represented as Kotlin strings.

### public.user

- id: UUID primary key, references auth.users.id (the same ID; not a new generated UUID).
- name: non-null text; email: non-null text from the authenticated account.
- role: non-null text, one of engineer, manager, admin.
- status: non-null text, one of pending, active, rejected; the Member 6 admin migration
  adds suspended.
- company_id: nullable company foreign key; team_id: nullable team foreign key.
- onboarding_done: non-null boolean.
- New signup: role=engineer, status=pending, onboarding_done=false, no membership.
- Submitted request: status=pending, onboarding_done=true, selected membership IDs.
- Approved: status=active, onboarding_done=true, correct company and team.
- Rejected: status=rejected; keep onboarding_done=true so the account stays blocked.
- Provision manager/admin accounts through trusted backend/admin operations only.

Create each profile from trusted backend logic after Auth registration. A display name
may come from user_metadata.name, but role/status/membership must never be trusted from
user-editable metadata. The app refuses to enter a dashboard without a readable profile.

### public.company

- id: UUID primary key; name: non-null text.
- status: pending or active. Only active rows are selectable.
- email_domain: optional nullable text (not used to grant membership).

### public.team

- id: UUID primary key; company_id: company foreign key; name: non-null text.
- manager_id: optional user foreign key for manager authorization.
- Other members may add cadence/timezone/threshold fields. Member 1 reads only id,
  company_id and name. Return an empty list when no teams are available.

### public.join_request

- id: UUID primary key; user_id: user foreign key; company_id and team_id: foreign keys.
- status: pending, approved or rejected; created_at: timestamp with timezone.
- reviewed_by and reviewed_at: nullable reviewer ID and timestamp.
- Prevent duplicate pending requests for the same engineer with a database constraint.
- Index user_id, team_id and company_id when used by filters/access policies.
- Enforce that the selected team belongs to the selected company.

## Edge Function: submit-join-request (required by Member 1)

Method: POST /functions/v1/submit-join-request.
Headers: apikey (public key), Authorization: Bearer <signed-in user's access token>,
Content-Type: application/json. The Supabase Kotlin SDK attaches these.

Request body contains only company_id and team_id:

    {"company_id":"<company UUID>","team_id":"<team UUID>"}

Validate the access token with Supabase Auth. Derive the user ID from that verified
identity. Check the profile is an engineer eligible for onboarding, the company is
active, and the team belongs to it. Never accept user_id, role or status from the app.

Atomically insert the pending join_request and set the profile's company_id, team_id,
status=pending and onboarding_done=true. Use a transaction through a carefully secured
database function; two unrelated REST writes are not atomic. If a privileged helper
is needed, keep it outside exposed schemas, restrict execute grants, fix search_path,
and validate the caller/ownership. Do not grant users direct role/status updates.

Make retries idempotent: an identical pending request returns success; a conflicting
request returns a conflict. A successful HTTP response must mean both writes committed.
Suggested success: HTTP 200/201 with {"status":"pending"}. The current Android app
uses HTTP success then re-reads public.user; it does not require a particular JSON
response body. Never return 200 for an error. Use 400 for invalid input, 401 for an
invalid/missing session, 403 for forbidden actions, 409 for conflicts, 500 for failure.

Keep JWT verification enabled and validate identity inside the function. Keep server
keys in server secrets only. Do not log access tokens, passwords or wellbeing data.

## Approval contract (Member 5 + backend owner)

Member 1 refreshes public.user when Refresh status is tapped. No Realtime subscription
is required for this version. The manager's backend operation must verify the reviewer
manages the requested team and atomically update the request and user profile. Approve
sets request.status=approved and user.status=active; reject sets both to rejected.
Always retain onboarding_done=true and the correct membership IDs. The starter's
MockManagerRepository does not perform this live operation.

The deployed review-join-request Edge Function accepts request_id and boolean approve.
An active manager assigned to that team in the same company, or an active admin, may
review it. Member 5 must connect the manager UI to this endpoint; Member 1 does not call it.

## Access rules and integration notes for the whole team

- Enable RLS on every exposed table and add only necessary Data API grants. A table
  grant alone is not row authorization. Test policies with ordinary signed-in users.
- Engineers read their own profile. Prevent self-editing role/status/membership.
- Signed-in onboarding users can read active companies and their teams. Expose only
  the directory fields needed for selection; the UI is not an access-control boundary.
- Engineers can read only their own join requests. Managers can access requests only
  for teams they manage in their own company; active admins can also review them. Do not grant manager access to individual wellbeing records.
- Pending/rejected/suspended accounts must be blocked by backend policies for all protected
  check-ins, projects and other features, even if someone bypasses the Android UI.
- Member 2: write check-ins for auth.uid(), after active membership checks.
- Member 3: personal history is own-user data; privacy preview uses the same safe
  aggregate as Member 5. The existing minimum aggregate response count is 5.
- Member 4: projects/tasks require user/team authorization; never accept ownership
  from untrusted request fields without checking it.
- Member 5: live request review must update the profile as described above.
- Member 6: notifications, AI and admin remain your features. AI server secrets and
  privileged company/role changes belong on the backend. Sign-out is already shared.
  The admin dashboard lists account identity/role/status and aggregate counts only;
  it must not return individual wellbeing data. Only an active, onboarded admin can
  suspend/reactivate another non-admin account. Status writes are audited, and suspended
  accounts must lose protected project/task access even if their existing session remains.

## End-to-end acceptance checklist after backend deployment

1. Register an engineer, confirm the email, sign in, and read the new profile.
2. Show only active companies; changing company clears the selected team.
3. Submit once, then retry after a timeout: exactly one pending request remains.
4. Pending account cannot open protected screens or access protected REST data.
5. A manager for a different team cannot approve this request.
6. Correct manager approves; engineer taps Refresh status and reaches Home.
7. Rejected account shows a clear blocked screen and cannot enter protected features.
8. Manager/admin accounts route to their own dashboards based on trusted profiles.
9. Restart the app and verify saved-session restore; revoke a session and test expiry.
10. Sign out online and offline; restart/Back must not reopen the previous session.
11. Check weak password, incorrect password, unconfirmed email, empty directory,
    network loss, device rotation, and double taps. No app crashes or stuck spinners.
12. Build release and confirm no student preview can be entered.

Email confirmation currently happens in the email/browser; return to the app and sign
in manually. Configure a usable confirmation landing URL in Supabase Auth. Password
reset, Google sign-in and optional manager company registration are not part of this change.

## References

- Supabase Kotlin installation: https://supabase.com/docs/reference/kotlin/installing
- Pinned SDK source/API: https://github.com/supabase-community/supabase-kt/tree/3.0.3
- Row-level security: https://supabase.com/docs/guides/database/postgres/row-level-security
- Edge Function authentication: https://supabase.com/docs/guides/functions/auth

See validation.md for the actual checks performed. Backend-dependent checks above are
covered by SQL assertions and 28 live HTTP checks where applicable. Real email
confirmation and the full Android UI with live accounts still need manual verification.
