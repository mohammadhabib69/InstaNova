# PAGE 6 — DM INBOX — changed files

**Design only.** No behaviour changes: `ChatRepository` / `SearchRepository`, the API
calls, the models, the navigation targets and every view id consumed by code stay as they
are. Only XML, drawables, styles and presentation Java changed. Every colour and size
comes from a PAGE 0 token (or from a new PAGE 6 token added in the blocks below) — nothing
is hard-coded in a layout.

**Reference:** Image 6. Per the brief, the Notes / 'Leave a note' row is omitted (not a
feature of this app).

Read order: tokens → drawables → layouts → presentation Java, then the DESIGN CHECKLIST
and the NOTES + device verification at the end.

---

## 1. Token — global dimens (PAGE 6 block added)

**Path:** `app/src/main/res/values/dimens.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- ============================================================= -->
    <!-- PAGE 0 — GLOBAL DIMENS (typography, shape, icons, spacing)     -->
    <!-- ============================================================= -->

    <!-- Typography scale (Roboto) -->
    <dimen name="text_username">14sp</dimen>       <!-- usernames / button text (Medium) -->
    <dimen name="text_button">14sp</dimen>         <!-- button text (Medium) -->
    <dimen name="text_body">14sp</dimen>           <!-- body / caption / counts (Regular) -->
    <dimen name="text_title">16sp</dimen>          <!-- bar / sheet titles (Medium) -->
    <dimen name="text_screen_title">22sp</dimen>   <!-- big screen titles (Bold) -->
    <dimen name="text_meta">12sp</dimen>           <!-- meta / timestamps -->
    <dimen name="text_auth_title">22sp</dimen>
    <dimen name="text_wordmark_home">28sp</dimen>
    <dimen name="text_wordmark_login">42sp</dimen>

    <!-- Shape / corner radii -->
    <dimen name="radius_control">8dp</dimen>       <!-- buttons / search bars / chips -->
    <dimen name="radius_control_lg">10dp</dimen>
    <dimen name="radius_sheet">16dp</dimen>        <!-- bottom sheet top radius -->

    <!-- Icon sizes -->
    <dimen name="icon_default">24dp</dimen>
    <dimen name="icon_bottom_nav">28dp</dimen>

    <!-- Bottom navigation -->
    <dimen name="bottom_nav_height">52dp</dimen>
    <dimen name="divider_hairline">1dp</dimen>

    <!-- Avatars -->
    <dimen name="avatar_feed_header">36dp</dimen>
    <dimen name="avatar_nav">28dp</dimen>
    <dimen name="nav_avatar_ring_stroke">1.5dp</dimen>

    <!-- Verified badge -->
    <dimen name="badge_verified_size">14dp</dimen>

    <!-- Story tray / ring -->
    <dimen name="story_avatar_size">64dp</dimen>
    <dimen name="story_item_width">84dp</dimen>
    <dimen name="story_ring_gap">3dp</dimen>
    <dimen name="story_ring_stroke_seen">1.5dp</dimen>
    <dimen name="story_ring_stroke_unseen">2.5dp</dimen>
    <dimen name="story_tray_height">108dp</dimen>

    <!-- Misc existing -->
    <dimen name="button_auth_height">46dp</dimen>
    <dimen name="double_tap_heart_size">100dp</dimen>
    <dimen name="indicator_dot_size">6dp</dimen>
    <dimen name="indicator_dot_spacing">4dp</dimen>
    <dimen name="input_field_height">46dp</dimen>
    <dimen name="top_bar_height">50dp</dimen>

    <!-- ============================================================= -->
    <!-- PAGE 1 — HOME (top bar, story tray, feed post)                 -->
    <!-- Adds to / does not replace the PAGE 0 scale above.             -->
    <!-- ============================================================= -->

    <!-- Home top bar -->
    <dimen name="home_top_bar_height">48dp</dimen>
    <dimen name="home_top_bar_padding_horizontal">16dp</dimen>
    <dimen name="home_top_bar_icon_create">28dp</dimen>
    <dimen name="home_top_bar_icon_action">26dp</dimen>
    <dimen name="home_top_bar_icon_gap">20dp</dimen>
    <dimen name="home_badge_dot_size">8dp</dimen>
    <dimen name="home_badge_count_height">16dp</dimen>
    <dimen name="home_badge_padding_horizontal">4dp</dimen>
    <dimen name="text_badge">10sp</dimen>

    <!-- Story tray -->
    <dimen name="story_tray_padding_horizontal">12dp</dimen>
    <dimen name="story_ring_outer_size">76dp</dimen>
    <dimen name="story_ring_gap_item">4dp</dimen>
    <dimen name="story_ring_stroke_item">2dp</dimen>
    <dimen name="story_add_badge_size">22dp</dimen>
    <dimen name="story_add_badge_border">2dp</dimen>
    <dimen name="story_add_badge_icon">12dp</dimen>
    <dimen name="story_label_margin_top">4dp</dimen>

    <!-- Feed post -->
    <dimen name="post_padding_horizontal">12dp</dimen>
    <dimen name="post_spacing_bottom">12dp</dimen>
    <dimen name="post_header_height">56dp</dimen>
    <dimen name="post_header_avatar_size">32dp</dimen>
    <dimen name="post_header_avatar_gap">10dp</dimen>
    <dimen name="post_icon_more">24dp</dimen>
    <dimen name="post_line_gap">4dp</dimen>
    <dimen name="post_line_gap_tight">2dp</dimen>
    <dimen name="post_dots_margin_top">8dp</dimen>
    <dimen name="post_media_overlay_margin">12dp</dimen>
    <dimen name="post_media_avatar_size">32dp</dimen>
    <dimen name="post_media_avatar_border">1.5dp</dimen>
    <dimen name="post_media_badge_size">18dp</dimen>
    <dimen name="post_media_badge_icon">12dp</dimen>
    <dimen name="post_media_control_icon">28dp</dimen>
    <dimen name="post_media_control_padding">5dp</dimen>
    <dimen name="post_chip_padding_horizontal">8dp</dimen>
    <dimen name="post_chip_padding_vertical">3dp</dimen>
    <dimen name="post_action_row_height">48dp</dimen>
    <dimen name="post_action_icon">26dp</dimen>
    <dimen name="post_action_gap">16dp</dimen>
    <dimen name="post_count_gap">6dp</dimen>

    <!-- Home empty state / "You're all caught up" footer -->
    <dimen name="empty_state_padding">24dp</dimen>
    <dimen name="empty_state_gap">8dp</dimen>
    <dimen name="caught_up_icon_size">56dp</dimen>
    <dimen name="caught_up_gap">16dp</dimen>
    <dimen name="caught_up_gap_tight">6dp</dimen>
    <dimen name="caught_up_padding_vertical">40dp</dimen>

    <!-- ============================================================= -->
    <!-- PAGE 2 — PROFILE                                               -->
    <!-- ============================================================= -->

    <!-- Top bar -->
    <dimen name="profile_top_bar_height">56dp</dimen>
    <dimen name="profile_top_bar_padding_horizontal">16dp</dimen>
    <dimen name="profile_top_bar_icon">28dp</dimen>
    <dimen name="profile_top_bar_icon_gap">24dp</dimen>
    <dimen name="profile_username_size">22sp</dimen>
    <dimen name="profile_username_other_size">20sp</dimen>
    <dimen name="profile_nav_icon">24dp</dimen>
    <dimen name="profile_chevron_size">16dp</dimen>
    <dimen name="profile_chevron_gap">6dp</dimen>

    <!-- Header -->
    <dimen name="profile_header_padding">16dp</dimen>
    <dimen name="profile_header_gap">16dp</dimen>
    <dimen name="profile_avatar_frame">96dp</dimen>
    <dimen name="profile_avatar_size">86dp</dimen>
    <dimen name="profile_ring_stroke">3dp</dimen>
    <dimen name="profile_ring_gap">2dp</dimen>
    <dimen name="profile_display_name_size">16sp</dimen>
    <dimen name="profile_stat_number_size">17sp</dimen>
    <dimen name="profile_stat_label_size">14sp</dimen>
    <dimen name="profile_stat_margin_top">8dp</dimen>

    <!-- Bio -->
    <dimen name="profile_bio_size">14sp</dimen>
    <dimen name="profile_bio_margin_top">8dp</dimen>

    <!-- Buttons row -->
    <dimen name="profile_buttons_margin_top">12dp</dimen>
    <dimen name="profile_button_height">34dp</dimen>
    <dimen name="profile_button_gap">8dp</dimen>
    <dimen name="profile_button_square">34dp</dimen>
    <dimen name="profile_button_icon">20dp</dimen>

    <!-- Highlights row -->
    <dimen name="profile_highlights_margin_top">16dp</dimen>
    <dimen name="profile_highlight_size">64dp</dimen>
    <dimen name="profile_highlight_ring">1dp</dimen>
    <dimen name="profile_highlight_icon">24dp</dimen>
    <dimen name="profile_highlight_title_size">12sp</dimen>
    <dimen name="profile_highlight_title_margin_top">4dp</dimen>

    <!-- Tabs -->
    <dimen name="profile_tabs_height">48dp</dimen>
    <dimen name="profile_tab_indicator">1.5dp</dimen>
    <dimen name="profile_tab_padding">6dp</dimen>

    <!-- Grid -->
    <dimen name="profile_grid_gap">1dp</dimen>
    <dimen name="profile_grid_badge">18dp</dimen>
    <dimen name="profile_grid_badge_margin">6dp</dimen>

    <!-- Empty state -->
    <dimen name="profile_empty_icon">56dp</dimen>
    <dimen name="profile_empty_padding">24dp</dimen>
    <dimen name="profile_empty_margin_top">16dp</dimen>

    <!-- ============================================================= -->
    <!-- PAGE 3 — EXPLORE (idle Search tab)                             -->
    <!-- ============================================================= -->
    <dimen name="explore_search_row_margin">12dp</dimen>
    <dimen name="search_field_height_idle">38dp</dimen>
    <dimen name="search_field_height_active">40dp</dimen>
    <dimen name="search_field_radius">10dp</dimen>
    <dimen name="search_field_padding_horizontal">12dp</dimen>
    <dimen name="search_field_text_size">16sp</dimen>
    <dimen name="search_field_icon_size">18dp</dimen>
    <dimen name="search_field_icon_gap">8dp</dimen>
    <dimen name="search_field_close_size">20dp</dimen>
    <dimen name="search_scan_button_size">38dp</dimen>
    <dimen name="search_scan_icon_size">24dp</dimen>
    <dimen name="search_scan_gap">8dp</dimen>

    <dimen name="explore_chips_margin_top">8dp</dimen>
    <dimen name="explore_chips_padding_start">12dp</dimen>
    <dimen name="explore_chip_height">34dp</dimen>
    <dimen name="explore_chip_radius">8dp</dimen>
    <dimen name="explore_chip_gap">8dp</dimen>
    <dimen name="explore_chip_padding_horizontal">12dp</dimen>
    <dimen name="explore_chip_text_size">14sp</dimen>
    <dimen name="explore_chip_icon_size">16dp</dimen>
    <dimen name="explore_chip_icon_gap">6dp</dimen>

    <!-- Soft dark halo behind the grid badges -->
    <dimen name="media_badge_shadow_inset">-3dp</dimen>

    <!-- Uniform explore grid: 1dp gutters -->
    <dimen name="explore_grid_gap">1dp</dimen>

    <!-- ============================================================= -->
    <!-- PAGE 4 — SEARCH (focused + results)                            -->
    <!-- ============================================================= -->
    <dimen name="search_top_row_height">56dp</dimen>
    <dimen name="search_back_icon_size">24dp</dimen>
    <dimen name="search_back_padding_start">16dp</dimen>
    <dimen name="search_row_padding_end">12dp</dimen>
    <dimen name="search_field_margin_start">12dp</dimen>
    <dimen name="search_tabs_height">44dp</dimen>
    <dimen name="search_tab_text_size">14sp</dimen>
    <dimen name="search_tab_indicator">2dp</dimen>

    <dimen name="search_row_height">72dp</dimen>
    <dimen name="search_row_padding_horizontal">16dp</dimen>
    <dimen name="search_row_avatar_size">56dp</dimen>
    <dimen name="search_row_gap">12dp</dimen>
    <dimen name="search_row_icon_size">24dp</dimen>
    <dimen name="search_row_text_size">14sp</dimen>
    <dimen name="search_row_line_gap">2dp</dimen>
    <dimen name="search_row_title_icon_gap">4dp</dimen>
    <dimen name="search_row_verified_size">14dp</dimen>
    <dimen name="search_row_trailing_size">20dp</dimen>

    <dimen name="search_recent_header_height">48dp</dimen>
    <dimen name="search_page_title_size">16sp</dimen>
    <dimen name="search_clear_all_size">14sp</dimen>
    <dimen name="search_empty_icon_size">56dp</dimen>
    <dimen name="search_empty_margin_top">16dp</dimen>

    <!-- ============================================================= -->
    <!-- PAGE 5 — REELS TAB                                             -->
    <!-- Full-screen media page. Every value below sits on top of user   -->
    <!-- media, so it is theme-independent (white text, dark scrims).    -->
    <!-- ============================================================= -->

    <!-- Full-bleed scrims: 140dp top (dark -> clear), 280dp bottom (clear -> dark) -->
    <dimen name="reel_scrim_top_height">140dp</dimen>
    <dimen name="reel_scrim_bottom_height">280dp</dimen>

    <!-- Center play / pause indicator (unchanged from the previous layout) -->
    <dimen name="reel_play_indicator_size">64dp</dimen>

    <!-- Top chrome: 'Reels' + chevron (start) / camera (end) -->
    <dimen name="reel_top_bar_min_height">48dp</dimen>
    <dimen name="reel_top_bar_padding_horizontal">16dp</dimen>
    <dimen name="reel_top_bar_padding_vertical">8dp</dimen>
    <dimen name="reel_chevron_size">18dp</dimen>
    <dimen name="reel_chevron_gap">6dp</dimen>
    <dimen name="reel_camera_size">28dp</dimen>

    <!-- Right action column -->
    <dimen name="reel_actions_margin_end">16dp</dimen>
    <dimen name="reel_actions_margin_bottom">24dp</dimen>
    <dimen name="reel_action_icon_size">30dp</dimen>
    <dimen name="reel_action_icon_more_size">28dp</dimen>
    <dimen name="reel_action_count_size">14sp</dimen>
    <dimen name="reel_action_count_gap">4dp</dimen>
    <dimen name="reel_action_group_gap">24dp</dimen>

    <!-- Audio thumbnail: 32dp rounded square, 2dp white border + music badge -->
    <dimen name="reel_audio_thumb_size">32dp</dimen>
    <dimen name="reel_audio_thumb_radius">6dp</dimen>
    <dimen name="reel_audio_thumb_border">2dp</dimen>
    <dimen name="reel_audio_inner_radius">4dp</dimen>
    <dimen name="reel_audio_badge_size">14dp</dimen>
    <dimen name="reel_audio_badge_icon_size">10dp</dimen>
    <dimen name="reel_audio_badge_padding">1dp</dimen>
    <dimen name="reel_audio_badge_radius">3dp</dimen>

    <!-- Bottom-left author + caption block (max 75% of the page width) -->
    <dimen name="reel_meta_margin_start">16dp</dimen>
    <dimen name="reel_meta_margin_bottom">16dp</dimen>
    <dimen name="reel_meta_avatar_size">32dp</dimen>
    <dimen name="reel_meta_avatar_gap">8dp</dimen>
    <dimen name="reel_meta_username_size">14sp</dimen>
    <dimen name="reel_meta_badge_size">14dp</dimen>
    <dimen name="reel_meta_badge_gap">4dp</dimen>
    <dimen name="reel_meta_pill_gap">12dp</dimen>
    <dimen name="reel_meta_caption_gap">8dp</dimen>
    <dimen name="reel_meta_caption_size">14sp</dimen>

    <!-- Outlined 'Follow' pill -->
    <dimen name="reel_follow_pill_height">28dp</dimen>
    <dimen name="reel_follow_pill_radius">8dp</dimen>
    <dimen name="reel_follow_pill_border">1dp</dimen>
    <dimen name="reel_follow_pill_padding_horizontal">12dp</dimen>
    <dimen name="reel_follow_pill_text_size">12sp</dimen>

    <!-- ============================================================= -->
    <!-- PAGE 6 — DM INBOX                                              -->
    <!-- ============================================================= -->

    <!-- Top bar: back + own username (22sp) + chevron / video + new message -->
    <dimen name="inbox_top_bar_height">56dp</dimen>
    <dimen name="inbox_top_bar_padding_horizontal">16dp</dimen>
    <dimen name="inbox_top_bar_back_box">36dp</dimen>
    <dimen name="inbox_top_bar_back_padding">6dp</dimen>
    <dimen name="inbox_top_bar_icon">26dp</dimen>
    <dimen name="inbox_top_bar_icon_gap">20dp</dimen>

    <!-- Search field (surfaceVariant, 10dp radius) -->
    <dimen name="inbox_search_margin_horizontal">16dp</dimen>
    <dimen name="inbox_search_height">38dp</dimen>

    <!-- 'Messages' / 'Requests' section row -->
    <dimen name="inbox_section_margin_top">12dp</dimen>
    <dimen name="inbox_section_height">40dp</dimen>
    <dimen name="inbox_section_padding_horizontal">16dp</dimen>
    <dimen name="inbox_section_action_size">14sp</dimen>

    <!-- Conversation rows -->
    <dimen name="inbox_row_height">76dp</dimen>
    <dimen name="inbox_row_padding_horizontal">16dp</dimen>
    <dimen name="inbox_avatar_size">56dp</dimen>
    <dimen name="inbox_avatar_ring_size">64dp</dimen>
    <dimen name="inbox_avatar_ring_thickness">4dp</dimen>
    <dimen name="inbox_avatar_gap">12dp</dimen>
    <dimen name="inbox_name_size">15sp</dimen>
    <dimen name="inbox_message_size">14sp</dimen>
    <dimen name="inbox_row_line_gap">2dp</dimen>
    <dimen name="inbox_unread_dot_size">8dp</dimen>
    <dimen name="inbox_unread_dot_gap">16dp</dimen>
    <dimen name="inbox_camera_size">26dp</dimen>
</resources>
```

