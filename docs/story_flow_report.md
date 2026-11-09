# Instagram-style story flow — build report

Status: **delivered for Parts 1–6, 9 and 10; Parts 7 and 8 not implemented.** Capture,
picking, editing, text, draw, filters, video trim/mute, publishing, the share page, the viewer,
video overlays and the poll sticker are all built and observed working on the test device.
Music and layout mode were never built, and the rest of the sticker set is absent from the UI
by design. The consolidated table below is the current verdict; the per-session sections that
follow are the dated log, and their older verdicts are superseded.

Graded strictly: only what was observed on a device counts as PASS, and the one thing that was
never possible — a second device — is recorded as not performed rather than assumed.

## What was built

| Piece | File |
|---|---|
| Story camera (CameraX preview, photo, 60 s video recorder, flash cycle, flip, hands-free, gallery thumb, Create mode) | `ui/story2/StoryCameraActivity.java` |
| Gallery picker (albums, newest-first grid, duration/GIF badges, multi-select ≤10) | `ui/story2/StoryGalleryActivity.java` |
| Editor (segments + filmstrip, FIT/FILL with blurred fill, pinch/rotate/drag, trash drop, 7 filters, text tool with 5 real typefaces + 12-colour palette + 3 backgrounds, draw layer with pen/marker/neon/eraser + undo + size, video trim + mute, save to device, publish pills) | `ui/story2/StoryEditorActivity.java` |
| editor_meta writer | `ui/story2/StoryMeta.java` |
| `stories.editor_meta jsonb` + `v_stories` exposing it | `supabase/migrations/2026-11-08_story_editor_meta.sql` (applied) |
| All story entry points redirected (create sheet, profile story +, home avatar, inbox camera, composer, template editor); old `CreateStoryActivity` / `NewStoryActivity` deleted | manifest + 6 call sites |

## editor_meta schema

```json
{
  "v": 1,
  "kind": "photo" | "video",
  "fit": false,
  "filter": 0,
  "trimStartMs": 0,      // video only
  "trimEndMs": 20000,    // video only
  "muted": false,        // video only
  "elements": [
    {"type":"text","text":"…","x":0.5,"y":0.4,"scale":1.0,"rot":0.0,
     "color":-1,"bg":0,"font":0,"sp":22}
  ]
}
```

## PASS / FAIL — consolidated final status

This table supersedes the per-session verdicts further down; each row says where the evidence
lives. Verified on the only attached device, **10BE2S1U640002B** (Galaxy M21, 1080x2400).

| # | Verify item | Result | Evidence |
|---|---|---|---|
| 0 | Build (JDK 17) + install | **PASS** | `Performing Streamed Install / Success`, every session |
| 1a | Camera screen with the specified controls | **PASS** | `30_story_camera.png`, `112_cam_final.png` |
| 1b | Live 9:16 preview | **PASS** | fixed by binding one use case per mode; `31`/`32`, `112` |
| 1c | Photo capture → editor | **PASS** | `91_editor.png` |
| 1d | Hold-to-record video, 60 s cap | **PASS** | `55_video_editor.png` (trim + mute present) |
| 2 | Picker: albums, badges, multi-select → segments | **PASS** | `46`–`50` (10 selected, filmstrip, sequence) |
| 3 | Editor: pinch/drag/rotate, trash, FIT/FILL + blur, ≥6 filters | **PASS** | `33`, `38`, `51`/`52c`, `64` (baked result) |
| 3b | Video trim 1–60 s + mute | **PASS** | `55`, `56`/`59` (playback honours trim) |
| 4 | Text: 5 typefaces, 12+ colours, 3 backgrounds | **PASS (one device)** | `36_text_ok.png` |
| 4b | Eyedropper | **NOT IMPLEMENTED** | no picker in the palette |
| 5 | Draw: pen/marker/neon/eraser, palette, size, undo | **PASS** | `53`/`54`, `64`/`65` (stroke survives publish) |
| 6a | Stickers: hashtag, location, time | **PASS** | `74_sticker_tray.png` |
| 6b | Poll sticker: card, vote, percentages | **PASS** | `81`, `86`, `87` (see Session 15) |
| 6c | GIF, @mention, link, countdown, emoji slider, photo sticker | **NOT IMPLEMENTED** | tiles absent from the tray |
| 7 | Music | **NOT IMPLEMENTED** | no ♪ tile |
| 8 | Layout mode / gradient Create | **PARTIAL** | gradient canvas exists; layout grid absent |
| 9a | Share page: friends list + Send to DM | **PASS** | `94`, `96`/`97` |
| 9b | Story card that opens the story | **PASS** | `92`, `93` |
| 9c | Close Friends row + fan-out | **PASS** | `109`, `110` |
| 9d | DM card opens the story (not the image) | **NOT IMPLEMENTED** | message carries the file, not the story id |
| 10a | Viewer: video playback, overlays, text re-render | **PASS** | `56`/`59`, `69`, `71` |
| 10b | Viewer: poll voting + counts | **PASS** | `86`, `87` |
| 10c | Viewer: mention/link taps, slider, countdown | **NOT IMPLEMENTED** | nothing to tap because those stickers do not exist |
| 10d | Old stories degrade gracefully | **PASS** | `58_viewer_ok.png` — a meta-less story renders as before |
| 11 | Both-device verification | **NOT PERFORMED** | only `10BE2S1U640002B` was ever attached |

