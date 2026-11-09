# NOVA — Implementation Plan (InstaNova Android)

## 0. Verified baseline (before writing code)

| Check | Result |
|---|---|
| Module | `:app`, Java 17, ViewBinding on, Groovy Gradle, `com.insta.nova` |
| Existing screens | 223 Java files, 5-tab `BottomNavigationView` (Home, Search, **Create**, Reels, Profile) |
| Auth / API | `RetrofitClient` + `AuthInterceptor` (Firebase ID token) — **not touched** |
| Supabase Nova backend | **Exists.** `POST /rest/v1/rpc/nova_hub_summary` → `401 42501 permission denied for function` (function present, anon correctly revoked). Same for `nova_safety_stats`, `nova_study_stats`, `nova_my_insights`, `nova_unread_alerts`, `nova_blocked_ids`. Tables/views `nova_settings`, `nova_alerts`, `nova_donors`, `nova_blood_requests`, `nova_universities`, `nova_study_sessions`, `nova_confessions`, `nova_v_eventas`… all present and RLS-locked. No schema change needed. |
| Device | `10BE2S1U640002B` connected, app installed and signed in (baseline screenshot captured) |
| Package note | The app namespace is `com.insta.nova`; Nova code therefore lives in `com.insta.nova.{hub,safe,sos,campus,lite,study,insights,alerts,widget,data}`. A parallel `com.insta.clone.nova` tree would duplicate the namespace and break `R`/manifest references, so the existing root is kept. |

## 1. Architecture

```
UI (Activity/Fragment + ViewBinding + ViewModel)
        │  LiveData<Resource<T>>
Repository (NovaRepository, NovaSettingsStore, BlockMuteCache)
        │  Retrofit Call / Room DAO / WorkManager
NovaApi (PostgREST + RPC)   NovaDb (Room)   NovaDataSaver   NovaModerator (pure Java)
        │
Supabase (RLS, request_uid())        Firebase Auth (unchanged)
```

## 2. File tree (new / touched)

```
app/build.gradle                                   (touched: room, work, test)
app/src/main/AndroidManifest.xml                   (touched: 9 Nova activities, worker)
app/src/main/res/values/colors_nova.xml            (new)
app/src/main/res/values-night/colors_nova.xml      (new)
app/src/main/res/values/dimens_nova.xml            (new)
app/src/main/res/values/styles_nova.xml            (new)
app/src/main/res/values/strings_nova.xml           (new)
app/src/main/res/values-bn/strings_nova.xml        (new)
app/src/main/res/values-bn/plurals.xml             (touched: Nova plurals)

drawable/  ic_nova, ic_nova_fill, ic_nova_safe, ic_nova_sos, ic_nova_campus,
           ic_nova_lite, ic_nova_study, ic_nova_insights, ic_bell_nova,
           ic_shield_alert, ic_phone_call, ic_calendar_add, ic_lock_anon,
           ic_flame, ic_trophy, ic_wifi_off, ic_cloud_queue,
           bg_nova_gradient, bg_nova_pill, bg_nova_card, bg_nova_card_high,
           bg_nova_chip, bg_nova_chip_selected, bg_nova_input, bg_nova_sheet,
           bg_nova_glow, bg_nova_dot, bg_nova_glass
layout/    fragment_nova_hub, item_nova_module, activity_nova_intro, sheet_nova_about,
           activity_nova_safe, item_safe_stat, item_blocked_user, item_muted_user,
           activity_nova_alerts, item_nova_alert,
           activity_nova_sos, fragment_sos_feed, item_blood_request,
           activity_sos_create, activity_sos_detail, fragment_sos_donor,
           fragment_sos_my_requests, item_sos_pledge,
           activity_nova_campus, activity_campus_join, fragment_campus_events,
           item_campus_event, activity_campus_event_create, fragment_campus_lost,
           item_campus_lost, activity_campus_lost_create, fragment_campus_confessions,
           item_confession, sheet_confession_compose, sheet_campus_join_result,
           activity_nova_lite, activity_nova_benchmark, sheet_nova_data_saver,
           activity_nova_study, fragment_study_home, fragment_study_leaderboard,
           fragment_study_qa, activity_focus_timer, item_leader_row, item_question,
           activity_study_question, activity_ask_question, item_answer,
           activity_nova_insights, item_insight_total,
           sheet_nova_block_confirm, sheet_nova_mute, sheet_nova_report,
           sheet_nova_guard_warning, view_nova_skeleton, view_nova_empty

java/com/insta/nova/
  widget/  NovaBackgroundView, NovaHeroView, NovaChartView, ProgressRingView,
           ConfettiView, GlowView, NovaUi (helpers: dp, animator scale, haptics, stagger)
  data/    NovaApi, NovaDtos, NovaRepository, NovaSettingsStore, BlockMuteCache,
           NovaDataSaver, NovaGlide, NovaNetMeter, NovaDb,
           entity/{NetUsage, CachedFeed, QueuedPost}, dao/{NetUsageDao, FeedCacheDao, PostQueueDao},
           worker/NovaPostUploadWorker, worker/NovaStudyAlarmWorker
  safe/    NovaModerator, NovaLexicon, NovaGuard, NovaSafeActivity, NovaSafeViewModel,
           SafeActions (block/mute/report), NovaModerationDialog
  hub/     NovaHubFragment, NovaHubViewModel, NovaIntroActivity, NovaAboutSheet
  alerts/  NovaAlertsActivity, AlertsPoller, NovaNotifications
  sos/     NovaSosActivity, NovaSosViewModel, SosFeedFragment, SosCreateActivity,
           SosDetailActivity, SosDonorFragment, SosMyRequestsFragment, BloodCompat
  campus/  NovaCampusActivity, CampusJoinActivity, CampusEventsFragment,
           CampusLostFoundFragment, CampusConfessionsFragment, CampusEventCreateActivity,
           CampusLostCreateActivity
  study/   NovaStudyActivity, NovaStudyViewModel, FocusTimerActivity,
           StudyQuestionActivity, AskQuestionActivity
  insights/NovaInsightsActivity, NovaInsightsViewModel
  ui/settings/SettingsActivity (touched: Nova section)
  ui/main/MainActivity (touched: Nova tab, badge, glow, breathing)
  ui/home/*, ui/comment/*, ui/chat/UserProfileActivity, ui/story/*,
  ui/reels/*, ui/post/*, ui/explore/* (touched: block/mute filter + donor badge +
           data-saver hooks + real Block/Mute/Report)
  util/Constants (touched: Nova extras)

app/src/main/assets/nova_lexicon.json
app/src/test/java/com/insta/nova/safe/NovaModeratorTest.java
app/src/test/java/com/insta/nova/sos/BloodCompatTest.java
app/src/test/java/com/insta/nova/util/NovaFormatTest.java
docs/NOVA_REPORT.md
```