## 2. Token — global colors (PAGE 6 block added)

**Path:** `app/src/main/res/values/colors.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- ============================================================= -->
    <!-- PAGE 0 — GLOBAL DESIGN TOKENS (LIGHT)                          -->
    <!-- Semantic names are config-aware: values-night overrides them. -->
    <!-- ============================================================= -->

    <!-- Core semantic surfaces & text -->
    <color name="color_background">#FFFFFF</color>
    <color name="color_text_primary">#000000</color>
    <color name="color_text_secondary">#737373</color>
    <color name="color_surface_variant">#EFEFEF</color>
    <color name="color_divider">#DBDBDB</color>
    <color name="color_sheet">#FFFFFF</color>
    <color name="color_scrim">#99000000</color>

    <!-- Brand / accent -->
    <color name="color_primary">#0095F6</color>
    <color name="color_primary_pressed">#1877F2</color>
    <color name="color_primary_dark">#1877F2</color>
    <color name="color_primary_disabled">#4D0095F6</color>
    <color name="color_disabled_blue">#B2DFFC</color>
    <color name="color_like">#FF3040</color>
    <color name="color_destructive">#FF3040</color>
    <color name="color_mention">#00376B</color>

    <!-- Fields -->
    <color name="color_field_background">#EFEFEF</color>
    <color name="color_field_border">#DBDBDB</color>

    <!-- Story ring (seen) -->
    <color name="color_ring_seen">#C7C7C7</color>
    <color name="dot_inactive">#C7C7C7</color>

    <!-- Basic -->
    <color name="white">#FFFFFF</color>
    <color name="black">#000000</color>
    <color name="transparent">#00000000</color>

    <!-- ============================================================= -->
    <!-- Instagram Story 5-color linear gradient                        -->
    <!-- (bottom-left -> top-right)                                     -->
    <!-- ============================================================= -->
    <color name="story_gradient_1">#FEDA77</color>
    <color name="story_gradient_2">#F58529</color>
    <color name="story_gradient_3">#DD2A7B</color>
    <color name="story_gradient_4">#8134AF</color>
    <color name="story_gradient_5">#515BD4</color>

    <!-- Direct Messages bubbles / misc -->
    <color name="dm_gradient_start">#7000FF</color>
    <color name="dm_gradient_end">#0099FF</color>
    <color name="dm_bubble_received">#EFEFEF</color>
    <color name="dm_bubble_received_dark">#262626</color>
    <color name="shimmer_base">#EFEFEF</color>
    <color name="shimmer_highlight">#FFFFFF</color>

    <!-- Toast pill (always dark, both themes) -->
    <color name="toast_pill_bg">#E6000000</color>
    <color name="toast_pill_text">#FFFFFF</color>

    <!-- ============================================================= -->
    <!-- PAGE 1 — HOME                                                  -->
    <!-- ============================================================= -->
    <!-- Media overlay badge (audio / repost attribution on the post    -->
    <!-- media). Sits on top of user media, so it is theme-independent  -->
    <!-- and is intentionally not overridden in values-night.           -->
    <color name="color_media_badge">#6A38E0</color>

    <!-- PAGE 3 — soft dark halo behind grid badges (sits on top of user media,
         so it is theme-independent and not overridden in values-night). -->
    <color name="color_badge_shadow">#4D000000</color>

    <!-- ============================================================= -->
    <!-- PAGE 5 — REELS                                                 -->
    <!-- ============================================================= -->
    <!-- 70% white outline of the reel 'Follow' pill. Drawn on top of user
         media, so it is theme-independent (not overridden in values-night). -->
    <color name="color_reel_pill_border">#B3FFFFFF</color>

    <!-- ============================================================= -->
    <!-- PAGE 6 — DM INBOX                                              -->
    <!-- ============================================================= -->
    <!-- Muted trailing icon of a fully read conversation row (camera).
         Fixed by the PAGE 6 spec, so it is not overridden in values-night. -->
    <color name="color_icon_muted">#A8A8A8</color>
</resources>
```

