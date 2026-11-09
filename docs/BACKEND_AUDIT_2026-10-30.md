# InstaNova — Backend Architecture Audit (Read-Only)

**Project:** InstaNova (`com.insta.nova`)
**Firebase project:** `instanova-62ff0` (number 39751114981)
**Supabase project:** `poqdemxiuoocbzkwbyhy` (region not captured)
**Audit date:** 2026-10-30
**Auditor:** Accio (Senior Android Backend Architect / Security Auditor)
**Mode:** Read-only. No source, Gradle, schema, policy, bucket, rule or credential was modified. No data was written or deleted.

### Evidence basis and verification limits

| Source | What was verified |
|---|---|
| Source tree (291 Java files, `app/src/main/java/com/insta/nova/**`) | Every integration point, traced from call site to transport |
| Gradle + AndroidManifest + `google-services.json` (structure only, no key values) | Dependencies, providers, FCM service registration, project IDs |
| Supabase MCP (SQL, read-only) | 51 tables, 9 views, 66 functions, 110 RLS policies, 9 triggers, 5 storage buckets, 28 stored objects, `auth.users` row count, identity mapping counts, advisors (security/performance) |
| Supabase project files (`supabase/schema.sql`, `supabase/migrations/*`, `supabase/functions/*`) | Intended schema and edge-function source |
| Firebase CLI (read-only) | Logged-in identity, accessible projects, active project, `firebase.json` presence |
| **Not verifiable from here** | Firebase console toggles (enabled auth providers, Firestore/RTDB instance existence and rules, App Check enforcement, Remote Config contents, Analytics/Crashlytics enablement); Supabase dashboard third-party-auth toggle; actual runtime behaviour on a device |

Anything marked **Verified** was read from code or read from live database metadata. Anything marked **Inferred** is a reasoned conclusion that was not directly observable. Anything marked **Unverified** is explicitly left open.

---

## 1. Executive Summary

InstaNova is **not** a Firebase app with Supabase bolted on, and it is **not** a Supabase app. It is a **Firebase-Auth + PostgREST application**: Firebase provides only identity and device push, while ~95% of product data and every business rule lives in Supabase Postgres, Storage, Realtime and Edge Functions.

The single most important structural fact: **the app does not use the Supabase SDK at all.** Every Postgres read/write is a hand-written Retrofit call against PostgREST (`/rest/v1/*`), every upload is a raw `POST /storage/v1/object/...`, and Realtime is a hand-rolled Phoenix-channels WebSocket client. That is unusual and it materially changes migration cost: there is no vendor lock-in at the client layer, but there is a very heavy lock-in at the **server** layer — the business logic is 66 PL/pgSQL/SQL functions plus 9 triggers plus 9 views, not Java.

Second structural fact: **identity is split across two systems.** Firebase Auth owns the credential and issues a *string* UID; Supabase owns the canonical profile row, and its primary key is a **client-generated UUID** with a nullable `firebase_uid` text column bridging the two. A `request_uid()` SQL function resolves the JWT `sub` (the Firebase UID) back to `profiles.id` on every RLS check. This bridge works, but it is the highest-risk area for any migration and it is already partially broken for legacy rows.

Headline findings:

| # | Finding | Severity |
|---|---|---|
| 1 | Business logic (66 SQL functions, 9 triggers, 9 views, RLS) is server-side; a Firestore migration means re-implementing all of it | **Blocking for migration** |
| 2 | Two edge functions that dispatch FCM pushes are deployed with `verify_jwt=false`, open CORS, and **no caller verification** | **High** |
| 3 | `local.properties` — containing the Supabase URL and anon key — is **tracked in git** despite `.gitignore` | **High** |
| 4 | All 5 storage buckets are **public-read**, including `chat` (DM media) | **Medium-High** |
| 5 | `profiles.fcm_token`, `firebase_uid`, `last_active_at` are selectable by any signed-in user | **Medium** |
| 6 | In-app notifications are **simulated on-device**; the `notifications` table is empty and unused | **Medium** (feature gap) |
| 7 | 12 of 20 profile rows have **no `firebase_uid`** — they cannot be resolved from a Firebase token | **Medium** (migration blocker) |
| 8 | Two parallel call stacks (`calls` + realtime vs `call_sessions`/`call_signals` + polling) | **Medium** (tech debt) |
| 9 | No Crashlytics, Analytics, App Check, Remote Config or Performance Monitoring at all | **Medium** (observability void) |
| 10 | Chat and presence are **polling-based** (60 s); only the `calls` table uses Realtime | **Low-Medium** |

**Bottom line on the stated target architecture:** the target (Firebase for everything except media) is achievable, but it is a **rewrite of the data and rules layer, not a service swap.** Roughly 60–70% of current backend behaviour has no Firebase equivalent that can be adopted declaratively — it must be rebuilt as Cloud Functions/Firestore rules, or kept in SQL and exposed to Firestore through a different mechanism.

---

## 2. Current Backend Architecture

```
┌───────────────────────────── Android app (com.insta.nova) ─────────────────────────────┐
│                                                                                        │
│  AuthRepository ──► FirebaseAuth (email/password, email verification, password reset)  │
│        │                                                                               │
│        └──► FirebaseTokens.current()  ──► Firebase ID token (never persisted)          │
│                                                                                        │
│  AuthInterceptor  ──► apikey: SUPABASE_ANON_KEY + Authorization: Bearer <Firebase ID>  │
│                                                                                        │
│  RetrofitClient (OkHttp)                                                               │
│    ├── /rest/v1/*          → PostgREST  (Postgres, RLS, 66 RPCs, 9 views)              │
│    ├── /storage/v1/object/*→ Storage    (avatars, posts, stories, reels, chat)         │
│    ├── /functions/v1/*     → Edge Fns   (send-call-push, dispatch-call-notification)    │
│    └── wss://…/realtime/v1 → Realtime   (postgres_changes on public.calls only)        │
│                                                                                        │
│  InstaFirebaseMessagingService ◄── FCM (incoming_call / call_ended / call_cancelled)    │
│                                                                                        │
│  Local duplicate stores: LocalChatStore, LocalSocialStore, CommentStore,               │
│  NotificationStore, FeedCacheDao (Room), BlockMuteCache, NovaBadgeStore                │
└────────────────────────────────────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┴────────────────────────────┐
        │                                                        │
  Firebase (identity + push only)                        Supabase (data plane)
  • Auth: email/password                                 • Postgres: 51 tables, 9 views,
  • FCM: call signalling pushes                            66 functions, 110 policies,
  • (declared but unused: Storage bucket,                   9 triggers
     RTDB URL, App Invite service)                       • Storage: 5 public buckets
  • NOT configured: Firestore, RTDB,                     • Realtime: calls only
    Crashlytics, Analytics, App Check,                    • Edge Functions: 3 deployed
    Remote Config, Performance                           • Auth: UNUSED (0 users)
```

**Push path detail (Verified):** the device never calls FCM send APIs. A call row insert/update in Supabase triggers an edge function, which mints an OAuth2 token from a Google service-account key and calls the FCM v1 HTTP API. So FCM is used as a *transport*, with Supabase as the sender — a cross-system dependency that must be preserved or re-homed during migration.

