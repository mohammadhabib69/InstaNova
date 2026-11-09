# Nova rework — final report

**Project:** Insta_Clone (`/Users/mohammadhabib/Programming/InstaNova`)
**Stack:** Java only, Gradle Groovy. Firebase untouched (Auth, Firestore, Realtime Database stay on Firebase).
Supabase stays on Supabase — Nova media goes to a dedicated Supabase Storage bucket (`nova`).
**Updated:** 2026-11-02, after on-device testing on a Samsung Galaxy M21 (`R58N752AYPD`, Android 12 / SDK 31).

---

## 0. Status

| Item | Status |
|---|---|
| Nova UI rearrangement | Done |
| Isolation fix (separate Nova tables) | Done — schema + client, verified on device |
| Shared Post Options module | Done — both surfaces, verified on device |
| Supabase schema, RLS, views, RPCs, indexes, pg_cron, storage | Done — applied and verified live |
| Nova sub-modules on real data | No fake data found; a few error/empty states still missing (see §7) |
| APK build | Done |
| Install on device | **Done** — `adb install -r` → `Success` |
| On-device tests | **Done** — see §5: isolation, anonymous, the full expiry lifecycle, share, votes, sorting, chips, save, comments, replies, collapse, feed paging, the SOS and Alerts error states, six sub-screens and the donor register/edit path all verified; eleven bugs found and fixed |

Build note: **JDK 17 is required.** The machine default is JDK 26, which AGP 8.x cannot use.

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home
./gradlew :app:assembleDebug --offline
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 1. The isolation bug — audit

### Where Nova wrote into Instagram

| # | Place | What it did | Fix |
|---|---|---|---|
| 1 | `PostRepository.getAllPosts(space)` → `v_posts` | Nova read the **shared** `posts` table through `v_posts`, which filtered on `space` only in the query, not in the view | Nova now reads `nova_posts_public` only |
| 2 | `CreatePostActivity` (called with `Post.SPACE_NOVA` from `NovaSheet`) | Wrote Nova posts into the **same** `public.posts` table | `NovaComposerActivity` writes `nova_posts` via `create_nova_post`; `NovaSheet` no longer opens `CreatePostActivity` |
| 3 | `NovaPostQueue` → `PostQueueWorker.post()` → `restApi.createPost()` | The offline outbox wrote to `public.posts` with no `space` | `posts.space` now defaults to `'insta'`; the outbox is no longer surfaced in Nova |

### Where Nova content leaked into Instagram reads

Every Instagram read path went through `v_posts`, which had **no `space` filter**. Only `v_feed` filtered `space = 'insta'`. So a Nova post was visible on:

| Path | Repository method | Fix |
|---|---|---|
| Instagram home feed | `v_feed` | already filtered — now also filters expired rows |
| **Reels tab** | `ReelRepository.getReels` | added `space=eq.insta` |
| **Profile grid** | `PostRepository.getUserPosts` | added `space=eq.insta` |
| **Saved list** | `PostRepository.getSavedPosts` | added `space=eq.insta` |
| Post by ids | `getPostsByIds` | added `space=eq.insta` |
| Archived posts | `getArchivedPosts` | added `space=eq.insta` |
| **Local merge into the home feed** | `HomeViewModel.mergeLocalPosts` merged **every** locally created post, both spaces | now skips `local.isNova()` |

---

## 2. Supabase — what was run

Five migrations, all applied through the connected Supabase connector.

### `2026-11-02_nova_separation_post_options.sql`
* **Tables** — `nova_posts`, `nova_comments`, `nova_votes`, `nova_saves`, `nova_expired_media`; RLS on for all.
* **Base tables sealed.** `revoke all ... from anon, authenticated`. The real `author_id` of an anonymous Nova post is therefore unreachable from any client role — not merely hidden by a view.
* **Public views** — `nova_posts_public`, `nova_comments_public`: `author_id` → `NULL` for anonymous rows, `alias` → the per-post random alias, `avatar_url` → `NULL`; `is_mine` / `my_alias` still resolve for the author; `my_vote`, `is_saved` are per-request.
* **RPCs** — `create_nova_post`, `create_nova_comment`, `nova_cast_vote`, `cast_vote`, `cast_comment_vote`, `toggle_nova_save`, `share_nova_to_instagram`.
* **Indexes** — feed/sort/expiry/author indexes; unique `nova_votes(voter_id, target_type, target_id)`.
* **pg_cron** — job `expire_nova_posts`, `* * * * *`, active: `nova_expire_posts()` → `nova_expire_instagram_posts()` → `nova_purge_expired_media()`.
* **Storage** — bucket `nova`, public read, insert/select/update/delete policies.
* **Legacy data** — existing `posts` rows with `space='nova'` migrated into `nova_posts` then deleted from `posts`. Verified: `posts where space='nova'` → 0 rows; the migrated row (`nova-space-test`, created 2026-11-01) survives in `nova_posts`.

### `2026-11-02_post_options_instagram_surface.sql`
* `posts` gained `is_anonymous`, `anonymous_alias`, `expires_at`, `deleted_at`.
* **`posts_anon_owners`** holds the real author of an anonymous Instagram post; no client role may read it. For such a post the row's `user_id` is a **system profile** (`00000000-0000-4000-8000-00000000a001`), so the real author is not in `public.posts` at all.
* `v_posts`, `v_feed`, `v_saved` rebuilt with masking and expired-row filtering.
* RPCs `create_instagram_post`, `delete_my_post`, `update_my_post_caption`, `post_is_mine`.

### `2026-11-02_nova_fade_window_and_hot_rank.sql`
* `duration_seconds` on `nova_posts` and `posts` — the fade needs the chosen window, not just the end moment.
* `hot_rank` on `nova_posts_public`: `score / (age_hours + 2) ^ 1.5`.
* Views dropped and recreated in dependency order (`CREATE OR REPLACE` cannot insert a column mid-list).

### `2026-11-02_create_instagram_post_media_array.sql`
* `create_instagram_post` takes `p_media_urls jsonb` and attaches every media row itself.

### `2026-11-02_drop_stale_rpc_overloads.sql` — **added after on-device testing**
PostgREST resolves an RPC by the keys in the request body. Three functions had two or more overloads with every argument defaulted, so a body that omitted the newer argument matched both candidates and the call failed with *"Could not choose the best candidate function"*. Drops the stale signatures, keeping one per operation:

* `create_nova_post(...)` without `p_duration_seconds` — **this broke every Nova post set to "Never" expire.**
* `create_instagram_post(text,text,boolean,text,text,boolean,timestamptz)` and `(...,integer)` — only the `p_media_urls jsonb` variant is used.
* `get_suggested_users(integer)` — the app sends only `{p_limit}`, which matched both overloads (pre-existing bug).

Verified afterwards: a query for `proname` with `count(*) > 1` in `public` returns **zero rows**.

### `2026-11-02_harden_posts_grants.sql` — **APPLIED 2026-11-03**
Auditing the grant surface on `public.posts` turned up something broader than the report previously described: `anon` **and** `authenticated` both held the full table privilege set — `SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER`. RLS is on with four policies (`select` / `insert-own` / `update-own` / `delete-own`), which covers the four DML privileges. **RLS does not apply to `TRUNCATE`, `REFERENCES` or `TRIGGER`.** PostgREST cannot issue any of those, so nothing was reachable over the API, but no client needs them.

The migration revoked only those three from both client roles, leaving DML untouched.

Verified live after applying:

```
grantee        privs
anon           DELETE,INSERT,SELECT,UPDATE
authenticated  DELETE,INSERT,SELECT,UPDATE
```

The app was then smoke tested on device (`10BE2S1U640002B`): `GET /rest/v1/v_feed?is_reel=eq.false&limit=10` returned **200**, no `permission denied` / `42501` / `PGRST` entries in logcat, and the Instagram feed rendered with media and interactions. A companion `.rollback.sql` re-grants the three privileges.

**The larger change — revoking table-level `SELECT` — was investigated and rejected as ineffective.** All three client-facing views already expose `user_id` in their column list:

| view | exposes `user_id` |
|---|---|
| `v_feed` | yes |
| `v_posts` | yes |
| `v_saved` | yes |

The views are owned by `postgres` with `security_invoker` unset, so they execute with the owner's rights and are not subject to the caller's grants. Column-level revocation of `user_id` on the base table would therefore change nothing that any client can actually see — clients read the views, and the views hand back `user_id` regardless. Making it effective would mean setting `security_invoker=true`, at which point the views would need column-level grants themselves and would still have to expose `user_id` to render post authors.

It is also not a live privacy issue. `posts_select` is `using (true)`, so the only identity column is `user_id` — the *public author* of a normal post, or the system profile for an anonymous one. The real author of an anonymous post lives in `posts_anon_owners`, which is **sealed**: RLS on, and only `postgres` and `service_role` hold any grant. The anonymity boundary holds independent of `posts` grants.