## 3. Token — strings (PAGE 6 block added)

**Path:** `app/src/main/res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- App & General -->
    <string name="app_name">Instagram</string>
    <string name="wordmark">Instagram</string>
    <string name="welcome_to_instanova">Welcome to InstaNova</string>
    <string name="english_language">English (United States)</string>
    <string name="next">Next</string>
    <string name="more">more</string>
    <string name="post">Post</string>
    <string name="posts">Posts</string>
    <string name="comments">Comments</string>
    <string name="add_a_comment">Add a comment…</string>
    <string name="view_all_comments">View all %1$d comments</string>
    <string name="reply">Reply</string>
    <string name="delete">Delete</string>
    <string name="unfollow">Unfollow</string>
    <string name="share">Share</string>
    <string name="copy_link">Copy link</string>
    <string name="your_story">Your story</string>
    <string name="direct_messages">Direct Messages</string>
    <string name="notifications">Notifications</string>
    <string name="cd_search">Search</string>

    <!-- Home & Feed -->
    <string name="likes_count">%1$s likes</string>
    <string name="liked_by">Liked by</string>
    <string name="and">and</string>
    <string name="others">others</string>
    <string name="carousel_position">%1$d/%2$d</string>
    <string name="empty_state_subtitle">Follow people and share your first photo.</string>
    <string name="all_caught_up">You\'re All Caught Up</string>
    <string name="all_caught_up_subtitle">You\'ve seen all new posts from the past 3 days.</string>
    <string name="appbar_scrolling_view_behavior">com.google.android.material.appbar.AppBarLayout$ScrollingViewBehavior</string>

    <!-- Auth & Login -->
    <string name="login_hint_identifier">Phone number, username or email</string>
    <string name="login_hint_password">Password</string>
    <string name="login_button">Log in</string>
    <string name="forgot_password">Forgot password?</string>
    <string name="create_new_account">Create new account</string>
    <string name="already_have_an_account">Already have an account?</string>
    <string name="error_incorrect_credentials">Incorrect username or password. Please try again.</string>

    <!-- Forgot Password -->
    <string name="find_your_account">Find your account</string>
    <string name="forgot_password_subtitle">Enter your email or phone number and we\'ll send you a link to get back into your account.</string>
    <string name="email_sent_to">We\'ve sent a recovery link to %1$s</string>

    <!-- Signup Wizard -->
    <string name="signup_title_email">What\'s your email?</string>
    <string name="signup_subtitle_email">Enter the email address where you can be contacted. No one will see this on your profile.</string>
    <string name="hint_email">Email address</string>
    <string name="check_email_confirm">Please check your email to confirm your account.</string>

    <string name="signup_title_password">Create a password</string>
    <string name="signup_subtitle_password">Create a password with at least 6 letters or numbers. It should be something others can\'t guess.</string>

    <string name="signup_title_birthday">What\'s your birthday?</string>
    <string name="signup_error_age">You must be at least 13 years old to use Instagram.</string>

    <string name="signup_title_name">What\'s your name?</string>
    <string name="hint_full_name">Full name</string>

    <string name="signup_title_username">Create a username</string>
    <string name="hint_username">Username</string>
    <string name="signup_btn_create">Complete sign up</string>

    <!-- Profile (PAGE 2) -->
    <string name="profile_stat_posts">posts</string>
    <string name="profile_stat_followers">followers</string>
    <string name="profile_stat_following">following</string>
    <string name="profile_edit">Edit profile</string>
    <string name="profile_share">Share profile</string>
    <string name="profile_follow">Follow</string>
    <string name="profile_following">Following</string>
    <string name="profile_message">Message</string>
    <string name="profile_highlight_new">New</string>
    <string name="profile_no_reposts">No reposts yet</string>
    <string name="profile_no_photos_of_you">No photos of you yet</string>

    <!-- Explore / Search (PAGE 3 + PAGE 4) -->
    <string name="search_hint">Search</string>
    <string name="explore_chip_reels">Reels</string>
    <string name="explore_chip_shop">Shop</string>
    <string name="explore_tag_architecture">Architecture</string>
    <string name="explore_tag_travel">Travel</string>
    <string name="explore_tag_decor">Decor</string>
    <string name="explore_tag_food">Food</string>
    <string name="explore_tag_art">Art</string>
    <string name="explore_tag_nature">Nature</string>
    <string name="search_tab_top">Top</string>
    <string name="search_tab_accounts">Accounts</string>
    <string name="search_tab_audio">Audio</string>
    <string name="search_tab_tags">Tags</string>
    <string name="search_tab_places">Places</string>
    <string name="search_recent">Recent</string>
    <string name="search_clear_all">Clear all</string>
    <string name="search_no_results">No results</string>
    <string name="tag_posts_count">%1$s posts</string>
    <string name="tag_count_fewer_than_100">Fewer than 100</string>

    <!-- Navigation -->
    <string name="nav_home">Home</string>
    <string name="nav_search">Search</string>
    <string name="nav_create">Create</string>
    <string name="nav_reels">Reels</string>
    <string name="nav_profile">Profile</string>

    <!-- Decorative UI (Constants.DECORATIVE_UI) -->
    <string name="not_available">Not available</string>

    <!-- Reels (PAGE 5) -->
    <string name="reel_camera">Camera</string>
    <string name="reel_audio_thumbnail">Audio</string>
    <string name="reel_more_options">More options</string>
    <string name="reel_empty_title">No Reels Yet</string>
    <string name="reel_empty_action">Create reel</string>

    <!-- DM Inbox (PAGE 6) -->
    <string name="inbox_messages">Messages</string>
    <string name="inbox_requests">Requests</string>
    <string name="inbox_no_requests">No message requests</string>
    <string name="inbox_empty_title">Your Messages</string>
    <string name="inbox_empty_subtitle">Send private photos and messages to a friend or group.</string>
    <string name="inbox_send_message">Send Message</string>
    <string name="inbox_back">Back</string>
    <string name="inbox_video_call">Video call</string>
    <string name="inbox_new_message">New message</string>
    <string name="inbox_camera">Camera</string>
    <string name="inbox_sent_a_photo">Sent a photo</string>
</resources>
```

## 4. Token — themes / styles (PAGE 6 styles added)

