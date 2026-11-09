# Nova Redesign — Implementation Plan

Scope: brand mark, Nova tab (feed + filters + plus sheet), Nova theme, module screens,
device verification. Native Android (Java, XML + ViewBinding), no new libraries.

---

## 1. Goals

| # | Requirement | Delivered by |
|---|-------------|--------------|
| 1 | New InstaNova logo from `logo.jpg`, transparent PNG, Instagram-polished | `brand/instanova_logo.png`, adaptive + legacy launcher icons, splash, in-app marks |
| 2 | Nova icon becomes an Instagram-type mark | `ic_nova.xml` (camera outline) / `ic_nova_fill.xml` (gradient camera, selected) |
| 3 | Inside Nova: every post in one place, like an Instagram feed | 3-column, edge-to-edge grid of all posts (`v_posts`, photos + reels) |
| 4 | Post filter option | Single-select chip rail (All / Photos / Reels / Saved / Mine) + sort sheet (Newest / Oldest / Most liked) |
| 5 | Plus icon on top opens every option and allows posting | `NovaSheet`: Post / Story / Reel / Live + SOS / Safe / Campus / Study / Lite / Insights / Alerts |
| 6 | Instagram-like colour, theme and structure everywhere in Nova | Nova tokens re-pointed at the PAGE 0 Instagram palette (light + night), 8/12dp radii, no gradients or starfield |
| 7 | Polish and clean | Metal bars 48–52dp, hairline dividers, one accent colour, no glow/breathing animation |

---

## 2. Brand assets

`tools/build_brand_assets.py` (re-runnable):

1. Flood-fills the flat plate from the image border, so the white camera glyph enclosed
   by the disc is never keyed out.
2. Un-premultiplies the feathered rim against the sampled plate colour, so no grey halo
   survives on dark backgrounds.
3. Writes:
   - `brand/instanova_logo.png` — 1024² transparent master
   - `drawable-nodpi/ic_brand_mark.png` — in-app bitmap
   - `mipmap-{m,h,x,xx,xxx}hdpi/ic_launcher(.round).png` — legacy icons (white tile)
   - `mipmap-xxxhdpi/ic_launcher_foreground.png` — adaptive foreground inside the 66dp safe zone

Vector twins (crisp at any size, theme-aware):
`ic_brand_mark.xml` (in-app + welcome flows), `vd_logo_mark_splash.xml`
(keeps the `star_group` animation target), `ic_launcher_monochrome.xml` (themed icons),
`ic_logo_mark_mono.xml` (notification small icon).

Old star marks removed: `ic_logo_mark.xml`, `ic_logo_mark_outline.xml`,
`ic_logo_mark_filled_gradient.xml`, `ic_launcher_foreground.xml`.
`WordmarkView` now draws "InstaNova" in one solid colour (no cyan→violet gradient,
no superscript star): the camera carries the colour, the wordmark stays a logotype.

---

## 3. Nova tab anatomy

```
┌───────────────────────────────────────────┐
│ Nova                          [filter] [+]│ 48dp + status inset
├───────────────────────────────────────────┤ hairline
│ (All) Photos Reels Saved Mine             │ chip rail, 34dp chips
│              ● 3 new alerts               │ pill, only when unread > 0
├───────────────────────────────────────────┤
│ ▢ ▢ ▢                                     │ 3 columns, 1dp gutters
│ ▢ ▢ ▢        square tiles, carousel /     │
│ ▢ ▢ ▢        reel badge in the corner     │
└───────────────────────────────────────────┘
```

- **Plus** → `NovaSheet` (`sheet_nova_create.xml`): create tiles + module rows with live
  sublines ("3 open requests · 1 critical", "42 min focused today", …) and the unread
  badge on Alerts.
- **Filter glyph** → `sheet_nova_sort.xml`: Newest / Oldest / Most liked.
- Grid tap → `PostDetailActivity` (same extras Explore uses).
- Tab re-selected → grid scrolls to top (wired in `MainActivity`).

## 4. Data flow

`NovaFeedViewModel`

- `PostRepository.getAllPosts(cursor, pageSize, reelsOnly, authorId)` — new query on
  `v_posts`, newest first, keyset cursor on `created_at`. Unlike `getFeed` it does not
  force `is_reel=eq.false`.
