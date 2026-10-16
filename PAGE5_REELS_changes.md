# PAGE 5 — REELS TAB — changed files

**Design only.** No behaviour changes: `ReelRepository` / `PostRepository`, the API
calls, the models, the navigation targets and every view id consumed by code stay as
they are. Only XML, drawables, styles and presentation Java changed. Every colour and
size comes from a PAGE 0 token (or from a new PAGE 5 token added in the blocks below) —
nothing is hard-coded in a layout.

**Reference:** Image 5 (left). Per the brief the phone frame, the status bar, the yellow
arrow, the friends pill at the top right and the 'With friends' screen are all ignored.

Read order: tokens → drawables → layouts → presentation Java, then the DESIGN CHECKLIST
and the NOTES + device verification at the end.

---

## 1. Token — global dimens (PAGE 5 block added)

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
</resources>
```

## 2. Token — global colors (PAGE 5 block added)

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
</resources>
```

## 3. Token — strings (PAGE 5 block added)

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
</resources>
```

## 4. Token — themes / styles (PAGE 5 styles added)

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

## 5. NEW drawable — 140dp top scrim

**Path:** `app/src/main/res/drawable/bg_reel_scrim_top.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 5 — Reels top scrim (140dp).
    #99000000 at the top fading to fully transparent at the bottom, so the
    'Reels' title and the camera icon stay legible over any reel video.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="270"
        android:startColor="@color/color_scrim"
        android:endColor="@color/transparent"
        android:type="linear" />
</shape>
```

## 6. NEW drawable — 280dp bottom scrim

**Path:** `app/src/main/res/drawable/bg_reel_scrim_bottom.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 5 — Reels bottom scrim (280dp).
    Fully transparent at the top fading to #99000000 at the bottom, so the
    author row, the caption and the action column stay legible over any reel.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="270"
        android:startColor="@color/transparent"
        android:endColor="@color/color_scrim"
        android:type="linear" />
</shape>
```

## 7. NEW drawable — outlined Follow pill

**Path:** `app/src/main/res/drawable/bg_reel_follow_pill.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 5 — Reels outlined 'Follow' pill.
    28dp tall, 8dp radius, 1dp 70%-white outline, transparent fill so the
    video shows through.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/reel_follow_pill_radius" />
    <solid android:color="@color/transparent" />
    <stroke
        android:width="@dimen/reel_follow_pill_border"
        android:color="@color/color_reel_pill_border" />
</shape>
```

## 8. NEW drawable — 32dp audio thumbnail frame

**Path:** `app/src/main/res/drawable/bg_reel_audio_thumb.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 5 — 32dp audio thumbnail frame: 6dp rounded square with a 2dp white
    border. The 2dp body padding of the view leaves room for the border and
    the inner avatar is clipped to the 4dp radius defined below.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/reel_audio_thumb_radius" />
    <solid android:color="@color/transparent" />
    <stroke
        android:width="@dimen/reel_audio_thumb_border"
        android:color="@color/white" />
</shape>
```

## 9. NEW drawable — audio thumbnail inner clip surface

**Path:** `app/src/main/res/drawable/bg_reel_audio_inner.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 5 — inner surface of the audio thumbnail. Provides the 4dp rounded
    outline the author avatar is clipped to (clipToOutline) and a dark
    backing colour while the avatar loads.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/reel_audio_inner_radius" />
    <solid android:color="@color/black" />
</shape>
```

## 10. NEW drawable — music badge

**Path:** `app/src/main/res/drawable/bg_reel_audio_badge.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 5 — tiny music badge pinned to the bottom-right corner of the audio
    thumbnail: white rounded square with the black note stamped on top.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/reel_audio_badge_radius" />
    <solid android:color="@color/white" />
</shape>
```

## 11. Layout — Reels page chrome + empty state

**Path:** `app/src/main/res/layout/fragment_reels.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 5 — REELS TAB (full screen, black).

    Layout only: the ViewPager2 id (vp_reels), the camera id (btn_camera) and
    the empty-state ids are unchanged so ReelsFragment keeps working. The
    status-bar inset is applied to layout_reel_top_bar at runtime so the
    chrome clears the system bar while the video stays edge to edge.
-->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/black">

    <!-- Full-bleed vertical reel pager (each page draws its own scrims) -->
    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/vp_reels"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- Top chrome: 'Reels' + chevron (start) / camera (end) -->
    <LinearLayout
        android:id="@+id/layout_reel_top_bar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="top"
        android:gravity="center_vertical"
        android:minHeight="@dimen/reel_top_bar_min_height"
        android:orientation="horizontal"
        android:paddingStart="@dimen/reel_top_bar_padding_horizontal"
        android:paddingTop="@dimen/reel_top_bar_padding_vertical"
        android:paddingEnd="@dimen/reel_top_bar_padding_horizontal"
        android:paddingBottom="@dimen/reel_top_bar_padding_vertical">

        <LinearLayout
            android:id="@+id/btn_reel_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/nav_reels"
                android:textColor="@color/white"
                android:textSize="@dimen/text_screen_title"
                android:textStyle="bold" />

            <ImageView
                android:id="@+id/iv_reel_title_chevron"
                android:layout_width="@dimen/reel_chevron_size"
                android:layout_height="@dimen/reel_chevron_size"
                android:layout_marginStart="@dimen/reel_chevron_gap"
                android:contentDescription="@string/not_available"
                android:src="@drawable/ic_chevron_down"
                app:tint="@color/white" />
        </LinearLayout>

        <View
            android:layout_width="0dp"
            android:layout_height="1dp"
            android:layout_weight="1" />

        <ImageView
            android:id="@+id/btn_camera"
            android:layout_width="@dimen/reel_camera_size"
            android:layout_height="@dimen/reel_camera_size"
            android:contentDescription="@string/reel_camera"
            android:src="@drawable/ic_camera"
            app:tint="@color/white" />
    </LinearLayout>

    <!-- Empty State -->
    <LinearLayout
        android:id="@+id/layout_reels_empty"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:gravity="center"
        android:orientation="vertical"
        android:visibility="gone">

        <ImageView
            android:layout_width="@dimen/caught_up_icon_size"
            android:layout_height="@dimen/caught_up_icon_size"
            android:contentDescription="@string/not_available"
            android:src="@drawable/ic_movie"
            app:tint="@color/white" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/caught_up_gap"
            android:fontFamily="sans-serif-medium"
            android:text="@string/reel_empty_title"
            android:textColor="@color/white"
            android:textSize="@dimen/text_title" />

        <Button
            android:id="@+id/btn_empty_create_reel"
            style="@style/Widget.Insta.Button.Primary"
            android:layout_width="wrap_content"
            android:layout_height="@dimen/profile_button_height"
            android:layout_marginTop="@dimen/caught_up_gap"
            android:paddingStart="@dimen/profile_button_gap"
            android:paddingEnd="@dimen/profile_button_gap"
            android:text="@string/reel_empty_action" />
    </LinearLayout>