**Path:** `app/src/main/res/values/themes.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">

    <!-- ============================================================= -->
    <!-- PAGE 0 — BASE APP THEME (LIGHT; DayNight picks night variant)  -->
    <!-- Instagram-flat: no purple, no shadows, no elevation.          -->
    <!-- ============================================================= -->
    <style name="Theme.InstaClone" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <!-- Neutralize Material brand palette (remove purple defaults) -->
        <item name="colorPrimary">@color/color_primary</item>
        <item name="colorPrimaryVariant">@color/color_primary</item>
        <item name="colorSecondary">@color/color_primary</item>
        <item name="colorSecondaryVariant">@color/color_primary</item>
        <item name="colorOnPrimary">@color/white</item>
        <item name="colorAccent">@color/color_primary</item>
        <item name="android:colorControlActivated">@color/color_primary</item>

        <!-- Surfaces & text -->
        <item name="android:colorBackground">@color/color_background</item>
        <item name="android:windowBackground">@color/color_background</item>
        <item name="colorSurface">@color/color_background</item>
        <item name="colorOnSurface">@color/color_text_primary</item>
        <item name="android:textColorPrimary">@color/color_text_primary</item>
        <item name="android:textColorSecondary">@color/color_text_secondary</item>

        <!-- Edge-to-edge, transparent system bars; icons adapt to theme -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:windowLightNavigationBar" tools:targetApi="o_mr1">true</item>
        <item name="android:windowDrawsSystemBarBackgrounds">true</item>

        <!-- Kill shadows / elevation globally -->
        <item name="elevationOverlayEnabled">false</item>
        <item name="android:fontFamily">sans-serif</item>

        <!-- Custom semantic attributes -->
        <item name="themeBackground">@color/color_background</item>
        <item name="themeTextPrimary">@color/color_text_primary</item>
        <item name="themeTextSecondary">@color/color_text_secondary</item>
        <item name="themeSurfaceVariant">@color/color_surface_variant</item>
        <item name="themeDivider">@color/color_divider</item>
        <item name="themePrimary">@color/color_primary</item>
        <item name="themeLike">@color/color_like</item>
        <item name="themeSheet">@color/color_sheet</item>
    </style>

    <!-- Splash Screen Theme -->
    <style name="Theme.InstaClone.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/color_background</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
        <item name="postSplashScreenTheme">@style/Theme.InstaClone</item>
        <item name="themeBackground">@color/color_background</item>
        <item name="themeTextPrimary">@color/color_text_primary</item>
        <item name="themeTextSecondary">@color/color_text_secondary</item>
        <item name="themeSurfaceVariant">@color/color_surface_variant</item>
        <item name="themeDivider">@color/color_divider</item>
        <item name="themePrimary">@color/color_primary</item>
        <item name="themeLike">@color/color_like</item>
        <item name="themeSheet">@color/color_sheet</item>
    </style>

    <!-- ============================================================= -->
    <!-- Bottom Sheet themes — flat sheet, 16dp top radius, no shadow   -->
    <!-- ============================================================= -->
    <style name="InstagramBottomSheetDialog" parent="Theme.MaterialComponents.DayNight.BottomSheetDialog">
        <item name="bottomSheetStyle">@style/InstagramBottomSheetStyle</item>
        <item name="android:windowIsFloating">false</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@color/color_sheet</item>
        <item name="elevationOverlayEnabled">false</item>
    </style>

    <style name="InstagramBottomSheetStyle" parent="Widget.MaterialComponents.BottomSheet.Modal">
        <item name="android:background">@drawable/bg_bottom_sheet</item>
        <item name="shapeAppearanceOverlay">@style/ShapeAppearance.Insta.BottomSheet</item>
        <item name="android:elevation">0dp</item>
    </style>

    <style name="ShapeAppearance.Insta.BottomSheet" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSizeTopLeft">@dimen/radius_sheet</item>
        <item name="cornerSizeTopRight">@dimen/radius_sheet</item>
        <item name="cornerSizeBottomLeft">0dp</item>
        <item name="cornerSizeBottomRight">0dp</item>
    </style>

    <!-- ============================================================= -->
    <!-- Typography text appearances (Roboto)                           -->
    <!-- ============================================================= -->
    <style name="TextAppearance.Insta.Username" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:textStyle">normal</item>
        <item name="android:textSize">@dimen/text_username</item>
        <item name="android:textColor">?attr/themeTextPrimary</item>
    </style>

    <style name="TextAppearance.Insta.Button" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:textSize">@dimen/text_button</item>
        <item name="android:textAllCaps">false</item>
    </style>

    <style name="TextAppearance.Insta.Body" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:textSize">@dimen/text_body</item>
        <item name="android:textColor">?attr/themeTextPrimary</item>
    </style>

    <style name="TextAppearance.Insta.Secondary" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:textSize">@dimen/text_body</item>
        <item name="android:textColor">?attr/themeTextSecondary</item>
    </style>

    <style name="TextAppearance.Insta.Title" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:textSize">@dimen/text_title</item>
        <item name="android:textColor">?attr/themeTextPrimary</item>
    </style>

    <style name="TextAppearance.Insta.ScreenTitle" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:textStyle">bold</item>
        <item name="android:textSize">@dimen/text_screen_title</item>
        <item name="android:textColor">?attr/themeTextPrimary</item>
    </style>

    <style name="TextAppearance.Insta.Meta" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:textSize">@dimen/text_meta</item>
        <item name="android:textColor">?attr/themeTextSecondary</item>
    </style>

    <!-- ============================================================= -->
    <!-- Button styles (flat, 8dp radius, no elevation)                -->
    <!-- ============================================================= -->
    <!-- PAGE 3 — Explore filter chip (34dp, 8dp radius, 1dp border) -->
    <style name="Widget.Insta.Chip" parent="">
        <item name="android:layout_width">wrap_content</item>
        <item name="android:layout_height">@dimen/explore_chip_height</item>
        <item name="android:layout_marginEnd">@dimen/explore_chip_gap</item>
        <item name="android:background">@drawable/bg_chip_outline</item>
        <item name="android:gravity">center</item>
        <item name="android:paddingStart">@dimen/explore_chip_padding_horizontal</item>
        <item name="android:paddingEnd">@dimen/explore_chip_padding_horizontal</item>
        <item name="android:drawablePadding">@dimen/explore_chip_icon_gap</item>
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:maxLines">1</item>
        <item name="android:textColor">?attr/themeTextPrimary</item>
        <item name="android:textSize">@dimen/explore_chip_text_size</item>
    </style>

    <!-- PAGE 4 — Search tab label (14sp Medium, no caps) -->
    <style name="TextAppearance.Insta.SearchTab" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:textAllCaps">false</item>
        <item name="android:textSize">@dimen/search_tab_text_size</item>
    </style>

    <!-- PAGE 6 — DM Inbox section row ('Messages' / 'Requests') -->
    <style name="TextAppearance.Insta.SectionTitle" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:textStyle">bold</item>
        <item name="android:textSize">@dimen/text_title</item>
        <item name="android:textColor">?attr/themeTextPrimary</item>
    </style>

    <style name="TextAppearance.Insta.SectionAction" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:textSize">@dimen/inbox_section_action_size</item>
        <item name="android:textColor">?attr/themeTextSecondary</item>
    </style>

    <!-- PAGE 5 — Reels action counts (14sp white, drawn on top of the video) -->
    <style name="TextAppearance.Insta.ReelCount" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:textSize">@dimen/reel_action_count_size</item>
        <item name="android:textStyle">bold</item>
        <item name="android:textColor">@color/white</item>
    </style>

    <!-- PAGE 5 — Reels outlined 'Follow' pill (28dp, 8dp radius, 1dp 70% border) -->
    <style name="Widget.Insta.ReelFollowPill" parent="">
        <item name="android:layout_height">@dimen/reel_follow_pill_height</item>
        <item name="android:background">@drawable/bg_reel_follow_pill</item>
        <item name="android:gravity">center</item>
        <item name="android:paddingStart">@dimen/reel_follow_pill_padding_horizontal</item>
        <item name="android:paddingEnd">@dimen/reel_follow_pill_padding_horizontal</item>
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:maxLines">1</item>
        <item name="android:textColor">@color/white</item>
        <item name="android:textSize">@dimen/reel_follow_pill_text_size</item>
        <item name="android:textStyle">bold</item>
    </style>

    <style name="Widget.Insta.Button.Primary" parent="Widget.AppCompat.Button.Borderless">
        <item name="android:background">@drawable/bg_button_primary</item>
        <item name="android:textColor">@color/white</item>
        <item name="android:textAppearance">@style/TextAppearance.Insta.Button</item>
        <item name="android:gravity">center</item>
        <item name="android:stateListAnimator">@null</item>
        <item name="android:elevation">0dp</item>
    </style>

    <style name="Widget.Insta.Button.Secondary" parent="Widget.AppCompat.Button.Borderless">
        <item name="android:background">@drawable/bg_button_secondary</item>
        <item name="android:textColor">?attr/themeTextPrimary</item>
        <item name="android:textAppearance">@style/TextAppearance.Insta.Button</item>
        <item name="android:gravity">center</item>
        <item name="android:stateListAnimator">@null</item>
        <item name="android:elevation">0dp</item>
    </style>

    <!-- Auth input field -->
    <style name="InstaAuthField" parent="Widget.AppCompat.EditText">
        <item name="android:layout_width">match_parent</item>
        <item name="android:layout_height">@dimen/input_field_height</item>
        <item name="android:background">@drawable/bg_input_field</item>
        <item name="android:paddingStart">12dp</item>
        <item name="android:paddingEnd">12dp</item>
        <item name="android:textSize">@dimen/text_body</item>
        <item name="android:textColor">?attr/themeTextPrimary</item>
        <item name="android:textColorHint">?attr/themeTextSecondary</item>
    </style>

    <style name="ShapeAppearance.Insta.ChatImage" parent="">
        <item name="cornerSize">18dp</item>
    </style>
</resources>
```