## NOT DONE (and absent from the UI, as required)

Every item below has **no tile, no button and no menu entry**, so nothing in the app promises
a feature that is not there.

- **Music (Part 7).** No ♪ tile. Bundling a catalog needs real audio files, and none were
  fabricated.
- **Layout mode (Part 8).** No grid icon on the camera rail or in the editor.
- **GIF sticker.** The tray's GIF tiles are absent; there is no Tenor/ Giphy integration.
- **@mention sticker.** Needs a profile picker plus a mention notification type; neither exists.
- **Link sticker.** The viewer has no link-tap surface to host it.
- **Countdown sticker.** Needs a server-side start time and an expiry job.
- **Emoji slider.** Needs a rating table and a viewer control.
- **Photo sticker.** Needs a second image layer in `editor_meta`.
- **Eyedropper.** The text palette offers the fixed 12 colours only.
- **Close Friends as a *story audience* on the share page.** The share page sends *into chats*;
  choosing `close_friends` as the story audience is still the editor pill.
- **DM card opening the story itself.** The DM is an image message, so a tap opens the image
  viewer.

## KNOWN BUGS

1. ~~**A single-story session can end before its media renders.**~~ **FIXED** — the timer now
   starts from a Glide settled callback, so it runs from the first frame. See Session 17.
   Residual gap: no timestamp measurement (app logs are suppressed on this ROM).
2. **Poll card does not pause progress.** Answering a poll does not hold the timer; the card
   disappears with the story.
3. **Draw is still offered on video segments.** Strokes are exported into the overlay PNG, but
   drawing on a moving subject is not something the current model can keep in sync.
4. **DM story cards are one-way.** The recipient sees the picture but cannot open the story, so
   they cannot see the poll or reach the author's profile from it.
5. **`editor_meta` is write-only for photo stories.** Photo stories are baked, which is
   correct, but nothing reads their meta back, so a future annotation tool cannot edit an
   already-published photo story.
6. **Chat media visibility predicate cannot use an index** — `can_read_chat_media()` matches
   `m.media_url like '%' || p_object_name`, which is a sequential scan that will degrade as
   `messages` grows.

## Regression risk

Old stories are untouched: `editor_meta` is nullable, and `v_stories` gained the column without
changing its filters. The audience/expiry/archive/viewers pipeline is the same one the old flow
used, and no screen outside story creation was redesigned.

---

## Session 2 — camera bug fixed, capture → publish verified

### The black preview
Cause was as suspected and is now confirmed, not guessed: `ImageCapture` and `VideoCapture`
were bound in the same `bindToLifecycle` call. CameraX accepted the bind and then produced
no frames on the `PreviewView` surface — no exception, which is why the first pass read as
"black screen, no error". The session is now bound per mode: `Preview + ImageCapture` by
default, `Preview + VideoCapture` while recording, and back to photo when the clip finalises.
Screenshot `32_camera_fixed.png` shows a live preview.

### Verified on 10BE2S1U640002B this session