- Page size 24, `loadMore()` on scroll within 4 rows of the end.
- Sorts are applied to the loaded window (a likes-ordered cursor would skip rows).
- Locally created posts (`LocalSocialStore`) are merged above the server page; repost
  copies (`repost_…` ids) are skipped so media never appears twice.
- Blocked / muted authors filtered through `BlockMuteCache`; saved flags reconciled with
  `LocalSocialStore`.
- `LocalSocialStore.revision()` triggers a re-merge, so a post created on another tab
  appears here without a reload.

---

## 5. Theme

`values/colors_nova.xml` and `values-night/colors_nova.xml` are rewritten to the
Instagram palette (white/black canvas, `#DBDBDB` / `#262626` dividers, `#0095F6`
primary, `#ED4956` destructive). The legacy `nova_space*` names resolve to normal
surfaces, so no layout can paint a dark "space" panel any more.

`dimens_nova.xml`: radii 12dp cards / 16dp sheets / 8dp chips, 44dp buttons, 16dp screen
padding. Drawables re-cut: solid blue pill, outline button, filled vs outline chip,
filled input, hairline card. Module accents darkened to ≥4.5:1 on white.

Module screens: header bar 64dp → 52dp, title 24sp → 18sp, header glyph un-tinted in
`NovaScreen.bindHeader`. Nova intro loses the starfield and the space hero, and uses the
brand mark instead.

---

## 6. Corner cases

| Case | Handling |
|------|----------|
| Signed out / no session | Grid shows empty state; create flows redirect to auth exactly as before |
| Zero posts | Empty state with "Create a post" |
| Filter with no results | "Nothing here yet" + no create button (except Mine) |
| Saved filter empty | "Save a post and it will show up here." |
| Network failure | Bottom banner with the real message + Retry; local posts still render |
| Offline with cached feed | Local posts and previously loaded rows stay on screen |
| Pagination end | `hasMore=false`, no further requests |
| Malformed DTO | Row skipped, rest of the page renders |
| Quick filter switching | `generation` counter drops stale responses |
| Rotation | ViewModel survives; `loadInitial()` is idempotent; no duplicate fetch |
| Pull-to-refresh during initial load | Spinner and swipe refresh never both show |
| Locally created post | Merged above server rows; no server call for like/save (no uuid) |
| Repost rows | Excluded from the grid (would duplicate media) |
| Blocked / muted author | Filtered from the grid |
| Dark mode | Full token parity in `values-night` |
| RTL | start/end padding only |
| Rounded / themed launcher | Adaptive + monochrome layers provided |
| Notification icon | Stays monochrome (`ic_logo_mark_mono`) |

---

## 7. Verification matrix (device: I2302, 2026-10-28)

| Check | Result |
|---|---|
| Cold start / splash | camera mark + solid wordmark, theme background |
| Nova tab chrome | 48dp bar, filter + plus, chip rail, 3-column grid |
| Nova shows no Insta posts | pass (`verify/07_nova_after_migration.png`) |
| Home shows no Nova post | pass (`verify/16_home_no_nova.png`) |
| Chip: All / Photos / Reels | pass (query per filter, empty states) |
| Chip: Saved | pass — "Save a post and it will show up here.", no create CTA |
| Chip: Mine | pass — "Your posts will appear here…" + Create a post |
| Filter glyph → sort sheet | pass — three rows, selection closes the sheet, no crash |
| Plus glyph → Nova sheet | pass — New Nova post + 7 tools, no Insta create tiles |
| Module rows | pass — SOS, Safe, Campus, Study, Lite, Insights, Alerts all open and return |
| Nova post round-trip | pass — row written with `space='nova'`, shown in Nova, absent from Home |
| Resume refresh | fixed — new post appears without a manual pull |
| Rotation (landscape) | pass — no clipping, filter state preserved (`verify/25_rotate.png`) |
| Light mode | pass — white canvas, black type, blue primary (`verify/19`, `verify/20`) |
| Dark mode | pass — black canvas, `#262626` dividers |
| Back navigation | pass — every module returns to Nova |
| Grid tap → post detail | **not tested** (verification post was deleted first) |
| Pull-to-refresh gesture | pass — swipe triggers one refresh, no crash, spinner clears |
| Launcher icon asset | pass — gradient camera on white tile (`mipmap-*/ic_launcher.png`), adaptive + monochrome layers in the APK |
| Signed-out Nova | not tested (existing session on device) |