## 5. NEW drawable — unread dot, brand blue

**Path:** `app/src/main/res/drawable/bg_unread_dot_primary.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 6 — DM Inbox unread marker: 8dp filled dot in the brand blue
    (#0095F6). The PAGE 0 red dot (bg_unread_dot) is still used by the Home
    top bar and is intentionally left untouched.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/color_primary" />
</shape>
```

## 6. NEW drawable — conversation avatar story ring

**Path:** `app/src/main/res/drawable/bg_story_avatar_ring.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 6 — DM Inbox story ring: a 4dp ring built from the PAGE 0 story
    gradient (5-color linear gradient, bottom-left -> top-right). The inner
    oval is punched out in the theme background colour and is exactly the size
    of the 56dp avatar, so the ring hugs the avatar.

    Used by the conversation row when the other user has an active story.
-->
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">

    <item>
        <shape android:shape="oval">
            <gradient
                android:angle="45"
                android:startColor="@color/story_gradient_1"
                android:centerColor="@color/story_gradient_3"
                android:endColor="@color/story_gradient_5"
                android:type="linear" />
        </shape>
    </item>

    <item
        android:bottom="@dimen/inbox_avatar_ring_thickness"
        android:left="@dimen/inbox_avatar_ring_thickness"
        android:right="@dimen/inbox_avatar_ring_thickness"
        android:top="@dimen/inbox_avatar_ring_thickness">
        <shape android:shape="oval">
            <solid android:color="@color/color_background" />
        </shape>
    </item>

</layer-list>
```

## 7. Layout — DM inbox screen

**Path:** `app/src/main/res/layout/activity_inbox.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 6 — DM INBOX.

    Layout only. Every id consumed by InboxActivity is preserved
    (iv_back, tv_title, iv_new_chat, swipe_refresh, rv_conversations,
    layout_empty, btn_empty_send) and the ids used by the conversation
    adapter live in item_conversation.xml.

    The Notes / 'Leave a note' row from the reference is intentionally
    omitted: it is not a feature of this app.
-->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/themeBackground"
    android:orientation="vertical">

    <!-- ============================================================= -->
    <!-- Top bar 56dp: back | own username + chevron | video + new msg  -->
    <!-- ============================================================= -->
    <LinearLayout
        android:id="@+id/layout_inbox_top_bar"
        android:layout_width="match_parent"
        android:layout_height="@dimen/inbox_top_bar_height"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/inbox_top_bar_padding_horizontal"
        android:paddingEnd="@dimen/inbox_top_bar_padding_horizontal">

        <ImageView
            android:id="@+id/iv_back"
            android:layout_width="@dimen/inbox_top_bar_back_box"
            android:layout_height="@dimen/inbox_top_bar_back_box"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/inbox_back"
            android:padding="@dimen/inbox_top_bar_back_padding"
            android:src="@drawable/ic_arrow_back"
            app:tint="?attr/themeTextPrimary" />

        <LinearLayout
            android:id="@+id/layout_inbox_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/tv_title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:ellipsize="end"
                android:maxLines="1"
                android:text="@string/direct_messages"
                android:textColor="?attr/themeTextPrimary"
                android:textSize="@dimen/text_screen_title"
                android:textStyle="bold" />

            <ImageView
                android:id="@+id/iv_title_chevron"
                android:layout_width="@dimen/profile_chevron_size"
                android:layout_height="@dimen/profile_chevron_size"
                android:layout_marginStart="@dimen/profile_chevron_gap"
                android:contentDescription="@string/not_available"
                android:src="@drawable/ic_chevron_down"
                app:tint="?attr/themeTextPrimary" />
        </LinearLayout>

        <View
            android:layout_width="0dp"
            android:layout_height="1dp"
            android:layout_weight="1" />

        <ImageView
            android:id="@+id/iv_video"
            android:layout_width="@dimen/inbox_top_bar_icon"
            android:layout_height="@dimen/inbox_top_bar_icon"
            android:contentDescription="@string/inbox_video_call"
            android:src="@drawable/ic_video"
            app:tint="?attr/themeTextPrimary" />

        <ImageView
            android:id="@+id/iv_new_chat"
            android:layout_width="@dimen/inbox_top_bar_icon"
            android:layout_height="@dimen/inbox_top_bar_icon"
            android:layout_marginStart="@dimen/inbox_top_bar_icon_gap"
            android:contentDescription="@string/inbox_new_message"
            android:src="@drawable/ic_edit_square"
            app:tint="?attr/themeTextPrimary" />
    </LinearLayout>

    <!-- ============================================================= -->
    <!-- Search field (existing conversation filter)                     -->
    <!-- ============================================================= -->
    <LinearLayout
        android:id="@+id/layout_inbox_search"
        android:layout_width="match_parent"
        android:layout_height="@dimen/inbox_search_height"
        android:layout_marginStart="@dimen/inbox_search_margin_horizontal"
        android:layout_marginEnd="@dimen/inbox_search_margin_horizontal"
        android:background="@drawable/bg_search_field"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/search_field_padding_horizontal"
        android:paddingEnd="@dimen/search_field_padding_horizontal">

        <ImageView
            android:layout_width="@dimen/search_field_icon_size"
            android:layout_height="@dimen/search_field_icon_size"
            android:contentDescription="@string/search_hint"
            android:src="@drawable/ic_search"
            app:tint="?attr/themeTextSecondary" />

        <EditText
            android:id="@+id/et_inbox_search"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_marginStart="@dimen/search_field_icon_gap"
            android:layout_weight="1"
            android:background="@null"
            android:hint="@string/search_hint"
            android:imeOptions="actionSearch"
            android:inputType="text"
            android:maxLines="1"
            android:padding="0dp"
            android:textColor="?attr/themeTextPrimary"
            android:textColorHint="?attr/themeTextSecondary"
            android:textSize="@dimen/search_field_text_size" />
    </LinearLayout>

    <!-- ============================================================= -->
    <!-- Section row: 'Messages' (left) / 'Requests' (right)            -->
    <!-- ============================================================= -->
    <LinearLayout
        android:id="@+id/layout_inbox_section"
        android:layout_width="match_parent"
        android:layout_height="@dimen/inbox_section_height"
        android:layout_marginTop="@dimen/inbox_section_margin_top"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/inbox_section_padding_horizontal"
        android:paddingEnd="@dimen/inbox_section_padding_horizontal">

        <TextView
            android:id="@+id/tv_messages"
            style="@style/TextAppearance.Insta.SectionTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/inbox_messages" />

        <View
            android:layout_width="0dp"
            android:layout_height="1dp"
            android:layout_weight="1" />

        <TextView
            android:id="@+id/tv_requests"
            style="@style/TextAppearance.Insta.SectionAction"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="?attr/selectableItemBackground"
            android:text="@string/inbox_requests" />
    </LinearLayout>

    <!-- ============================================================= -->
    <!-- Conversation list                                              -->
    <!-- ============================================================= -->
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
            android:id="@+id/swipe_refresh"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/rv_conversations"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:clipToPadding="false"
                android:overScrollMode="never" />
        </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>

        <!-- 'Requests' destination: an empty screen (design-only) -->
        <TextView
            android:id="@+id/layout_requests_empty"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:gravity="center"
            android:padding="@dimen/empty_state_padding"
            android:text="@string/inbox_no_requests"
            android:textColor="?attr/themeTextSecondary"
            android:textSize="@dimen/text_title"
            android:visibility="gone" />

        <!-- Empty State -->
        <LinearLayout
            android:id="@+id/layout_empty"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:gravity="center"
            android:orientation="vertical"
            android:padding="@dimen/empty_state_padding"
            android:visibility="gone">

            <ImageView
                android:layout_width="@dimen/caught_up_icon_size"
                android:layout_height="@dimen/caught_up_icon_size"
                android:contentDescription="@string/not_available"
                android:src="@drawable/ic_send"
                app:tint="?attr/themeTextSecondary" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/caught_up_gap"
                android:text="@string/inbox_empty_title"
                android:textColor="?attr/themeTextPrimary"
                android:textSize="@dimen/text_screen_title"
                android:textStyle="bold" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/empty_state_gap"
                android:gravity="center"
                android:text="@string/inbox_empty_subtitle"
                android:textColor="?attr/themeTextSecondary"
                android:textSize="@dimen/text_body" />

            <Button
                android:id="@+id/btn_empty_send"
                style="@style/Widget.Insta.Button.Primary"
                android:layout_width="wrap_content"
                android:layout_height="@dimen/profile_button_height"
                android:layout_marginTop="@dimen/caught_up_gap"
                android:paddingStart="@dimen/empty_state_padding"
                android:paddingEnd="@dimen/empty_state_padding"
                android:text="@string/inbox_send_message" />
        </LinearLayout>
    </FrameLayout>

</LinearLayout>
```

## 8. Layout — one conversation row