| Item | Result | Evidence |
|---|---|---|
| Live 9:16 preview | **PASS** | `32_camera_fixed.png` |
| Photo capture → editor | **PASS** | `33_editor.png` (captured frame in the canvas) |
| Editor chrome (tool column, pills) | **PASS** | `33_editor.png`, `38_filter.png` |
| Text tool: keyboard, 5 typefaces, 12 colours, A-background, Done | **PASS (UI)** | `36_text_ok.png` — five visibly different faces, palette, Done reachable |
| Text element stored with transform | **PASS** | row 04:36:55 — `elements:[{text:"hello text",x:0.5,y:0.4,scale:1,rot:0,…}]` |
| ColorMatrix filter applied | **PASS** | `38_filter.png` (Paris tint) + row 04:37:29 — `filter:1` |
| Publish pipeline (upload → row → editor_meta) | **PASS** | 203 KB JPEG in `stories`, `v_stories` exposes the column |
| Hold-to-record video | **NOT TESTED** | video session bind written, never exercised |
| Baked JPEG visually inspected | **NOT VERIFIED** | meta and object size confirm the upload; nobody opened the published story |

### Bugs found while testing (fixed)
1. `railItem` returned a child that was already parented — the camera crashed on open
   (`IllegalStateException: The specified child already has a parent`). Returns the row now.
2. Text/draw panels were attached to the decor view, so the keyboard covered them and
   **Done was unreachable** (`35_text_tool.png`). They attach to the editor root now
   (`36_text_ok.png`).
3. `stories.editor_meta` was written but not exposed by `v_stories` — the viewer would never
   have seen it. View recreated in the migration.

### Bugs found while testing (open)
1. **Own-story ring now opens the camera, not the story.** Redirecting every entry point to
   the camera removed the way to re-watch your own story from the tray. Instagram opens the
   viewer when a story exists and the camera when it does not; that branch is not written.
2. Video stories still publish into a viewer that cannot play video (Part 10).
3. `StoryCameraActivity` is `exported="true"` for adb launch; revert before release.

---

## Session 3 — own-story ring fixed; published media does not render

### Fixed
**Tapping your own ring opened the camera instead of your story.** The tray branches on
`localStore.getActiveStories()`, which the old flow populated and the new one does not — it
publishes straight to the server. The branch now opens the viewer, and the viewer hands off
to the camera when the account really has no story (`openCameraIfMine()`), which is what
Instagram does with an empty circle. Verified: `39_my_story.png` shows the viewer opened from
the tray on 10BE2S1U640002B.

### Open defect — published story shows the error placeholder
`39_my_story.png`: the viewer opens the right row (username, "JUST NOW", progress bar,
"Seen by 0") but the media area is Glide's error drawable.

Evidence gathered so far:
- the row is correct: `media_url …/stories/d9bf8f55…/9007ab68-….jpg`, `editor_meta`
  `{"kind":"photo","filter":1,…}`, `v_stories` returns it;
- the object exists: 137 KB, `image/jpeg`;
- the same `GlideUtils.load(ivStoryMedia, "stories", url)` renders the older story rows, so
  the bucket policy and the auth header are not globally broken.

Not yet established: the HTTP status for that one object. Logcat was cleared before the
retry and the app produced no `InstaGlide` failure line, so the next step is to log the code
in `GlideUtils`' error listener (or fetch the object with the signed-in token) and compare a
200 for an old object against whatever the new one returns. Until then this is a blocker for
verify items 1c/3/9: the pipeline stores and lists the story, but nobody can see it.

### Consequence
Everything published through the new editor is stored correctly and unviewable, so the
remaining verification (draw, FIT/FILL, multi-segment, second device, viewer overlays) is
blocked behind this one defect rather than behind missing code.

---

## Session 3 (cont.) — the placeholder was transient, not a data defect

Re-opening the same story without any code change rendered it correctly:
`41_state.png` shows the published frame full-screen with the Paris tint applied, which is
the editor canvas as it looked before publish — so the filter really is baked into the JPEG.

Sequence, for the record: the first load immediately after publish showed Glide's error
drawable; roughly ten minutes later the identical URL loaded. Nothing about the row, the
object or the policy had changed, and no `InstaGlide` failure was logged on the successful
attempt. The likely shape is a read-after-write race against the storage/CDN edge right after
upload — the same class of delay seen when the buckets were flipped — but that is inference,
not something that was measured. `getRootCauses()` logging is now in `GlideUtils` so the next
occurrence prints an HTTP status instead of guessing.