---

## 3. Android — what changed

### New files
| File | Purpose |
|---|---|
| `data/repository/NovaPostRepository.java` | Nova's only data source: `nova_posts_public`, `nova_comments_public`, Nova RPCs |
| `ui/post/PostComposerOptionsSheet.java` | **Shared Post Options** — `SURFACE_NOVA` / `SURFACE_INSTAGRAM`, anonymous toggle, expiry picker |
| `ui/nova/NovaComposerActivity.java` | Nova composer: text / photo / poll, Post Options, destination row |
| `hub/NovaPostAdapter.java` | Post cards: votes, score, comments, save, expiry badge, fade |
| `hub/NovaCommentAdapter.java` | Threaded comments: nesting, collapse, votes, reply |
| `hub/NovaPostDetailActivity.java` | Post + comment thread + comment composer |
| `res/layout/popup_nova_sort.xml` | Styled sort popup (added after device testing) |
| `res/drawable/bg_nova_popup.xml` | Rounded popup surface (added after device testing) |
| `res/layout/item_nova_post.xml`, `item_nova_comment.xml`, `activity_nova_post_detail.xml`, `activity_nova_composer.xml`, `sheet_post_options.xml`, `sheet_nova_filter.xml` | New layouts |
| `res/drawable/ic_nova_upvote.xml`, `ic_nova_downvote.xml`, `ic_nova_sort.xml`, `ic_nova_anon_avatar.xml` | New icons |

### Rewritten
| File | Change |
|---|---|
| `hub/NovaHubFragment.java` | Header: **Nova**, filter glyph, **+**, **bell with unread badge**. Chip rail + sort selector. Vertical card feed. Filter sheet. Loading / empty / error states. |
| `hub/NovaFeedViewModel.java` | Reads `nova_posts_public` only; chips (All, Photos, Reels, Saved, Mine); sort (Hot/New/Top) applied server-side; filter state; client-side expiry filtering |
| `hub/NovaSheet.java` | Sections **CREATE → COMMUNITY → YOU → SETTINGS**; **Alerts removed** |
| `res/layout/fragment_nova_hub.xml`, `sheet_nova_create.xml` | Rewritten to match |
| `res/layout/activity_nova_lite.xml` + `lite/NovaLiteActivity.java` | Instagram post outbox removed from the Nova screen |

### Modified
| File | Change |
|---|---|
| `data/NovaDtos.java` | `NovaPost` (now `Serializable` so the feed can hand a card to the detail screen), `NovaComment`, `VoteOutcome`, `CreatedRef` |
| `data/NovaApi.java` | Nova views + RPC endpoints |
| `data/repository/PostRepository.java`, `ReelRepository.java` | `space=eq.insta` on every Instagram read path |
| `data/repository/StorageRepository.java` | `uploadNovaMedia()` → the `nova` bucket |
| `ui/home/HomeViewModel.java` | `mergeLocalPosts` skips Nova rows |
| `ui/create/CreatePostActivity.java` + `res/layout/create_step_caption.xml` | Instagram composer gained the same **Post options** sheet and an **"Also share to Nova"** switch; the existing write path is untouched unless options or the Nova share are used |
| `AndroidManifest.xml` | Registered `NovaComposerActivity` and `NovaPostDetailActivity` |
| `res/values/strings.xml`, `strings_nova.xml`, `styles_nova.xml` | New strings and styles |

**Untouched by design:** the Instagram theme, colours, fonts, icons, page layouts and every existing Instagram screen. The Nova sub-module screens kept their existing layouts and colours.

---

## 4. Bugs found by running it on the device

All four were found only by exercising the app on the handset, all are fixed, and each fix was rebuilt, reinstalled and re-tested.