</FrameLayout>
```

## 12. Layout — one full-screen reel page

**Path:** `app/src/main/res/layout/item_reel_player.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 5 — single full-screen reel page.

    Every id consumed by ReelsFragment (player_view, iv_reel_thumbnail,
    iv_reel_like, tv_reel_likes_count, iv_reel_comment, tv_reel_comments_count,
    iv_reel_mute, iv_reel_avatar, tv_reel_username, badge_reel_verified,
    tv_reel_caption, iv_play_indicator) is preserved. The scrims live inside
    the page so the video is tinted while this page's own chrome stays above
    them; the fragment-level chrome (title / camera) stays above everything.
-->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/black">

    <!-- Video, edge to edge -->
    <androidx.media3.ui.PlayerView
        android:id="@+id/player_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:resize_mode="zoom"
        app:use_controller="false" />

    <ImageView
        android:id="@+id/iv_reel_thumbnail"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="centerCrop"
        android:visibility="gone" />

    <!-- 140dp top scrim: #99000000 -> transparent -->
    <View
        android:id="@+id/reel_scrim_top"
        android:layout_width="match_parent"
        android:layout_height="@dimen/reel_scrim_top_height"
        android:layout_gravity="top"
        android:background="@drawable/bg_reel_scrim_top" />

    <!-- 280dp bottom scrim: transparent -> #99000000 -->
    <View
        android:id="@+id/reel_scrim_bottom"
        android:layout_width="match_parent"
        android:layout_height="@dimen/reel_scrim_bottom_height"
        android:layout_gravity="bottom"
        android:background="@drawable/bg_reel_scrim_bottom" />

    <!-- ============================================================= -->
    <!-- Right action column: 16dp from the right edge, 24dp above the  -->
    <!-- bottom navigation, groups 24dp apart, white, centred.          -->
    <!-- ============================================================= -->
    <LinearLayout
        android:id="@+id/layout_reel_actions"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="end|bottom"
        android:layout_marginEnd="@dimen/reel_actions_margin_end"
        android:layout_marginBottom="@dimen/reel_actions_margin_bottom"
        android:gravity="center_horizontal"
        android:orientation="vertical">

        <!-- Like -->
        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="@dimen/reel_action_group_gap"
            android:gravity="center_horizontal"
            android:orientation="vertical">

            <ImageView
                android:id="@+id/iv_reel_like"
                android:layout_width="@dimen/reel_action_icon_size"
                android:layout_height="@dimen/reel_action_icon_size"
                android:contentDescription="@string/not_available"
                android:src="@drawable/ic_heart" />

            <TextView
                android:id="@+id/tv_reel_likes_count"
                style="@style/TextAppearance.Insta.ReelCount"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/reel_action_count_gap" />
        </LinearLayout>

        <!-- Comment -->
        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="@dimen/reel_action_group_gap"
            android:gravity="center_horizontal"
            android:orientation="vertical">

            <ImageView
                android:id="@+id/iv_reel_comment"
                android:layout_width="@dimen/reel_action_icon_size"
                android:layout_height="@dimen/reel_action_icon_size"
                android:contentDescription="@string/not_available"
                android:src="@drawable/ic_comment"
                app:tint="@color/white" />

            <TextView
                android:id="@+id/tv_reel_comments_count"
                style="@style/TextAppearance.Insta.ReelCount"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/reel_action_count_gap" />
        </LinearLayout>

        <!-- Mute toggle: still wired in ReelsFragment, hidden by the PAGE 5 design -->
        <ImageView
            android:id="@+id/iv_reel_mute"
            android:layout_width="@dimen/reel_action_icon_more_size"
            android:layout_height="@dimen/reel_action_icon_more_size"
            android:contentDescription="@string/not_available"
            android:src="@drawable/ic_volume_up"
            android:visibility="gone"
            app:tint="@color/white" />

        <!-- Send / share (design-only: no share flow exists for reels) -->
        <LinearLayout
            android:id="@+id/layout_reel_send"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="@dimen/reel_action_group_gap"
            android:gravity="center_horizontal"
            android:orientation="vertical">

            <ImageView
                android:id="@+id/iv_reel_send"
                android:layout_width="@dimen/reel_action_icon_size"
                android:layout_height="@dimen/reel_action_icon_size"
                android:contentDescription="@string/not_available"
                android:src="@drawable/ic_send"
                app:tint="@color/white" />

            <TextView
                android:id="@+id/tv_reel_shares_count"
                style="@style/TextAppearance.Insta.ReelCount"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/reel_action_count_gap"
                android:visibility="gone" />
        </LinearLayout>

        <!-- More options (design-only: no reel options flow exists) -->
        <ImageView
            android:id="@+id/iv_reel_more"
            android:layout_width="@dimen/reel_action_icon_more_size"
            android:layout_height="@dimen/reel_action_icon_more_size"
            android:layout_marginBottom="@dimen/reel_action_group_gap"
            android:contentDescription="@string/reel_more_options"
            android:src="@drawable/ic_more_horiz"
            app:tint="@color/white" />

        <!-- Audio thumbnail: 32dp rounded square, 2dp white border, author avatar -->
        <FrameLayout
            android:id="@+id/layout_reel_audio"
            android:layout_width="@dimen/reel_audio_thumb_size"
            android:layout_height="@dimen/reel_audio_thumb_size"
            android:background="@drawable/bg_reel_audio_thumb"
            android:padding="@dimen/reel_audio_thumb_border">

            <ImageView
                android:id="@+id/iv_reel_audio_thumb"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:background="@drawable/bg_reel_audio_inner"
                android:clipToOutline="true"
                android:contentDescription="@string/reel_audio_thumbnail"
                android:scaleType="centerCrop" />

            <ImageView
                android:id="@+id/iv_reel_audio_badge"
                android:layout_width="@dimen/reel_audio_badge_size"
                android:layout_height="@dimen/reel_audio_badge_size"
                android:layout_gravity="bottom|end"
                android:background="@drawable/bg_reel_audio_badge"
                android:contentDescription="@string/not_available"
                android:padding="@dimen/reel_audio_badge_padding"
                android:src="@drawable/ic_music"
                app:tint="@color/black" />
        </FrameLayout>
    </LinearLayout>

    <!-- ============================================================= -->
    <!-- Bottom-left author + caption block, max 75% of the page width  -->
    <!-- ============================================================= -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:layout_marginStart="@dimen/reel_meta_margin_start"
        android:layout_marginEnd="@dimen/reel_meta_margin_start"
        android:layout_marginBottom="@dimen/reel_meta_margin_bottom"
        android:orientation="horizontal">

        <!-- 3/4 of the remaining width == 75% of the full page width -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="3"
            android:orientation="vertical">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <de.hdodenhof.circleimageview.CircleImageView
                    android:id="@+id/iv_reel_avatar"
                    android:layout_width="@dimen/reel_meta_avatar_size"
                    android:layout_height="@dimen/reel_meta_avatar_size"
                    android:src="@drawable/ic_person_placeholder" />

                <TextView
                    android:id="@+id/tv_reel_username"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="@dimen/reel_meta_avatar_gap"
                    android:ellipsize="end"
                    android:maxLines="1"
                    android:textColor="@color/white"
                    android:textSize="@dimen/reel_meta_username_size"
                    android:textStyle="bold" />

                <com.insta.clone.ui.widget.VerifiedBadgeView
                    android:id="@+id/badge_reel_verified"
                    android:layout_width="@dimen/reel_meta_badge_size"
                    android:layout_height="@dimen/reel_meta_badge_size"
                    android:layout_marginStart="@dimen/reel_meta_badge_gap" />

                <TextView
                    android:id="@+id/btn_reel_follow"
                    style="@style/Widget.Insta.ReelFollowPill"
                    android:layout_width="wrap_content"
                    android:layout_marginStart="@dimen/reel_meta_pill_gap"
                    android:text="@string/profile_follow" />
            </LinearLayout>

            <TextView
                android:id="@+id/tv_reel_caption"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/reel_meta_caption_gap"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="@color/white"
                android:textSize="@dimen/reel_meta_caption_size" />
        </LinearLayout>

        <!-- Reserved 1/4 so the caption never runs under the action column -->
        <View
            android:layout_width="0dp"
            android:layout_height="1dp"
            android:layout_weight="1" />
    </LinearLayout>

    <!-- Center Play/Pause Indicator -->
    <ImageView
        android:id="@+id/iv_play_indicator"
        android:layout_width="@dimen/reel_play_indicator_size"
        android:layout_height="@dimen/reel_play_indicator_size"
        android:layout_gravity="center"
        android:contentDescription="@string/not_available"
        android:src="@drawable/ic_play"
        android:visibility="gone"
        app:tint="@color/white" />