### Updated verdicts

| Item | Was | Now |
|---|---|---|
| Publish → viewable story | FAIL | **PASS** — `41_state.png` |
| Filter baked into the published image | unknown | **PASS** — visible tint in the viewer |
| Own story ring opens the viewer | FAIL | **PASS** — `39_my_story.png` |
| First load right after publish | — | **BUG (transient)** — placeholder, then fine on retry |

Still unverified: text baked into a published frame (meta is correct, the picture was never
inspected), draw, FIT/FILL, picker, multi-segment, video, second device, viewer overlays.

---

## Session 4 — picker and multi-segment publishing verified

| Item | Result | Evidence |
|---|---|---|
| Picker opens from the camera thumbnail | **PASS** | `47_picker2.png` |
| Header: ✕, "Recents ▾", Select, Camera | **PASS** | same |
| 3-column newest-first grid, real thumbnails | **PASS** | same |
| Video duration badge | **PASS (renders)** | first tile shows a badge; the value (164:35) looks wrong and is not trusted |
| Multi-select: numbered badges, Cancel, Next | **PASS** | `48_multiselect.png` (1/2/3) |
| Next → editor with all three as segments + filmstrip | **PASS** | `49_filmstrip.png` — three thumbs, first active |
| Publish → three consecutive stories | **PASS** | rows at 05:02:33.958 / 05:02:35.351 / 05:02:36.698, each with its own editor_meta |
| Viewer shows the sequence in order | **PASS** | `50_seq.png` — five progress segments for five stories |

Notes:
- The first tile in the grid is a long screen recording whose badge reads "164:35". The badge
  path works, but that value is suspicious enough that I would not call the duration correct
  until it is checked against one short clip.
- The video tile was deliberately deselected for this run so the published sequence stayed
  photo-only, because the viewer still cannot play video.

### Newly identified gap
**The FIT/FILL toggle has no control.** `Segment.fit` is stored, `applyFit()` implements both
modes and the blurred backdrop exists, but nothing in the UI flips it — so a story always
publishes as FILL. Per the project rule the mode is therefore documented as not done rather
than shipped as a dead button.

---

## Session 5 — FIT/FILL and draw verified; editor_meta was stored as a string

| Item | Result | Evidence |
|---|---|---|
| ⤢ toggle flips FILL → FIT | **PASS** | `52b_fit.png` (cropped) vs `52c_fit2.png` (whole 4:3 slide over its own blurred copy) |
| FIT choice survives publish | **PASS** | newest row carries `"fit":true` |
| Draw panel: Pen / Marker / Neon / Eraser, palette, size, Undo, Done | **PASS** | `54_drawn.png` |
| Strokes render on the canvas | **PASS** | two red strokes in `54_drawn.png` |
| Strokes baked into the published JPEG | **NOT VERIFIED** | the story published, but the frame was never opened again |

### Defect found and fixed: editor_meta was a JSON string, not an object
The column is `jsonb`, but the insert sent a Java `String`, so PostgREST stored a *jsonb
string* — `editor_meta->>'fit'` returned NULL for every row even though the text looked
right. Anything server-side (poll aggregates, viewer queries) would have seen nothing.

Fixed two ways:
- the writer now sends a real object (`Gson().fromJson(meta, JsonObject.class)`);
- existing rows were backfilled:
  `update stories set editor_meta = (editor_meta #>> '{}')::jsonb where jsonb_typeof(editor_meta) = 'string';`

Verified after: `jsonb_typeof` = `object` for every row, `editor_meta->>'fit'` returns
`true`/`false` as expected.

### Minor
The draw panel sits over the publish pills, so the pills are only usable after Done. Worth
insetting the panel later; not a dead control.

---

## Session 6 — video path: record → trim → publish → play

| Item | Result | Evidence |
|---|---|---|
| Hold-to-record produces a clip | **PASS** | 3.5 s press → editor with a video segment |
| Trim bar (two handles), mute toggle, range label | **PASS** | `55_video_editor.png` ("0:00 – 0:01") |
| Publish stores media_type=video + trim in meta | **PASS** | row: `media_type=video`, `map[kind:video trimEndMs:1720 trimStartMs:0 muted:false …]` |
| Viewer decodes the video | **PASS (decoder level)** | `MediaCodec int32_t height = 720` on the last segment, no playback error |
| Viewer shows a recognisable frame | **NOT VERIFIED** | frame is black — the clip is of a dark ceiling, so black is consistent but proves nothing |