| # | Symptom | Cause | Fix |
|---|---|---|---|
| 1 | **"Also share to Instagram" never engaged** — tapping the switch left it off | `NovaComposerActivity` had *two* handlers that each inverted the state (a switch `OnClickListener` plus the row's), so a tap on the switch cancelled itself out. The row label worked, which masked it. | One `setOnCheckedChangeListener` that records the state and calls `paintDestination()`; the row now calls `toggle()`. Verified: toggle turns blue and the Post/Story/Reel buttons appear. |
| 2 | **A Nova post set to "Never" expire could not be created** — `nova post failed: Could not choose the best candidate function between: public.create_nova_post(...)` | Two `create_nova_post` overloads, both with all arguments defaulted; the composer omits `p_duration_seconds` when the post never expires | Migration drops the stale overloads (§2). Verified: post created, then shared. |
| 3 | **Post detail crashed** — `NullPointerException ... List.iterator() on a null object reference` at `NovaCommentAdapter.rebuild:107` | `byParent.get("")` is null when the thread is empty or every comment is a reply, and it was iterated directly. So **any post with no comments crashed the detail screen.** | Null-check the root list before iterating. |
| 4 | **Thread crashed when a comment existed** — `ClassCastException: RecyclerView$LayoutParams cannot be cast to LinearLayout$LayoutParams` at `NovaCommentAdapter.CommentHolder.bind:176` | Indentation cast the row's layout params to `LinearLayout.LayoutParams`; inside a RecyclerView they are `RecyclerView.LayoutParams` | Use `ViewGroup.MarginLayoutParams` (the common supertype) and only fall back to a new one when the current params are not margin params. |
| 5 | **Post detail header was completely blank** — the card rendered with no author, body, score or badge | `start()` passed only the post id and `onCreate` never loaded or bound the post, so the inflated `<include>` stayed empty | `NovaPost` is now `Serializable` and handed over in the intent; the header is painted with the **feed's own renderer** (`PostHolder.bind`) so there is one card implementation, and `onVote` / `onSave` write through the repository and repaint. |
| 6 | **Sort popup was unstyled** and the sort chip touched the "Mine" chip | A bare `PopupMenu` (the only one in the app) with the default popup theme | Replaced with a `PopupWindow` over `popup_nova_sort.xml` / `bg_nova_popup.xml`: rounded surface, hairline edge, checkmark on the active sort; added an 8dp gap so the chip cannot collide. |
| 7 | **SOS blood-group chips: the selected chip looked empty** — a solid white pill with no label | `bg_nova_chip_on` is filled with `@color/nova_text`, and the selected label was also painted `@color/nova_text` — white on white in dark mode | Paint the selected label with `@color/color_background`, the inverse of the fill. Verified: the first chip now reads **"All groups"** in black on white. |
| 8 | **`SosMyActivity` had no error state** — a failed load was indistinguishable from "you have no requests", because both observers returned early on non-SUCCESS | Added `tvSosMyError` to the layout and a `showLoadError()` path that hides the two empty labels. Verified with wifi and mobile data both off: the screen now says *"Could not load your requests…"*. |
| 9 | **`SosDonorActivity` re-created the donor row on every save** instead of updating it | `save()` always called `createDonor`, so editing an existing registration tried to insert a duplicate row | Choose `updateDonor` when `registered` is already true, `createDonor` otherwise. |
| 10 | **Every selected chip in the Nova sub-modules rendered a blank pill** — Off/**Auto**/Always in Nova Lite showed an empty white segment; the composer's Post/Story/Reel buttons were label-less when selected | `bg_nova_chip_on` is filled with `@color/nova_text`, but ~12 call sites painted the selected label with `@color/nova_text` too — white on white in dark mode. This was systemic, not one screen: Lite, Insights, Campus, Campus Lost, Study, Q&A, SOS create, the SOS donor screen and the composer all had it. | Paint the selected label with `@color/color_background`, the inverse of the fill. The composer's `chooseShareType` now also sets the text colour per state, which it never did before. |
| 11 | **Nova Alerts drew two messages on top of each other when offline** — "All quiet in your universe" and "Something went wrong" overlapped into unreadable text | The error branch set `alertsError` VISIBLE while leaving `alertsEmpty` visible in the same slot | On error, hide `alertsEmpty` and `btnMarkAll`; on success, hide `alertsError`. Verified with wifi and data off: only "Something went wrong" shows, and the empty state returns cleanly when connectivity is back. |

---

## 5. On-device results

Device: Samsung Galaxy M21 (`R58N752AYPD`), Android 12 / SDK 31. Signed-in account `mrtest001` (`d9bf8f55-abca-4885-b44d-9501667949de`). Installed with `adb install -r` → `Success`. No crash on launch; reached `MainActivity` already authenticated.

| # | Test | Result |
|---|---|---|
| 1 | **Nova-only post absent from Instagram** | **PASS.** A Nova post was created, then checked against every Instagram surface: the home feed request `v_feed?is_reel=eq.false&limit=10&order=created_at.desc`, the Reels tab and the profile grid (`v_posts`), and stories. The Nova caption appears **0 times** in all captured Instagram responses. `v_posts` is sent with `space=eq.insta`. Visually: the feed and the 5-post profile grid contain no Nova post. |
| 2 | **Share toggle** | **PASS** (after fix #1). With the toggle on, the app called `create_nova_post` then `share_nova_to_instagram {"p_post_id":"a1d7118e-fa0d-478d-9f0b-380880252baa","p_type":"post"}`. The database holds Instagram row `616b8162-e5d4-4bb4-b1d2-7ed86d9d9330` with `space='insta'`, caption "Nova share test 2", `user_id=d9bf8f55-…` and **`source_nova_post_id=a1d7118e-…`**; the linkage check returned `true`. The post is visible on the Instagram feed. |
| 3 | **Anonymous hides identity** | **PASS.** Request body `{"p_is_anonymous":true,"p_media_type":"text","p_content_type":"text","p_duration_seconds":3600,"p_body":"Nova device test anonymous 1h","p_expires_at":"2026-11-02T10:19:08.850Z"}`. Response: `{"id":"f70f34c8-453d-4a81-b5b6-ad97944f4832","alias":"Anonymous Hidden Heron","author_id":null,"expires_at":"2026-11-02T10:19:08.85+00:00","is_anonymous":true}` — **`author_id` is null on the wire**. The feed row also carries `author_id:null, avatar_url:null, full_name:null`, and the card renders "Anonymous Hidden Heron" with the **An** badge and "JUST NOW · **Yours**". |
| 4 | **Expiry — badge, fade, disappearance** | **PASS, all three.** The live badge ticked down by itself (59m → 55m → 52m → 41m) without a refresh. A post seeded with 6 minutes left of a 1-hour window dimmed while a post with no expiry stayed full: peak glyph brightness measured **102 vs 255 = exactly 0.40**, the clamp floor (`Math.max(0.4f, Math.min(1f, ratio))` in `fadeAlpha`). After its window closed the card **disappeared from the feed** while the other post remained. |
| 5 | **Votes** | **PASS.** Upvote on another author's post → HTTP 200, arrow turns blue, score 0 → 1. Tapping the same arrow again → score back to **0** with both arrows grey. Downvote → score **−1** with the down arrow blue. Voting your own post is refused by the server with `own_content` (HTTP 400). |
| 6 | **Sorting** | **PASS.** Each option changes the server query, read straight off the wire: Hot → `order=hot_rank.desc`, New → `order=created_at.desc`, Top → `order=score.desc%2Ccreated_at.desc`. |
| 7 | **Filter sheet** | **PASS.** Renders Sort (Hot / New / Top with the live one ticked), Content Type (All posts / Photos / Reels / Text / Polls), **Hide anonymous**, **Hide expiring soon**, and Apply. |
| 8 | **`+` sheet** | **PASS.** CREATE → New Nova post; COMMUNITY → Nova Campus, Nova Study, Nova SOS; YOU → Nova Insights, Nova Safe; SETTINGS → Nova Lite. **No Alerts row.** |
| 9 | **Composer default** | **PASS.** "Nova only" pre-ticked with "Nova posts stay in Nova. Nothing reaches Instagram unless you ask."; "Also share to Instagram" off; options summary line reads "Anonymous · Expires in 1h". |
| 10 | **Comments** | **PASS.** A root comment posted through `create_nova_comment` and rendered with author, relative time, vote arrows and a **Reply** action. |
| 11 | **Threaded replies** | **PASS.** Tapping Reply shows a "Reply · mrtest001" banner; sending issued `{"p_is_anonymous":false,"p_parent_id":"b12f67af-ffe4-4a15-8e4f-77b88905574b","p_post_id":"62eefa34-…","p_body":"threaded reply"}`. The reply renders indented under its parent, one level deeper. |
| 12 | **Collapse thread** | **PASS.** Tapping "Collapse thread" hides the reply and the link becomes **"Show 1 replies"**. |
| 13 | **Chips** | **PASS.** Each chip changes the server query: Photos → `content_type=eq.photo`, Reels → `content_type=eq.reel`, Saved → `is_saved=eq.true`, Mine → `is_mine=eq.true`. Mine correctly shows "No posts yet / Your posts will appear here as soon as you share one." with a Create a post button. |
| 14 | **Save / bookmark** | **PASS.** `toggle_nova_save {"p_save":true,"p_post_id":"62eefa34-…"}`; the bookmark fills blue and the post then appears under the **Saved** chip. |
| 15 | **SOS error state** | **PASS.** With wifi and mobile data both disabled, My requests shows *"Could not load your requests. Pull down or open this screen again to retry."*; with connectivity restored the two empty labels return. No crash either way. |
| 16 | **Nova sub-screens open cleanly** | **PASS.** Campus, Study, Insights, Safe and Lite each resolved to the right activity with **0 fatal exceptions**; SOS and My requests were already covered. Nova Lite's segmented control reads **Off / Auto / Always** with Auto selected. |
| 17 | **Donor register vs edit** | **PASS.** First save → `POST /nova_donors` **201**; changing the district and saving again → `PATCH /nova_donors?user_id=eq.…` **204**, then a 200 reload. A deliberately invalid district (`Dhaka2`) was rejected by the server with **409 / `23503`**, the foreign key to `nova_districts` — correct validation, not a client fault. |
| 18 | **Nova Alerts** | **PASS.** With alerts seeded for the test user: the bell and the Nova tab icon both showed a **2** badge matching the 2 unread rows; the list rendered all three with correct titles, bodies, relative times (5m / 12m / 2h), a pink dot and bold title on unread rows only. "Mark all read" issued `PATCH /nova_alerts?user_id=eq.…&is_read=eq.false` → **204** and the badge cleared from both places. With wifi and data off the screen showed a single clean "Something went wrong"; restoring connectivity brought back the empty state. No crash in any state. |
| 19 | **Feed paging** | **PASS.** With 121 Nova posts seeded: the first request went out with no offset and `limit=100`, scrolling issued exactly one follow-up with `offset=100`, the second page appended (21 rows) and the feed reached the true oldest row (`nova-space-test`, 1 DAY AGO) after the seeded posts. No further request was made once the list was exhausted, and exactly **2** GETs were sent in total. Ties in the Hot order now fall back to `created_at.desc`, so the feed reads newest-first instead of by uuid. |
| 20 | **Build** | **PASS.** `BUILD SUCCESSFUL`; APK rebuilt, reinstalled and retested after every fix. Final smoke pass (launch → Nova → post detail → Instagram → Nova) produced **0 fatal exceptions**. |

### Cleanup
All test rows were deleted afterwards. The database is back to its pre-test state: one Nova post (`nova-space-test`, the pre-existing row migrated out of `posts`), score 0, **0 comments, 0 votes, 0 saves, 0 shared Instagram rows**.

### One false alarm, worth recording
A "NOVA TOOLS" panel appeared over the profile screen and looked like a leftover legacy sheet. It is **not** a bug: it is the fourth profile post — a *screenshot image* of the old Nova sheet, uploaded as normal Instagram media. `v_posts.media` for that account returns `.jpg` URLs, and `nova_sheet_tools` is referenced by no layout or class.

---

## 6. Not finished

1. ~~`public.posts` table-level read exposure~~ — **closed.** Applied 2026-11-03: revoked the unused `TRUNCATE` / `REFERENCES` / `TRIGGER` privileges from `anon` and `authenticated` and verified on device. Revoking table-level `SELECT` was investigated and dropped as ineffective — see the section above for why (`v_feed`, `v_posts` and `v_saved` all expose `user_id` and run with owner rights, so base-table column grants are invisible to clients). No further work needed here.
2. `NovaPostQueue` / `PostQueueWorker` still insert through `restApi.createPost` without options; they inherit `space='insta'`.
3. **Paging uses offset, not a keyset cursor.** The order is total (`id.desc` tiebreak) and rows are merged by id, so a card cannot appear twice, but a post whose rank or score changes mid-scroll can be shifted between pages. A keyset cursor on `(hot_rank, created_at, id)` would remove that; it was not done because `hot_rank` and `score` are mutable floats.
4. The detail header's comment count is the snapshot taken when the card was opened, so it does not increment after posting a comment from that screen (the feed shows the correct count on refresh).
5. Nova Safe's "By category" block has a cosmetic gap — the Insult/Hate/Spam legend sits below the empty label instead of above it. Pre-existing layout order, left alone.

---

## 7. Files changed (this work only)

The working tree already contained 157 modified files from earlier, unrelated work. The files **this task** touched:

**SQL** — `supabase/migrations/2026-11-02_nova_separation_post_options.sql` (+ `.rollback.sql`, `.verify.sql`), `2026-11-02_post_options_instagram_surface.sql`, `2026-11-02_nova_fade_window_and_hot_rank.sql`, `2026-11-02_create_instagram_post_media_array.sql`, `2026-11-02_drop_stale_rpc_overloads.sql`, `2026-11-02_harden_posts_grants.sql` (+ `.rollback.sql`, **prepared only — not applied**)

**Java** — `data/NovaDtos.java`, `data/NovaApi.java`, `data/repository/NovaPostRepository.java` (new), `data/repository/PostRepository.java`, `data/repository/ReelRepository.java`, `data/repository/StorageRepository.java`, `ui/post/PostComposerOptionsSheet.java` (new), `ui/nova/NovaComposerActivity.java` (new), `hub/NovaHubFragment.java`, `hub/NovaFeedViewModel.java`, `hub/NovaSheet.java`, `hub/NovaPostAdapter.java` (new), `hub/NovaCommentAdapter.java` (new), `hub/NovaPostDetailActivity.java` (new), `ui/home/HomeViewModel.java`, `lite/NovaLiteActivity.java`, `ui/create/CreatePostActivity.java`, `sos/NovaSosActivity.java`, `sos/SosMyActivity.java`, `sos/SosDonorActivity.java`, `sos/SosCreateActivity.java`, `lite/NovaLiteActivity.java` (chip labels), `insights/NovaInsightsActivity.java`, `campus/NovaCampusActivity.java`, `campus/CampusLostActivity.java`, `study/NovaStudyActivity.java`, `study/NovaQaActivity.java`, `alerts/NovaAlertsActivity.java`

**Resources** — `layout/fragment_nova_hub.xml`, `layout/sheet_nova_create.xml`, `layout/sheet_nova_filter.xml` (new), `layout/sheet_post_options.xml` (new), `layout/item_nova_post.xml` (new), `layout/item_nova_comment.xml` (new), `layout/activity_nova_post_detail.xml` (new), `layout/activity_nova_composer.xml` (new), `layout/popup_nova_sort.xml` (new), `layout/activity_nova_lite.xml`, `layout/activity_sos_my.xml`, `layout/create_step_caption.xml`, `values/strings.xml`, `values/strings_nova.xml`, `values/styles_nova.xml`, `drawable/ic_nova_upvote.xml` (new), `drawable/ic_nova_downvote.xml` (new), `drawable/ic_nova_sort.xml` (new), `drawable/ic_nova_anon_avatar.xml` (new), `drawable/bg_nova_popup.xml` (new)

**Manifest** — `app/src/main/AndroidManifest.xml`

**Removed** — `hub/NovaGridAdapter.java` (dead after the feed became a card list). The file is untracked in git, so it was **moved to `_archive/removed-code/`** rather than deleted outright; the dead string `nova_sheet_tools` was dropped from `strings_nova.xml`. Its `item_saved_media` layout was **kept** — the Saved screen still uses it.

---

# Part 2 — Settings audit (2026-11-03)

## 8. Every Settings row, audited

`SettingsActivity.buildItems()` defines **40 rows across 9 sections**. Each destination was launched directly with `am start` (plus its extras) and checked for a resume, a crash and its HTTP traffic.

All 40 opened. `AccountCenterActivity` and `SavedActivity` are `exported="false"`, so adb could not start them; they were reached by tapping instead. No fatal exceptions.

### Removed on request
The **Nova section** (header + 9 rows) was deleted from Settings, along with its 9 `onRowClick` branches. Five string resources became unreachable and were dropped from **both** `values/strings_nova.xml` and `values-bn/strings_nova.xml` — the first build warned `removing resource … without required default value` until the Bengali copies went too. The Nova modules remain reachable from the Nova hub.

## 9. Options that existed but changed nothing

Counting readers for every preference getter found five settings with **zero consumers**, plus three that only fed the data-download JSON:

| Setting | Readers before | What it did |
|---|---|---|
| `vibrateCalls` | 0 | `CallRingtone` vibrated unconditionally |
| `largerText` | 0 | Accessibility switch had no effect |
| `requestWho` | 0 | Message request controls gated nothing |
| `language` | 0 | see §10 |
| `tagWho` / `mentionWho` / `commentWho` | 1 (`DownloadDataActivity`) | exported only, never enforced |

Fixed:
- `CallRingtone.start()` now reads `vibrateCalls()` before arming the vibrator.
- `InstaApp` registers an `ActivityLifecycleCallbacks` and applies a 1.18× font scale in `onActivityPreCreated` — the last hook that still runs before `onCreate` inflates anything. Scale is normalised against 1.0 each time so repeated Activity creation cannot compound it.
- The four who-can pages now mirror the choice onto the `profiles` row, and `tg_message_request_state` reads `request_who` (see §11).

## 10. Language — the real cause is missing translations

Two fixes were attempted first: declaring `AppLocalesMetadataHolderService` with `autoStoreLocales`, and making `applySavedLocale()` treat `AppSettingsStore` as the source of truth instead of trusting AppCompat's stored copy. Neither changed the result.

The cause is data, not code:

```
res/values/      strings.xml 261   settings_ext.xml 338   (599 total)
res/values-bn/   strings.xml   1   settings_ext.xml   -   (1 total)
```

There is **no** `values-bn/settings_ext.xml`, and the Bengali `strings.xml` holds a single string. Bengali could never render regardless of how the locale was applied. Making this option work is a translation project covering ~599 strings, not a code fix — left open deliberately.

## 11. Restricted accounts — implemented end to end

The list already round-tripped (verified: seeded a `restricted_users` row, it appeared with **Unrestrict**, tapping it issued `DELETE /rest/v1/restricted_users` and the row count returned to 0). What was missing was the behaviour the screen promises.

**Migration** `2026-11-03_settings_restrict_enforcement.sql` (validated in a rolled-back transaction, then applied):
- `profiles.comment_who / tag_who / mention_who / request_who` with check constraints
- `is_held` on `comments`, `nova_comments`, and the `nova_comments_public` view
- `tg_hold_restricted_comment` / `tg_hold_restricted_nova_comment` — hold on insert, server-side so a modified client cannot skip it
- `comments_select` rewritten from `using (true)` to hide held rows from everyone except their author and the post owner
- `approve_held_comment` / `approve_held_nova_comment`
- `request_who` enforced inside `tg_message_request_state`

**Instagram comments are Firestore, not Postgres.** Reading `CommentsBottomSheet` showed the thread comes from `firestore/comments`, so the Postgres `is_held` work was inert for that client. Closed the gap properly:
- `firestore.rules` — `canViewComment()` now hides a held comment from everyone but its author and the post owner, using `c.get('held', false)` so documents predating the field do not error. Only the owner may clear the flag (`affectedKeys().hasOnly(['held'])`).
- `am_i_restricted_by_post_owner(p_post)` RPC — the check runs server-side because the restricted list belongs to the owner.
- Client writes `held` on create, labels the row, and offers approve-by-long-press to the owner.

**Deployed:** `firebase deploy --only firestore:rules --project instanova-62ff0` → *rules compiled successfully*, *released to cloud.firestore*.

**One bug caught before shipping:** the owner was going to be passed as `Post.getUserId()`, which is the Supabase uuid, while a comment's `authorUid` is a Firebase uid. The two never match, so the approve action would never have appeared. The sheet now reads `authorUid` from the Firestore post document — the same value the rules compare against.

## 12. Insights screens — charts

Both screens already read real Supabase rows (`getPosts`, `getProfilesRaw`, `getLikes`, `getCommentsRaw`), so no data layer changed.

New `ui/widget/InsightsChartView` — Canvas only, no new dependency: **AREA**, **BAR** and **DONUT**, theme-attribute colours, 600 ms draw animation, touch scrubbing with a tooltip, and an empty state.

**Post and Reel insights:** 7 / 30 / 90 day pills → four stat cards → interactions-per-day area chart → interactions-by-post bar chart → likes/comments ring with legend.

**Audience analytics:** follower/following cards → interactions-by-audience bar chart (#1–#5) → Top 5 vs Everyone else ring → most-engaged list.

Instagram's own screen leads with Views / Accounts reached / Interactions / Followers over a 7-30-90 range ([oposto.app breakdown](https://oposto.app/en/blog/read-instagram-insights)). This app has no view or reach counters, so interactions stand in and the note says so plainly. Age/city/gender need data the app does not collect, so none is shown rather than filled with placeholders.

Three defects found by looking at the rendered output:
1. **Charts blocked page scrolling** — `requestDisallowInterceptTouchEvent(true)` fired on `ACTION_DOWN`, so any gesture starting on a chart (most of these pages) never scrolled. Now the gesture must be horizontal before the chart claims it.
2. **Labels clipped** — the last x label sat centred on the plot edge and rendered "3 No" instead of "3 Nov". Text is clamped by measured width.
3. **Bar labels useless** — five test usernames truncated to the same "novatest…". Now ranked `#1…#5`, with the list underneath naming them.

Also changed the audience ring from likes-vs-comments to **Top 5 vs Everyone else**, because the former duplicated the ring on the insights screen one tap away.

Verified on device with real data: 9 posts, 1 reel, 23 likes, 16 comments, ring centre 39; followers 4 / following 3, top engaged `@novatester1` with 6 interactions.

## 13. Still open

1. **Language** needs ~599 strings translated (§10).
2. `vibrateCalls`, `largerText`, `request_who` and the held-comment approve flow are **built and compile, but not verified on device** — each needs a second account or an incoming call to observe.
3. A leftover test comment reading `restrict_probe` sits on this account's own post from the RPC plumbing test. It is author-deletable by long-press in the app.
4. Offset paging rather than a keyset cursor; the legacy Instagram queue still inherits `space='insta'`; the Nova detail comment count is a snapshot (carried over from Part 1).

## 14. Verification added after §13 was written

**Held-comment trigger, both cases** — run inside a transaction that was rolled back, so no test row survived:

```
restricted author   → insert comment → is_held = true
unrestricted author → insert comment → is_held = false
```

**Larger text** — switched on, force-stopped the app, cold-started Settings: text rendered at the new scale ("Message request controls" wrapped to two lines) and `larger_text_<uid>=true` was still in `insta_app_settings.xml`. Switched back off afterwards, so the account is left as it was found.

**Insights date range** — `7 days` re-queried the same fetch into a 7-day window: 1 post / 0 reels / 0 likes / 0 comments, axis 28 Oct → 3 Nov, against 9 / 1 / 23 / 16 for `30 days`. The chips re-render from data already in memory, with no extra request.

**Two defects that check turned up**, both fixed and re-verified on device:
1. An all-zero range drew a flat line at zero, which reads as a rendering fault. A range where every bucket is zero now shows "No interactions in this range".
2. That message was initially reused as "Nothing posted in this range" for the bar chart — wrong, because the Posts card directly above read 1. Both charts now use the interactions wording.

**Firestore rules deployed** to `instanova-62ff0`: compiled successfully, released to cloud.firestore. A comment thread was opened and a comment posted afterwards to confirm no regression — the thread loaded and the new comment appeared.

## 15. Settings options exercised one by one

Each control was tapped on the device and the result read back from SharedPreferences
(`insta_app_settings.xml`, `insta_settings.xml`) and, for the who-can settings, from
the `profiles` row.

| Option | Written value observed | Verdict |
|---|---|---|
| Notifications → master | `push_master` | **was broken, now fixed** |
| Notifications → likes/follows/DMs/stories/calls | `push_likes_comments`, `push_follows`, `push_dms`, … | works |
| Read receipts / online status | `read_receipts`, `show_activity` | works |
| Data saver | `data_saver` | works |
| Accessibility → larger text | `larger_text` + visible scale change | works |
| Theme | `app_theme_mode` in `insta_settings.xml` | works |
| Autoplay | `video_autoplay=never` | works |
| Upload quality | `upload_quality=high` | works |
| Tags and mentions | `tag_who`/`mention_who`, mirrored to `profiles` | works |
| Comment controls | `comment_who=followers` | works |
| Message request controls | `request_who=nobody`, enforced in `tg_message_request_state` | works |
| Account type | `account_type=business` locally + on `profiles` | works |
| Cache, permissions, saved, archive, insights, audience, help, legal, about | open and render | no crash |

### Two defects fixed

1. **Notifications master switch could not be turned off.** Its listener called
   `recreate()`, which re-dispatched the touch, so one tap toggled twice. Logcat
   showed two `Schedule relaunch activity` lines and `push_master` never changed.
   Replaced with in-place enabling of the five dependent switches — the rebuild only
   existed to update their enabled state anyway.
2. **Disabled sub-switches still looked enabled.** `styleSwitch` assigns explicit
   tint lists that ignore the disabled state, so the switches stayed full blue and
   swallowed taps without any visual cue. They are now dimmed to 40% as well as
   disabled.

### Testing notes (my tooling, not the app)
Two false negatives cost time and are worth recording: a failed `uiautomator dump`
silently re-reported the previous screen's XML, and my prefs regex only matched
`value="…"` attributes while string entries store their value as element text. Both
made working options look broken.

Settings changed during this audit (who-can values, autoplay, upload quality, theme,
account type) were restored afterwards; `profiles` was set back to `everyone` for all
four who-columns and `personal` for account type.

---

# Part 3 — Does the feature behind each setting exist?

Part 2 checked that Settings *writes* a value. That was the wrong question, and this
part corrects it: a setting can persist perfectly and still control nothing, because
the feature it governs was never built.

## 16. Feature-existence audit

Each preference getter was counted **outside** the settings and export screens, which
is what "does anything else read this?" means:

| Setting | Feature readers | Reality before this pass |
|---|---|---|
| Tags and mentions (`tagWho`) | **0** | No way to tag anyone anywhere |
| Comment controls (`commentWho`) | **0** | Nobody to gate — no enforcement |
| ~~Story audience (`storyAudience`)~~ | ~~0~~ | **corrected in section 22 — this was wrong** |
| Close friends | 3 | works |
| Hidden words | 1 | filters comments |
| Data saver | 1 | works |
| Autoplay | 1 | works |
| Upload quality | 1 | works |

## 17. Tagging — implemented

`public.post_tags` already existed but **nothing ever inserted into it**. Its only
reader was `ProfileFragment`'s "Tagged" tab, so that tab could only ever show the
empty state. `CreatePostActivity` even declared a `row_tag_people` and then hid it in
a `decorativeRows` array marked design-only. So the tag settings controlled a feature
that did not exist.

**Migration** `2026-11-03_post_tagging.sql` (validated in a rolled-back transaction,
then applied):
- `x` / `y` position columns with a 0–1 range check, unique index on `(post_id, user_id)`
- RLS: tags readable by all, creatable and deletable only by the post's author
- `tg_enforce_tag_who` — rejects a tag when the tagged account is `nobody`, and
  requires the poster to follow them when it is `following`
- `set_post_tags(p_post, p_users)` — replaces the set, refuses anyone but the author

**Client:**
- New `TagPeopleActivity` — search, multi-select, "Done (n)", returns ids and names
- Composer's Tag people row is live again and shows `@a, @b`
- `applyTags()` runs after the post is created and writes the set

**Verified on device, end to end:** composer → Tag people → selected @novatester1 and
@novatester12 → row showed both → Share → two rows landed in `post_tags` for post
`8b6f9d56…`. The test post and its tags were then deleted.

**Enforcement verified at the database:** inserting a tag for an account with
`tag_who='nobody'` fails with `42501 this account does not accept tags`; the same
insert succeeds when `tag_who='everyone'`.

## 18. Still missing behind a working setting

1. ~~**Comment controls** (`commentWho`) — still 0 readers.~~ **Closed in section 21.**
2. ~~**Story audience** (`storyAudience`) — still 0 readers.~~ **Wrong, see section 22.**
3. **Language** — unchanged; the translations themselves are absent.

These are recorded rather than half-built, so the next pass starts from a known list.

One correction belongs here, because the method matters more than the score. Counting
"readers of a preference getter" found real gaps for `tagWho` and `commentWho`, but it
mislabelled Story audience. That row is not a preference at all — it opens a people
list and its subtitle is a count, and the decision is read from `story_audience` by
`is_story_hidden()`. Searching for a `storyAudience()` getter was searching for
something that was never built that way, and reporting "0 readers" from an empty
search is not evidence a feature is missing. Section 22 has the real finding.

## 19. Story tagging, and the viewer's duplicate menu

**Story tagging did not exist either.** The story editor already had a
`btn_editor_tag` icon, but it sat in the same hidden "design-only" list as the
sticker and music icons, and `stories` had no tag storage at all — `post_tags`
cannot reference a story.

Migration `2026-11-03_story_tagging.sql` (validated in a rolled-back transaction,
then applied): `story_tags` table with position columns and a 0–1 check, RLS limited
to the story's author for writes, `tg_enforce_story_tag_who` applying the same
`tag_who` rule as posts, and `set_story_tags(p_story, p_users)`.

Client: the tag icon is visible again and opens `TagPeopleActivity`; the selection
appears on the preview as `@username`; `applyStoryTags()` writes the set once the
story row exists, using the id from the create response, which the old code discarded.

**Verified on device end to end:** story editor → tag icon → selected @novatester1 →
preview showed the tag → published → one row in `story_tags` for story `3f8c7e3b…`.
Story and tags were deleted afterwards.

**Viewer cleanup:** the story viewer bound *two* controls to the same `showMoreSheet()`
— `btn_story_more` (the top-right 3-dot, kept) and `btn_owner_more`, a "More" text
next to the viewers count. The second was redundant and has been removed from
`activity_story_viewer.xml` along with its binding. Confirmed on device: the viewer
now shows the 3-dot and "Seen by 0" with no trailing "More".

## 20. Story reactions were delivered as direct messages

`StoryViewerActivity.sendReaction()` called `sendMessageToOwner(payload, null)`, which
runs `getOrCreateConversation()` and then `sendMessage()`. So tapping an emoji on
someone's story created a conversation and posted the emoji into it as a DM — the
story owner saw a message thread rather than a notification.

**Fix:**
- `react_to_story(p_story, p_reaction)` RPC — resolves the story's owner and inserts a
  `notifications` row (`type='story_reaction'`, `body` = the emoji). It never touches
  `conversations` or `messages`, and it returns early when reacting to your own story.
- `sendReaction()` now calls that RPC. The on-screen floating emoji animation is kept.
- `sendMessageToOwner()` is still used by `sendTypedReply()`, which is right: a typed
  reply **is** a message; tapping an emoji is not.
- `NotificationFeedAdapter` gained a `story_reaction` branch. Without it the type fell
  into the catch-all and rendered as "liked your post".

**Verification status:** the RPC exists and the DM path is gone from `sendReaction`
(zero references to `sendMessageToOwner` inside it). The end-to-end tap was **not**
observed: it needs a story owned by another account, and the one seeded for the test
showed a placeholder ring and closed the viewer before the reaction bar appeared.
Counts taken before/after a tap attempt were unchanged (`notifications` 0,
`messages` 143, `conversations` 14) — consistent with no request being made, but not
proof either way. Test story and follow row were removed afterwards.

## 21. Comment controls — enforced

`profiles.comment_who` was written by Settings and read by nothing except the
data-export screen, so any account could comment on any post regardless of the
owner's choice. Migration `2026-11-03_comment_controls.sql`, validated in a
rolled-back transaction then applied:

- **Nova — real enforcement.** `tg_enforce_nova_comment_who` rejects a `nova_comments`
  insert when the post owner's `comment_who` excludes the author. Nova comments are
  Postgres rows, so this cannot be skipped by a modified client.
- **Instagram — advisory + client gate.** Firestore rules cannot read Postgres, so
  `can_comment_on_post(p_post)` exposes the same decision and `CommentsBottomSheet`
  asks before writing, rolling back the optimistic row and showing
  "This account does not allow comments from you" when refused. A determined client
  could still write; that limit is inherent to storing comments in Firestore, and is
  recorded rather than hidden.

**Verified at the database, both directions:**

```
owner comment_who='followers', non-follower inserts
  -> 42501 the author of this post only allows comments from their followers
owner comment_who='everyone', same author inserts
  -> 1 row written
```

**Verified in the app:** posting a comment now issues `can_comment_on_post` and
`am_i_restricted_by_post_owner` before the Firestore write (logcat shows both).

**Left over:** a comment reading `cc_gate_probe` remains on this account's own post
from that check — Firestore documents are not reachable from the SQL used here, and
the automation could not drive the long-press delete.

## 22. Story audience — the real gap, and a correction

**First, a retraction.** The audit table said Story audience had 0 feature readers.
That was produced by searching for an `storyAudience()` preference getter and finding
nothing, which was the wrong search. The row is not a preference: Settings opens
`PeopleListActivity` in `MODE_STORY_AUDIENCE` and the subtitle is a count of hidden
people. The decision lives in the `story_audience` table and is read by
`is_story_hidden()`. An empty search for a getter that was never written that way is
not evidence a feature is absent. The filters were already there:

```
v_stories        -> story_visible(user_id, audience) + is_story_hidden(...)
get_story_groups -> the same two, plus a followed-users restriction for the tray
story_visible    -> 'close_friends' honoured through is_close_friend()
is_story_hidden  -> story_audience(owner_id, viewer_id)
```

So the hide list and close_friends did work in the app's own screens. What was actually
broken is narrower and worse: `public.stories` carried `stories_select USING (true)`.
The views filtered, the table did not. `GET /rest/v1/stories` returned every active
story with its `media_url` regardless of the hide list, so the whole guarantee rested
on the client choosing to use the view — the same shape as the `posts` grant issue.

**Measured, as a simulated end user inside a rolled-back transaction** (viewer
`3c96a7cc-…`, a public story inserted for owner `b0000000-…`):

| step | rows | meaning |
|---|---|---|
| base table, old `USING (true)` | 23 | every story, expired or hidden |
| view, old policy | 3 | view already filtered |
| base table, new policy | 3 | **base now equals the view** |
| view, new policy | 3 | unchanged |
| another owner's public story | 1 | still readable |
| after adding that owner to the hide list | 0 | hide list enforced on both |
| `audience='close_friends'`, viewer not a member | 0 | closed |
| same, after adding viewer to close friends | 1 | open |
| anon, `stories` | 0 rows | no caller, no data |

**One extra hole the test found:** after fixing the table, anon still read 3 rows from
`v_stories`, because a view owned by postgres runs with owner rights and never applies
the caller's RLS — and `story_visible()` returns true when `request_uid()` is null.
`v_stories` lost its `anon` grant. `get_story_groups()` already excluded anon.

Migration `2026-11-03_story_audience_rls.sql`, applied. Also revokes
TRUNCATE / REFERENCES / TRIGGER on the three story tables, matching the posts hardening.

**Device check after the change:** feed renders, tray shows "Your story", the viewer
opens and plays, "Seen by 0" and the top three-dot menu render, and logcat shows no
42501, no PGRST error and no fatal exception. Only this account has active stories, so
the other-party cases are proven at the database rather than on the phone.

**Still open, honestly:** the storage bucket is not part of this. RLS stops the row and
with it the `media_url`, but anyone already holding a URL keeps it. Making media itself
audience-aware means signed URLs or private-bucket policies, which is a separate change.

## 23. Story reactions — proven, and the gap behind them

The earlier section recorded that the DM was gone but the notification was never
observed. It is observed now, and chasing it exposed two further defects.

**The tap works.** A public story was created for the second account, that account
followed, its tray bubble opened, and the heart in the reply bar tapped. Counts either
side:

| | before | after |
|---|---|---|
| `notifications` (all) | 3 | 4 |
| `notifications` where `type='story_reaction'` | 0 | **1** |
| `messages` | 143 | **143** |
| `conversations` | 14 | **14** |

The row itself:

```
user_id   = b0000000-…  (story owner — correct recipient)
actor_id  = 3c96a7cc-…  (the reactor)
type      = story_reaction
body      = ❤️
read      = false
```

No message row, no conversation row. A reaction notifies; it does not open a DM.

**Defect 1 — the notification screen never read the table.** `NotificationCenter`
generates rows locally from in-app events; `RestApi.getNotifications()` existed but was
called only from `SyncTestActivity`. So `react_to_story()` wrote a correct row into
`public.notifications` that no screen on earth would ever show the owner. The insert
was right and the read side simply did not exist.

Fixed: `NotificationCenter.syncFromServer()` fetches this account's rows
(`user_id=eq.…`, newest first, limit 60), resolves `actor_id` to a name and avatar from
`profiles`, and merges them into `NotificationStore.mergeAll()` — idempotent on id, and
an already-read local row keeps its read flag. `NotificationsActivity` renders the local
list first so the screen is never blank, then re-renders after the sync. This also
brings real `follow` rows into the screen, which had the same problem.

**Defect 2 — server rows were dated to zero.** `createdAt` was `Long.parseLong(...)` in
two places. Local rows carry millis, server rows carry ISO-8601, so every synced row
parsed to 0 and sorted to the very bottom of the list. Even after the sync ran, the
reaction looked absent. Fixed with `Notification.createdAtMillis()`, which accepts both
shapes; both call sites now delegate to it.

**On device, final state:** the row renders as
`novatester1 reacted to your story 🔥`, and a synced follow row renders as
`mrtest001 started following you` with its Follow button — the sync is general, not a
special case for reactions.

**Cleanup:** the test story, the follow edge, both test notification rows and the local
mirror were removed; counts are back to 3 notifications / 0 reactions / 143 messages /
14 conversations. The two leftover test comments (`cc_gate_probe`, `restrict_probe`)
were deleted from the post in the app, which also closes the caveat in section 21.

**Remaining limit, stated plainly:** a reaction appears in the app when the owner opens
the Notifications screen. There is no realtime subscription and no FCM message for
`story_reaction`, so nothing arrives while the app is closed. Push delivery was not part
of this request and has not been added.

## 24. Push delivery — a react reaches a closed app

Section 23 closed with a limit: the reaction only appeared when the owner opened
Notifications. This adds the delivery half.

**Server side.** Migration `2026-11-03_notification_push.sql` adds `push_dispatched_at`
to `public.notifications` and an AFTER INSERT trigger `tg_push_notification` that posts
the new row to a new Edge Function through `pg_net`. `pg_net` queues and returns, so the
insert is never blocked by Google and a slow FCM response cannot fail the reaction. The
trigger only fires for `story_reaction`, `follow`, `follow_request` — the types the app
actually presents.

**The function.** `supabase/functions/send-notification-push`, deployed to
`poqdemxiuoocbzkwbyhy`. It claims the row by setting `push_dispatched_at` before doing
any network work, so a retried webhook or a duplicated call still produces exactly one
buzz per notification. It reads the recipient's `fcm_token` from `profiles`, resolves
the actor's name and avatar, and sends a **data-only** high-priority message — the same
choice made for calls — so the client's per-type switches stay authoritative instead of
being bypassed by a `notification` block.

Deployed with `--no-verify-jwt` because the trigger carries no user session. That is
safe here only because the function acts on rows that already exist and are undispatched:
a guessed or repeated call delivers an existing notification once, and can never create
one.

**Client side.** `InstaFirebaseMessagingService` now handles these types alongside calls:
checks `allowsNotificationType()`, builds `"<actor> reacted to your story <emoji>"`, and
posts it on a new `instanova_activity_v1` channel that opens Notifications on tap.

**Measured, on the device with the app backgrounded:**

```
InstaFCM: FCM Message received from: 39751114981
InstaFCM: FCM Message data payload: type=story_reaction, callId=null
```

and the shade showed **`novatester1 — reacted to your story 🔥`** with the app icon.
The pg_net response for the same row:

```
{"success":true,"notification_id":"d00dfeed-…","type":"story_reaction",
 "dispatched":1,"fcm_status":200,
 "result":{"name":"projects/instanova-62ff0/messages/0:1793715430659639%fd9369ec…"}}
```

**A silent failure worth recording.** On this install `POST_NOTIFICATIONS` was declared
but not granted (Android 16). `NotificationManagerCompat.notify()` is a **no-op in that
state — it does not throw**, so the push was delivered, the service ran, and nothing
appeared with no error anywhere. `MainActivity` does request the permission, but a
declined prompt leaves this silent gap. The service now checks
`areNotificationsEnabled()` and logs why nothing was shown.

**Not exercised:** tapping the notification. The intent is explicit to
`NotificationsActivity` and the OS accepts the `PendingIntent` (`startActivity` is
visible in `dumpsys notification`), but automation could not land a tap on it — a
Messenger in-call notification kept taking the top slot and every attempt opened that
instead. Tap-through therefore remains unverified on device rather than assumed.

Test rows were deleted afterwards; notifications are back to 3, reactions 0.

## 25. Notifications that are switched off now say so

Section 24 left a hole: when the OS blocks the app's notifications, every push is
dropped with no trace — `notify()` neither throws nor logs. Nothing inside the app
admitted it.

**The banner.** `activity_notifications.xml` gained a strip under the top bar, shown
only while delivery is actually blocked: "Notifications are off for InstaNova, so
reactions and follows will not reach you." with a single **Turn on** action.

**The action behaves honestly.** `NotificationPermission` (new, `util/`) decides between
two different recoveries:

- while the OS would still show a prompt, ask for `POST_NOTIFICATIONS`;
- otherwise open `ACTION_APP_NOTIFICATION_SETTINGS`, because a third `launch()` produces
  no dialog and no result — a button that would appear to do nothing.

**Cold-start nagging removed.** `MainActivity.ensureNotificationPermission()` used to
call `launch()` on every launch regardless of whether the OS would show anything. It now
asks once, then only while `shouldShowRequestPermissionRationale()` is still true. A
double denial is left to the Notifications screen, which explains the state and offers
the settings link.

**Verified on device, both directions:**

| state | observed |
|---|---|
| `pm revoke POST_NOTIFICATIONS` | banner visible under the top bar with Turn on |
| tapping Turn on | system notification settings for the app opened (`NotificationSettingsActivity`) |
| `pm grant POST_NOTIFICATIONS` | banner gone, list starts immediately under the top bar |
| push after grant, app backgrounded | notification posted (`cmd notification list` shows the row) |

**Decision on the third bullet — keep dispatching when notifications are off.** The
server cannot read a device's permission state, and hiding the push would not preserve
anything: the row is the source of truth and `NotificationCenter.syncFromServer()` reads
it regardless, so a user who enables notifications later still sees every reaction in the
list. Marking the row dispatched therefore loses nothing — it only means they were not
interrupted at the time. No change made, deliberately.

## 26. Story media — public by default, until now

Section 22 stopped a hidden story's row from being read, and with it `media_url`. The
bytes were untouched: all six buckets (`avatars`, `chat`, `nova`, `posts`, `reels`,
`stories`) were `public = true`.

**Measured before touching anything** — fetching the newest story's `media_url` with
`curl` and no credentials at all:

```
no-auth HTTP 200, 57927 bytes
```

So an expired or hidden story stayed downloadable by anyone holding the link, forever.

**What changed** (migration `2026-11-03_story_media_private.sql`, applied):

1. `stories` bucket → `public = false`.
2. `storage_stories_read` was `bucket_id = 'stories'` — every signed-in account could read
   every story object. It now allows exactly two cases:
   - the object sits in the caller's own folder (`storage.foldername(name)[1] = request_uid()`),
     which the archive screen needs for expired stories and the upload verification needs
     before the row exists;
   - otherwise there is a matching `stories` row that is unexpired, permitted by
     `story_visible(audience)`, and not in the owner's hide list.
3. Upload / update / delete policies untouched — publishing still works.

**Client.** `MediaUrl.authenticated()` swaps `/object/public/` for `/object/authenticated/`,
and `GlideUtils` attaches `apikey` + `Bearer` headers for buckets in `PRIVATE_BUCKETS`.
The headers are `LazyHeaders`, because resolving a Firebase token blocks and Glide
evaluates them on its own loading thread.

**A defect this would have introduced, caught before shipping.** `CreateStoryActivity`
verified every upload with a `HEAD` against the **public** URL and refused to create the
story row unless it returned 200. With the bucket private that check would have failed
forever, and every story publish would have reported "HEAD verification failed" while the
bytes were in fact stored. The check now targets the authenticated endpoint, which is why
the policy grants owners their own folder.

**Verified:**

| check | result |
|---|---|
| `storage.buckets.public` for `stories` | `false` |
| same `media_url`, no credentials, cache-buster | HTTP 400 `NoSuchBucket` |
| every active story object, no credentials | 400 |
| storage policy, owner's own object (simulated JWT) | visible |
| storage policy, another owner's visible story | visible |
| same after adding that owner to the hide list | **0 rows** |
| app, story viewer, Glide cache cleared | image renders |
| app, Archive → Stories (expired, owner) | thumbnails render |
| app, publish a new story end to end | row created, viewer shows the new object |

**One honest caveat.** For a short window after the flip, Cloudflare kept serving the
already-cached public response: the plain URL returned 200 while the same URL with a
cache-buster returned 400. By the final check both returned 400, so the window closed —
but anyone who fetched during it kept a copy, and nothing can undo that.

**Not done, deliberately:** `posts`, `reels`, `nova`, `chat` and `avatars` are still
public buckets. Those are public by design in this product (a post is public once posted),
and making them private would mean signing every media URL across the app — a change
large enough that it should be a decision, not a side effect. `stories` differed because
it is the one surface with a per-row audience.

## 27. Nova bugs reported on 6 Nov — what was actually wrong

### Polls
No vote existed: `nova_poll_votes` was absent and `nova_cast_vote` accepts only
`post` / `comment`, so `poll_votes` could only be null. The post card had no poll UI
either — `item_nova_post.xml` had no container, so a poll rendered as a plain text post.
The user's own poll ("Bello", options `["1","2"]`) proved it: stored correctly, invisible.

Added: `nova_poll_votes` (one row per voter, RLS lets a voter read only their own),
`nova_vote_poll(post, option)` which validates the option, upserts the choice and **rebuilds**
the counts array so changing a vote cannot inflate the total, and `pollBox` + row rendering
in the adapter with tap-to-vote.

Verified: rolled-back call gave `[1,0,0]` then `[0,0,1]` with total 1 after changing the
vote, and rejected option 7 with `invalid_option`. On device, tapping option 1 wrote
`poll_votes = [1,0]` and one `nova_poll_votes` row.

### Campus
- "Create event" was a white pill with white text: `bg_nova_chip_on` is a solid
  `nova_text` fill and the label used `nova_text` too — invisible in the night palette.
  `btnCreateEvent` and `btnNew` now use `color_background`.
- Every event insert failed on `null value in column "category"`; the dialog always passed
  null. A category picker (academic/cultural/sports/tech/club/other) now supplies it.
  Verified: "Campus test event", category `other`, listed in the app.
- "Not verified" was a dead label. It is now tappable: it re-runs the same university-email
  domain check the join flow uses and, when it fails, names the account email and the
  domains the university accepts.

### SOS
District was free text against a foreign key. Measured 409 / `23503` "Key is not present
in table nova_districts". Replaced with a picker over `nova_districts`; donor insert became
an upsert so a failed read can no longer cause a duplicate-key error. Verified by creating
a donor (Chattogram) and a blood request (Chattogram, "Test hospital") on the device.

### Campus — post types walked on the device

| screen | result |
|---|---|
| Events → Create event | works; row `Campus test event`, category `other`, listed as a card |
| Lost & Found → Report | visible now (it was the second white-on-white pill) and works; row `Test wallet`, kind `lost`, university 1 |
| Confessions | opens, shows "No confessions yet" and offers no way to post |

Confessions is the honest remainder: posting requires `verified = true`, and this account's
email is not a university address, so the button never appears. The screen does not say
why, which reads as "broken" to the user.

Two smaller defects found while walking these screens and **not yet fixed**:
- The Lost & Found report dialog is labelled "Event title" and "Venue" — copied from the
  event dialog.
- The Confessions empty state gives no explanation of the verification requirement.

### The two small defects, closed

**Lost & Found report dialog** reused the event dialog's copy: it asked for an "Event
title" and a "Venue" when reporting a missing phone. Now "Item" and "Where it was lost or
found". Verified on device.

**Confessions** showed an empty screen with no composer and no explanation. The cause was
in `CampusConfessionsActivity`: the verification observer reacted only to `SUCCESS`, so a
failed or absent check left the composer hidden **and** the explanation hidden. The state
is now rendered before the check and on `ERROR`, defaulting to unverified-with-reason.
Verified on device: the screen now reads "Log in with your university email to get
Verified. Confessions need a verified account." above the empty list.

## 28. "Available to donate" was a label, not a control

Reported as: the availability option does nothing.

**What it actually was.** There was no availability control at all. `btnSave` — the main
action button — was simply *labelled* `nova_sos_available` ("Available to donate"), and
both write paths passed a literal:

```java
repository.updateDonor(group, district, null, last, true)
repository.createDonor(group, district, null, last, true)
```

So a donor could never step back, and the only visible "available" affordance was a button
whose label contradicted what it did. The stored value mattered: `nova_match_count` joins
`nova_donors` with `dn.available`, so this flag decides whether a donor is counted for a
request.

**Fix.** A real `SwitchCompat` row ("Available to donate") above the action button; the
switch is loaded from `donor.available` and its checked state is what the save now sends.
The action button says **Save** (the existing `nova_save` string) instead of borrowing the
switch's label.

**Verified on device**, both directions, reading the row back each time:

| action | `nova_donors.available` |
|---|---|
| toggle off, Save | `false` |
| toggle on, Save | `true` |

## 29. Nova header reorganised

Requested layout, and what the device now shows:

| position | before | now |
|---|---|---|
| left | title "Nova" | **+** create |
| centre | — | **Nova** title |
| right (1st) | filter | filter |
| right (2nd) | + create | **bell** (with unread badge) |
| right (3rd) | bell | **grid** — tools |

The create sheet used to list every Nova space in one list of seven. It is now split:

- **+** → New Nova post · Nova Campus · Nova Study · Nova SOS
- **grid** → Nova Insights · Nova Safe · Nova Lite

`NovaSheet` keeps one layout and hides the rows that belong to the other menu, so the
subtitles inside those rows disappear with them. Verified by reading the rendered text of
both sheets on the device: the create menu contains exactly those four entries and the
tools menu exactly those three.

## 30. Follow-ups from 7 Nov

### Campus verification by student email
Added because the join-time check cannot help an account registered with a personal mailbox.
Rule taken from the product owner: student mailboxes are issued as `@stud.<university domain>`,
so the domain after the @ must be exactly `stud.` + one of the university's `email_domains`.

`nova_campus_verify_email(p_email)` (migration 2026-11-07_campus_email_verify.sql) records
`verified_email` and sets `verified = true` only on that match. Checked in a rolled-back
transaction:
- `me@gmail.com` -> verified false, reason `domain`, expected `["stud.kuet.ac.bd"]`
- `bad` -> verified false, reason `invalid_email`
- `me@stud.kuet.ac.bd` -> verified true

On device, tapping "Not verified" now opens a dialog with the address hint; submitting a
valid address wrote `verified = true` and `verified_email = test@stud.kuet.ac.bd`. The test
address was reverted afterwards so the account is not left falsely verified.

### Header menus, second pass
The first split left the section captions behind, so the create menu still showed YOU and
SETTINGS and the tools menu still showed CREATE and COMMUNITY. The captions now belong to
their menus. Device, reading the rendered sheet text:
- create: CREATE, New Nova post, COMMUNITY, Nova Campus, Nova Study, Nova SOS
- tools: YOU, Nova Insights, SETTINGS, Nova Safe, Nova Lite

### Availability vs eligibility
Eligibility only looked at the last donation date, so a donor who had switched availability
off still read "Eligible now". The switch now decides: off shows "Not available to donate"
in the secondary colour, on returns to the medical eligibility text. Verified both ways on
device.

## 31. Media privacy: every bucket is private now

Task 18 stopped after `stories` because signing the remaining five buckets touched the whole
app. This pass finished it, and the shape of the change is the point: no call site was
rewritten and no stored URL was migrated.

### What was actually exposed
Measured before the change, not assumed:

- `select id, public from storage.buckets` -> `avatars`, `chat`, `nova`, `posts`, `reels`
  were `true`; only `stories` was `false`.
- `pg_policies` showed their SELECT policies granted to role `public`. Since the anon key
  ships inside the APK, "private bucket, public read policy" would have been no privacy at
  all: a `/object/authenticated/` request bearing the anon key as its bearer is answered by
  that policy.

### One transport choke point
`MediaUrl.PRIVATE_BUCKETS` now lists all six, and `MediaUrl.toAuthenticated(String)`
rewrites `/storage/v1/object/public/<private>/...` to `/storage/v1/object/authenticated/...`
by inspecting the bucket segment in the URL itself, not an argument a caller might get
wrong.

The rewrite is applied by `StoragePrivacyInterceptor`, added to the single OkHttp client in
`RetrofitClient` **before** `AuthInterceptor`. That covers three things at once:

1. Glide — `NovaGlideModule` already routes every image through this client, so all 90-odd
   `Glide.with(...)` sites inherit it, including the ones that bypass `GlideUtils`.
2. Retrofit upload calls.
3. The `HEAD` probe each upload runs against the object URL before it writes a row. That
   check would otherwise have turned every upload into "HEAD verification failed" while the
   bytes were stored — the same trap the stories work hit.

Rows still hold `/public/` strings. That is deliberate: the string is an identifier, and the
endpoint is decided at request time.

### Two defects found by building and running it
- **Local previews broke.** With `posts` on the private list, a `file://` template render was
  handed to the OkHttp loader, which rejects any scheme but http(s):
  `IllegalArgumentException(Expected URL scheme 'http' or 'https' but was 'file')`. Fixed by
  taking the auth path only for remote URLs.
- **Reels failed with HTTP 400.** ExoPlayer does not use the app's OkHttp client, so it was
  rewritten explicitly and given headers — but the token came back null, because
  `streamHeaders()` resolved it eagerly on the main thread and `Tasks.await` refuses to run
  there; the catch block returned null and the request carried the anon key as its bearer.
  The fix moves resolution into `DataSource.Factory.createDataSource()`, which ExoPlayer
  calls on its loading thread (`MediaUrl.streamFactory()`). Reels then played.

### Server change
`2026-11-08_media_private_buckets.sql` (+ `.rollback.sql`):

- `public = false` for `avatars`, `chat`, `nova`, `posts`, `reels`.
- The five read policies recreated `to authenticated`.
- `chat` is scoped to the conversation rather than to any signed-in user:
  `can_read_chat_media(name)` accepts the uploader's own folder (the bytes exist before the
  message row) or a viewer who is a member of the conversation holding the message that
  references the object.

### Verification
Server:

```
buckets           avatars/chat/nova/posts/reels/stories -> public = false   (6/6)
read policies     nova_media_select, storage_{avatars,chat,posts,reels,stories}_read
                  -> roles {authenticated}
chat policy       participant true | sender true | stranger false
public route      never-fetched object -> 400 NoSuchBucket
authenticated     no token -> 400 | anon key as bearer -> 400
```

Device (10BE2S1U640002B, debug build from this source):

- feed and avatars, Nova feed, explore grid, profile grid, story image: rendered, requests
  logged as `GET /storage/v1/object/authenticated/... -> 200`.
- reels: video decoded and played after the data-source fix.
- chat: a photo was uploaded from the picker, the message was delivered, and both the
  incoming and outgoing photo rendered — which exercises the participant branch of the chat
  policy on a real device, not only in SQL.

### Residual, stated plainly
- **CDN cache.** An object fetched while its bucket was public can still be served from the
  Cloudflare edge for a while: `cf-cache-status: HIT`, `cache-control: no-cache` yet no
  revalidation. Measured after the flip: that one cached URL still returned 200, while an
  object never fetched before returned 400. There is no caller-side purge; the exposure ends
  when the edge entry expires.
- Chat reads are scoped by a `LIKE '%' || name` match against `messages.media_url`, the same
  shape the stories policy uses. It is correct, but it cannot use an index; at current
  message volume it is irrelevant and it is the first thing to revisit if that table grows.