**Path:** `app/src/main/res/layout/item_conversation.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 6 — one DM inbox conversation row (76dp, 16dp horizontal padding,
    no divider).

    Ids consumed by InboxActivity.ConversationAdapter are preserved:
    iv_avatar, tv_username, tv_last_message, tv_time, iv_unread_dot.
    tv_time is kept (hidden) because the adapter still resolves it: the PAGE 6
    design folds the timestamp into the second line "{message} · {time}" via
    tv_last_message.

    The avatar sits in a 56dp frame so it starts exactly at the 16dp page
    padding; the story ring is a 64dp layer behind it and may overflow the
    frame (clipChildren=false), which is why the avatar size never changes.
-->
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="@dimen/inbox_row_height"
    android:background="?attr/selectableItemBackground"
    android:paddingStart="@dimen/inbox_row_padding_horizontal"
    android:paddingEnd="@dimen/inbox_row_padding_horizontal">

    <FrameLayout
        android:id="@+id/frame_avatar"
        android:layout_width="@dimen/inbox_avatar_size"
        android:layout_height="@dimen/inbox_avatar_size"
        android:layout_centerVertical="true"
        android:clipChildren="false">

        <!-- Gradient story ring: shown only when the other user has an
             active story (the Conversation model carries no story flag yet) -->
        <View
            android:id="@+id/ring_story"
            android:layout_width="@dimen/inbox_avatar_ring_size"
            android:layout_height="@dimen/inbox_avatar_ring_size"
            android:layout_gravity="center"
            android:background="@drawable/bg_story_avatar_ring"
            android:visibility="gone" />

        <de.hdodenhof.circleimageview.CircleImageView
            android:id="@+id/iv_avatar"
            android:layout_width="@dimen/inbox_avatar_size"
            android:layout_height="@dimen/inbox_avatar_size"
            android:layout_gravity="center"
            android:contentDescription="@string/not_available"
            android:src="@drawable/ic_person_placeholder" />
    </FrameLayout>

    <!-- Text column: username (15sp) over "{last message} · {time}" (14sp) -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_centerVertical="true"
        android:layout_marginStart="@dimen/inbox_avatar_gap"
        android:layout_marginEnd="@dimen/inbox_avatar_gap"
        android:layout_toStartOf="@id/layout_end"
        android:layout_toEndOf="@id/frame_avatar"
        android:orientation="vertical">

        <TextView
            android:id="@+id/tv_username"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="?attr/themeTextPrimary"
            android:textSize="@dimen/inbox_name_size" />

        <TextView
            android:id="@+id/tv_last_message"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/inbox_row_line_gap"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="?attr/themeTextSecondary"
            android:textSize="@dimen/inbox_message_size" />
    </LinearLayout>

    <!-- Right cluster: unread dot + camera -->
    <LinearLayout
        android:id="@+id/layout_end"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_alignParentEnd="true"
        android:layout_centerVertical="true"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <View
            android:id="@+id/iv_unread_dot"
            android:layout_width="@dimen/inbox_unread_dot_size"
            android:layout_height="@dimen/inbox_unread_dot_size"
            android:background="@drawable/bg_unread_dot_primary"
            android:visibility="gone" />

        <ImageView
            android:id="@+id/iv_camera"
            android:layout_width="@dimen/inbox_camera_size"
            android:layout_height="@dimen/inbox_camera_size"
            android:layout_marginStart="@dimen/inbox_unread_dot_gap"
            android:contentDescription="@string/inbox_camera"
            android:src="@drawable/ic_camera"
            app:tint="?attr/themeTextPrimary" />

        <!-- Legacy timestamp view: the PAGE 6 row shows the time inside
             tv_last_message, so this stays hidden but resolvable. -->
        <TextView
            android:id="@+id/tv_time"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/inbox_row_line_gap"
            android:textColor="?attr/themeTextSecondary"
            android:textSize="@dimen/text_meta"
            android:visibility="gone" />
    </LinearLayout>

</RelativeLayout>
```

## 9. Java — InboxActivity (presentation only)

**Path:** `app/src/main/java/com/insta/clone/ui/chat/InboxActivity.java`