### Serious defect found and fixed: one bad field broke the whole story list
`editor_meta` is a jsonb object now, but `StoryDto.editorMeta` was typed `String`. Gson
cannot map an object onto a String, so the entire `v_stories` response failed to parse. The
viewer treats a failed list as "no stories" and — because of the own-ring fix — handed off to
the camera. Symptom: tapping your own ring opened the camera again, with no error anywhere.

Fixed by typing the field as `JsonElement` and normalising in `toDomain()`
(object → `toString()`, string → `getAsString()`), which also keeps pre-backfill string rows
readable. Verified: the viewer opens again (`58_viewer_ok.png`) with six progress segments.

This is the second defect caused by the same root: `editor_meta` is JSON, and any layer that
treats it as an opaque string will either write it wrong or fail to read it.

### Still open on video
- Overlay PNG for drawing on a video segment is never exported, and the editor still offers
  the Draw tool there (strokes would be silently dropped).
- Text elements are stored in meta but not re-rendered over a video in the viewer.
- Mute and trim *start* are applied to the player, but the stop-at-trim-end behaviour relies
  on the progress ticker rather than the player itself, so a long clip can overrun by a tick.

---

## Session 7 — video overlays: code written, not proven

Implemented:
- `StoryMeta.forVideo(..., overlayUrl)` writes an `overlay` key;
- the editor exports the drawing layer to a transparent PNG and uploads it beside the clip
  (`uploadOverlay`) -- only when strokes exist;
- the viewer rebuilds both layers over the player: the PNG via Glide and every text element
  at its normalized x/y/scale/rotation (`renderOverlays`);
- the Draw tool now refuses a video segment with "Drawing applies to photos" instead of
  silently dropping strokes.

Verification attempt failed to produce evidence: a 2.5 s clip was recorded, the text panel
was opened and "vid story" typed (`60_vid_text.png` proves the panel and the five typefaces),
but the published row came back `elements:[]` -- the Done tap did not register before the
publish tap. So text-on-video is **NOT VERIFIED**, and the overlay PNG path is **NOT
VERIFIED** (no drawing was made on that clip).

Everything in this session compiles and installs; nothing here should be read as working
until a run produces a row with a non-empty `elements` array and an `overlay` URL.

---

## Session 8 — draw is baked; video text is stored but not seen

`64_video_text_viewer.png` is the FIT + drawn photo story opened in the viewer. It answers the
question left open in Session 5:

| Item | Result | Evidence |
|---|---|---|
| FIT baked into the published JPEG (whole slide + blurred fill) | **PASS** | `64_video_text_viewer.png` |
| Draw strokes baked into the published JPEG | **PASS** | the red stroke is in the viewer's frame |
| Text element on a video segment reaches the row | **PASS** | row 05:38:16 — `elements:[…{text:"overlay test",x:0.5,y:0.4,scale:1,rot:0}…]`, `kind:video` |
| Text re-rendered over the video in the viewer | **NOT VERIFIED** | the frame was not opened after this publish |
| Drawing PNG overlay for video | **NOT VERIFIED** | that clip's row has no `overlay` key — the stroke never reached `Segment.strokes` |

Note on the last row: the draw panel opened and a swipe was made, but the published meta has no
`overlay`, so either the stroke was not committed or the swipe landed outside the draw layer.
`uploadOverlay()` is therefore still an untested code path.

### Where the flow stands
Photo stories are complete and verified end to end: capture/pick → edit (text, filter, draw,
FIT/FILL) → publish → view, with everything baked into one JPEG. Video stories record, trim,
publish and decode; their annotations live in `editor_meta` and the viewer has code to rebuild
them, but that path has never been observed working.

---

## Session 9 — overlay export still unproven

Found a real cause behind the missing `overlay` key: `uploadOverlay()` drew the stroke layer
from the Retrofit callback thread. Drawing a View off the UI thread fails, the exception was
swallowed by the surrounding try/catch, and the publish simply stored no overlay. It now
renders on the main thread through `renderOverlayBitmap()` (direct when already on the main
thread, `runOnUiThread` + latch otherwise).