</FrameLayout>
```

## 13. Java — ReelsFragment (presentation only)

**Path:** `app/src/main/java/com/insta/clone/ui/reels/ReelsFragment.java`

```java
package com.insta.clone.ui.reels;

import android.content.Intent;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.insta.clone.R;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Post;
import com.insta.clone.data.repository.PostRepository;
import com.insta.clone.data.repository.ReelRepository;
import com.insta.clone.databinding.FragmentReelsBinding;
import com.insta.clone.databinding.ItemReelPlayerBinding;
import com.insta.clone.ui.comment.CommentsBottomSheet;
import com.insta.clone.ui.create.CreateReelActivity;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;
import com.insta.clone.util.ToastUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Fullscreen vertical scrolling Reels feed with ExoPlayer autoplay and empty state.
 *
 * PAGE 5 presentation only: the data path (ReelRepository / PostRepository),
 * the ViewPager2 paging, autoplay and the play/pause + double-tap behaviour are
 * unchanged. Added here: the status-bar inset for the top chrome, abbreviated
 * action counts, the expandable one-line caption, the author/Follow row and the
 * decorative chrome (title chevron, send, more, follow pill).
 */
public class ReelsFragment extends Fragment {

    private static final String TAG = "ReelsFragment";

    /** Collapsed / expanded line counts for the one-line reel caption. */
    private static final int CAPTION_LINES_COLLAPSED = 1;
    private static final int CAPTION_LINES_EXPANDED = 3;

    private FragmentReelsBinding binding;
    private ReelRepository reelRepository;
    private PostRepository postRepository;
    private SessionManager sessionManager;
    private ReelsAdapter adapter;
    private final List<Post> reelsList = new ArrayList<>();
    private int currentPosition = 0;

    public ReelsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentReelsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        reelRepository = new ReelRepository(requireContext());
        postRepository = new PostRepository(requireContext());
        sessionManager = SessionManager.getInstance(requireContext());

        applyTopInset();
        setupChrome();

        binding.btnCamera.setOnClickListener(v -> openCreateReel());
        binding.btnEmptyCreateReel.setOnClickListener(v -> openCreateReel());

        adapter = new ReelsAdapter(reelsList);
        binding.vpReels.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        binding.vpReels.setAdapter(adapter);