```java
package com.insta.clone.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.clone.R;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Conversation;
import com.insta.clone.data.model.Profile;
import com.insta.clone.data.repository.ChatRepository;
import com.insta.clone.data.repository.SearchRepository;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;
import com.insta.clone.util.TimeUtils;
import com.insta.clone.util.ToastUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Direct messaging inbox activity showing conversation threads.
 *
 * PAGE 6 presentation only: the repository, the empty state and the
 * Conversation -> ChatActivity navigation are unchanged. Added here: the
 * own-username top bar, the conversation filter over the already loaded list,
 * the row typography/colour states, the compact "{message} · {time}" second
 * line and the decorative chrome (title chevron, video, requests, row camera).
 */
public class InboxActivity extends AppCompatActivity {

    private ChatRepository chatRepository;
    private SearchRepository searchRepository;
    private SessionManager sessionManager;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvConversations;
    private View layoutEmpty;
    private View layoutRequestsEmpty;
    private TextView tvTitle;
    private TextView tvMessages;
    private TextView tvRequests;
    private ImageView ivTitleChevron;
    private ImageView ivVideo;
    private EditText etSearch;
    private ConversationAdapter adapter;
    private final List<Conversation> allConversations = new ArrayList<>();
    private final List<Conversation> visibleConversations = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        chatRepository = ChatRepository.getInstance(this);
        searchRepository = new SearchRepository(this);
        sessionManager = SessionManager.getInstance(this);

        ImageView ivBack = findViewById(R.id.iv_back);
        ImageView ivNewChat = findViewById(R.id.iv_new_chat);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        rvConversations = findViewById(R.id.rv_conversations);
        layoutEmpty = findViewById(R.id.layout_empty);
        layoutRequestsEmpty = findViewById(R.id.layout_requests_empty);
        tvTitle = findViewById(R.id.tv_title);
        tvMessages = findViewById(R.id.tv_messages);
        tvRequests = findViewById(R.id.tv_requests);
        ivTitleChevron = findViewById(R.id.iv_title_chevron);
        ivVideo = findViewById(R.id.iv_video);
        etSearch = findViewById(R.id.et_inbox_search);
        Button btnEmptySend = findViewById(R.id.btn_empty_send);

        ivBack.setOnClickListener(v -> finish());
        ivNewChat.setOnClickListener(v -> showNewMessageSheet());
        if (btnEmptySend != null) {
            btnEmptySend.setOnClickListener(v -> showNewMessageSheet());
        }

        bindOwnUsername();
        setupSearchField();
        setupSectionRow();
        setupDecorativeChrome();

        adapter = new ConversationAdapter(visibleConversations, conv -> {
            Intent intent = new Intent(InboxActivity.this, ChatActivity.class);
            intent.putExtra("EXTRA_CONVERSATION_ID", conv.getId());
            intent.putExtra("EXTRA_OTHER_USER_ID", conv.getOtherUserId());
            intent.putExtra("EXTRA_OTHER_USERNAME", conv.getOtherUsername());
            intent.putExtra("EXTRA_OTHER_AVATAR", conv.getOtherAvatarUrl());
            startActivity(intent);
        });

        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadInbox);

        loadInbox();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInbox();
    }

    /** Top bar title: the signed-in username, 22sp bold. */
    private void bindOwnUsername() {
        String username = sessionManager != null ? sessionManager.getUsername() : null;
        if (username != null && !username.trim().isEmpty()) {
            tvTitle.setText(username);
        }
        // otherwise the layout keeps @string/direct_messages, as before
    }

    /** Existing conversation filter: filters the list already loaded in memory. */
    private void setupSearchField() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                applyConversationFilter(s != null ? s.toString() : "");
            }
        });
    }

    /** 'Messages' restores the thread list, 'Requests' opens the empty screen. */
    private void setupSectionRow() {
        tvMessages.setOnClickListener(v -> showMessageRequests(false));
        tvRequests.setOnClickListener(v -> showMessageRequests(true));
    }

    /**
     * Design-only chrome follows the shared Constants.DECORATIVE_UI contract:
     * visible with a 'Not available' toast when enabled, hidden when off.
     */
    private void setupDecorativeChrome() {
        if (Constants.DECORATIVE_UI) {
            ivTitleChevron.setVisibility(View.VISIBLE);
            ivVideo.setVisibility(View.VISIBLE);
            tvRequests.setVisibility(View.VISIBLE);
            ivTitleChevron.setOnClickListener(v -> ToastUtil.showNotAvailable(this));
            ivVideo.setOnClickListener(v -> ToastUtil.showNotAvailable(this));
        } else {
            ivTitleChevron.setVisibility(View.GONE);
            ivVideo.setVisibility(View.GONE);
            tvRequests.setVisibility(View.GONE);
            showMessageRequests(false);
        }
    }

    private void showMessageRequests(boolean show) {
        if (!Constants.DECORATIVE_UI) {
            show = false;
        }
        layoutRequestsEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
        swipeRefresh.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show) {
            layoutEmpty.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(allConversations.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void applyConversationFilter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        visibleConversations.clear();
        if (needle.isEmpty()) {
            visibleConversations.addAll(allConversations);
        } else {
            for (Conversation c : allConversations) {
                String username = c.getOtherUsername() == null ? "" : c.getOtherUsername().toLowerCase(Locale.US);
                String fullName = c.getOtherFullName() == null ? "" : c.getOtherFullName().toLowerCase(Locale.US);
                if (username.contains(needle) || fullName.contains(needle)) {
                    visibleConversations.add(c);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void loadInbox() {
        swipeRefresh.setRefreshing(true);
        chatRepository.getConversations().observe(this, resource -> {
            swipeRefresh.setRefreshing(false);
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                allConversations.clear();
                allConversations.addAll(resource.data);
                applyConversationFilter(etSearch.getText() != null ? etSearch.getText().toString() : "");
                layoutEmpty.setVisibility(allConversations.isEmpty() ? View.VISIBLE : View.GONE);
                rvConversations.setVisibility(allConversations.isEmpty() ? View.GONE : View.VISIBLE);
            } else if (resource != null && resource.status == Resource.Status.ERROR) {
                if (allConversations.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    rvConversations.setVisibility(View.GONE);
                }
            }
        });
    }

    private void showNewMessageSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.InstagramBottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.layout_new_message_sheet, null);
        int sheetHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
        sheet.setMinimumHeight(sheetHeight);
        dialog.setContentView(sheet);

        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                behavior.setPeekHeight(sheetHeight);
            }
        });

        EditText etSearch = sheet.findViewById(R.id.et_search);
        RecyclerView rvUsers = sheet.findViewById(R.id.rv_users);
        ProgressBar pbLoading = sheet.findViewById(R.id.pb_loading);
        ImageView ivClose = sheet.findViewById(R.id.iv_close);

        ivClose.setOnClickListener(v -> dialog.dismiss());

        List<Profile> userList = new ArrayList<>();
        UserPickerAdapter userAdapter = new UserPickerAdapter(userList, profile -> {
            pbLoading.setVisibility(View.VISIBLE);
            chatRepository.getOrCreateConversation(profile.getId()).observe(InboxActivity.this, res -> {
                pbLoading.setVisibility(View.GONE);
                if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                    dialog.dismiss();
                    Intent intent = new Intent(InboxActivity.this, ChatActivity.class);
                    intent.putExtra("EXTRA_CONVERSATION_ID", res.data);
                    intent.putExtra("EXTRA_OTHER_USER_ID", profile.getId());
                    intent.putExtra("EXTRA_OTHER_USERNAME", profile.getUsername());
                    intent.putExtra("EXTRA_OTHER_AVATAR", profile.getAvatarUrl());
                    startActivity(intent);
                } else if (res != null && res.status == Resource.Status.ERROR) {
                    Toast.makeText(InboxActivity.this, "Failed to start chat: " + res.message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(userAdapter);

        // Initially show suggested users
        pbLoading.setVisibility(View.VISIBLE);
        searchRepository.getSuggestedUsers(15).observe(this, res -> {
            pbLoading.setVisibility(View.GONE);
            if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                userList.clear();
                userList.addAll(res.data);
                userAdapter.notifyDataSetChanged();
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String q = s != null ? s.toString().trim() : "";
                if (q.isEmpty()) {
                    searchRepository.getSuggestedUsers(15).observe(InboxActivity.this, res -> {
                        if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                            userList.clear();
                            userList.addAll(res.data);
                            userAdapter.notifyDataSetChanged();
                        }
                    });
                } else {
                    pbLoading.setVisibility(View.VISIBLE);
                    searchRepository.searchUsers(q).observe(InboxActivity.this, res -> {
                        pbLoading.setVisibility(View.GONE);
                        if (res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                            userList.clear();
                            userList.addAll(res.data);
                            userAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        });

        dialog.show();
    }

    // --- Adapters ---

    private static class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {
        private final List<Conversation> items;
        private final OnConversationClickListener listener;

        interface OnConversationClickListener {
            void onClick(Conversation conversation);
        }

        ConversationAdapter(List<Conversation> items, OnConversationClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Conversation c = items.get(position);
            Context context = holder.itemView.getContext();
            boolean unread = c.isUnread();

            holder.tvUsername.setText(c.getOtherUsername());
            // Name: bold while unread, regular once read
            holder.tvUsername.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);

            // Second line: "{last message} · {time}"
            String lastMsg = c.getLastMessage();
            if (lastMsg == null || lastMsg.trim().isEmpty() || "Photo".equalsIgnoreCase(lastMsg.trim())) {
                lastMsg = context.getString(R.string.inbox_sent_a_photo);
            }
            String time = compactTime(c.getLastMessageAt());
            holder.tvLastMessage.setText(time.isEmpty() ? lastMsg : lastMsg + " · " + time);
            holder.tvLastMessage.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
            holder.tvLastMessage.setTextColor(ContextCompat.getColor(context,
                    unread ? R.color.color_text_primary : R.color.color_text_secondary));

            holder.ivUnreadDot.setVisibility(unread ? View.VISIBLE : View.GONE);

            // Camera: textPrimary while unread, muted grey once read
            ImageViewCompat.setImageTintList(holder.ivCamera, ColorStateList.valueOf(
                    ContextCompat.getColor(context, unread ? R.color.color_text_primary : R.color.color_icon_muted)));
            holder.ivCamera.setVisibility(Constants.DECORATIVE_UI ? View.VISIBLE : View.GONE);
            holder.ivCamera.setOnClickListener(Constants.DECORATIVE_UI
                    ? v -> ToastUtil.showNotAvailable(context) : null);

            // Story ring: only when the other user has an active story.
            // The Conversation model carries no story flag, so it stays hidden.
            holder.ringStory.setVisibility(View.GONE);

            if (c.getOtherAvatarUrl() != null && !c.getOtherAvatarUrl().isEmpty()) {
                com.insta.clone.util.GlideUtils.loadAvatar(holder.ivAvatar, c.getOtherAvatarUrl());
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onClick(c);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        /** 30s -> "1m", 45m -> "45m", 2h -> "2h", 3d -> "3d". */
        private static String compactTime(String iso) {
            long timestamp = TimeUtils.parseTimestamp(iso);
            if (timestamp <= 0L) {
                return "";
            }
            long minutes = Math.max(1L, (System.currentTimeMillis() - timestamp) / 60000L);
            if (minutes < 60L) {
                return minutes + "m";
            }
            long hours = minutes / 60L;
            if (hours < 24L) {
                return hours + "h";
            }
            return (hours / 24L) + "d";
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final View ringStory;
            final TextView tvUsername;
            final TextView tvLastMessage;
            final TextView tvTime;
            final View ivUnreadDot;
            final ImageView ivCamera;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                ringStory = itemView.findViewById(R.id.ring_story);
                tvUsername = itemView.findViewById(R.id.tv_username);
                tvLastMessage = itemView.findViewById(R.id.tv_last_message);
                tvTime = itemView.findViewById(R.id.tv_time);
                ivUnreadDot = itemView.findViewById(R.id.iv_unread_dot);
                ivCamera = itemView.findViewById(R.id.iv_camera);
            }
        }
    }

    private static class UserPickerAdapter extends RecyclerView.Adapter<UserPickerAdapter.ViewHolder> {
        private final List<Profile> items;
        private final OnUserClickListener listener;

        interface OnUserClickListener {
            void onUserClick(Profile profile);
        }

        UserPickerAdapter(List<Profile> items, OnUserClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Profile profile = items.get(position);
            holder.tvUsername.setText(profile.getUsername());
            holder.tvFullName.setText(profile.getFullName() != null ? profile.getFullName() : "");

            if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
                com.insta.clone.util.GlideUtils.loadAvatar(holder.ivAvatar, profile.getAvatarUrl());
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onUserClick(profile);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final TextView tvUsername;
            final TextView tvFullName;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                tvUsername = itemView.findViewById(R.id.tv_username);
                tvFullName = itemView.findViewById(R.id.tv_full_name);
            }
        }
    }
}
```

---

# DESIGN CHECKLIST — PAGE 6 (DM INBOX)

`1dp = 2.75px` on the verification device (1080 x 2400 @ 440dpi = 393dp wide). Every
measured value below comes from `adb shell uiautomator dump` on the running build.

