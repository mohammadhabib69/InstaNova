# Claim provisioner (Apps Script)

Grants `role: "authenticated"` to the Firebase user that presents its own ID token, so
Supabase stops treating new signups as `anon`.

This exists because:

- Supabase picks the Postgres role from the JWT's `role` claim.
- Firebase never mints that claim.
- New accounts therefore run as `anon`, and every `to authenticated` RLS policy — the
  first being the signup profile insert — rejects them with `42501`.
- Only a trusted server can add the claim, and this project's organization policy
  (`iam.disableServiceAccountKeyCreation`) forbids service-account keys, so the usual
  Edge Function / Cloud Function route has no credential to use.

The script runs **inside Google as its owner** and calls Identity Toolkit with
`ScriptApp.getOAuthToken()`. Nothing secret is stored in the app, in Supabase, or on
disk. The uid always comes from Google's answer about the presented token, never from
the request body, so a caller can only provision their own account.

## Files

| File | Purpose |
|---|---|
| `Code.gs` | `doPost` endpoint: verify token → merge claim |
| `appsscript.json` | Web app access + the two OAuth scopes it needs |

## Deploy

```bash
cd tools/claim-provisioner-appsscript
node deploy.mjs      # opens one consent page, then creates + deploys the web app
```

`deploy.mjs` reuses `~/.instanova-secrets/appsscript-project.json` and
`appsscript-token.json`, so repeated runs do not leave duplicate projects behind or ask
for consent again.

Two settings are required once and cannot be set through the API:

1. **Apps Script API toggle** — <https://script.google.com/home/usersettings>.
2. **GCP project association** — the script's *Project Settings → Google Cloud Platform
   (GCP) Project* must point at `instanova-62ff0` (project number `39751114981`).
   Without this the script runs under Google's default project, where
   `identitytoolkit.googleapis.com` is not enabled, and every admin write fails with
   `Identity Toolkit API has not been used in project <n>`.

After moving the script to the project, the owner must re-authorize once by opening the
`/exec` URL, because the move invalidates the previous grant.

The manifest must not declare `https://www.googleapis.com/auth/identitytoolkit`.
Apps Script silently drops the web-app entry point for it; `cloud-platform` is the
scoped alternative that works.

## App contract

The app POSTs JSON — `{"idToken": "<Firebase ID token>"}` — because Apps Script drops
custom headers on its 302. The endpoint always answers HTTP 200, so the app reads
`"ok":true` from the body rather than trusting the status line.

Then point the app at it in `local.properties`:

```properties
CLAIM_PROVISION_URL=https://script.google.com/macros/s/<DEPLOYMENT_ID>/exec
```

and rebuild. `AuthRepository` calls it once when an ID token has no claim, then forces a
fresh token and continues. If the claim still is not there, the write is not sent and
the wizard reports the missing claim instead of a generic permission error.

## Verify

```bash
curl -s -X POST "<URL>" -H 'Content-Type: application/json' \
  -d "{\"idToken\":\"<a real Firebase ID token>\"}"
```

- First call for a fresh account: `{"ok":true,"changed":true}`.
- Repeat call: `{"ok":true,"changed":false}` (idempotent).
- Bad token: `{"ok":false,"error":"invalid or expired Firebase ID token"}`.
