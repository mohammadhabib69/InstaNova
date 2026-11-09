# Nova — Module Report

**Scope:** the Nova feature set inside InstaNova (hub + SOS, Safe, Campus, Lite, Study,
Insights, Alerts).
**Basis:** source read on 2026-10-23, plus checks on the installed build (device 1
`I2302`, device 2 `OnePlus 9R`, accounts `qaverify` / `wovo`).
**Status vocabulary:** *Verified* = seen working on a device in this session. *Built* =
present in code and wired to the backend, not exercised in this pass.

---

## 1. What Nova is

Nova is not a separate app. It is the middle tab of InstaNova and a set of six modules
behind it, aimed at Bangladeshi students. The social feed stays the product; Nova is the
utility layer on top of the same account, the same Postgres project and the same auth.

Everything is reached from `NovaHubFragment`, which renders each module as a card with a
live counter from `nova_hub_summary`: how many blood requests are open, how many guard
warnings this week, whether you joined a campus, the data-saver state, today's study
minutes, and a growth dashboard entry.

A three-page intro (`NovaIntroActivity`) explains the modules once per account; there is
also an About sheet. Both are skippable.

---

## 2. Module map

| Module | One line | Primary data |
|---|---|---|
| Nova SOS | Blood requests near you, matched by compatibility | `nova_blood_requests`, `nova_blood_pledges`, `nova_donors`, `nova_districts` |
| Nova Safe | Offline text moderation applied on every posting surface | `nova_moderation_events`, block/mute lists |
| Nova Campus | University membership, confessions, events, lost & found | `nova_campus_members`, `nova_universities`, `nova_questions`-like feeds |
| Nova Study | Focus sessions, streak, leaderboard, Q&A board | `nova_study_sessions`, `nova_questions`, `nova_answers` |
| Nova Lite | Data saver with measured savings | local `NetUsage` rows + settings |
| Nova Insights | 7/30-day growth charts | `nova_my_insights` |
| Alerts | Polling notifier for SOS and campus events | `nova_alerts` |

---

## 3. Module detail

### 3.1 Nova SOS

**What it does.** Lets a student post a blood request (group, units, urgency, hospital,
district, needed-by) and lets nearby donors see only the requests they can actually
serve. Donors pledge, the requester can request contact, a donation can be confirmed, and
the donor earns a badge.

**How it works.**
- `BloodCompat` is pure Java and encodes the full recipient→donor table (8 groups, 64
  pairs) plus a **90-day** eligibility window. Donor feeds are filtered by that table, not
  by a free-text group field.
- Urgency is one of `critical`, `today`, `this week`, each with its own chip and copy.
- Creation goes through `nova_create_blood_request`; the feed is `nova_blood_feed`;
  matching counts come from `nova_match_count`.
- Pledge lifecycle is real: `nova_pledge` / `nova_unpledge`, `nova_request_pledges`,
  `nova_confirm_donation`, `nova_donor_badge`, `nova_set_request_status`.
- New requests fan out as system notifications through `AlertsPoller` (see §3.7).

**On device.** The hub card read `1 open · 0 critical` — live data, not placeholder.

**Not exercised in this pass:** end-to-end request creation and the donor pledge flow.
The rules are unit-tested (`app/src/test/.../sos`), but I did not place a request.

### 3.2 Nova Safe

**What it does.** Blocks or warns on abusive text before it is posted, on every surface
that accepts text: captions, comments, confessions, Q&A, chat.

**How it works.**
- `NovaModerator` is deterministic and fully offline: NFKC normalise → lowercase → strip
  zero-width characters and diacritics → leetspeak map → collapse runs of 3+ characters →
  join single-letter chains (`f u c k`). Whole-word matching first, then guarded
  substrings, with a whitelist for harmless look-alikes and context reducers that clamp
  jokes to severity 1.
- Severity is banded 0–3 (score bases 26 / 52 / 78). Severity 3 always blocks; strict
  surfaces such as confessions block from severity 2. A user setting decides whether a
  warning appears before posting.