---

## 3. Complete Supabase Usage Report

### 3.1 Client-side integration points

| Layer | File | Notes |
|---|---|---|
| Configuration | `app/build.gradle:12–49` | `SUPABASE_URL`, `SUPABASE_ANON_KEY` read from `local.properties`, injected as `BuildConfig.SUPABASE_URL`, `SUPABASE_ANON_KEY`, `BASE_URL`, `API_BASE_URL` |
| HTTP client | `data/remote/RetrofitClient.java:47–158` | Base URL = `BuildConfig.SUPABASE_URL`; interceptors: auth, calling-fallback, error tracker, `NovaNetMeter`; debug logging |
| Anonymous/auth header | `data/remote/AuthInterceptor.java` | Always sends `apikey`; sends Firebase ID token as Bearer when signed in; falls back to the anon key as Bearer when signed out |
| Token refresh | `data/remote/TokenAuthenticator.java`, `data/remote/FirebaseTokens.java` | One forced refresh on 401; permanent `supabaseRejectsFirebaseToken` flag on `PGRST301` |
| PostgREST surface | `data/remote/api/RestApi.java` | ~120 endpoints: tables, views, RPCs, `functions/v1/send-call-push` |
| Storage surface | `data/remote/api/StorageApi.java` | `POST/DELETE storage/v1/object/{bucket}/{path}` with `x-upsert: true` |
| Realtime | `calling/CallRealtimeClient.java` | Raw OkHttp WebSocket, Phoenix v1.0.0 protocol, `postgres_changes` on `realtime:public:calls` |
| URL normalisation | `util/MediaUrl.java` | Converts stored paths → `{SUPABASE_URL}/storage/v1/object/public/{bucket}/{path}` |
| Supabase SDK | — | **Not used.** No `io.supabase:*` dependency anywhere |

### 3.2 Service-by-service usage

| Supabase service | Used? | Where | Status |
|---|---|---|---|
| **Auth (GoTrue)** | **No** | No `/auth/v1/*` call exists (`util/Constants.java:21` mentions it only in a comment). `auth.users` = **0 rows** | Dead. Firebase is the only identity provider |
| **Postgres / PostgREST** | **Yes — core** | 51 tables, 9 views, ~30 RPCs across `data/repository/*` | Fully functional, heavily used |
| **Storage** | **Yes** | 5 buckets, uploads from 6 call sites + offline worker | Functional, but **public** |
| **Edge Functions** | **Yes (2 of 3)** | `dispatch-call-notification`, `send-call-push`; `test-deploy` leftover | Functional, **unauthenticated** |
| **Realtime** | **Yes (narrow)** | `public.calls` only | Functional |
| **RLS** | **Yes** | 110 policies across 48 tables | Functional; 1 ERROR-level advisor finding |
| **Database triggers** | **Yes** | 9 triggers maintain counters and call state | Functional |
| **Supabase role claim** | **Yes (fragile)** | Supabase derives Postgres role from JWT `role`; Firebase does not mint it | Requires external provisioner |

### 3.3 Feature → Supabase service matrix (verified from call sites)

| Feature | Supabase service | Primary files | Status |
|---|---|---|---|
| Authentication (credential) | *not Supabase* — Firebase Auth | `data/repository/AuthRepository.java` | Firebase |
| Signup profile creation | PostgREST `profiles` INSERT | `AuthRepository.createProfile()` (~666) | Working, gated on `role` claim |
| User profile read/update | PostgREST `profiles`, `profiles!fkey` joins | `ProfileRepository`, `UserProfileRepository`, `ui/profile/*` | Working |
| Avatar upload | Storage `avatars` | `ui/settings/EditProfileActivity.java:170`, `ui/profile/ProfileFragment.java:532`, `ui/auth/WizardVibeFragment.java:243` | Working |
| Posts / feed / archive | PostgREST `posts`, `post_media`, views `v_feed`, `v_posts` | `data/repository/PostRepository.java`, `ui/create/CreatePostActivity.java` | Working |
| Post media upload | Storage `posts` | `CreatePostActivity.java:953`, `data/repository/StorageRepository.java:74/88` | Working |
| Offline post queue | Room + WorkManager → Storage → PostgREST | `data/NovaPostQueue.java`, `data/work/PostQueueWorker.java`, `data/dao/PostQueueDao.java` | Working (single-device queue) |
| Reels | `posts.is_reel/video_url` + Storage `reels` + `posts` (cover) | `data/repository/ReelRepository.java`, `ui/create/CreateReelActivity.java:153,193` | Working |
| Stories | `stories`, `story_views`, view `v_stories`, RPC `get_story_groups` | `data/repository/StoryRepository.java`, `CreateStoryActivity.java:647` | Working |
| Highlights | `highlights`, `highlight_items` | `data/repository/HighlightRepository.java` | Working |
| Likes / saves | `likes`, `saved_posts`, view `v_saved` | `data/remote/InteractionConflict.java`, `PostRepository` | Working |
| Comments / comment likes | `comments`, `comment_likes`, view `v_comments` | `data/repository/CommentRepository.java` | Working + **local mirror** (`CommentStore`) |
| Follows / requests / close friends | `follows`, `follow_requests`, `close_friends`, RPCs `follow_user`, `approve_follow_request` | `data/repository/FollowRepository.java`, `RelationshipRepository.java` | Working |
| Direct messages (text + image) | `conversations`, `conversation_members`, `messages`, `message_reactions`, RPCs `get_inbox`, `get_or_create_conversation`, `get_unread_counts`; Storage `chat` | `data/repository/ChatRepository.java`, `ui/chat/ChatViewModel.java:473` | Working, **pull-only** |
| Presence / last active | `profiles.last_active_at`, RPC `touch_active` | `ChatRepository.java:392,550,724`, `InstaApp.java:27` | Working, 60 s heartbeat + poll |
| Calls (audio/video) | `calls` + Realtime; legacy `call_sessions` + `call_signals`; RPC `on_call_session_created` | `calling/CallManager.java`, `CallRealtimeClient.java`, `CallSignalingClient.java` | Working, **two stacks** |
| Push dispatch | Edge Functions (`send-call-push`, `dispatch-call-notification`) → FCM v1 | `supabase/functions/*`, `RestApi.sendCallPush()` | Working, **unauthenticated** |
| Device tokens | `profiles.fcm_token`, `user_devices` | `calling/InstaFirebaseMessagingService.java:147–190` | Working, **duplicated storage** |
| In-app notification feed | **Local only** (`NotificationStore`, `NotificationCenter`) | `data/local/NotificationStore.java`, `data/repository/NotificationCenter.java` | **Simulated / mock** |
| Settings & privacy | `nova_settings`, RPC `set_account_privacy`, client `AppSettingsStore`/`ChatSettingsStore` | `data/repository/SettingsRepository.java` | Split client/server |
| Blocks, mutes, restricted | `blocks`, `nova_blocks`, `nova_mutes`, `restricted_users`, `story_audience` | `RelationshipRepository`, `BlockMuteCache` | Working |
| Search | `profiles` queries | `data/repository/SearchRepository.java` | Working |
| NOVA hub (campus, blood/SOS, study, confessions, lost&found, events, Q&A, insights, alerts, moderation) | 20+ `nova_*` tables and 30+ `nova_*` RPCs | `data/NovaRepository.java`, `CampusRepository`, `StudyRepository`, `SosRepository`, `InsightsRepository` | Working |
| Data export | PostgREST reads | `ui/settings/DownloadDataActivity.java` | Working (client-side JSON) |