Build: `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL; `testDebugUnitTest` green.

## 8. Rollback

All edits are source-level and reversible with git. Regenerate brand assets with
`python3 tools/build_brand_assets.py` (idempotent).

---

## 9. Phase 2 — Nova is its own space (2026-10-28)

The first pass made the Nova tab mirror `v_posts` (every post), which is wrong: Nova and
Insta are two separate parts of the app. Fixes:

### Data
- `posts.space text not null default 'insta'` with `check (space in ('insta','nova'))`
  and an index on `(space, created_at desc)`.
- `v_posts` exposes `space` (appended as the last column so `create or replace view` works).
- `v_feed` is rebuilt with `space = 'insta'`, so the Insta Home feed can never show a
  Nova post. `v_saved` is rebuilt to carry the new column.
- Migration: `supabase/migrations/2026-10-28_nova_space.sql` (+ `.rollback.sql`).
  **Must be run in Supabase before Nova can load.**

### App
- `Post.space` (`SPACE_INSTA` / `SPACE_NOVA`) + `PostDto.space`.
- `PostRepository.getAllPosts(..., space)` sends `space=eq.<space>`.
- `NovaFeedViewModel` queries `space=eq.nova` for every filter, keeps only Nova rows in
  the Saved filter, and merges only Nova local posts.
- `SearchRepository.getExplorePosts` sends `space=eq.insta` — Explore is an Insta surface.
- `CreatePostActivity.start(context, space)` writes `space` on insert; default `insta`,
  so the Home '+' is unchanged.
- Nova sheet: Insta create tiles removed. One "New Nova post" row → `CreatePostActivity`
  with `space=nova`, then the seven Nova tools.
- Nova empty-state CTA also writes into the Nova space.

### Verified on device (I2302, dark mode)
- Nova tab shows **no Insta posts**; with the migration unapplied it shows the empty state
  plus "Nova space is not set up on the server yet. Run the Nova migration, then retry."
  with Retry (`verify/05_nova_empty.png`).
- Plus sheet contains only "New Nova post" + SOS / Safe / Campus / Study / Lite / Insights /
  Alerts (`verify/06_sheet_nova.png`).

### Migration applied (2026-10-28, project poqdemxiuoocbzkwbyhy)
- `posts.space` added; 11 existing posts are all `insta`; `v_feed` still returns 10 rows.
- Nova tab loads with no error and shows its own empty state (`verify/07_nova_after_migration.png`).
- Home still renders the normal Insta feed (`verify/09_home_still_insta.png`).
- "Create a post" on Nova opens `CreatePostActivity` in Nova space (`verify/08_create_nova_post.png`).

### Module rows verified (device)
Each row in the Nova sheet opened its screen: `NovaSosActivity`, `NovaSafeActivity`,
`NovaCampusActivity`, `NovaStudyActivity`, `NovaLiteActivity`, `NovaInsightsActivity`,
`NovaAlertsActivity`, plus `CreatePostActivity` in Nova space.

### Light mode (device)
Nova tab and Nova Safe were checked in light mode: white canvas, black type, hairline
borders, blue primary, no purple/space styling (`verify/19_nova_light.png`,
`verify/20_safe_light.png`).

### Round-trip verified (device, 2026-10-28)
- A neutral test image was pushed to the gallery and posted through Nova's own
  "Create a post" flow.
- Insert landed with `space='nova'` (`posts.id 99744fa2…`), appeared in the Nova grid
  (`verify/15_nova_with_post.png`) and did **not** appear on Home
  (`verify/16_home_no_nova.png`).
- Fixed while testing: the grid did not refresh on resume, so a post created in the Nova
  flow stayed invisible until a manual pull. `NovaHubFragment.onResume` now re-merges local
  posts and issues a quiet refresh.
- The verification post and its `post_media` rows were deleted afterwards, and the test PNG
  was removed from the device gallery, so no test data remains (`space='nova'` → 0 rows).