- Every decision can be logged as one of warned / edited / blocked / posted anyway /
  hidden into `nova_moderation_events`, which is what the "guards this week" counter
  counts (`nova_safety_stats`).
- Block and mute are separate lists (`nova_block_user`, `nova_mute_user`,
  `nova_blocked_ids`, `nova_muted_users`).

**Why offline matters.** No text leaves the device for moderation, and the same code runs
in JVM tests — 298 test methods exist in the repo, with the largest suites under `safe/`
and `sos/`.

**Not exercised in this pass:** a live block/warn on a real post.

### 3.3 Nova Campus

**What it does.** Ties the account to a university and unlocks campus-only surfaces.

**How it works.**
- Joining writes a row via `nova_campus_join` against a university from `nova_universities`
  (which carries `email_domains`); leaving uses `nova_campus_leave`.
- The campus screen distinguishes verified from unverified membership and says so.
- **Confessions** read from `nova_confessions_feed`, with `nova_post_confession`,
  `nova_like_confession`, `nova_delete_confession`. Posting requires the server flag
  `nova_is_verified_student`; when it is false the composer is hidden and a note explains
  why. The flag is set server-side, keyed to the university email domain — the client
  cannot set it.
- **Events** with RSVP (`nova_events`, `nova_event_rsvps`, read via `nova_v_events`).
- **Lost & found** (`nova_lostfound`, read via `nova_v_lostfound`).

**Verified limitation.** The working account signs in with `rahman2307076@gmail.com`, which
is not a university domain, so `is_verified` stays false and confessions cannot be posted
from this account. This is correct behaviour, not a bug — but it means the confessions flow
is unverified end-to-end.

### 3.4 Nova Study

**What it does.** A focus timer with subjects, a streak, a leaderboard and a Q&A board.

**How it works.**
- Subjects: General / Math / Physics / ICT. Durations: 25, 45, 60 minutes or custom.
- A session writes `user_id`, `subject`, `minutes`, `completed`, `started_at` to
  `nova_study_sessions`. Giving up early still saves elapsed minutes after an explicit
  confirm ("Elapsed minutes are still saved").
- Stats (`nova_study_stats`) compute streak and best from days with completed sessions,
  bucketed in **Asia/Dhaka** — the same timezone the app displays.
- Leaderboard: `nova_study_leaderboard(p_scope, p_limit)`. `p_scope` is an *audience* —
  `campus` or `following` — and, since the 2026-10-23 patch, also `week` and `all`, which
  rank the people you follow plus you over 7 days or all time. Ranks tie on equal minutes.
- Q&A: `nova_questions` / `nova_answers` (via `nova_v_questions`, `nova_v_answers`), votes
  through `nova_toggle_vote`, accepted answers through `nova_accept_answer`, reporting
  through `nova_report`. Board filters: Latest / Unsolved / Mine, and by subject.

**Verified on device.** Streak `1 day`, `Today 2 min · Best 1`; both leaderboard tabs render
`@qaverify 2 min` and `@wovo 2 min`; a question created on one phone was upvoted from the
other and read back as `1 votes`.

### 3.5 Nova Lite

**What it does.** Saves mobile data, and reports what it actually saved.

**How it works.**
- Three modes: Off / Auto / Always. Auto means "the active network is metered, or the
  system Data Saver restricts background data".
- One `LiveData<Boolean>` carries the state, with `peekActive()` as a cheap synchronous
  check on the image-loading hot path — no polling.
- Savings are **measured**, not estimated: usage rows recorded by the meter feed both the
  savings figure and a benchmark. Rows can be exported via `FileProvider`.

**On device.** Hub card reads `Auto — on Wi-Fi`.

### 3.6 Nova Insights

**What it does.** Growth dashboard for likes, comments, followers and posts over 7 or 30
days.

**How it works.** All numbers come from `nova_my_insights`; the chart is a custom
`NovaChartView`. When the period has no activity the screen says so rather than drawing an
empty chart.