### 3.4 Database inventory (live, verified)

| Object | Count |
|---|---|
| Tables in `public` | 51 |
| Row-level-security policies | 110 across 48 tables |
| Views | 9 (`v_feed`, `v_posts`, `v_saved`, `v_comments`, `v_stories`, `nova_v_answers`, `nova_v_events`, `nova_v_lostfound`, `nova_v_questions`) |
| Functions in `public` | 66 |
| Triggers | 9 |
| Storage buckets | 5 (`avatars` 9 objects, `posts` 8, `chat` 5, `stories` 5, `reels` 1) |
| `auth.users` | **0** |
| `public.profiles` | 20 rows, **8 with `firebase_uid`**, 12 without |
| `public.notifications` | **0 rows** |

Largest tables (indicative of real usage): `call_signals` 1 019, `call_sessions` 73, `calls` 68, `messages` 43, `likes` 22, `profiles` 20, `comments` 16, `posts` 11.

---

## 4. Complete Firebase Usage Report

### 4.1 Configuration

| Item | Value / state | Verified from |
|---|---|---|
| Project | `instanova-62ff0` | `app/google-services.json` |
| Package | `com.insta.nova` | same |
| API keys in config | 1 (value redacted) | same |
| OAuth clients | 1 (Android type) | same |
| Declared services in config | `appinvite_service` only | same |
| Storage bucket declared | `instanova-62ff0.firebasestorage.app` | same |
| RTDB URL declared | `instanova-62ff0-default-rtdb.firebaseio.com` | same |
| `firebase.json` in repo | **absent** — `firebase_get_environment` reports `<NO CONFIG PRESENT>` | Firebase CLI |
| Firebase CLI auth | `mohammadhabib42730@gmail.com`, active project `instanova-62ff0` | `firebase projects:list` |
| Gradle plugin | `com.google.gms.google-services` 4.4.2 | `build.gradle` (root) |
| SDK deps | BOM 33.7.0, **`firebase-auth`, `firebase-messaging` only** | `app/build.gradle:133–135` |
| Initialisation | `FirebaseApp.initializeApp(this)` wrapped in try/catch that swallows all exceptions | `InstaApp.java:20–23` |

### 4.2 Service matrix

| Firebase service | Configured | Actually used | Features depending on it | Files | Status |
|---|---|---|---|---|---|
| **Authentication** | Yes (plugin + `google-services.json` + dep) | **Yes** | Email/password signup, email verification, sign-in, password reset, password change, session persistence, ID tokens for Supabase | `data/repository/AuthRepository.java`, `ui/auth/*`, `data/remote/FirebaseTokens.java`, `data/local/SessionManager.java` | **Functional**; email/password only |
| Google Sign-In | Partially (1 Android OAuth client in config) | **No** | — | none | **Not implemented** — zero references to `GoogleAuthProvider`/`GoogleSignIn`/`signInWithCredential` |
| **Cloud Firestore** | No (no dependency, no client) | No | — | none | **Not configured** |
| **Realtime Database** | URL present in config only | **No** | — | none | **Not configured / unused** |
| **FCM** | Yes (dep + service + `MESSAGING_EVENT` filter) | **Yes, narrowly** | Incoming-call ringing, call cancel/end; token refresh | `calling/InstaFirebaseMessagingService.java`, `AndroidManifest.xml:205–210` | **Functional**, call-signalling only. No like/comment/follow/chat pushes |
| **Firebase Storage** | Bucket name in config | **No** (no `firebase-storage` dep) | — | none | **Unused** |
| **Crashlytics** | No | No | — | none | **Absent** — no crash reporting |
| **Analytics** | No | No | — | none | **Absent** |
| **App Check** | No | No | — | none | **Absent** — API-key abuse surface |
| **Remote Config** | No | No | — | none | **Absent** — feature flags are compiled constants + `AppSettingsStore` |
| **Performance Monitoring** | No | No | — | none | **Absent** |
| Dynamic Links / App Invite | config lists `appinvite_service` | No | — | none | Legacy leftover in config |

### 4.3 Authentication detail (verified from code)

- Signup is a **multi-step wizard**: `createUserWithEmailAndPassword(email, randomPassword())` → `sendEmailVerification()` → poll `FirebaseUser.reload()` for verification (`ui/auth/WizardCodeFragment.java`) → password creation step replaces the throwaway password.
- Email verification is required; an unverified user is signed out (`AuthRepository.java:139–142`).
- Password reset uses `sendPasswordResetEmail`; password change handles `ERROR_REQUIRES_RECENT_LOGIN` (`data/repository/PasswordRecovery.java`).
- Sessions are owned by `FirebaseAuth` (persisted by the SDK). `SessionManager` stores only identifiers: `firebase_uid`, `user_id` (profile UUID), username, avatar, flags — no credentials or tokens (`data/local/SessionManager.java:29–35`).
- **No Firebase ID token is ever persisted** (`data/remote/FirebaseTokens.java`). Good.

### 4.4 The `role` claim problem (critical coupling)

Supabase chooses the Postgres role from the JWT `role` claim. **Firebase never mints `role`.** Consequences, all verified in code:

1. A brand-new Firebase token is treated as `anon` by Supabase, so every `to authenticated` policy rejects the signup profile insert.
2. The app works around it with a bounded sequence: inspect token → force one refresh → call a **trusted external provisioner** once → block (`data/remote/RoleClaimPolicy.java`, `AuthRepository.ensureSupabaseRole()` ~600–660).
3. The provisioner URL is `BuildConfig.CLAIM_PROVISION_URL` (`local.properties:12`), pointing at a `https://script.google.com/...` Apps Script endpoint (prefix observed, value not printed).
4. A **Supabase Edge Function `provision-role` exists in the repo** (`supabase/functions/provision-role/index.ts`) that does the same job properly (verifies the Firebase token with Identity Toolkit, then merges `{role:"authenticated"}` via service account) — **but it is not in the deployed function list.** Deployed: `dispatch-call-notification`, `send-call-push`, `test-deploy`.

Net effect: new-user signup depends on an external Apps Script being reachable. If that endpoint is down or unset in a build, signup **fails by design** (`auth_claim_missing`).

---

## 5. Feature-by-Feature Backend Mapping

Legend: **A** = already Firebase (correct), **B** = Supabase → should move to Firebase, **C** = Supabase → should stay (media), **D** = not implemented, **E** = partial/broken.