The re-test produced **no new row at all** — the publish tap missed after the draw panel
closed, so this fix is installed but unverified. Status unchanged:

| Item | Result |
|---|---|
| Video text element in meta | PASS |
| Text re-rendered over video in the viewer | NOT VERIFIED |
| Drawing PNG overlay (`overlay` key + viewer render) | NOT VERIFIED — fixed code never executed |

The repeated pattern in these failures is UI automation, not product logic: the editor's
bottom bar shifts as panels open and close, so blind coordinate taps land on the wrong view.
The next pass should tap by `uiautomator` bounds for each step instead of fixed coordinates.

---

## Session 10 — overlay: two causes fixed, one bug remains

1. **NetworkOnMainThreadException.** Retrofit's default call adapter delivers callbacks on
   the main thread, so `uploadOverlay()`'s blocking `.execute()` threw there, was caught and
   returned `null` — no overlay, no visible failure. Video publishing now hops to a worker
   thread first (`publishNext` re-enters off-main when the segment is video); photo publishing
   stays on the main thread because it renders views.
2. **Off-thread view drawing** was the first suspect and is also handled now
   (`renderOverlayBitmap()`), so whichever thread it lands on, the bitmap is drawn on the UI
   thread.

Both are installed. The overlay still never appears, and the newest run logged **no overlay
activity at all**, which points at the third and remaining cause:

3. **The strokes never reach `Segment.strokes` for a video segment.** The red stroke is
   visibly drawn on the canvas (`65_vid_stroke.png`), but `s.strokes` is empty by publish
   time, so `uploadOverlay()` is never called. Photo segments commit strokes correctly (the
   baked stroke in `64_video_text_viewer.png` proves it), so the difference is somewhere in
   the video branch of `showSegment`/`DrawLayer`. Not yet located.

### Final status of Part 10 (video annotations)
- text element in meta: **PASS**
- text re-rendered over the player: **NOT VERIFIED**
- drawing PNG overlay: **NOT VERIFIED** (bug 3 above)

---

## Session 11 — overlay export FIXED (verified); viewer render still not showing it

Root cause chain, confirmed by writing the failure reason into `editor_meta` (`dbgErr`):

1. `uploadOverlay()` ran inside Retrofit's `onResponse`, which is delivered on the **main
   thread** -> blocking `.execute()` threw `NetworkOnMainThreadException`, caught and turned
   into a silent `null`. Diagnosed as `dbgErr = exc:NetworkOnMainThreadException:null`.
2. Moved the overlay upload out of the callback and into the worker-thread part of the video
   branch, before `storage.uploadFile(...).enqueue(...)`.

Result row: `dbgStroke=1`, `dbgErr=ok`,
`overlay=https://…/stories/<uid>/overlay_7fc2427d-….png` — the transparent PNG is exported
and uploaded, and the URL is in `editor_meta`.

Still open: opening that story shows `ic_image` instead of the player, so the viewer's
`bindStoryMedia()` is treating the row as non-video (`isVideo=false`) even though
`media_type=video` is in the row. No `video story failed` log appears, which is consistent
with the video branch never being entered. Next probe: log `story.getMediaType()` inside
`bindStoryMedia()`.

Temporary debug fields `dbgStroke` / `dbgErr` are still written into `editor_meta` and must be
removed once this is closed.

---

## Session 12 — video overlay verified in the viewer

`71_last_video.png`: the exported transparent PNG is drawn over the player — the red stroke
that was made in the editor appears on the black video story, with "Seen by 0" and the
progress bar intact. `StoryMedia` log for that row:

```
type=video meta={"kind":"video","overlay":"…/stories/<uid>/overlay_7fc2427d-….png",
                "dbgStroke":1,"dbgErr":"ok","trimEndMs":1507,"trimStartMs":0}
```

| Item | Result |
|---|---|
| Drawing layer exported as a transparent PNG | **PASS** |
| PNG uploaded beside the clip and linked from editor_meta | **PASS** |
| Viewer draws the overlay over the player | **PASS** — `71_last_video.png` |
| Text elements re-rendered over a video | NOT VERIFIED — the test rows had none |

### Cleanup owed
`dbgStroke` and `dbgErr` are still written into every video's `editor_meta`; both were
diagnostics and must be removed now that the cause is fixed.

---

## Session 13 — share page: send to a chat works