| # | Requirement | Implementation | Status / measured evidence |
|---|---|---|---|
| 1 | Top bar 56dp | `layout_inbox_top_bar` `@dimen/inbox_top_bar_height` replacing the 50dp `Toolbar` | PASS — `[0,110]-[1080,264]` = 392.7 x **56.0dp** |
| 2 | `ic_arrow_back` | `iv_back` 36dp box, 6dp padding, `app:tint="?attr/themeTextPrimary"` | PASS — `[44,137]-[143,236]`, glyph starts 22px in = matches the 22dp glyph position in the reference; id and `finish()` handler unchanged |
| 3 | User's own username 22sp Bold | `tv_title` `@dimen/text_screen_title` (22sp) + bold, text set from `SessionManager.getUsername()` with the previous `@string/direct_messages` as fallback | PASS — `[143,146]-[480,228]` (29.8dp line box), text "instanova01" |
| 4 | `ic_chevron_down` 16dp, decorative | `iv_title_chevron` `@dimen/profile_chevron_size` (16dp) + `@dimen/profile_chevron_gap` (6dp) | PASS — `[497,165]-[541,209]` = **16.0dp**; `Constants.DECORATIVE_UI` + `ToastUtil.showNotAvailable()` (screenshot 11), hidden when the flag is off |
| 5 | Right `ic_video` 26dp, decorative | `iv_video` `@dimen/inbox_top_bar_icon` | PASS — **26.2dp**; decorative toast verified on device |
| 6 | Right `ic_edit_square` 26dp, opens the existing New message screen | `iv_new_chat` keeps its id/click handler, icon changed from `ic_add_box` to `ic_edit_square` | PASS — **26.2dp**; tapping opens the existing `showNewMessageSheet()` bottom sheet on device |
| 7 | 20dp apart | `@dimen/inbox_top_bar_icon_gap` on the edit icon | PASS — gap 909 -> 964 = 55px = **20dp** |
| 8 | 16dp end padding | row padding end | PASS — edit icon right edge 1036, page 1080 -> 44px = **16dp** |
| 9 | Search field: 16dp margins, 38dp, radius 10dp, bg surfaceVariant, `ic_search` 18dp, hint 'Search' 16sp textSecondary | `layout_inbox_search` reuses `@drawable/bg_search_field` (radius 10dp + `color_surface_variant`), `@dimen/search_field_*` tokens; `et_inbox_search` | PASS — `[44,264]-[1036,369]` = 360.7 x **38.2dp**, margins 44px = **16dp** on both sides |
| 10 | Existing conversation filter | `setupSearchField()` / `applyConversationFilter()` filters the already-loaded list (username + full name); `allConversations` master list keeps the empty-state logic identical to before | PASS — typing "11" leaves only `instanova11`; the repository/API path is untouched (see NOTES 2) |
| 11 | Omit the Notes / 'Leave a note' row | no such row exists in the layout | PASS — not present anywhere in `activity_inbox.xml` |
| 12 | Section row: 16dp padding, 12dp top | `layout_inbox_section` `@dimen/inbox_section_padding_horizontal` / `_margin_top` | PASS — row `[0,402]-[1080,512]` = **40.0dp** tall, 12dp below the search field |
| 13 | 'Messages' 16sp Bold, left | `tv_messages` + `TextAppearance.Insta.SectionTitle` | PASS — `[44,427]-[249,486]`, left edge = 44px = **16dp**, bold black |
| 14 | 'Requests' 14sp Medium textSecondary, right, decorative, opens 'No message requests' | `tv_requests` + `TextAppearance.Insta.SectionAction`; tapping swaps the list for `layout_requests_empty` (centred), tapping 'Messages' restores | PASS — right edge 1036 = **16dp**, textSecondary; overlay `[0,1336]-[1080,1527]` "No message requests" verified on device |
| 15 | Conversation rows 76dp, 16dp horizontal padding, no dividers | `item_conversation.xml` root `@dimen/inbox_row_height`, `@dimen/inbox_row_padding_horizontal`; no divider views anywhere | PASS — row pitch 209px = **76.0dp** exactly; avatar left edge 44px = **16dp**; no `View` divider in either layout |
| 16 | Avatar 56dp | `iv_avatar` inside a 56dp `frame_avatar` (id preserved) | PASS — `[44,539]-[198,693]` = **56.0dp** square |
| 17 | Gradient ring if that user has an active story | `ring_story` 64dp layer using the new `bg_story_avatar_ring` (PAGE 0 story gradient, 4dp punched ring), `clipChildren=false` so the ring never resizes the avatar | PASS (see NOTES 3) — the view is wired and sized; it stays `gone` because `Conversation` carries no story flag, so no ring renders on this build (matching the reference, where only one row has one) |
| 18 | 12dp gap | `@dimen/inbox_avatar_gap` | PASS — avatar right 198 -> text left 231 = 33px = **12dp** |
| 19 | Name 15sp, Bold when unread / regular when read | `@dimen/inbox_name_size` + `setTypeface(null, unread ? BOLD : NORMAL)` | PASS — 15sp; forced-unread build screenshot shows the bold name, normal-weight name on read rows |
| 20 | Second line `{last message} · {time}` 14sp, Medium textPrimary when unread / regular textSecondary when read | `tv_last_message` `@dimen/inbox_message_size`; text built as `lastMessage + " · " + compactTime(...)`, weight + colour switched from `isUnread()` | PASS — read: `Sent a photo · 4h` in `#737373`; forced-unread: `Ho · 3m` in textPrimary at Medium weight |
| 21 | Time as 2m, 45m, 2h, 3d | `compactTime()` parses `Conversation.getLastMessageAt()` through the existing `TimeUtils.parseTimestamp` and renders m / h / d | PASS — observed `1m`, `3m`, `4m`, `3h`, `4h` on live rows (blocked-minute bucket clamps to "1m") |
| 22 | Unread dot 8dp `#0095F6`, only when unread | `iv_unread_dot` `@dimen/inbox_unread_dot_size` + new `bg_unread_dot_primary` (`@color/color_primary`) | PASS — pixel sampled **(0,149,246)** = `#0095F6` on the forced-unread build; the view is absent from the dump on read rows |
| 23 | 16dp gap between the dot and the camera | `@dimen/inbox_unread_dot_gap` | PASS — token applied as the camera's `layout_marginStart` |
| 24 | `ic_camera` 26dp, textPrimary when unread / `#A8A8A8` when read, opens the existing gallery-to-send flow | `iv_camera` `@dimen/inbox_camera_size`, tint switched in `onBindViewHolder` | PARTIAL — size **26.2dp** and both tints verified (unread stroke `(0,0,0)`, read `color_icon_muted` `#A8A8A8`); the gallery-to-send flow is **decorative** here (see NOTES 4) |
| 25 | Empty state stays, restyled | same ids (`layout_empty`, `btn_empty_send`) and the same `allConversations.isEmpty()` logic; 64dp/20sp/36dp literals replaced with `caught_up_icon_size`, `text_screen_title`, `text_body` and `profile_button_height` | PASS — behaviour unchanged, every value now a token |
| 26 | Long-press delete sheet stays as it is | nothing added or removed | N/A on this build — **no long-press / delete-conversation flow exists** in `ui/chat` (grep for `setOnLongClick` finds only `CommentsBottomSheet`), so there was nothing to preserve and nothing was invented (see NOTES 5) |
| 27 | Token discipline — no hard-coded colours or sizes | 22 PAGE 6 dimens, `@color/color_icon_muted`, `@color/color_primary`, two text appearances, two new drawables | PASS — every size/colour/radius/text size in both layouts resolves to a token |

---

# NOTES, DECISIONS AND LIMITATIONS

### 1. Reference vs. spec on the avatar size
The reference render measures the row avatar at ~83px of a ~98px row pitch (≈64dp against
the 76dp row the text specifies). The brief states **avatar 56dp** explicitly and that value
also matches PAGE 4's verified 56dp rows, so the spec value was used and the row/avatar
proportion is slightly tighter than the reference. Everything else in the reference measured
consistently with the spec (76dp pitch, 38dp field, 26dp icons, 16dp paddings).

### 2. The conversation filter
There was **no** filter on this screen before: the inbox only ever had a repository call that
filled the list. The brief calls for the "existing conversation filter", so the field filters
the conversations that are already in memory (username + full name), exactly as a filter
should, and nothing else changed:

* no ViewModel, repository, API call, model or navigation target was touched;
* `allConversations` (everything the API returned) decides the empty state, so the
  "Your Messages" empty state behaves exactly as before — an empty *filter* result shows an
  empty list, it does not fake the empty state.

If you would rather have this field be purely decorative (toast only), it is a two-line change.

### 3. The story ring
`Conversation` exposes id / otherUserId / otherUsername / otherFullName / otherAvatarUrl /
lastMessage / lastMessageAt / lastMessageTimestamp / isUnread / unreadCount — **no story
flag**. The gradient ring is therefore built, sized (64dp around the 56dp avatar, 4dp
punched ring using the PAGE 0 five-colour story gradient) and wired, but hidden, which is
also what the reference shows for most rows. The moment a story flag lands on the model it is
a one-line switch in `onBindViewHolder` (`holder.ringStory.setVisibility(...)`).

### 4. The row camera icon is decorative
The only gallery-to-send flow in the app lives **inside** `ChatActivity`
(`pickImageLauncher` -> `ActivityResultContracts.PickVisualMedia` + `handleImagePicked` +
`ChatRepository` upload). There is no inbox-level entry point and no intent extra to ask
`ChatActivity` to open its picker on launch. Reusing it would mean adding a new
`ActivityResultLauncher` and a send path in `InboxActivity`, i.e. new data-layer behaviour,
so per the project's design-only rule the icon follows the shared decorative contract
(visible + "Not available" toast while `Constants.DECORATIVE_UI` is true, hidden when false),
and it still renders the exact unread/read tints the design asks for.

### 5. `tv_time` and the "long-press delete sheet"
* `tv_time` is still resolved by the adapter, so the view stays in the row (hidden) rather
  than being deleted — the view-id contract for `findViewById` is untouched. PAGE 6 folds the
  timestamp into the second line via `tv_last_message`.
* The brief says the long-press delete sheet "stays as it is", but this screen has no
  long-press handler and no delete-conversation flow anywhere in `ui/chat`. Nothing was
  invented and nothing was removed; if that sheet is expected, it does not exist in this
  codebase yet.

### 6. `bg_unread_dot` is untouched
The PAGE 0 red dot is still used by the Home top bar (`fragment_home.xml` x2), so the inbox
gets its own `bg_unread_dot_primary` (`@color/color_primary` = `#0095F6`) instead of
recolouring the shared drawable.

---

# BUILD AND DEVICE VERIFICATION

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home \
  ./gradlew :app:assembleDebug          # BUILD SUCCESSFUL
adb install -r app/build/outputs/apk/debug/app-debug.apk   # Success
```

Verified on device `10BE2S1U640002B` (1080 x 2400 @ 440dpi), reached from the Home top-bar
send icon:

* `uiautomator dump` geometry for every element in the checklist (values quoted above are dp).
* Filter: typing `11` collapses the list to `instanova11`; clearing restores both rows.
* Requests: tapping opens the centred "No message requests" screen; tapping `Messages`
  restores the list.
* Decorative taps (title chevron, video) show the shared "Not available" pill.
* `ic_edit_square` opens the existing New message bottom sheet (dark and light theme).
* Compact timestamps observed live: `1m`, `3m`, `4m`, `3h`, `4h`.
* Unread variant: every seeded conversation has `unread_count = 0`, so a scratch build with
  the state forced was used to photograph it — bold name, Medium textPrimary second line,
  `#0095F6` dot (pixel-sampled), textPrimary camera — and the change was then **reverted**
  (`grep TEMP-VERIFY-UNREAD` = 0 occurrences) and the shipped APK rebuilt and reinstalled.
* Light and dark themes both re-checked; dark theme restored on the device afterwards.