| Feature | Current backend (verified) | Target backend | Class | Migration required |
|---|---|---|---|---|
| Authentication (email/password) | Firebase Auth | Firebase Auth | **A** | None (add Google provider + App Check) |
| Google Sign-In | — | Firebase Auth | **D** | New implementation |
| Session management | FirebaseAuth persistence + `SessionManager` | Firebase Auth | **A** | None |
| User profile | Supabase `profiles` (UUID PK + `firebase_uid`) | Firestore | **B** | Full rewrite; identity re-key |
| Profile search / suggestions | Supabase `profiles` + RPC `get_suggested_users` | Firestore | **B** | Rewrite; needs composite indexes |
| Posts / feed | Supabase `posts`, `post_media`, `v_feed`, `v_posts` | Firestore | **B** | Rewrite; feed fan-out decision needed |
| Post media files | Supabase Storage `posts` | Supabase Storage | **C** | Keep; add URL metadata in Firestore |
| Reels | `posts(is_reel,video_url)` + `reels` bucket | Firestore + Supabase Storage | **B/C** | Split metadata vs bytes |
| Stories (+ views, audience, close friends) | `stories`, `story_views`, `v_stories`, RPC `get_story_groups` | Firestore | **B** | Rewrite; TTL/expiry logic |
| Highlights | `highlights`, `highlight_items` | Firestore | **B** | Rewrite |
| Likes / saved posts | `likes`, `saved_posts` (+ counter triggers) | Firestore | **B** | Rewrite; counters need transactions |
| Comments / comment likes | `comments`, `comment_likes`, `v_comments` | Firestore | **B** | Rewrite |
| Follows / follow requests | `follows`, `follow_requests`, RPC `follow_user`, `approve_follow_request` | Firestore | **B** | Rewrite |
| Blocks / mutes / restricted | `blocks`, `nova_blocks`, `nova_mutes`, `restricted_users` | Firestore | **B** | Rewrite |
| Direct messages | `conversations`, `conversation_members`, `messages`, `message_reactions` (pull-only) | **Realtime Database** | **B** | Full rewrite + live listeners |
| Chat media | Supabase Storage `chat` | Supabase Storage | **C** | Keep; make bucket private |
| Presence / last active | `profiles.last_active_at` + RPC `touch_active` (60 s) | RTDB `onDisconnect` | **B** | Rewrite |
| Typing indicators | **not implemented** | RTDB | **D** | New |
| Calls (audio/video signalling) | `calls` + Realtime websocket; WebRTC media peer-to-peer | RTDB (or keep Realtime) | **B/E** | Rewrite signalling; two stacks to collapse first |
| Legacy call polling | `call_sessions`, `call_signals`, `on_call_session_created` | — | **E** | Remove after collapsing |
| Push notifications (calls) | FCM via Supabase edge functions | FCM via Cloud Functions | **B** | Re-home sender |
| Push notifications (likes/comments/follows/DMs) | **not implemented** | FCM via Cloud Functions | **D** | New |
| In-app notification feed | **Local simulation** (`NotificationCenter`) | Firestore + FCM | **E** | Replace; table exists but unused/empty |
| User settings | `nova_settings` + client `AppSettingsStore` | Firestore | **E** | Split-brain; consolidate |
| Device tokens | `profiles.fcm_token` **and** `user_devices` | Firestore (single collection) | **E** | De-duplicate |
| NOVA hub (campus, blood, study, confessions, lost&found, events, Q&A, insights, alerts, moderation) | 20+ tables, 30+ RPCs | Firestore + Cloud Functions | **B** | Largest single rewrite block |
| Offline caches | Room + 6 SharedPreferences stores | Firestore offline persistence | **E** | Retire custom caches |
| Analytics / Crashlytics / App Check / Remote Config / Performance | **not implemented** | Firebase | **D** | New (low risk, high value) |

**Verified-vs-assumption note:** every row above was confirmed by grepping the call site, not by the presence of a dependency. There is no Firestore, RTDB, Firebase Storage, Analytics, Crashlytics, App Check, Remote Config or Performance code path anywhere in the 291 Java files.

---

## 6. Duplicate and Conflicting Implementations

### 6.1 Identity: Firebase UID vs Supabase UUID (the master conflict)

| Aspect | Firebase Auth | Supabase `public.profiles` |
|---|---|---|
| Identifier | `uid` — opaque string, e.g. `sJAgg9jRNOWbF6EswEeAUKiTQU72` | `id` — **uuid**, client-generated (`AuthRepository.createProfile()` uses `UUID.randomUUID()`) |
| Bridge column | — | `profiles.firebase_uid text` (nullable, no unique constraint verified) |
| Resolution | `request_uid()` SQL: `select id from profiles where firebase_uid = auth.jwt()->>'sub'`, **falling back to casting `sub` as uuid** | Same function |
| Active implementation | Credential + session | **Canonical identity for all 51 tables** |
| Duplicate data | UID appears in `profiles.firebase_uid` **and** every FK is the uuid | — |

Verified consequences:

1. **All foreign keys point at `profiles.id` (uuid), never at the Firebase UID.** Every table (`posts.user_id`, `messages.sender_id`, `follows.*`, `calls.*`, `nova_*`) is anchored to the UUID.
2. **8 of 20 profiles have a `firebase_uid`; 12 do not.** Those 12 rows are pre-Firebase leftovers. `request_uid()` cannot resolve them from a token unless the Firebase UID happens to be a UUID (it is not), so those accounts cannot perform authenticated RLS operations — they are effectively orphaned.
3. `profiles.id` used to reference `auth.users(id)`; that FK was deliberately dropped (`supabase/migrations/2026-10-18_drop_profiles_auth_fk.sql`). The legacy `handle_new_user()` trigger still exists but is inert because `auth.users` is empty.
4. `local.properties`/`shared_prefs` in the repo contain a real-looking `firebase_uid` example (`shared_prefs/insta_session_prefs.xml:6`) — sample device data, not a secret, but it shows the mapping shape.

### 6.2 Authentication: Firebase vs Supabase Auth

| Question | Answer |
|---|---|
| Which is active? | **Firebase Auth only.** |
| Which is unused? | **Supabase Auth** — `auth.users` = 0 rows, no `/auth/v1/*` call, no GoTrue client. |
| Duplicate data? | No duplicate users, but `profiles.id` was designed as a Supabase-auth identity and is now a free-standing UUID. |
| Both called? | No. Firebase issues the token; Supabase only *verifies* it (Third-Party Auth). |
| Migration problem | The `role:"authenticated"` claim must be minted by an external provisioner for every user; the deployed provisioner (`provision-role`) is missing, so a Google Apps Script is load-bearing. |

### 6.3 Two parallel call stacks (both live)

| Stack | Tables | Client | Transport |
|---|---|---|---|
| **New** | `calls` (+ `call_sessions` mirror row) | `calling/CallManager.java`, `CallRealtimeClient.java` | Supabase Realtime `postgres_changes` |
| **Legacy** | `call_sessions`, `call_signals` | `calling/CallSignalingClient.java`, `CallHistoryActivity.java` | REST polling |