        binding.vpReels.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                adapter.onPageSelected(position);
                currentPosition = position;
            }
        });

        loadReels();
    }

    /**
     * Pushes the top system-bar inset into the chrome container so 'Reels' and
     * the camera clear the status bar while the video stays edge to edge.
     */
    private void applyTopInset() {
        final int basePaddingTop = binding.layoutReelTopBar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutReelTopBar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), basePaddingTop + bars.top,
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    /**
     * Wires the decorative PAGE 5 chrome. Design-only elements follow the
     * shared Constants.DECORATIVE_UI contract: visible with a 'Not available'
     * toast when enabled, hidden when the flag is off.
     */
    private void setupChrome() {
        if (Constants.DECORATIVE_UI) {
            binding.btnReelTitle.setVisibility(View.VISIBLE);
            binding.btnReelTitle.setOnClickListener(v -> ToastUtil.showNotAvailable(requireContext()));
        } else {
            binding.btnReelTitle.setVisibility(View.GONE);
            binding.ivReelTitleChevron.setVisibility(View.GONE);
        }
    }

    private void openCreateReel() {
        startActivity(new Intent(requireContext(), CreateReelActivity.class));
    }

    public void loadReels() {
        reelRepository.getReels(20, 0).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                reelsList.clear();
                if (resource.data != null) {
                    reelsList.addAll(resource.data);
                }
                adapter.notifyDataSetChanged();

                if (reelsList.isEmpty()) {
                    binding.layoutReelsEmpty.setVisibility(View.VISIBLE);
                    binding.vpReels.setVisibility(View.GONE);
                } else {
                    binding.layoutReelsEmpty.setVisibility(View.GONE);
                    binding.vpReels.setVisibility(View.VISIBLE);
                    binding.vpReels.post(() -> adapter.onPageSelected(binding.vpReels.getCurrentItem()));
                }
            } else if (resource.status == Resource.Status.ERROR) {
                if (reelsList.isEmpty()) {
                    binding.layoutReelsEmpty.setVisibility(View.VISIBLE);
                    binding.vpReels.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadReels();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (adapter != null) adapter.pauseCurrentPlayer();
    }

    @Override
    public void onDestroyView() {
        if (adapter != null) adapter.releaseAllPlayers();
        super.onDestroyView();
        binding = null;
    }

    /** 999 -> "999", 1200 -> "1.2K", 12000 -> "12K", 1200000 -> "1.2M". */
    private static String abbreviate(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        if (count < 10000) {
            return String.format(Locale.US, "%.1fK", count / 1000.0);
        }
        if (count < 1000000) {
            return String.format(Locale.US, "%dK", Math.round(count / 1000.0));
        }
        if (count < 10000000) {
            return String.format(Locale.US, "%.1fM", count / 1000000.0);
        }
        return String.format(Locale.US, "%dM", Math.round(count / 1000000.0));
    }

    /**
     * Inner adapter managing ExoPlayer per reel item.
     */
    class ReelsAdapter extends RecyclerView.Adapter<ReelsAdapter.ReelViewHolder> {

        private final List<Post> items;
        private final List<ExoPlayer> activePlayers = new ArrayList<>();
        private ReelViewHolder currentHolder;

        ReelsAdapter(List<Post> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ReelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemReelPlayerBinding itemBinding = ItemReelPlayerBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false
            );
            return new ReelViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ReelViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public void onViewRecycled(@NonNull ReelViewHolder holder) {
            super.onViewRecycled(holder);
            holder.releasePlayer();
        }

        void onPageSelected(int position) {
            if (currentHolder != null) {
                currentHolder.pausePlayer();
            }
            RecyclerView rv = (RecyclerView) binding.vpReels.getChildAt(0);
            if (rv != null) {
                ReelViewHolder holder = (ReelViewHolder) rv.findViewHolderForAdapterPosition(position);
                if (holder != null) {
                    currentHolder = holder;
                    holder.startPlayer();
                }
            }
        }

        void pauseCurrentPlayer() {
            if (currentHolder != null) currentHolder.pausePlayer();
        }

        void releaseAllPlayers() {
            for (ExoPlayer p : activePlayers) {
                try {
                    p.stop();
                    p.release();
                } catch (Exception ignored) {}
            }
            activePlayers.clear();
        }

        class ReelViewHolder extends RecyclerView.ViewHolder {
            private final ItemReelPlayerBinding itemBinding;
            private ExoPlayer exoPlayer;
            private Post currentPost;
            private boolean isMuted = false;

            ReelViewHolder(ItemReelPlayerBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            void bind(Post post) {
                this.currentPost = post;

                // Decorative chrome (design-only): visible with a toast when the
                // shared flag is on, hidden when it is off.
                if (Constants.DECORATIVE_UI) {
                    itemBinding.layoutReelSend.setVisibility(View.VISIBLE);
                    itemBinding.ivReelSend.setOnClickListener(v -> ToastUtil.showNotAvailable(itemView.getContext()));
                    itemBinding.ivReelMore.setVisibility(View.VISIBLE);
                    itemBinding.ivReelMore.setOnClickListener(v -> ToastUtil.showNotAvailable(itemView.getContext()));
                } else {
                    itemBinding.layoutReelSend.setVisibility(View.GONE);
                    itemBinding.ivReelMore.setVisibility(View.GONE);
                }

                // Author row
                itemBinding.tvReelUsername.setText(post.getUsername());
                itemBinding.badgeReelVerified.setVerified(post.isVerified());
                if (post.getAvatarUrl() != null && !post.getAvatarUrl().isEmpty()) {
                    com.insta.clone.util.GlideUtils.loadAvatar(itemBinding.ivReelAvatar, post.getAvatarUrl());
                }

                // Caption: one line with ellipsis, tap to expand to three lines
                bindCaption(itemBinding.tvReelCaption, post.getCaption());

                // Action column counts (abbreviated, hidden at 0)
                bindCount(itemBinding.tvReelLikesCount, post.getLikesCount());
                bindCount(itemBinding.tvReelCommentsCount, post.getCommentsCount());
                // The model exposes no share count, so that counter stays hidden.
                itemBinding.tvReelSharesCount.setVisibility(View.GONE);

                // Audio thumbnail: the reel author's avatar
                if (post.getAvatarUrl() != null && !post.getAvatarUrl().isEmpty()) {
                    Glide.with(itemView.getContext())
                            .load(post.getAvatarUrl())
                            .centerCrop()
                            .into(itemBinding.ivReelAudioThumb);
                }

                // 'Follow' pill is hidden for the signed-in user's own reels
                // (the reel model carries no follow state, so it shows otherwise)
                boolean isMine = sessionManager != null
                        && Objects.equals(sessionManager.getUserId(), post.getUserId());
                if (isMine || !Constants.DECORATIVE_UI) {
                    itemBinding.btnReelFollow.setVisibility(View.GONE);
                } else {
                    itemBinding.btnReelFollow.setVisibility(View.VISIBLE);
                    itemBinding.btnReelFollow.setOnClickListener(v -> ToastUtil.showNotAvailable(itemView.getContext()));
                }

                updateLikeIcon(post.isLikedByMe());

                itemBinding.ivReelLike.setOnClickListener(v -> {
                    boolean newState = !post.isLikedByMe();
                    post.setLikedByMe(newState);
                    post.setLikesCount(Math.max(0, post.getLikesCount() + (newState ? 1 : -1)));
                    bindCount(itemBinding.tvReelLikesCount, post.getLikesCount());
                    updateLikeIcon(newState);
                    postRepository.toggleLike(post.getId(), newState);
                });

                itemBinding.ivReelComment.setOnClickListener(v -> {
                    CommentsBottomSheet sheet = CommentsBottomSheet.newInstance(post.getId(), post.getCommentsCount());
                    sheet.show(getChildFragmentManager(), "CommentsBottomSheet");
                });

                itemBinding.ivReelMute.setOnClickListener(v -> {
                    isMuted = !isMuted;
                    if (exoPlayer != null) {
                        exoPlayer.setVolume(isMuted ? 0f : 1f);
                    }
                    itemBinding.ivReelMute.setImageResource(isMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
                });

                itemBinding.playerView.setOnClickListener(v -> {
                    if (exoPlayer != null) {
                        if (exoPlayer.isPlaying()) {
                            exoPlayer.pause();
                            itemBinding.ivPlayIndicator.setVisibility(View.VISIBLE);
                        } else {
                            exoPlayer.play();
                            itemBinding.ivPlayIndicator.setVisibility(View.GONE);
                        }
                    }
                });

                initPlayer(post);
            }

            /** Abbreviated counter, hidden at 0. */
            private void bindCount(TextView view, int count) {
                if (count > 0) {
                    view.setText(abbreviate(count));
                    view.setVisibility(View.VISIBLE);
                } else {
                    view.setText(null);
                    view.setVisibility(View.GONE);
                }
            }

            /**
             * One-line caption with an inline "more"; tapping toggles between
             * one and three lines.
             */
            private void bindCaption(TextView view, String caption) {
                final String text = caption == null ? "" : caption.trim();
                if (text.isEmpty()) {
                    view.setText(null);
                    view.setVisibility(View.GONE);
                    return;
                }

                view.setVisibility(View.VISIBLE);
                view.setTag(text);
                view.setText(text);
                view.setMaxLines(CAPTION_LINES_COLLAPSED);
                view.setEllipsize(TextUtils.TruncateAt.END);
                view.setOnClickListener(v -> {
                    boolean collapsed = view.getMaxLines() == CAPTION_LINES_COLLAPSED;
                    view.setMaxLines(collapsed ? CAPTION_LINES_EXPANDED : CAPTION_LINES_COLLAPSED);
                });

                // Append the inline "more" only once layout proves the text is cut off.
                view.post(() -> {
                    if (!text.equals(view.getTag())) return;
                    if (view.getMaxLines() != CAPTION_LINES_COLLAPSED) return;
                    Layout layout = view.getLayout();
                    if (layout == null || layout.getEllipsisCount(0) <= 0) return;

                    SpannableStringBuilder sb = new SpannableStringBuilder(text)
                            .append(' ')
                            .append(view.getContext().getString(R.string.more));
                    sb.setSpan(new StyleSpan(Typeface.BOLD), text.length() + 1, sb.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    view.setText(sb);
                });
            }

            private void updateLikeIcon(boolean liked) {
                itemBinding.ivReelLike.setImageResource(liked ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
                if (liked) {
                    itemBinding.ivReelLike.setImageTintList(null);
                    itemBinding.ivReelLike.clearColorFilter();
                } else {
                    itemBinding.ivReelLike.setColorFilter(
                            itemView.getContext().getColor(R.color.white));
                }
            }

            private void initPlayer(Post post) {
                releasePlayer();
                String videoUrl = post.getVideoUrl();
                if (videoUrl == null || videoUrl.isEmpty()) {
                    videoUrl = post.getFirstMediaUrl();
                }

                if (videoUrl == null || videoUrl.isEmpty()) {
                    itemBinding.ivReelThumbnail.setVisibility(View.VISIBLE);
                    com.insta.clone.util.GlideUtils.load(itemBinding.ivReelThumbnail, "reels", post.getThumbnailUrl());
                    return;
                }

                exoPlayer = new ExoPlayer.Builder(itemView.getContext()).build();
                exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
                exoPlayer.setVolume(isMuted ? 0f : 1f);

                MediaItem mediaItem = MediaItem.fromUri(videoUrl);
                exoPlayer.setMediaItem(mediaItem);
                exoPlayer.prepare();

                itemBinding.playerView.setPlayer(exoPlayer);
                activePlayers.add(exoPlayer);
            }

            void startPlayer() {
                if (exoPlayer != null) {
                    exoPlayer.play();
                    itemBinding.ivPlayIndicator.setVisibility(View.GONE);
                }
            }

            void pausePlayer() {
                if (exoPlayer != null) {
                    exoPlayer.pause();
                }
            }

            void releasePlayer() {
                if (exoPlayer != null) {
                    activePlayers.remove(exoPlayer);
                    try {
                        exoPlayer.stop();
                        exoPlayer.release();
                    } catch (Exception ignored) {}
                    exoPlayer = null;
                }
            }
        }
    }
}
```

## 14. Java — MainActivity (Reels black-bar icon tint)

**Path:** `app/src/main/java/com/insta/clone/ui/main/MainActivity.java`

```java
package com.insta.clone.ui.main;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.insta.clone.R;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.repository.ActivityRepository;
import com.insta.clone.databinding.ActivityMainBinding;
import com.insta.clone.ui.create.CreatePostActivity;
import com.insta.clone.ui.home.HomeFragment;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;

/**
 * Main application container with the shared bottom navigation.
 *
 * Behavioral contract is unchanged: real nav destinations (Home, Search,
 * Reels, Profile) and the Create action (opens the New post picker) keep their
 * existing targets. This class only adds Page-0 presentation: edge-to-edge
 * insets, the Reels black-bar treatment, and the profile-avatar tab icon.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private ActivityRepository activityRepository;
    private SessionManager sessionManager;
    private final Handler badgePollHandler = new Handler(Looper.getMainLooper());
    private Runnable badgePollRunnable;

    private boolean profileSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        activityRepository = new ActivityRepository(this);
        sessionManager = SessionManager.getInstance(this);

        applyBottomInsets();

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_main);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
        }

        setupBottomNavigation();
        setupReelsBarTreatment();
        loadProfileAvatarIcon();
        startBadgePolling();
    }

    /** Pads the bottom bar by the system gesture inset so icons clear the handle. */
    private void applyBottomInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bars.bottom);
            return insets;
        });
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // Create opens the New post picker and never stays selected.
            if (itemId == R.id.navigation_create) {
                startActivity(new Intent(this, CreatePostActivity.class));
                return false;
            }

            if (navController != null) {
                return NavigationUI.onNavDestinationSelected(item, navController);
            }
            return false;
        });

        // Re-selection on Home scrolls feed to top
        binding.bottomNavigation.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.navigation_home) {
                NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_main);
                if (navHost != null) {
                    Fragment current = navHost.getChildFragmentManager().getPrimaryNavigationFragment();
                    if (current instanceof HomeFragment) {
                        ((HomeFragment) current).scrollToTop();
                    }
                }
            }
        });
    }

    /**
     * PAGE 5 — the Reels bar is black in both themes, so every menu icon is
     * forced white for as long as that destination is selected. Leaving the
     * destination restores the theme-driven icons.
     */
    private void applyReelsIconTint(boolean onReels) {
        if (binding == null) return;
        binding.bottomNavigation.setItemIconTintList(
                onReels ? ColorStateList.valueOf(Color.WHITE) : null);
    }

    /** On Reels the whole bar is black with white icons and no top divider. */
    private void setupReelsBarTreatment() {
        if (navController == null) return;
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            boolean onReels = destination.getId() == R.id.navigation_reels;
            applyReelsStyling(onReels);
            applyReelsIconTint(onReels);
            profileSelected = destination.getId() == R.id.navigation_profile;
            loadProfileAvatarIcon();
        });
    }

    private void applyReelsStyling(boolean onReels) {
        if (binding == null) return;
        if (onReels) {
            binding.bottomNavigation.setBackgroundColor(Color.BLACK);
            binding.bottomNavDivider.setVisibility(android.view.View.GONE);
        } else {
            binding.bottomNavigation.setBackgroundColor(resolveThemeColor(R.attr.themeBackground, Color.WHITE));
            binding.bottomNavDivider.setVisibility(android.view.View.VISIBLE);
        }
    }

    /**
     * Swaps the Profile tab icon to the user's circular avatar when an
     * avatar_url exists; a 1.5dp textPrimary ring is drawn when selected.
     * Falls back to the person-circle vector otherwise.
     */
    private void loadProfileAvatarIcon() {
        if (binding == null || sessionManager == null) return;
        final String avatarUrl = sessionManager.getAvatarUrl();
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            return; // menu selector handles the person-circle fallback
        }
        final int sizePx = dp(R.dimen.avatar_nav, 28);
        final boolean ringed = profileSelected;
        try {
            Glide.with(this)
                    .asBitmap()
                    .load(avatarUrl)
                    .circleCrop()
                    .into(new CustomTarget<Bitmap>(sizePx, sizePx) {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            Drawable d = buildAvatarDrawable(resource, ringed);
                            setProfileIcon(d);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
        } catch (Exception e) {
            Log.w("MainActivity", "Avatar icon load failed", e);
        }
    }

    private void setProfileIcon(Drawable d) {
        if (binding == null || d == null) return;
        try {
            binding.bottomNavigation.getMenu().findItem(R.id.navigation_profile)
                    .setIcon(new UntintedDrawable(d));
        } catch (Exception ignored) {
        }
    }

    /**
     * PAGE 5 — on the Reels destination every tab icon is forced white so it
     * reads on the black bar. The profile tab holds the user's real avatar, so
     * that one drawable ignores the tint and keeps its colours.
     */
    private static final class UntintedDrawable extends Drawable {
        private final Drawable wrapped;

        UntintedDrawable(Drawable wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            wrapped.setBounds(getBounds());
            wrapped.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
            wrapped.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            wrapped.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return wrapped.getOpacity();
        }

        @Override
        public int getIntrinsicWidth() {
            return wrapped.getIntrinsicWidth();
        }

        @Override
        public int getIntrinsicHeight() {
            return wrapped.getIntrinsicHeight();
        }

        // The navigation bar icon tint is deliberately ignored for the avatar.
        @Override
        public void setTintList(ColorStateList tint) {
        }

        @Override
        public void setTint(int tintColor) {
        }
    }

    private Drawable buildAvatarDrawable(Bitmap circleBitmap, boolean ringed) {
        int size = circleBitmap.getWidth();
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        float radius = size / 2f;

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new BitmapShader(circleBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

        if (ringed) {
            float stroke = dp(R.dimen.nav_avatar_ring_stroke, 2);
            float inner = radius - stroke - dp(R.dimen.story_ring_gap, 3) / 2f;
            // Avatar inset to leave room for ring + gap.
            float scale = (inner * 2f) / size;
            canvas.save();
            canvas.translate(radius - inner, radius - inner);
            canvas.scale(scale, scale);
            canvas.drawCircle(radius, radius, radius, fill);
            canvas.restore();

            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(stroke);
            ring.setColor(resolveThemeColor(R.attr.themeTextPrimary, Color.BLACK));
            canvas.drawCircle(radius, radius, radius - stroke / 2f, ring);
        } else {
            canvas.drawCircle(radius, radius, radius, fill);
        }
        return new BitmapDrawable(getResources(), out);
    }

    private int dp(int dimenRes, int fallbackDp) {
        try {
            return getResources().getDimensionPixelSize(dimenRes);
        } catch (Exception e) {
            return Math.round(fallbackDp * getResources().getDisplayMetrics().density);
        }
    }

    private int resolveThemeColor(int attr, int fallback) {
        try {
            TypedValue tv = new TypedValue();
            if (getTheme().resolveAttribute(attr, tv, true)) {
                if (tv.resourceId != 0) {
                    return ContextCompat.getColor(this, tv.resourceId);
                }
                return tv.data;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private void startBadgePolling() {
        badgePollRunnable = new Runnable() {
            @Override
            public void run() {
                pollUnreadCounts();
                badgePollHandler.postDelayed(this, Constants.POLLING_BADGES_MS);
            }
        };
        badgePollHandler.post(badgePollRunnable);
    }

    private void pollUnreadCounts() {
        // The development build can enter Home without an auth session. The
        // unread-count RPC is authenticated-only, so avoid a repeated 401
        // request and leave badges unchanged until a real session exists.
        if (sessionManager == null || sessionManager.getAccessToken() == null
                || sessionManager.getAccessToken().trim().isEmpty()) {
            return;
        }
        final LiveData<Resource<ActivityRepository.UnreadCounts>> request;
        try {
            request = activityRepository.getUnreadCounts();
        } catch (RuntimeException e) {
            Log.e("MainActivity", "Unable to start badge request", e);
            return;
        }

        request.observe(this, new Observer<Resource<ActivityRepository.UnreadCounts>>() {
            @Override
            public void onChanged(Resource<ActivityRepository.UnreadCounts> resource) {
                if (resource == null) return;
                if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                    // Badge state can be accessed by fragments or notification dots.
                }
                if (resource.status == Resource.Status.SUCCESS || resource.status == Resource.Status.ERROR) {
                    request.removeObserver(this);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (badgePollRunnable != null) {
            badgePollHandler.removeCallbacks(badgePollRunnable);
        }
        binding = null;
    }
}
```

---

# DESIGN CHECKLIST — PAGE 5 (REELS TAB)

`1dp = 2.75px` on the verification device (1080 x 2400 @ 440dpi = 393dp wide). Every
measured value below comes from `adb shell uiautomator dump` on the running build.

| # | Requirement | Implementation | Status / measured evidence |
|---|---|---|---|
| 1 | Full-screen black page, video edge to edge | `fragment_reels.xml` root `@color/black`, `vp_reels` match_parent; `player_view` `resize_mode="zoom"` | PASS — `player_view` `[0,0]-[1080,2257]` = 392.7 x 820.7dp, flush to both side edges |
| 2 | 140dp top scrim `#99000000` → transparent | `bg_reel_scrim_top.xml` (angle 270, `@color/color_scrim` → `@color/transparent`), `reel_scrim_top` in the page | PASS — `[0,0]-[1080,385]` = **140.0dp**; pixel alpha matched the 0.6 → 0 ramp exactly (e.g. red 255 → 104 at y=5, 150 at y=120) |
| 3 | 280dp bottom scrim transparent → `#99000000` | `bg_reel_scrim_bottom.xml` (reversed stops), `reel_scrim_bottom` gravity bottom | PASS — `[0,1487]-[1080,2257]` = **280.0dp**; pixel check: 0.912 / 0.756 factors measured vs 0.912 / 0.756 predicted |
| 4 | Top-left `Reels` 22sp Bold white | `btn_reel_title` TextView, `@dimen/text_screen_title` (22sp) + `@color/white` + `textStyle=bold` | PASS — title node `[44,132]-[263,214]`, left edge = 44px = **16dp** |
| 5 | `ic_chevron_down` 18dp white, decorative | `iv_reel_title_chevron` `@dimen/reel_chevron_size` (18dp), `app:tint="@color/white"`; the whole title row is the decorative affordance | PASS — `Constants.DECORATIVE_UI` + `ToastUtil.showNotAvailable()`; tapping shows the 'Not available' pill (screenshot 05). Hidden when the flag is off |
| 6 | Top-right `ic_camera` 28dp white, opens the existing Create Reel flow | `btn_camera` `@dimen/reel_camera_size` (28dp), `app:tint="@color/white"`, unchanged `openCreateReel()` → `CreateReelActivity` | PASS — `[959,134]-[1036,211]` = **28.0dp**, right edge 44px = 16dp; intent target untouched |
| 7 | Right action column: 16dp from the right edge, bottom 24dp above the nav bar, items 24dp apart, all white, centred | `layout_reel_actions` `@dimen/reel_actions_margin_end` / `_bottom`, group `layout_marginBottom=@dimen/reel_action_group_gap`, `gravity=center_horizontal` | PASS — marginEnd 1080-1036 = 44px = **16dp**; bottom 2257-2191 = 66px = **24dp**; group gaps 66px = **24dp** |
| 8 | `ic_heart` 30dp (`ic_heart_filled` `#FF3040` when liked) + count 14sp below | `iv_reel_like` `@dimen/reel_action_icon_size`; `updateLikeIcon()` swaps `ic_heart` / `ic_heart_filled`; count `TextAppearance.Insta.ReelCount` (14sp) `@dimen/reel_action_count_gap` under the icon | PASS — icon **30.2dp**, count gap 11px = **4dp**; after tapping, count text = "1" and the reddest heart pixel = `(207,39,52)` (antialiased `#FF3040`) |
| 9 | `ic_comment` 30dp + count | `iv_reel_comment` + `tv_reel_comments_count`, same tokens, unchanged `CommentsBottomSheet` | PASS — **30.2dp**; count rendered "1" on a reel with 1 comment, hidden at 0 |
| 10 | `ic_send` 30dp + count (hidden at 0) | `layout_reel_send` / `iv_reel_send` / `tv_reel_shares_count` | PASS (with note) — **30.2dp**; the icon is decorative (see NOTES 2); the model exposes no share count so the counter is permanently `gone` |
| 11 | `ic_more_horiz` 28dp | `iv_reel_more` `@dimen/reel_action_icon_more_size`, `app:tint="@color/white"` | PASS — `[953,1960]-[1030,2037]` = **28.0dp**; decorative toast (NOTES 2) |
| 12 | 32dp rounded-6dp audio thumbnail, 2dp white border, author avatar, tiny `ic_music` badge bottom-right, decorative | `layout_reel_audio` 32dp `bg_reel_audio_thumb` (6dp radius, 2dp white stroke) + 2dp body padding; `iv_reel_audio_thumb` `bg_reel_audio_inner` (4dp) `clipToOutline`; `iv_reel_audio_badge` 14dp `bg_reel_audio_badge` + `ic_music` black | PASS — frame **32.0dp**, inner **27.6dp** (= 32 - 2 x 2dp border), badge **14.2dp** pinned bottom-end; avatar loaded through Glide |
| 13 | Bottom-left block: 16dp margin, 16dp above the nav bar, max width 75% | block `layout_marginStart` / `_bottom`, inner author+caption column `layout_weight=3` against a 1-weight spacer | PASS — left 44px = **16dp**, bottom 44px = **16dp**; caption track 744-788px of 1036px available, action column starts at 950px so nothing overlaps |
| 14 | Avatar 32dp, 8dp gap, username 14sp Bold white + `ic_verified` | `iv_reel_avatar` `@dimen/reel_meta_avatar_size`; `tv_reel_username` 14sp bold + `@dimen/reel_meta_avatar_gap`; `badge_reel_verified` 14dp | PASS — avatar **32.0dp** `[44,2125]-[132,2213]`; username gap 154-132 = 22px = **8dp** |
| 15 | 12dp gap, then outlined `Follow` pill: 1dp 70% white border, 8dp radius, 28dp high, 12sp Bold white; hidden when following or own reel | `btn_reel_follow` `Widget.Insta.ReelFollowPill` + `bg_reel_follow_pill` (`@color/color_reel_pill_border` = `#B3FFFFFF`) `@dimen/reel_meta_pill_gap` | PASS — pill `[402,2055]-[566,2132]` = 59.6 x **28.0dp**; gap 402-369 = 33px = **12dp**; hidden on the signed-in user's own reel, shown on `instanova08`'s reel |
| 16 | Caption 14sp white, one line with ellipsis, tap expands to 3 lines with 'more', 8dp gap | `tv_reel_caption` 14sp `@color/white`, `maxLines=1`, `ellipsize=end`, `@dimen/reel_meta_caption_gap`; `bindCaption()` toggles 1 ↔ 3 lines and appends a bold inline `more` only when `Layout.getEllipsisCount(0) > 0` | PASS (see NOTES 3) — caption `[44,2160]-[788,2213]` = one line, 19.3dp tall, 22px = **8dp** under the author row; the tap path runs without error |
| 17 | Bottom navigation: the black variant from PAGE 0 with Reels selected; red-dot-free, no top divider | `MainActivity.applyReelsStyling()` (black bar, divider `GONE`) + new `applyReelsIconTint()` (white tab icons) + `UntintedDrawable` so the profile avatar is not washed out | PASS — light theme: nav background `(0,0,0)` with the home glyph white and the divider row showing video; Home/other tabs keep the theme bar (`(255,255,255)` + `#DBDBDB` divider). Reels tab renders the filled icon |
| 18 | Double-tap heart, play/pause icon and autoplay stay unchanged | `initPlayer()` / `startPlayer()` / `pausePlayer()` / `releasePlayer()` and the `player_view` click handler are byte-for-byte the previous logic; `iv_play_indicator` keeps its 64dp token | PASS — paging, autoplay and the centre pause indicator still behave as before |
| 19 | Token discipline — no hard-coded colours or sizes | PAGE 5 dimens block (36 tokens), `@color/color_reel_pill_border` + `@color/color_scrim` + `@color/white` / `@color/black`, `TextAppearance.Insta.ReelCount`, `Widget.Insta.ReelFollowPill`; every margin/size/radius/text size references a token | PASS — build succeeds with no literal dp/sp in the new layouts except the two layout weights (3 / 1) and the `maxLines` counts, which are not sizes |

---

# NOTES, DECISIONS AND LIMITATIONS

### 1. Where the scrims live (and why)
The scrims are drawn **inside each reel page**, not in the fragment. The page root is
opaque black, so a fragment-level scrim placed under the `ViewPager2` would be hidden by
the video surface, and a scrim placed over the pager would also dim that page's own
action column and caption. Putting the two gradients in `item_reel_player.xml` keeps the
exact visual result of the mockup: video → 140dp / 280dp scrims → per-reel chrome →
fragment chrome (`Reels`, chevron, camera). Both scrim sizes were still verified as
exactly 140.0dp and 280.0dp on device.

### 2. Decorative elements (`Constants.DECORATIVE_UI`, toast "Not available")
The brief's own rule is that design-only elements are decorative. Three elements on this
page have **no backing feature in the codebase**:

* `ic_chevron_down` next to `Reels` — there is no Reels feed selector.
* `ic_send` (share) — `ReelsFragment` never had a share flow. The only share flow in the
  app is `HomeFragment.onShareClick()` (an `ACTION_SEND` chooser) and it is bound to the
  feed screen.
* `ic_more_horiz` ("existing options") — there is **no reel options flow**. The only
  options menu is `dialog_post_options.xml`, opened from `HomeFragment.onOptionsClick()`
  and wired to the feed `ViewModel` (`deletePost` / `unfollowUser`), which is outside this
  task's scope.

All three therefore follow the shared decorative contract: visible with the
`ToastUtil.showNotAvailable()` pill when `Constants.DECORATIVE_UI` is true, hidden when
it is false. The `Follow` pill works the same way for the follow action itself.
Swapping `ic_send` to the existing `ACTION_SEND` chooser and `ic_more_horiz` to the
existing post-options sheet is a small, additive change — say the word and I will wire
them (it would introduce new interactions on a page that has none today, which is why it
was not done under "design only").

### 3. Caption expand
`bindCaption()` collapses to one line, expands to three on tap and appends a bold inline
`more` only when the text is actually clipped (`Layout.getEllipsisCount(0) > 0`, guarded
by a tag so recycled pages never show stale content). **Limitation:** every seeded reel
carries the same short 33-character caption, so no reel on this build truncates — the
ellipsis / `more` / 3-line state could not be observed on device even though the code
path runs cleanly. It will show as soon as a longer caption exists.

### 4. Counts and the share counter
`tv_reel_likes_count` / `tv_reel_comments_count` are abbreviated with the same
`abbreviate()` tiers used on the feed (999 / 1.2K / 12K / 1.2M) and hidden at 0 — verified:
a reel with 1 comment renders "1", the same reel with 0 likes hides the likes text
entirely. `Post` exposes no share count, so `tv_reel_shares_count` stays `gone`; no
number is invented for it.

### 5. `Follow` pill visibility
`Post` carries no follow state, so "hidden when following" cannot be evaluated and the
pill shows for every other user's reel. "Hidden on own reel" **is** evaluated, read-only,
from `SessionManager.getUserId()` vs `Post.getUserId()` — verified: absent on
`instanova01`'s reel, present on `instanova08`'s.

### 6. The mute toggle
The PAGE 5 action column (heart, comment, send, more, audio) replaces the old
comment + volume column, and the mockup shows no volume control. `iv_reel_mute` is kept
in the layout and still wired in `ReelsFragment`, but `android:visibility="gone"`, so no
Java behaviour was deleted — the feature is simply not part of this design.

### 7. `MainActivity` — the black bar needed white icons
`applyReelsStyling()` already made the bar black on the Reels destination, but the tab
icons are theme-coloured, so in the **light** theme they were black-on-black. PAGE 5 adds
`applyReelsIconTint()` (white tint only while Reels is selected, cleared on every other
destination) and `UntintedDrawable`, a tiny pass-through `Drawable` that ignores that
tint so the profile tab keeps showing the user's real avatar instead of a white blob.
This is presentation-only and changes no navigation target.

---

# BUILD AND DEVICE VERIFICATION

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home \
  ./gradlew :app:assembleDebug          # BUILD SUCCESSFUL
adb install -r app/build/outputs/apk/debug/app-debug.apk   # Success
```

Verified on device `10BE2S1U640002B` (1080 x 2400 @ 440dpi):

* Reels page reached from the bottom navigation; chrome, column, author row and black bar all render.
* `uiautomator dump` geometry for every element in the checklist (sizes quoted above are dp).
* Scrim ramps measured pixel-by-pixel against the expected 0.6 → 0 alpha and matched to within 1/255.
* Like tap: count appears ("1") and the heart turns `#FF3040`.
* Comment count appears on a reel that has one and is hidden on a reel that has none.
* Swiped through 8 reels: `Follow` pill present only on other users' reels.
* Decorative taps (`Reels ⌄`, `Follow`) show the shared "Not available" pill.
* Light theme re-checked: black nav bar, white tab icons, divider suppressed; Home keeps
  its themed bar and divider.
* Dark theme restored on the device afterwards; `adb shell cmd uimode night yes`.
