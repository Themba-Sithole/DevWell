# DevWell backend

Deployed to project `whtsjwiqcesngltfghyb` on 25 September 2026.

## What is live

- Four tables: `public.user`, `company`, `team`, `join_request`, all with RLS.
- Auth signup automatically creates an engineer/pending profile. Metadata supplies
  only a display name, never privileges. Auth email changes sync to the profile.
- Clients have SELECT only: own profile, active company/team directory, and own
  requests. Active managers see requests for assigned teams in their own company.
  Active admins can review all teams in active companies.
- `submit-join-request` creates a pending request and updates the profile atomically.
- `review-join-request` approves/rejects the request and updates the profile atomically,
  recording reviewer and timestamp. Opposite repeat decisions return a conflict.
- Identical retries are idempotent. A profile lock serializes concurrent submissions.

Both deployed functions have `verify_jwt=true` and also verify identity through Auth.
They forward the caller's token to Postgres; no service-role key is used. The built-in
server `SUPABASE_ANON_KEY` is used only as the project API key.
Public RPC wrappers are SECURITY INVOKER. Private helpers check `auth.uid()`, role,
status and team ownership before making privileged writes. Their `search_path` is
fixed, and anonymous execution is revoked. Keep `private` out of exposed API schemas.
Calling RPC directly is allowed and cannot bypass those authorization checks.

## App integration

Member 3's `team-aggregate` endpoint is deployed to the live project, and both the
engineer privacy preview and manager dashboard use the shared `TeamInsightsRepository`.
The function verifies the signed-in user's active team access before using the
server-only service-role key to read check-ins for active engineers on that team. The
service-role key is never sent to Android. Below five distinct participants, the
response contains only the count. At five or more, it returns anonymous per-metric
averages after averaging each participant's check-ins equally. A metric remains hidden
unless at least five distinct participants have a valid numeric value for it. The
endpoint accepts an inclusive local-date range; the app sends the same recent
three-month range to the engineer preview and manager dashboard. Deploy updates only
after reviewing the source against the live function and checking the target project.

Member 1 already submits join requests and refreshes `public.user` after submission
and when Refresh status is tapped. The directory is intentionally empty until real
company/team names are available. No invented manager/admin accounts were left behind.

Member 5 must replace `MockManagerRepository` with live approval calls. Other members'
check-ins, projects, history and notifications still use sample repositories; their
backend features are outside this Member 1 onboarding implementation.

## Member 6 admin dashboard (not deployed)

The local `admin-dashboard` Edge Function lists account identity, role, status,
team display name and aggregate platform counts. It never returns check-in answers,
scores or notes. The database RPCs require the caller to be an active, onboarded admin;
status changes are limited to suspending/reactivating another non-admin account and are
audited in the unexposed `private.admin_account_status_audit` table. The caller's JWT
is verified and forwarded to Postgres; no service-role key is required. The migration
also makes active status a database requirement for project/task access, so an already
signed-in suspended account is blocked without waiting for the Android UI to refresh.

This migration and function have not been applied to the hosted Supabase project.
First validate them against a disposable local/development database and review the
target project's migration history. After approval, deploy the database migration
through the normal migration workflow and deploy only this function with:

```text
supabase functions deploy admin-dashboard --project-ref whtsjwiqcesngltfghyb --use-api
```

Validate locally with `tests/admin_dashboard.sql` and the Deno test/check commands
below. The SQL test is transactional and expects the migrations to be applied; run it
with a trusted database role in a development database, never on production.

## AI assistant

The Android assistant sends a question to the `ai-assistant` Edge Function only
after the user checks the consent box. Live requests require an active account; preview
requests are limited by the Supabase gateway IP. Both are capped at 10 requests per hour
per account or IP using a hashed identifier stored in a private table. Prompts are not
stored. `GEMINI_API_KEY` and `SUPABASE_SERVICE_ROLE_KEY` stay in Edge Function secrets;
the latter is used only for the rate-limit RPC. The free Gemini tier may use prompts and
responses to improve Google products, which the consent box discloses.