`CallManager.java:882` explicitly labels one path "legacy `call_sessions` polling" while line 906 still publishes the legacy row so `CallSignalingClient` keeps working. Both tables hold live rows (73 / 1 019). **Migration must collapse these first**, otherwise chat/call rewrite happens twice.

### 6.4 Notifications: server table vs on-device simulation

| Aspect | `public.notifications` | `NotificationStore` + `NotificationCenter` |
|---|---|---|
| Rows | **0** | up to 80 per account, SharedPreferences |
| Written by | nothing (no trigger, no client write path) | `NotificationCenter` — its own Javadoc says "Turns in-app activity into **simulated** incoming notifications" |
| Read by | only `ui/settings/SyncTestActivity.java:66` (diagnostic) | `ui/activity/NotificationsActivity.java` |
| Unread counts | RPC `get_unread_counts` (server) | local `LiveData` badge |
| Verdict | **Dead schema** | **Mock feature presented as real** |

### 6.5 Duplicate media upload layers

Same bucket, two different code paths:

- `data/repository/StorageRepository.java` — LiveData wrapper + public-URL HEAD probe, plus a blocking twin for WorkManager.
- Direct `storageApi.uploadFile(...)` calls inside activities: `ui/settings/EditProfileActivity.java:170`, `ui/profile/ProfileFragment.java:532`, `ui/create/CreatePostActivity.java:953`, `ui/create/CreateStoryActivity.java:647`, `ui/create/CreateReelActivity.java:153,193`, `ui/auth/WizardVibeFragment.java:243`.

The activity paths bypass the HEAD probe, so a failed/partial upload can still be written into `posts.thumbnail_url` etc.

### 6.6 Duplicate local copies of server data

| Store | Mirrors | Risk |
|---|---|---|
| `data/local/LocalChatStore.java` | conversations/messages | stale threads after server change |
| `data/local/LocalSocialStore.java` | likes/saves/follows | optimistic state drift |
| `data/local/CommentStore.java` | comments | duplicate/ghost comments |
| `data/local/NotificationStore.java` | notifications | fully simulated |
| `data/dao/FeedCacheDao.java` (Room) | feed | schema-shaped to Supabase DTOs |
| `data/BlockMuteCache.java`, `data/NovaBadgeStore.java`, `data/local/RelationshipStore.java` | blocks/mutes/badges | out-of-band truth |

All six caches store **Supabase-shaped payloads** (DTO field names, UUID keys, storage paths). Any backend change invalidates them; they must be versioned or dropped, not migrated.

### 6.7 Settings split-brain

`nova_settings` (server, 3 policies) coexists with `AppSettingsStore` + `ChatSettingsStore` (SharedPreferences). Some gates are enforced client-side only — e.g. `InstaFirebaseMessagingService` drops blocked/`who_can_call=nobody` pushes locally, and the open edge function will still have dispatched them. Server-side enforcement is partial.

---

## 7. Database Schema and Data Relationships

### 7.1 Identity model

```
Firebase Auth user  ──uid (string)──►  profiles.firebase_uid  ──┐
                                                               ├──► profiles.id (uuid) ──► 50 tables' FKs
Supabase auth.users (0 rows, legacy) ──id (uuid)───────────────┘
```

`request_uid()` (SECURITY DEFINER, verified source):

```sql
select coalesce(
  (select p.id from public.profiles p
    where p.firebase_uid is not null and p.firebase_uid = (auth.jwt() ->> 'sub') limit 1),
  case when (auth.jwt() ->> 'sub') ~* '^[0-9a-f]{8}-...$'
       then (auth.jwt() ->> 'sub')::uuid end);
```

**Identity issues:**

1. Not indexed/guaranteed unique on `firebase_uid` (no unique constraint observed in column metadata; `limit 1` in the function suggests the author did not assume uniqueness). Two rows with the same UID would make identity resolution non-deterministic.
2. 12 legacy profiles are unresolvable from a token.
3. The uuid fallback branch means a JWT whose `sub` *is* a uuid maps straight to `profiles.id` — a dual-mode identity that must be preserved or explicitly retired, not silently dropped.

### 7.2 Core schema (verified column-level)

| Table | Key columns | Relationships |
|---|---|---|
| `profiles` | `id uuid PK`, `username`, `full_name`, `bio`, `avatar_url`, `website`, `is_verified`, `posts_count`, `followers_count`, `following_count`, `birthday`, `firebase_uid`, `fcm_token`, `is_private`, `account_type`, `show_activity`, `is_deactivated`, `who_can_call`, `last_active_at` | Referenced by nearly every table |
| `posts` | `id`, `user_id`, `caption`, `location`, `is_reel`, `video_url`, `thumbnail_url`, `likes_count`, `comments_count`, `is_archived`, `space` | `user_id → profiles.id`; media in `post_media` |
| `post_media` | `post_id`, `url`, `position` | `post_id → posts.id` |
| `comments` / `comment_likes` | `post_id`, `user_id`, `body` | counter trigger `trg_comments_count` |
| `likes` / `saved_posts` | `post_id`, `user_id` | counter trigger `trg_likes_count` |
| `follows` / `follow_requests` / `close_friends` | `follower_id`, `following_id` | trigger `trg_follow_counts` |
| `stories` / `story_views` / `story_audience` / `highlights` / `highlight_items` | `user_id`, `media_url`, `media_type`, `expires_at`, `audience` | `v_stories` view joins `profiles` |
| `conversations` / `conversation_members` / `messages` / `message_reactions` | `conversation_id`, `sender_id`, `body`, `media_url`, `is_read` | RPC `get_or_create_conversation` |
| `calls` / `call_sessions` / `call_signals` / `user_devices` | `caller_id`, `callee_id`, `status`, `fcm_token` | `calls` is the realtime source |
| `notifications` | `user_id`, `actor_id`, `type`, `post_id`, `comment_id`, `body`, `read` | **unused** |
| `nova_*` (20 tables) | campus/blood/study/confession/lostfound/events/Q&A/moderation | `user_id → profiles.id` |

### 7.3 Policies, triggers, views

- **110 policies** across 48 tables; command coverage is mostly `SELECT/INSERT/UPDATE/DELETE`. `notifications`, `restricted_users`, `blocks`, `story_audience` use a single `ALL` policy.
- **9 triggers**, all counter/state maintenance: `trg_posts_count`, `trg_likes_count`, `trg_comments_count`, `trg_follow_counts`, `trg_nova_answers_count`, `trg_nova_votes_count`, `trg_nova_auto_hide_confession`, `trigger_on_call_session_created`, `trg_calls_prevent_terminal_overwrite`. **These triggers are the reason counters are trustworthy; they have no Firebase analogue and must become Cloud Functions or client transactions.**
- **9 views.** 8 are `security_invoker=true`; `v_stories` alone is `SECURITY DEFINER` (advisor ERROR). Its definition still applies `can_view(user_id) AND NOT is_story_hidden(...) AND story_visible(...)`, all of which re-derive the caller from the JWT — so **there is no verified active leak**, but the view bypasses `stories`/`profiles` RLS by design and will leak the moment someone edits the WHERE clause. Correct framing: *fragile*, not *currently broken*.
- `nova_confession_likes` has RLS enabled with **no policy** (advisor INFO). Direct PostgREST access is blocked, but writes go through the `SECURITY DEFINER` RPC `nova_like_confession` (exists, not anon-executable), so the feature works. Treat as intentional-hardening, not a bug.