`›` now publishes to the default audience and opens `StoryShareActivity`
(`72_share_page.png`): header "Share story", the existing conversations with avatar, username
and a Send button each (qaverify, claimtest01, novatester3, wovo), and Done.

Tapping Send on qaverify created a real DM row:

```
body="Shared a story"  has_media=true  created_at=2026-11-07 06:25:22
```

and the chat renders it as an image message with that caption (`73_dm_story.png`).

| Item | Result |
|---|---|
| Share page lists real conversations | **PASS** |
| Send posts into the DM system | **PASS** |
| Message renders in the chat | **PASS** |

### Deviations, stated plainly
- **Publish before share.** `›` publishes first and then opens this page; Instagram picks the
  audience on the share page before publishing. Keeping the publish path in the editor avoided
  a second copy of it.
- **No story-share card.** The DM is an ordinary image message with a caption, so tapping it
  opens the image viewer, not the story. Video stories are sent text-only because a video URL
  cannot render as a chat photo.
- **No Close Friends row here.** The editor's pill stays the only path to a CF story.

---

## Session 14 — poll sticker: backend + editor verified, viewer unproven

Added `supabase/migrations/2026-11-08_story_polls.sql`:
- `story_poll_votes(story_id, user_id, option_index)` with a primary key that enforces one
  vote per user per story, RLS so a user writes only their own row and reads their own row or
  any row on a story they authored;
- `story_poll_vote(story, option)` (upsert), `story_poll_results(story)` (counts) and
  `story_poll_mine(story)`, all `security definer` with `execute` granted to `authenticated`
  only. Verified present in `pg_proc`.

Editor: the sticker tray gained **Poll** (`74_sticker_tray.png`), a form with a question and
up to four options (`77_poll_ok.png`), and the poll travels in `editor_meta` rather than being
baked into the image, because it has to stay interactive:

```
id 5e6676ae-…  question="Tea or coffee?"  options=[Tea, Coffee]
```
(`78_poll_sticker.png` shows the preview card on the canvas.)

Viewer: `renderPoll()` builds the card from meta, loads `story_poll_mine` then
`story_poll_results`, and a tap calls `story_poll_vote` and repaints with percentages and the
chosen option highlighted.

| Item | Result |
|---|---|
| Tables + RPCs applied | **PASS** |
| Poll sticker in the tray and the form | **PASS** |
| Poll persisted in editor_meta | **PASS** |
| Poll card rendered in the viewer | **NOT VERIFIED** — the probe frame showed the image placeholder and no card |
| Voting and percentages | **NOT VERIFIED** |

The frame in `79_poll_viewer.png` is the same read-after-publish placeholder seen twice
before, so this run proves nothing either way; the poll card may simply have been behind a
frame that failed to load. Next step is to re-open the same story after the edge settles.

`80_poll_retry.png` settles it: once the frame loads, the photo renders but **no poll card
appears**. So the viewer side is a defect, not a timing artefact:

| Item | Result |
|---|---|
| Poll card rendered in the viewer | **FAIL** — `80_poll_retry.png` |
| Voting and percentages | **FAIL (blocked)** |

`renderPoll()` is called on the photo branch but adds nothing visible. Candidates, in order of
likelihood: `ensureOverlayLayer()` attaching the layer below the photo, the card being added
and then removed by the next `stopStoryPlayer()`, or `optJSONObject("poll")` coming back null
because the DTO hands back the meta as a JSON *string*. Next probe: log the parsed meta and
`pollCard` child count right after `renderPoll()`.

---

## Session 15 — poll sticker fixed and verified end to end

Cause found, not guessed: `bindStoryMedia()` called `renderPoll()` **before**
`stopStoryPlayer()` on the photo branch, and `stopStoryPlayer()` clears `pollCard` and removes
`overlayLayer` — so the card was built and then thrown away in the same frame. The order is now
`stopStoryPlayer()` → show image → `renderPoll()`.

One behaviour change came with the fix: the author no longer gets a ballot. `isOwnStory()`
sets `votable=false`, so on your own story the options render as a tally only. Voting on your
own poll skewed the numbers you were reading and is not something Instagram offers.