```text
POST /functions/v1/submit-join-request
{"company_id":"<UUID>","team_id":"<UUID>"}

POST /functions/v1/review-join-request
{"request_id":"<UUID>","approve":true}
```

Send the public key in `apikey` and the user's access token in
`Authorization: Bearer <token>`. Success returns `request_id` and `status`
(`pending`, `approved`, or `rejected`). Errors: 400 invalid input, 401 invalid session,
403 forbidden, 409 conflict, 413 oversized body, 500 save failure, 503 temporary failure.
Only intentional database error messages are exposed; internal details are hidden.

## Add a company, team and manager later

1. Have the manager register and confirm their email normally.
2. In a trusted Supabase SQL Editor session, create an active company and a team linked
   to it. Use their generated IDs for the following updates.
3. Update that exact registered profile to `role=manager`, `status=active`,
   `onboarding_done=true`, and `company_id=<company UUID>`.
4. Set the team's `manager_id` to that profile ID. Company IDs must match.
5. Sign in again. Engineers can now select that company/team.

Provision admins only through trusted SQL/admin operations. Never put role-changing
credentials in Android. Reconsidering a rejected request is an explicit trusted admin
operation in this version. No self-service reapplication flow is provided.

## Deploy and test

The migration filename matches hosted migration history. The hosted database already
has it; do not paste the migration into SQL Editor again. For future changes, use a new
migration. After Supabase CLI login, run from the Android repository root:

```text
supabase link --project-ref whtsjwiqcesngltfghyb
supabase db push --dry-run
supabase db push
supabase functions deploy submit-join-request --project-ref whtsjwiqcesngltfghyb --use-api
supabase functions deploy review-join-request --project-ref whtsjwiqcesngltfghyb --use-api
deno task --config supabase/functions/deno.json check
deno task --config supabase/functions/deno.json test
```

`config.toml` contains local development defaults and JWT function settings. It was
not used to overwrite hosted Auth settings. Configure a real email-confirmation landing
URL in the hosted dashboard. Keep credentials outside Git; do not reset the hosted DB.

Run `tests/identity.sql` through a trusted SQL session on a development project. It
uses fixed test IDs, expects an otherwise empty directory, and rolls back all changes.
To repeat live checks on an empty development project:

1. Run `python supabase/tests/prepare_live_tests.py` from the repository root. This
   generates random IDs/passwords and SQL in ignored `tmp/backend`.
2. Apply `tmp/backend/setup.sql` through a trusted SQL session. Directly inserted,
   confirmed Auth accounts are test fixtures only, not the real signup procedure.
3. Run `python supabase/tests/live_api.py`. It reads public project settings from
   ignored `local.properties` and records results in `tmp/backend`.
4. Always apply `tmp/backend/cleanup.sql`, including after failure. It targets only
   generated UUIDs. Delete the temporary credential files afterwards.

## Verification: 25 September 2026

- Deno type checks and all 10 function unit tests passed.
- SQL assertions passed before and after deployment: profile trigger, RLS, direct-write
  denial, membership validation, safe retries, cross-team review, approval and rejection.
- All 28 live HTTP checks passed with real user tokens. Three simultaneous submissions
  created exactly one request. Approval/rejection updated the profile correctly.
- Temporary sessions were logged out, and test users/data were removed by exact IDs.
  Auth user, profile, company, team and request counts were all zero after cleanup.
- Security Advisor returned no findings. Performance Advisor returned only unused-index
  information for this new database. Foreign-key/access indexes are intentionally kept.
- Real email delivery, public signup and the Android UI against live accounts remain
  manual checks. HTTP tests used confirmed accounts inserted through SQL, with no emails.
- Android code was unchanged. Earlier Android tests/builds are in `docs/validation.md`.

References: [Auth headers](https://supabase.com/docs/guides/functions/auth-headers),
[RLS](https://supabase.com/docs/guides/database/postgres/row-level-security),
[unused-index advisor](https://supabase.com/docs/guides/database/database-linter?lint=0005_unused_index).