### 7.4 Storage ownership

- Buckets: `avatars`, `posts`, `stories`, `reels`, `chat` — **all `public = true`**, no `file_size_limit`, no `allowed_mime_types`.
- Read policies: 5 × `SELECT` for role `public` (i.e. anyone, including unauthenticated).
- Write/update/delete: `authenticated` only (15 policies). Path convention is `{profile_uuid}/...` and `{profile_uuid}/nova/...` — ownership is expressed **only by path prefix**, and the policies do not verify that the first path segment equals `request_uid()` (no path check appears in the policies returned).
- Media URLs written into `posts.thumbnail_url`, `post_media.url`, `stories.media_url`, `messages.media_url`, `profiles.avatar_url` are **public URLs**, normalised by `util/MediaUrl.java`.

---

## 8. Security Audit Findings

No secret values are reproduced below. Locations only.

| # | Severity | Finding | Evidence | Implication | Recommended action |
|---|---|---|---|---|---|
| S1 | **High** | Two edge functions dispatch FCM pushes with **no caller authentication** and open CORS | `supabase/functions/dispatch-call-notification/index.ts:94–140` and `send-call-push/index.ts:125–175` accept any POST; both deployed with `verify_jwt=false`; `Access-Control-Allow-Origin: *`; recipient/token resolved server-side from the body | Anyone holding the project URL + public anon key (shipped in every APK) can trigger pushes to arbitrary users — call-spam / notification abuse | Require a verified Firebase/Supabase JWT, or accept only Database Webhook invocations with a shared secret; restrict CORS |
| S2 | **High** | `local.properties` is **tracked in git** and contains `SUPABASE_URL` (line 9) and `SUPABASE_ANON_KEY` (line 10, 208-char legacy JWT) plus `CLAIM_PROVISION_URL` (line 12) | `git ls-files` lists `local.properties`; `.gitignore:2,31` lists it but the file predates the rule | Anon key is public-by-design, but this hands an attacker the exact project endpoint **and** the claim-provisioning endpoint; combined with S1 it removes all friction | `git rm --cached local.properties`, rotate the anon key, keep only `local.properties.example` |
| S3 | **Medium-High** | `get_email_by_username(text)` is `SECURITY DEFINER` and **executable by `anon`** | function metadata: `anon_exec = true`; used by `RestApi.getEmailByUsername()` for username→email login | Unauthenticated username→email enumeration (PII disclosure) | `revoke execute ... from anon`; move lookup behind authenticated login flow; add rate limiting |
| S4 | **Medium** | `profiles` grants `SELECT` on `fcm_token`, `firebase_uid`, `last_active_at`, `account_type`, `who_can_call` to `authenticated`, with row policy `(is_deactivated IS NOT TRUE) OR id = request_uid()` | `information_schema.column_privileges`; `pg_policies.profiles_select` | **Any signed-in user can harvest every user's FCM token and Firebase UID** via PostgREST — enables targeted push abuse and identity correlation | Revoke column grants; expose only `id, username, avatar_url, is_verified` (+ self-only columns via a view/RPC). `anon` is already correctly narrowed to those 4 columns |
| S5 | **Medium** | All 5 buckets public-read, including `chat`; no size/MIME limits | `storage.buckets` (public=true, limits null); 5 `storage_*_read` policies for `public` | DM images and private-account media are retrievable by anyone with the URL; no upload-cost ceiling | Make `chat` (and ideally all) private + signed URLs; set `file_size_limit` and `allowed_mime_types`; add path-ownership check to write policies |
| S6 | **Medium** | `v_stories` is `SECURITY DEFINER` (advisor ERROR) | `pg_class.reloptions` lacks `security_invoker`; advisor `security_definer_view` | Bypasses `stories`/`profiles` RLS; predicates currently compensate, so no verified leak — but one edit away from one | `alter view public.v_stories set (security_invoker = true);` |
| S7 | **Medium** | 16 `SECURITY DEFINER` functions executable by `anon` (one earlier count said 19 including non-definer) | advisor `anon_security_definer_function_executable` | Attack surface; guards verified only for `delete_my_account()` (`raise exception 'not authenticated'`) and `can_view()` (`false` on null) | Revoke `EXECUTE` from `anon` on all RPCs not required pre-login (`is_username_available`, `get_email_by_username` are the only plausible exceptions) |
| S8 | **Medium** | Firebase **service-account credentials stored in the database** (`private.firebase_credentials`) and read by an edge function fallback | `supabase/functions/dispatch-call-notification/index.ts:189–210`; `get_firebase_credentials()` is `SECURITY DEFINER` but not anon/authenticated-executable (correct) | Long-lived admin credential at rest; rotates badly | Move to Supabase secrets/env only; delete the table row after confirmation |
| S9 | **Medium** | No App Check, no Crashlytics, no certificate pinning; Firebase API key restrictions unknown | no dependencies; `google-services.json` has 1 API key | API-key abuse (quota burn), no crash visibility, MITM feasible on hostile networks | Enable App Check (Play Integrity) + API key restrictions; add Crashlytics; consider pinning |
| S10 | **Low-Medium** | Signup depends on an external Apps Script to mint `role:"authenticated"` | `AuthRepository.requestRoleProvisioning()` ~615; `RoleClaimPolicy.BLOCKED` | Endpoint outage ⇒ all new signups blocked; the trusted script is outside both platforms | Deploy the existing `provision-role` edge function and point `CLAIM_PROVISION_URL` at it (or replace the mechanism entirely in migration) |
| S11 | **Low** | Firebase init swallows all exceptions | `InstaApp.java:20–23` `catch (Exception ignored) {}` | Silent misconfiguration becomes a runtime auth failure with no signal | Log/remote-log the failure |
| S12 | **Low** | Debug HTTP logging enabled with partial redaction | `data/remote/RetrofitClient.java:144–154` (filters lines containing `apikey:` / `Authorization:`) | Header redaction exists; **request bodies are not redacted** (message bodies, captions) | Disable body logging in release; assert `BuildConfig.DEBUG` |
| S13 | **Info** | `test-deploy` edge function is deployed and active (v6) with `verify_jwt=false` | deployed function list | Unnecessary public surface | Delete |
| S14 | **Info** | `firebase.json` absent ⇒ no version-controlled Firebase rules at all; no Firestore/RTDB rules exist because those services are unused | Firebase CLI | When Firestore/RTDB are introduced there is no rules baseline | Add `firebase.json` + `firestore.rules` + `database.rules.json` at migration start, default deny |

**Positive controls worth preserving:** column-level grants already narrow `anon` to 4 profile columns; Firebase tokens are never persisted; `TokenAuthenticator` treats `PGRST301` as a hard failure instead of retrying forever; RLS is enabled on every one of the 51 tables.