**Not exercised in this pass:** the chart with non-zero data.

### 3.7 Alerts

**What it does.** Turns new SOS requests and campus signals into system notifications,
without a push backend.

**How it works.** `AlertsPoller.tick()` reads up to 8 rows from `nova_alerts` for the
signed-in user and raises a notification **once per alert id** — announced IDs live in
`nova_alerts_seen`, so a restart does not re-notify the same blood request. The bell badge
uses `nova_unread_alerts`.

**Consequence:** delivery depends on the app running. There is no FCM push for these, so a
phone that has not opened the app recently will not be notified.

---

## 4. Cross-cutting architecture

- **Auth.** Nova RPCs are `SECURITY DEFINER` and resolve the caller with
  `public.request_uid()` — not `auth.uid()`. Any new function must follow that or it will
  raise `not_authenticated` (this exact mistake was caught while patching the leaderboard).
- **RLS.** Tables are row-scoped to the caller. Direct reads of another student's study
  sessions are not possible; cross-user aggregation has to happen inside a definer
  function.
- **Local caches.** Feeds and session lists are mirrored locally, which is what makes
  repeat opens instant; the cost is that a local mirror can outlive the server row.
- **Time.** Streaks and "today" counters use Asia/Dhaka explicitly; the leaderboard window
  is a rolling 7 days.
- **Schema.** The Nova schema lives only on the server — it is not in this repository, and
  `supabase db push` cannot be used (remote history uses timestamped names that match no
  local file). Schema changes go through the dashboard or a direct connection.

---

## 5. Verification matrix (this session)

| Surface | Check | Evidence | Status |
|---|---|---|---|
| Hub | Six module cards with live counters | `nova_hub_now.png` | Verified |
| SOS | Open/critical counts from server | `nova_hub_now.png` | Verified |
| SOS | Create request → pledge → donate | — | Built |
| Safe | Moderation blocks abusive text | — | Built (unit-tested) |
| Campus | Join, verified badge | — | Built |
| Campus | Confessions composer gate | prior run, `is_verified=false` | Verified (blocked by design) |
| Study | Session, streak, stats | `v5_lb_week.png` | Verified |
| Study | Leaderboard week + all, cross-account | `v5_lb_week.png`, `v5_lb_all2.png` | Verified |
| Study | Q&A create + upvote across devices | `d2_voted.png`, `v4_qa_vote_seen.png` | Verified |
| Lite | Auto mode on Wi-Fi | `d2_hub.png` | Verified |
| Insights | Charts with data | — | Built |
| Alerts | Badge count | home feed capture | Verified |

---

## 6. Limitations worth knowing

1. **Confessions need a university email.** A Gmail account can never post; the flag is
   server-side. Either accept that or add a real verification path (student ID upload).
2. **Leaderboard audience is narrow.** It ranks people you follow plus you. A true
   campus-wide or global board needs a different function, not a UI change.
3. **Alerts are polling.** No push means no notification while the app is closed for a
   long stretch — a problem for blood requests, where minutes matter.
4. **Safe is a word-list engine.** Deterministic and testable, but it will not catch
   context, sarcasm or novel spelling. Severity thresholds are policy, not truth.
5. **SOS eligibility depends on self-reported donation dates.** A stale date can hide a
   real donor or surface an ineligible one.
6. **Nova schema is not reproducible from this repo**, which is why schema work currently
   needs the dashboard.

---

## 7. Suggested next steps

- Add push (FCM) for SOS alerts — the one module where latency is the product.
- Build a genuine verification path for Campus, or relax confessions to "joined campus"
  with a disclaimer.
- Extend the leaderboard to campus scope once membership is real for more than one user.
- Add an end-to-end test for SOS pledge → donation so the 90-day rule is exercised against
  real rows, not just unit tests.
- Bring the Nova schema into the repo under `supabase/migrations` with timestamped names so
  `db push` becomes usable again.