## 3. Batch plan

| # | Batch | Deliverable |
|---|---|---|
| 1 | Gradle, theme, colors, dimens, styles, 17 icons, drawables, strings (en/bn), manifest | design tokens compile |
| 2 | Custom views: NovaHeroView, NovaBackgroundView, NovaChartView, ProgressRingView, ConfettiView, GlowView, NovaUi | render on device |
| 3 | Data layer: NovaApi, DTOs, NovaRepository, NovaSettingsStore, BlockMuteCache, NovaDataSaver, NovaGlide, Room + net meter + workers | `assembleDebug` green |
| 4 | NovaModerator + lexicon + NovaGuard + unit tests (60+) | `testDebugUnitTest` green |
| 5 | Nova tab, hub fragment, intro, About, badge, breathing star | screenshots |
| 6 | Nova Safe screen, playground, settings toggles, real Block/Mute/Report everywhere | screenshots + logcat |
| 7 | Alerts screen + poller + notifications + permission rationale | screenshots |
| 8 | SOS home/feed/create | screenshots |
| 9 | SOS detail, donor, my requests, badges | screenshots |
| 10 | Campus join + events | screenshots |
| 11 | Campus lost & found + confessions | screenshots |
| 12 | Lite settings, data saver, meter, benchmark, CSV | screenshots |
| 13 | Offline feed cache + offline post queue | airplane-mode evidence |
| 14 | Study focus timer + streak + leaderboard | screenshots |
| 15 | Q&A board + detail | screenshots |
| 16 | Insights + charts | screenshots |
| 17 | Settings Nova section, Bangla audit, accessibility pass | screenshots |
| 18 | docs/NOVA_REPORT.md + verification table (PASS/FAIL/BLOCKED) | report |

## 4. Honest constraints

- Three fresh accounts (`testa/b/c`) cannot be created autonomously: sign-up requires an emailed verification code, and the device is already signed in to the owner's account. Device verification therefore uses the signed-in account plus the real Supabase data it owns; multi-account flows (pledge → confirm, follow-gated chat) are marked **BLOCKED (needs testb/testc)** rather than claimed as PASS.
- Confessions need a verified university email. Per the brief, that is a stop-and-ask item, not something to fake.
- The Nova backend is read-verified only (RLS denies anon). Runtime results are evidenced by logcat + screenshots.