---

## 9. Broken or Incomplete Integrations

| Item | State | Evidence |
|---|---|---|
| In-app notifications | **Mock.** Client simulates likes/comments/follows locally | `data/repository/NotificationCenter.java:18–26`, `seedIfNeeded()`; `notifications` table 0 rows |
| Notification badge | Local LiveData for reads, server RPC for counts — two sources | `NotificationStore.UNREAD` vs `RestApi.getUnreadCounts` |
| Google Sign-In | Absent (OAuth client exists in config, no code) | zero `GoogleSignIn` references |
| Firestore / RTDB / Firebase Storage / Analytics / Crashlytics / App Check / Remote Config / Performance | Absent | `app/build.gradle:133–135` |
| Live chat | Not live. `getMessages` REST fetch; unread refresh every 60 s; no `postgres_changes` on `messages` | `data/repository/ChatRepository.java:154,758–768` |
| Typing indicators / online dot | Not implemented; only `last_active_at` polling | `ChatRepository.java:392,550` |
| Call stack | Two implementations alive simultaneously; new stack mirrors into legacy table | `CallManager.java:882,906` |
| `provision-role` | Source exists, **not deployed** | deployed list: `dispatch-call-notification`, `send-call-push`, `test-deploy` |
| `test-deploy` | Deployed leftover, public | deployed list |
| 12 profiles | No `firebase_uid` ⇒ unresolvable identity | `profiles` count query |
| Upload consistency | Activity call sites bypass the HEAD probe in `StorageRepository` | `CreatePostActivity.java:953` vs `StorageRepository.java:120` |
| `v_stories` | Security-definer view (works, fragile) | advisor ERROR |
| Feature flags | Compiled constants + SharedPreferences; Remote Config absent | `data/local/AppSettingsStore.java` |

---

## 10. Migration Requirements

What must exist before any Firestore/RTDB cut-over:

1. **Identity re-key plan.** Decide the canonical ID for the target system. Two viable options:
   - **(a) Keep the Supabase UUID** as the document key (`profiles/{uuid}`) and store `firebaseUid` as a field + `uidToUuid` lookup collection. Least disruptive to existing FKs and media paths.
   - **(b) Use the Firebase UID** as document key and rewrite every stored reference. Cleaner long-term, but every media path (`{uuid}/...`), every existing row and every client cache key changes.
   Recommendation: **(a)** for phase 1, with a later optional re-key.
2. **Backfill + reconciliation job** for 12 profiles lacking `firebase_uid` — they cannot authenticate today; they must be matched (email lookup in Firebase Auth) or explicitly deprecated.
3. **Business-logic re-implementation inventory.** 66 SQL functions + 9 triggers + 9 views must be mapped to: Firestore security rules (declarative, cannot call other database reads beyond `get()`), Cloud Functions (callable/triggered), or client-side transactions. Functions with cross-row reads (`can_view`, `story_visible`, `get_inbox`, `nova_*_feed`) are the hard ones: **Firestore rules cannot express them efficiently**; they must move to Cloud Functions, which changes latency and cost.
4. **Counter integrity strategy.** `likes_count` / `comments_count` / `followers_count` are trigger-maintained today. In Firestore these require `FieldValue.increment` inside batched writes or Cloud Functions — with a reconciliation job.
5. **Media contract.** Supabase Storage stays. Decide the canonical record shape: `{url, path, bucket, ownerId, mime, size, width, height, createdAt}` stored in Firestore, with the existing public URL pattern preserved so old links keep working.
6. **Push sender re-home.** FCM sends must move from Supabase edge functions to Cloud Functions triggered by Firestore/RTDB writes, with idempotency (the current functions "claim" call ids before sending — replicate that).
7. **Realtime replacement design.** RTDB for chat/presence/typing; define node layout, `onDisconnect` presence, and message security rules.
8. **Client data-layer rewrite.** All 120 Retrofit endpoints, the 6 local stores, and the Room cache are Supabase-shaped. Expect a new repository layer, not a patch.
9. **Dual-write / shadow-read window** plus an offline queue strategy (`NovaPostQueue` currently targets Supabase Storage + PostgREST).
10. **Observability before cut-over.** Crashlytics + Analytics + App Check must be live *before* migrating users, otherwise migration regressions are invisible.

---

## 11. Recommended Target Architecture

The stated target is sound in direction; two adjustments are recommended.

| Concern | Stated target | Recommendation |
|---|---|---|
| Identity | Firebase Auth | ✅ Keep. Also **add**: Google provider, App Check, and (optionally) custom claims for roles — replacing the fragile external `role` provisioner |
| App data | Cloud Firestore | ✅ Keep, with a **hybrid**: keep heavy relational/aggregate reads (feeds, inbox summaries, NOVA leaderboards) behind **Cloud Functions** rather than trying to express them in rules. Consider keeping a read-model in Postgres if cost explodes |
| Chat / presence / typing | Realtime Database | ✅ Correct choice over Firestore (cheaper per-message, native `onDisconnect`, ordered child lists) |
| Push | FCM | ✅ Keep sender in **Cloud Functions** (not Supabase), triggered by DB writes, idempotent per event |
| Media | Supabase Storage | ✅ Keep — but **make buckets private + signed URLs**, set size/MIME limits, and store metadata in Firestore |
| Notifications | (implied Firebase) | Add explicitly: `notifications/{uid}/items` in Firestore + FCM multicast, replacing the simulated local feed |
| Observability | Crashlytics/Analytics/App Check/Remote Config/Performance listed | ✅ Do these **first** (phase 0) — they are cheap, reversible, and de-risk everything after |
| Call signalling | (not stated) | Move to **RTDB** with the new `calls` model; retire `call_sessions`/`call_signals` in the same step |

Target shape:

```
Firebase Auth ──uid──► users/{uid}  (profile, settings, counters, fcmTokens{})
                        ├── posts/{postId}            (media metadata → Supabase URLs)
                        │     └── likes/{uid}, comments/{id}
                        ├── follows/{targetUid}
                        ├── notifications/{id}
                        └── devices/{deviceId}

RTDB ── chats/{conversationId}/messages/{id}
     └── presence/{uid} = {state, lastChanged}   (onDisconnect)

Cloud Functions ── onWrite(post/like/comment/follow/message) → FCM
                ── callable: feed query, inbox summary, NOVA feeds, moderation

Supabase Storage ── avatars/ posts/ stories/ reels/ chat/   (private + signed)
```

---