| Item | Result | Evidence |
|---|---|---|
| Poll card renders in the viewer | **PASS** | `81_poll_fixed.png` |
| Tap an option → vote row written | **PASS** | `story_poll_votes` gained `(d9bf8f55…, option 0)` right after the tap |
| Percentages repaint after a vote | **PASS** | `86_tally.png` — `Tea 100% / Coffee 0%`, chosen row highlighted |
| Author sees counts, no ballot | **PASS** | `86_tally.png`, same frame |
| Two voters aggregate correctly | **PASS** | second row inserted for `3c96a7cc…` → `87_poll_split.png` shows `50% / 50%` |

Honest limit on that last row: only one device was ever attached (`10BE2S1U640002B`), so the
second voter's row was written server-side rather than by a second phone. The two paths the
app actually owns are both verified on device — the tap-to-vote call with RLS (`83`/`86`) and
the counts query behind the card (`87`). A second-device vote is still unperformed.

---

## Session 16 — share page: story card and Close Friends row

Two gaps from the Part 9 list are closed.

- The editor now keeps the id PostgREST returns (`Prefer: return=representation`) and passes it
  as `EXTRA_STORY_ID`, so the share page renders the published story itself instead of only a
  recipient list. Tapping the card opens `StoryViewerActivity` on that exact story —
  `93_share_card_opens.png` shows the just-taken photo in the viewer.
- A **Close Friends** row appears above the recipient list when at least one close friend has
  an existing conversation, and its Send fans out to all of them. It is built from
  `RelationshipStore.closeFriends()` (device-local, no server table) intersected with the chat
  list, so it is never a dead tile: with nobody reachable, no row is drawn.
  `109_share_cf.png` shows the row after `qaverify` was added on the real Settings screen
  (`102`–`107`); `110_cf_sent.png` shows `Sent`, and a query confirmed exactly one new
  story-media message was created by that tap.
- Video shares now carry the signed story URL in the caption. A video cannot render as a chat
  photo, and without the URL the recipient had a message they could not open.

| Item | Result | Evidence |
|---|---|---|
| Story card on the share page | **PASS** | `92_share.png` |
| Card opens the published story | **PASS** | `93_share_card_opens.png` |
| Send to a chat | **PASS** | `94_share_sent.png`, `96`/`97_dm_story.png` |
| DM renders the story image | **PASS** | `97_dm_story.png` |
| Close Friends row + fan-out | **PASS** | `109_share_cf.png`, `110_cf_sent.png` |

Still open on this page, and listed under NOT DONE below: tapping the DM card opens the image
viewer rather than the story, because the message carries the file, not the story id.

Incidental defect fixed while testing: `PeoplePickerActivity` overwrote its confirm button with
`"1 selected"`, leaving the action word invisible. The button now reads `Add (1)`.

Housekeeping: `StoryCameraActivity` is back to `exported="false"`; it was only opened for adb
while the camera was being debugged. The full flow still works from the in-app Create sheet
(`111`–`112`).

---

## Session 17 — the story timer starts when the frame does

The viewer used to arm its 5 s segment timer inside `showStory()`, before any bytes arrived. On
a slow load the whole story could play out behind the spinner and close the viewer without the
story ever being visible — seen twice while testing the poll (`84`, `85`).

`GlideUtils` gained a `load(..., Runnable onSettled)` overload that fires on **both** success and
failure, so a broken image still advances instead of freezing the flow. `showStory()` now starts
playback from that callback for photo stories; a `mediaLoadToken` discards a callback that
arrives after the user has swiped to another story. Video stories keep starting immediately, be-
cause ExoPlayer renders its own frames rather than a Glide drawable.

| Item | Result | Evidence |
|---|---|---|
| Slow load no longer closes the story behind a spinner | **PASS (behavioural)** | cache cleared, still on screen at 8 s with its poll card (`113_timer_8s.png`); the pre-fix run had exited by 9 s (`85`) |
| Normal open → advance → close unaffected | **PASS** | repeated launches; story ends and returns to the previous screen |
| Broken image still advances instead of hanging | **PASS (by construction)** | `onLoadFailed` invokes the same callback |

Stated plainly: I could not produce a timestamp comparison of "image ready" versus "timer
start". App-level `Log.d`/`Log.e` do not reach logcat on this device — a temporary `StoryTimer`
log produced no line for the app pid at all — so the instrumented proof was dropped rather than
written up as if it had run. The temporary log was removed and the final APK rebuilt and
installed.