## 12. Migration Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | **Business logic loss.** 66 SQL functions encode real rules (visibility, blocks, close friends, story audience, moderation auto-hide, call state machine). Firestore rules cannot express them | High | Critical | Port function-by-function to Cloud Functions; build a behaviour test matrix from the SQL before touching the client |
| R2 | **Identity breakage.** 12 profiles lack `firebase_uid`; `request_uid()` has a dual uuid/uid branch | Certain | High | Backfill from Firebase Auth by email; freeze writes to orphan rows; add unique index on `firebase_uid` first |
| R3 | **Counter drift.** Trigger-maintained counts → increments | Medium | High | Nightly reconciliation Cloud Function; keep counts derivable |
| R4 | **Media path/URL breakage.** Stored values include both full URLs and relative paths; `MediaUrl.normalize()` already repairs 4 malformed shapes | High | High | Freeze a URL contract before migration; normalise all stored values in one pass; keep the Supabase public URL shape |
| R5 | **Chat history loss / ordering.** `messages` is the only chat store; local mirrors hold extra state | Medium | High | Export messages per conversation, verify count + last-N hash before/after |
| R6 | **Quota/cost shock.** Firestore charges per document read; current feed = one view query; target may fan out to N reads | Medium | High | Design read models; cache; monitor with budget alerts |
| R7 | **Client cache poisoning.** 6 local stores + Room hold Supabase DTOs keyed by UUID | High | Medium | Ship a migration that wipes/versions local caches on first launch after cut-over |
| R8 | **Push duplication/loss.** Two open edge functions today; Cloud Functions add another sender | Medium | Medium | Single sender, idempotency key per event, kill the old functions in the same release |
| R9 | **Signup outage.** External Apps Script mints the role claim | Medium | High | Deploy `provision-role` (or drop the need entirely by moving off Supabase RLS) **before** migrating |
| R10 | **Dual-write inconsistency** during transition | High | Medium | Shadow-read + compare dashboards; time-boxed window; one-way writes only after cut-over |
| R11 | **Silent failures.** No Crashlytics today; `catch(Exception ignored)` in `InstaApp` | High | Medium | Phase-0 observability before any data change |
| R12 | **Regulatory/PII.** Emails resolvable via anon RPC; FCM tokens readable by any user | — | Medium | Fix S3/S4 before migration; document data retention for the export feature |

---

## 13. Step-by-Step Migration Roadmap

Ordered so that each phase is independently shippable and reversible. **No phase below has been started.**

**Phase 0 — Foundations (no user-visible change)**
1. Untrack `local.properties`, rotate the anon key, purge history if required (S2).
2. Add Crashlytics + Analytics + App Check + Performance; log the swallowed init exception (S9, S11).
3. Add `firebase.json`, `firestore.rules`, `database.rules.json` with default-deny.
4. Close S1/S3/S4/S5: authenticate edge functions, revoke anon `EXECUTE`, narrow `profiles` column grants, privatise `chat` bucket.
5. Deploy `provision-role`; delete `test-deploy`.

**Phase 1 — Stabilise identity**
6. Add unique index on `profiles.firebase_uid`; backfill the 12 legacy profiles from Firebase Auth; mark unresolvable rows read-only.
7. Export a UID↔UUID mapping table; publish it as the contract for all later phases.

**Phase 2 — Collapse duplicates (before adding anything new)**
8. Remove the legacy `call_sessions`/`call_signals` path; keep `calls` + Realtime only.
9. Consolidate media uploads behind `StorageRepository`; delete the direct activity upload paths.
10. Replace the simulated `NotificationCenter` with a real server-backed feed (Supabase table first — cheapest correct step, and it becomes the Firestore source later).

**Phase 3 — Firebase groundwork without moving data**
11. Enable Google Sign-In; add FCM topics/multicast scaffolding; add Cloud Functions project with a health-check function.
12. Add App Check enforcement in staging; verify no legitimate traffic breaks.

**Phase 4 — Move leaf features to Firestore (low-risk first)**
13. Settings + device tokens + blocks/mutes (small, user-scoped, no joins).
14. Notifications feed + unread counters.
15. Highlights, saved posts, comments.

**Phase 5 — Move the social graph**
16. Profiles → `users/{uid}` with `uidToUuid` mirror (dual-write, shadow-read).
17. Follows/follow requests, likes, posts/feed. Port the 3 counter triggers to `FieldValue.increment` + reconciliation job.
18. Verify feed parity against `v_feed` results for N users before flipping reads.

**Phase 6 — Chat and presence**
19. RTDB schema + security rules; migrate `conversations`, `conversation_members`, `messages`, `message_reactions`.
20. Replace 60 s polling with listeners; add `onDisconnect` presence; add typing indicators.

**Phase 7 — Calls and NOVA**
21. Re-home call signalling to RTDB; move push senders to Cloud Functions (idempotent).
22. Port NOVA domain (campus/blood/study/confessions/lost&found/events/Q&A/insights) — one table at a time, each with its own parity check.

**Phase 8 — Decommission**
23. Freeze Supabase Postgres writes; keep Storage; export final snapshot; drop RLS/triggers/functions; retain read-only archive.
24. Remove the Retrofit/PostgREST layer, local duplicate stores, and the legacy call client.

Each phase gate: parity check + crash-free-session rate + push delivery rate + counter reconciliation delta = 0.

---

## Appendix A — Verification checklist (what the audit actually executed)

| Check | Method | Result |
|---|---|---|
| Firebase dependency surface | `app/build.gradle` grep | auth + messaging only |
| Firestore/RTDB/Storage/Analytics/Crashlytics/AppCheck/RemoteConfig/Performance usage | repo-wide symbol grep | zero references |
| Google Sign-In | symbol grep | zero references |
| Supabase SDK usage | dependency grep | none (all custom REST) |
| Supabase Auth usage | `/auth/v1` grep + `auth.users` count | none / 0 rows |
| PostgREST surface | `RestApi.java` annotation scan | ~120 endpoints |
| Storage buckets & policies | `storage.buckets`, `pg_policies` (storage) | 5 buckets, all public-read |
| RLS coverage | `pg_policies` aggregation | 110 policies / 48 tables |
| Identity mapping | `request_uid()` source + `profiles` counts | 8/20 mapped |
| Triggers/views | `pg_trigger`, `pg_class.relkind='v'` | 9 triggers, 9 views, 1 security-definer |
| Function exposure | `has_function_privilege('anon', …)` | 16 anon-executable definer functions |
| Advisors | `get_advisors` security + performance | 70 security (1 ERROR), 44 performance |
| Edge functions | deployed list vs repo | `provision-role` missing, `test-deploy` extra |
| Edge function auth | source read | no caller verification, CORS `*` |
| Secrets in VCS | `git ls-files` | `local.properties`, `google-services.json` |

## Appendix B — Assumptions explicitly NOT verified

1. Whether Firestore or RTDB instances exist (but are unused) in project `instanova-62ff0` — no client code touches them.
2. Whether Firebase console has Google sign-in enabled as a provider (client code would not use it regardless).
3. Whether Supabase "Third-Party Auth → Firebase" is toggle-enabled — the code's `PGRST301` fallback implies it has been flaky; runtime state not observable from here.
4. Actual production traffic patterns (which features real users exercise) — all usage inferences are from schema row counts and code paths.
5. Firebase API key restriction settings (HTTP referrer / Android app / API allow-list) — console-only.
6. Whether `private.firebase_credentials` currently contains a live key — presence of the table was verified, contents were not read.

---

*End of audit. No changes were made to any code, configuration, schema, policy, bucket, function or credential. Awaiting approval before any migration work.*
