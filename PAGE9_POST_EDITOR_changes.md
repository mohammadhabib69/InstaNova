# PAGE 9 — POST EDITOR — changed files

**Design only.** No behaviour changes to ViewModels, repositories, API calls, models,
navigation targets or the view ids used by code. Only XML, drawables, styles and
presentation Java changed.

**References:** Image 10 (left) and Image 8 (right) for the always-dark full-screen look.
Per the brief the music strip in the mockup is implemented as the **filter strip**.

The editor is step 2 of the create flow inside `CreatePostActivity` (reached with 'Next' from
the picker), so no screen was added to the navigation graph. Upload/publish still goes through
the same `StorageApi` + `RestApi` path.

Read order: tokens → drawables → layouts → Java, then the DESIGN CHECKLIST and the
NOTES + device verification at the end.

---

## 1. Token — global dimens (PAGE 9 block)

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

    <!-- ============================================================= -->
    <!-- PAGE 8 — CREATE SHEET + PICKER                                 -->
    <!-- ============================================================= -->

    <!-- Create sheet -->
    <dimen name="create_sheet_handle_width">36dp</dimen>
    <dimen name="create_sheet_handle_height">4dp</dimen>
    <dimen name="create_sheet_handle_margin_top">8dp</dimen>
    <dimen name="create_sheet_title_padding_vertical">12dp</dimen>
    <dimen name="create_sheet_row_height">56dp</dimen>
    <dimen name="create_sheet_row_padding_horizontal">16dp</dimen>
    <dimen name="create_sheet_row_icon">24dp</dimen>
    <dimen name="create_sheet_row_icon_gap">16dp</dimen>

    <!-- Picker top bar -->
    <dimen name="picker_top_bar_height">56dp</dimen>
    <dimen name="picker_top_bar_padding_horizontal">16dp</dimen>
    <dimen name="picker_close_icon">24dp</dimen>
    <dimen name="picker_action_text_size">@dimen/text_title</dimen>

    <!-- Picker preview -->
    <dimen name="picker_overlay_circle">36dp</dimen>
    <dimen name="picker_overlay_icon">18dp</dimen>
    <dimen name="picker_overlay_margin">12dp</dimen>
    <dimen name="picker_grid_line_width">1dp</dimen>

    <!-- Recents / tools row -->
    <dimen name="picker_recents_row_height">52dp</dimen>
    <dimen name="picker_circle_button">32dp</dimen>
    <dimen name="picker_circle_button_icon">18dp</dimen>
    <dimen name="picker_circle_button_padding">7dp</dimen>
    <dimen name="picker_overlay_padding">9dp</dimen>
    <dimen name="picker_row_gap">6dp</dimen>
    <dimen name="picker_select_pill_height">32dp</dimen>
    <dimen name="picker_select_pill_radius">16dp</dimen>
    <dimen name="picker_select_pill_icon">16dp</dimen>
    <dimen name="picker_select_pill_text_size">12sp</dimen>
    <dimen name="picker_select_pill_padding_horizontal">12dp</dimen>
    <dimen name="picker_recents_gap">8dp</dimen>

    <!-- Gallery grid -->
    <dimen name="picker_grid_gap">1dp</dimen>
    <dimen name="picker_thumb_badge">22dp</dimen>
    <dimen name="picker_thumb_badge_border">2dp</dimen>
    <dimen name="picker_thumb_badge_border_rest">1.5dp</dimen>
    <dimen name="picker_thumb_badge_text_size">12sp</dimen>

    <!-- Bottom POST / STORY / REEL switch -->
    <dimen name="picker_mode_pill_height">40dp</dimen>
    <dimen name="picker_mode_pill_radius">20dp</dimen>
    <dimen name="picker_mode_pill_padding_horizontal">20dp</dimen>
    <dimen name="picker_mode_pill_gap">8dp</dimen>
    <dimen name="picker_mode_text_size">13sp</dimen>
    <dimen name="picker_mode_margin_bottom">16dp</dimen>

    <!-- ============================================================= -->
    <!-- PAGE 9 — POST EDITOR                                           -->
    <!-- ============================================================= -->
    <dimen name="editor_top_bar_padding_horizontal">16dp</dimen>
    <dimen name="editor_top_bar_padding_top">8dp</dimen>
    <dimen name="editor_top_bar_padding_bottom">8dp</dimen>
    <dimen name="editor_icon">28dp</dimen>
    <dimen name="editor_icon_gap">20dp</dimen>
    <dimen name="editor_card_radius">12dp</dimen>
    <dimen name="editor_card_gap">12dp</dimen>
    <dimen name="editor_card_close_circle">24dp</dimen>
    <dimen name="editor_card_close_icon">14dp</dimen>
    <dimen name="editor_card_inset">8dp</dimen>
    <dimen name="editor_tabs_height">40dp</dimen>
    <dimen name="editor_tab_text_size">13sp</dimen>
    <dimen name="editor_tab_gap">16dp</dimen>
    <dimen name="editor_tab_underline">2dp</dimen>
    <dimen name="editor_strip_height">96dp</dimen>
    <dimen name="filter_item_width">76dp</dimen>
    <dimen name="filter_thumb">72dp</dimen>
    <dimen name="filter_thumb_radius">8dp</dimen>
    <dimen name="filter_thumb_border">2dp</dimen>
    <dimen name="filter_name_size">12sp</dimen>
    <dimen name="filter_name_gap">2dp</dimen>
    <dimen name="editor_bottom_row_height">56dp</dimen>
    <dimen name="editor_gallery_thumb">44dp</dimen>
    <dimen name="editor_gallery_thumb_radius">8dp</dimen>
    <dimen name="editor_next_pill_height">40dp</dimen>
    <dimen name="editor_next_pill_radius">20dp</dimen>
    <dimen name="editor_next_pill_padding_horizontal">16dp</dimen>
    <dimen name="editor_next_text_size">14sp</dimen>
    <dimen name="editor_next_icon">18dp</dimen>

    <!-- ============================================================= -->
    <!-- PAGE 10 — CAPTION + NEW POST                                   -->
    <!-- ============================================================= -->
    <dimen name="details_top_bar_height">56dp</dimen>
    <dimen name="details_padding_horizontal">16dp</dimen>
    <dimen name="details_photo_size">120dp</dimen>
    <dimen name="details_photo_radius">8dp</dimen>
    <dimen name="details_photo_gap">4dp</dimen>
    <dimen name="details_caption_size">16sp</dimen>
    <dimen name="details_caption_margin_top">12dp</dimen>
    <dimen name="details_chip_height">34dp</dimen>
    <dimen name="details_chip_radius">17dp</dimen>
    <dimen name="details_chip_icon">16dp</dimen>
    <dimen name="details_chip_text_size">14sp</dimen>
    <dimen name="details_chip_gap">8dp</dimen>
    <dimen name="details_chip_padding_horizontal">12dp</dimen>
    <dimen name="details_row_height">56dp</dimen>
    <dimen name="details_row_icon">24dp</dimen>
    <dimen name="details_row_icon_gap">16dp</dimen>
    <dimen name="details_row_chevron">20dp</dimen>
    <dimen name="details_row_value_size">16sp</dimen>
    <dimen name="details_row_desc_size">12sp</dimen>
    <dimen name="details_section_gap">8dp</dimen>
    <dimen name="details_share_height">48dp</dimen>
    <dimen name="details_share_radius">12dp</dimen>
    <dimen name="details_share_text_size">16sp</dimen>
    <dimen name="details_share_margin">16dp</dimen>

    <dimen name="caption_top_bar_height">56dp</dimen>
    <dimen name="caption_field_size">16sp</dimen>
    <dimen name="caption_field_padding">16dp</dimen>
    <dimen name="caption_action_button">36dp</dimen>
</resources>
```

## 2. Token — strings (PAGE 9 strings)

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

    <!-- Create sheet + picker (PAGE 8) -->
    <string name="create_title">Create</string>
    <string name="create_reel">Reel</string>
    <string name="create_post">Post</string>
    <string name="create_story">Story</string>
    <string name="create_story_highlight">Story highlight</string>
    <string name="create_live">Live</string>
    <string name="create_made_for_you">Made for you</string>
    <string name="picker_new_post">New post</string>
    <string name="picker_next">Next</string>
    <string name="picker_recents">Recents</string>
    <string name="picker_select_multiple">SELECT MULTIPLE</string>
    <string name="picker_mode_post">POST</string>
    <string name="picker_mode_story">STORY</string>
    <string name="picker_mode_reel">REEL</string>
    <string name="picker_fit_toggle">Toggle fit or fill</string>
    <string name="picker_album_source">Album source</string>

    <!-- Post editor (PAGE 9) -->
    <string name="editor_tab_filter">Filter</string>
    <string name="editor_tab_edit">Edit</string>
    <string name="editor_tab_crop">Crop</string>
    <string name="editor_aa">Aa</string>
    <string name="editor_add_photos">Add photos</string>
    <string name="editor_filter_normal">Normal</string>

    <!-- Caption + new post (PAGE 10) -->
    <string name="details_chip_poll">Poll</string>
    <string name="details_chip_prompt">Prompt</string>
    <string name="details_tag_people">Tag people</string>
    <string name="details_add_location">Add location</string>
    <string name="details_add_music">Add music</string>
    <string name="details_ai_label">Add AI label</string>
    <string name="details_ai_label_desc">We require you to label certain realistic content that\u2019s made with AI. Learn more</string>
    <string name="details_audience">Audience</string>
    <string name="details_audience_value">Everyone</string>
    <string name="details_also_share">Also share on...</string>
    <string name="details_also_share_value">Off</string>
    <string name="details_more_options">More options</string>
    <string name="details_share">Share</string>
    <string name="caption_title">Caption</string>
    <string name="caption_ok">OK</string>
    <string name="caption_hint">Write a caption...</string>
</resources>
```

## 3. Token — styles (editor card corner, tab + filter label)

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

    <!-- PAGE 9 — Editor card corner (12dp rounded image card) -->
    <style name="ShapeAppearance.Insta.EditorCard" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">@dimen/editor_card_radius</item>
    </style>

    <!-- PAGE 10 — selected photo strip corner (8dp rounded) -->
    <style name="ShapeAppearance.Insta.DetailsPhoto" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">@dimen/details_photo_radius</item>
    </style>

    <!-- PAGE 9 — Editor tab label (13sp bold caps white) -->
    <style name="TextAppearance.Insta.EditorTab" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:textStyle">bold</item>
        <item name="android:textAllCaps">true</item>
        <item name="android:maxLines">1</item>
        <item name="android:textSize">@dimen/editor_tab_text_size</item>
        <item name="android:textColor">@color/white</item>
    </style>

    <!-- PAGE 9 — Filter preset name (12sp white, one line) -->
    <style name="TextAppearance.Insta.FilterName" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:maxLines">1</item>
        <item name="android:ellipsize">end</item>
        <item name="android:gravity">center</item>
        <item name="android:textSize">@dimen/filter_name_size</item>
        <item name="android:textColor">@color/white</item>
    </style>

    <!-- PAGE 10 — Details row label (16sp, theme aware) -->
    <style name="TextAppearance.Insta.DetailsRow" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:maxLines">1</item>
        <item name="android:textSize">@dimen/text_title</item>
        <item name="android:textColor">?attr/themeTextPrimary</item>
    </style>

    <!-- PAGE 10 — Poll / Prompt chip label (14sp Medium) -->
    <style name="TextAppearance.Insta.DetailsChip" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:maxLines">1</item>
        <item name="android:textSize">@dimen/details_chip_text_size</item>
        <item name="android:textColor">?attr/themeTextPrimary</item>
    </style>

    <!-- PAGE 8 — Create sheet row label (16sp, one line) -->
    <style name="TextAppearance.Insta.CreateRow" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:maxLines">1</item>
        <item name="android:ellipsize">end</item>
        <item name="android:textSize">@dimen/text_title</item>
        <item name="android:textColor">?attr/themeTextPrimary</item>
    </style>

    <!-- PAGE 8 — Picker 'SELECT MULTIPLE' pill label (12sp bold caps) -->
    <style name="TextAppearance.Insta.PickerPill" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:textStyle">bold</item>
        <item name="android:textAllCaps">true</item>
        <item name="android:maxLines">1</item>
        <item name="android:textSize">@dimen/picker_select_pill_text_size</item>
        <item name="android:textColor">?attr/themeTextPrimary</item>
    </style>

    <!-- PAGE 8 — Picker bottom POST / STORY / REEL switch (13sp bold caps) -->
    <style name="TextAppearance.Insta.PickerMode" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:textStyle">bold</item>
        <item name="android:maxLines">1</item>
        <item name="android:textSize">@dimen/picker_mode_text_size</item>
        <item name="android:textColor">@color/color_icon_muted</item>
    </style>

    <!-- PAGE 8 — Picker top bar title / action (16sp bold) -->
    <style name="TextAppearance.Insta.PickerBar" parent="TextAppearance.AppCompat">
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:textStyle">bold</item>
        <item name="android:maxLines">1</item>
        <item name="android:textSize">@dimen/picker_action_text_size</item>
        <item name="android:textColor">@color/white</item>
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

## 4. Drawable — 8dp filter thumbnail frame

**Path:** `app/src/main/res/drawable/bg_filter_thumb.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- PAGE 9 — 8dp rounded background of a filter preset thumbnail. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/filter_thumb_radius" />
    <solid android:color="@color/color_surface_variant" />
</shape>
```

## 5. NEW drawable — selected filter border

**Path:** `app/src/main/res/drawable/bg_filter_selected_border.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- PAGE 9 — 2dp white border drawn over the selected filter preset. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/filter_thumb_radius" />
    <solid android:color="@color/transparent" />
    <stroke
        android:width="@dimen/filter_thumb_border"
        android:color="@color/white" />
</shape>
```

## 6. NEW drawable — editor Next pill

**Path:** `app/src/main/res/drawable/bg_editor_next_pill.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- PAGE 9 — 'Next' pill in the editor bottom bar. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/editor_next_pill_radius" />
    <solid android:color="@color/color_primary" />
</shape>
```

## 7. NEW drawable — add-more thumbnail frame

**Path:** `app/src/main/res/drawable/bg_editor_gallery_thumb.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- PAGE 9 — 44dp rounded-8dp 'add more photos' thumbnail frame. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/editor_gallery_thumb_radius" />
    <solid android:color="@color/color_surface_variant" />
    <stroke
        android:width="@dimen/divider_hairline"
        android:color="@color/white" />
</shape>
```

## 8. Drawable — reused overlay circle (from PAGE 8)

**Path:** `app/src/main/res/drawable/bg_picker_overlay_circle.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 8/9 — translucent control circle drawn on top of media (#99000000).
    Used for the fit/fill toggle and the per-card remove button.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/color_scrim" />
</shape>
```

## 9. Layout — editor step

**Path:** `app/src/main/res/layout/create_step_editor.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 9 — Post editor (step 2 of the create flow, the screen after 'Next').

    Full-screen black. Top bar (close / filters / add photos / Aa), the
    horizontal 4:5 image pager, the Filter | Edit | Crop tab row, the filter
    preset strip, and the bottom row with the add-more thumbnail and Next.

    The filter preset list is fed from the existing ColorMatrixPresets source.
-->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/black"
    android:orientation="vertical">

    <!-- Top bar -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/editor_top_bar_padding_horizontal"
        android:paddingTop="@dimen/editor_top_bar_padding_top"
        android:paddingEnd="@dimen/editor_top_bar_padding_horizontal"
        android:paddingBottom="@dimen/editor_top_bar_padding_bottom">

        <ImageView
            android:id="@+id/btn_editor_close"
            android:layout_width="@dimen/editor_icon"
            android:layout_height="@dimen/editor_icon"
            android:contentDescription="@string/not_available"
            android:src="@drawable/ic_close"
            app:tint="@color/white" />

        <View
            android:layout_width="0dp"
            android:layout_height="1dp"
            android:layout_weight="1" />

        <ImageView
            android:id="@+id/btn_editor_filters"
            android:layout_width="@dimen/editor_icon"
            android:layout_height="@dimen/editor_icon"
            android:contentDescription="@string/editor_tab_filter"
            android:src="@drawable/ic_filters"
            app:tint="@color/white" />

        <ImageView
            android:id="@+id/btn_editor_gallery"
            android:layout_width="@dimen/editor_icon"
            android:layout_height="@dimen/editor_icon"
            android:layout_marginStart="@dimen/editor_icon_gap"
            android:contentDescription="@string/editor_add_photos"
            android:src="@drawable/ic_gallery"
            app:tint="@color/white" />

        <TextView
            android:id="@+id/btn_editor_aa"
            android:layout_width="@dimen/editor_icon"
            android:layout_height="@dimen/editor_icon"
            android:layout_marginStart="@dimen/editor_icon_gap"
            android:gravity="center"
            android:text="@string/editor_aa"
            android:textColor="@color/white"
            android:textSize="@dimen/text_title"
            android:textStyle="bold" />
    </LinearLayout>

    <!-- Image pager: 88% wide cards, 4:5, next card peeking -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_editor_cards"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:overScrollMode="never"
        android:paddingStart="@dimen/details_padding_horizontal"
        android:paddingEnd="@dimen/details_padding_horizontal" />

    <!-- Filter | Edit | Crop -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="@dimen/editor_tabs_height"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/details_padding_horizontal"
        android:paddingEnd="@dimen/details_padding_horizontal">

        <LinearLayout
            android:id="@+id/btn_editor_tab_filter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tv_editor_filter"
                style="@style/TextAppearance.Insta.EditorTab"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/editor_tab_filter" />

            <View
                android:id="@+id/view_tab_underline_filter"
                android:layout_width="match_parent"
                android:layout_height="@dimen/editor_tab_underline"
                android:layout_marginTop="@dimen/filter_name_gap"
                android:background="@color/white" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/btn_editor_tab_edit"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/editor_tab_gap"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tv_editor_edit"
                style="@style/TextAppearance.Insta.EditorTab"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/editor_tab_edit" />

            <View
                android:id="@+id/view_tab_underline_edit"
                android:layout_width="match_parent"
                android:layout_height="@dimen/editor_tab_underline"
                android:layout_marginTop="@dimen/filter_name_gap"
                android:background="@color/white"
                android:visibility="gone" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/btn_editor_tab_crop"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/editor_tab_gap"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tv_editor_crop"
                style="@style/TextAppearance.Insta.EditorTab"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/editor_tab_crop" />

            <View
                android:id="@+id/view_tab_underline_crop"
                android:layout_width="match_parent"
                android:layout_height="@dimen/editor_tab_underline"
                android:layout_marginTop="@dimen/filter_name_gap"
                android:background="@color/white"
                android:visibility="gone" />
        </LinearLayout>
    </LinearLayout>

    <!-- Filter preset strip -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_filter_strip"
        android:layout_width="match_parent"
        android:layout_height="@dimen/editor_strip_height"
        android:clipToPadding="false"
        android:overScrollMode="never"
        android:paddingStart="@dimen/details_padding_horizontal"
        android:paddingEnd="@dimen/details_padding_horizontal" />

    <!-- Bottom row: add more photos (left) and Next (right) -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="@dimen/editor_bottom_row_height"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/details_padding_horizontal"
        android:paddingEnd="@dimen/details_padding_horizontal">

        <FrameLayout
            android:id="@+id/btn_editor_gallery_thumb"
            android:layout_width="@dimen/editor_gallery_thumb"
            android:layout_height="@dimen/editor_gallery_thumb"
            android:background="@drawable/bg_editor_gallery_thumb">

            <ImageView
                android:id="@+id/iv_editor_gallery_thumb"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:clipToOutline="true"
                android:contentDescription="@string/editor_add_photos"
                android:scaleType="centerCrop" />

            <ImageView
                android:layout_width="@dimen/editor_card_close_icon"
                android:layout_height="@dimen/editor_card_close_icon"
                android:layout_gravity="bottom|end"
                android:contentDescription="@string/editor_add_photos"
                android:src="@drawable/ic_add"
                app:tint="@color/white" />
        </FrameLayout>

        <View
            android:layout_width="0dp"
            android:layout_height="1dp"
            android:layout_weight="1" />

        <LinearLayout
            android:id="@+id/btn_editor_next"
            android:layout_width="wrap_content"
            android:layout_height="@dimen/editor_next_pill_height"
            android:background="@drawable/bg_editor_next_pill"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingStart="@dimen/editor_next_pill_padding_horizontal"
            android:paddingEnd="@dimen/editor_next_pill_padding_horizontal">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/picker_next"
                android:textColor="@color/white"
                android:textSize="@dimen/editor_next_text_size"
                android:textStyle="bold" />

            <ImageView
                android:layout_width="@dimen/editor_next_icon"
                android:layout_height="@dimen/editor_next_icon"
                android:layout_marginStart="@dimen/picker_row_gap"
                android:contentDescription="@string/picker_next"
                android:src="@drawable/ic_arrow_forward"
                app:tint="@color/white" />
        </LinearLayout>
    </LinearLayout>

</LinearLayout>
```

## 10. NEW layout — image card

**Path:** `app/src/main/res/layout/item_editor_card.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 9 — one image card in the horizontal pager (4:5, 12dp radius).
    The card width (88% of the screen) is applied by the adapter.
-->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content">

    <com.google.android.material.imageview.ShapeableImageView
        android:id="@+id/iv_editor_card"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:contentDescription="@string/not_available"
        android:scaleType="centerCrop"
        app:shapeAppearanceOverlay="@style/ShapeAppearance.Insta.EditorCard" />

    <!-- Remove this image -->
    <ImageView
        android:id="@+id/btn_editor_remove"
        android:layout_width="@dimen/editor_card_close_circle"
        android:layout_height="@dimen/editor_card_close_circle"
        android:layout_gravity="top|end"
        android:layout_margin="@dimen/editor_card_inset"
        android:background="@drawable/bg_picker_overlay_circle"
        android:contentDescription="@string/not_available"
        android:padding="@dimen/reel_audio_badge_padding"
        android:src="@drawable/ic_close"
        app:tint="@color/white" />

    <!-- Fit / fill toggle -->
    <ImageView
        android:id="@+id/btn_editor_expand"
        android:layout_width="@dimen/picker_overlay_circle"
        android:layout_height="@dimen/picker_overlay_circle"
        android:layout_gravity="bottom|start"
        android:layout_margin="@dimen/editor_card_inset"
        android:background="@drawable/bg_picker_overlay_circle"
        android:contentDescription="@string/picker_fit_toggle"
        android:padding="@dimen/picker_overlay_padding"
        android:src="@drawable/ic_expand"
        app:tint="@color/white" />

</FrameLayout>
```

## 11. NEW layout — filter preset

**Path:** `app/src/main/res/layout/item_filter_preset.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 9 — one filter preset: 72dp square thumbnail (8dp radius, 2dp white
    border when selected) with its name underneath.
-->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="@dimen/filter_item_width"
    android:layout_height="wrap_content"
    android:gravity="center_horizontal"
    android:orientation="vertical">

    <FrameLayout
        android:layout_width="@dimen/filter_thumb"
        android:layout_height="@dimen/filter_thumb">

        <com.insta.clone.ui.widget.SquareImageView
            android:id="@+id/iv_filter_thumb"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@drawable/bg_filter_thumb"
            android:clipToOutline="true"
            android:contentDescription="@string/not_available"
            android:scaleType="centerCrop" />

        <View
            android:id="@+id/view_filter_border"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@drawable/bg_filter_selected_border"
            android:visibility="gone" />
    </FrameLayout>

    <TextView
        android:id="@+id/tv_filter_name"
        style="@style/TextAppearance.Insta.FilterName"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/filter_name_gap" />

</LinearLayout>
```

## 12. Java — CreatePostActivity (editor step + everything else)

**Path:** `app/src/main/java/com/insta/clone/ui/create/CreatePostActivity.java`

```java
package com.insta.clone.ui.create;

import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.insta.clone.BuildConfig;
import com.insta.clone.R;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.api.StorageApi;
import com.insta.clone.data.remote.dto.PostDto;
import com.insta.clone.util.ColorMatrixPresets;
import com.insta.clone.util.Constants;
import com.insta.clone.util.ImageUtils;
import com.insta.clone.util.ToastUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The create flow: picker (PAGE 8B) → post editor (PAGE 9) → new post details
 * (PAGE 10A) → caption (PAGE 10B), all inside this one activity so no new
 * navigation target is introduced.
 *
 * Presentation only: the upload/publish path (StorageApi + RestApi +
 * ImageUtils), the picker launchers and every view id used below are
 * unchanged. Everything that had no backing feature in this codebase is wired
 * through the shared Constants.DECORATIVE_UI contract.
 */
public class CreatePostActivity extends AppCompatActivity {

    private static final String TAG_API = "InstaApi";
    private static final String TAG_GLIDE = "InstaGlide";

    private static final int STEP_PICKER = 0;
    private static final int STEP_EDITOR = 1;
    private static final int STEP_DETAILS = 2;
    private static final int STEP_CAPTION = 3;

    private static final int GALLERY_LIMIT = 60;
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#(\\w+)");

    private final List<Uri> selectedUris = new ArrayList<>();
    private final List<Uri> galleryUris = new ArrayList<>();
    private final List<String> uploadedPaths = new ArrayList<>();
    private final List<String> uploadedPublicUrls = new ArrayList<>();
    private final List<ColorMatrixPresets.FilterPreset> filterPresets = ColorMatrixPresets.getPresets();

    private StorageApi storageApi;
    private RestApi restApi;
    private okhttp3.OkHttpClient okHttpClient;
    private String currentUserId;

    // Steps
    private View stepPicker;
    private View stepEditor;
    private View stepDetails;
    private View stepCaption;

    // Picker
    private ImageView ivPreview;
    private TextView btnPickerNext;
    private View btnPickerMulti;
    private ImageView ivMultiIcon;
    private TextView tvMultiLabel;
    private View layoutPickerGridLines;
    private RecyclerView rvPickerGrid;
    private PickerGridAdapter pickerAdapter;
    private Uri previewUri;
    private boolean multiSelect = false;
    private boolean previewFill = true;

    // Editor
    private RecyclerView rvEditorCards;
    private RecyclerView rvFilterStrip;
    private EditorCardAdapter editorCardAdapter;
    private FilterAdapter filterAdapter;
    private View underlineFilter;
    private View underlineEdit;
    private View underlineCrop;
    private ImageView ivEditorGalleryThumb;
    private int selectedFilter = 0;

    // Details / caption
    private RecyclerView rvDetailsPhotos;
    private DetailsPhotoAdapter detailsPhotoAdapter;
    private android.widget.EditText etCaption;
    private android.widget.EditText etCaptionScreen;
    private View btnPublish;
    private View tvLocationValue;

    private View progress;

    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMediaLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        currentUserId = SessionManager.getInstance(this).getUserId();
        RetrofitClient client = RetrofitClient.getInstance(this);
        storageApi = client.getStorageApi();
        restApi = client.getRestApi();
        okHttpClient = client.getOkHttpClient();

        bindViews();
        setupPickers();
        setupPickerStep();
        setupEditorStep();
        setupDetailsStep();
        setupCaptionStep();

        showStep(STEP_PICKER);
        loadDeviceGallery();
    }

    // ------------------------------------------------------------------
    // Wiring
    // ------------------------------------------------------------------

    private void bindViews() {
        stepPicker = findViewById(R.id.step_picker);
        stepEditor = findViewById(R.id.step_editor);
        stepDetails = findViewById(R.id.step_details);
        stepCaption = findViewById(R.id.step_caption);
        progress = findViewById(R.id.progress);

        ivPreview = findViewById(R.id.iv_preview);
        btnPickerNext = findViewById(R.id.btn_picker_next);
        btnPickerMulti = findViewById(R.id.btn_picker_multi);
        ivMultiIcon = findViewById(R.id.iv_multi_icon);
        tvMultiLabel = findViewById(R.id.tv_multi_label);
        layoutPickerGridLines = findViewById(R.id.layout_picker_grid_lines);
        rvPickerGrid = findViewById(R.id.rv_picker_grid);

        rvEditorCards = findViewById(R.id.rv_editor_cards);
        rvFilterStrip = findViewById(R.id.rv_filter_strip);
        underlineFilter = findViewById(R.id.view_tab_underline_filter);
        underlineEdit = findViewById(R.id.view_tab_underline_edit);
        underlineCrop = findViewById(R.id.view_tab_underline_crop);
        ivEditorGalleryThumb = findViewById(R.id.iv_editor_gallery_thumb);

        rvDetailsPhotos = findViewById(R.id.rv_details_photos);
        etCaption = findViewById(R.id.et_caption);
        etCaptionScreen = findViewById(R.id.et_caption_screen);
        btnPublish = findViewById(R.id.btn_publish);
        tvLocationValue = findViewById(R.id.tv_location_value);
    }

    private void bindClick(int id, View.OnClickListener listener) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(listener);
    }

    /** Swaps the four steps of the create flow. */
    private void showStep(int step) {
        stepPicker.setVisibility(step == STEP_PICKER ? View.VISIBLE : View.GONE);
        stepEditor.setVisibility(step == STEP_EDITOR ? View.VISIBLE : View.GONE);
        stepDetails.setVisibility(step == STEP_DETAILS ? View.VISIBLE : View.GONE);
        stepCaption.setVisibility(step == STEP_CAPTION ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------
    // PAGE 8B — picker
    // ------------------------------------------------------------------

    private void setupPickerStep() {
        bindClick(R.id.btn_picker_close, v -> finish());
        bindClick(R.id.btn_picker_next, v -> {
            if (selectedUris.isEmpty()) return;
            refreshEditor();
            showStep(STEP_EDITOR);
        });

        // Album source is design-only (the app has no album browser)
        bindClick(R.id.btn_picker_album, v -> ToastUtil.showNotAvailable(this));

        btnPickerMulti.setOnClickListener(v -> setMultiSelect(!multiSelect));

        // Existing system photo picker stays the selection entry point
        bindClick(R.id.btn_pick_image, v -> pickMultipleMediaLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        // Fit / fill toggle on the preview
        bindClick(R.id.btn_picker_expand, v -> {
            previewFill = !previewFill;
            ivPreview.setScaleType(previewFill
                    ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        });

        // Rule-of-thirds guides while dragging / zooming
        ivPreview.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                layoutPickerGridLines.setVisibility(View.VISIBLE);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                layoutPickerGridLines.setVisibility(View.GONE);
            }
            return false;
        });

        pickerAdapter = new PickerGridAdapter();
        rvPickerGrid.setLayoutManager(new GridLayoutManager(this, 4));
        rvPickerGrid.addItemDecoration(new GridGapDecoration(4,
                Math.round(getResources().getDisplayMetrics().density * 1f)));
        rvPickerGrid.setAdapter(pickerAdapter);

        // Bottom POST / STORY / REEL switch
        bindClick(R.id.tv_mode_story, v -> startActivity(new Intent(this, CreateStoryActivity.class)));
        bindClick(R.id.tv_mode_reel, v -> startActivity(new Intent(this, CreateReelActivity.class)));

        updatePickerActions();
    }

    /** 'SELECT MULTIPLE' pill ⇄ 32dp blue circle with a white icon. */
    private void setMultiSelect(boolean multi) {
        multiSelect = multi;
        int circle = getResources().getDimensionPixelSize(R.dimen.picker_circle_button);
        ViewGroup.LayoutParams lp = btnPickerMulti.getLayoutParams();
        if (multi) {
            lp.width = circle;
            lp.height = circle;
            btnPickerMulti.setBackgroundResource(R.drawable.bg_picker_multi_active);
            btnPickerMulti.setPadding(0, 0, 0, 0);
            ivMultiIcon.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.white)));
            tvMultiLabel.setVisibility(View.GONE);
        } else {
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = getResources().getDimensionPixelSize(R.dimen.picker_select_pill_height);
            btnPickerMulti.setBackgroundResource(R.drawable.bg_picker_pill);
            int pad = getResources().getDimensionPixelSize(R.dimen.picker_select_pill_padding_horizontal);
            btnPickerMulti.setPadding(pad, 0, pad, 0);
            ivMultiIcon.setImageTintList(null);
            tvMultiLabel.setVisibility(View.VISIBLE);
        }
        btnPickerMulti.setLayoutParams(lp);
        pickerAdapter.notifyDataSetChanged();
    }

    private void updatePickerActions() {
        boolean hasSelection = !selectedUris.isEmpty();
        btnPickerNext.setEnabled(hasSelection);
    }

    private void setPreview(Uri uri) {
        previewUri = uri;
        if (uri == null) {
            Glide.with(this).clear(ivPreview);
            return;
        }
        Glide.with(this)
                .load(uri)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG_GLIDE, "Failed to load local preview with Glide: " + e);
                        return false;
                    }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .into(ivPreview);
    }

    /** Reads the device gallery. Declared READ_MEDIA_IMAGES only, no prompt. */
    private void loadDeviceGallery() {
        galleryUris.clear();
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
            pickerAdapter.notifyDataSetChanged();
            return;
        }
        String[] projection = {MediaStore.Images.Media._ID};
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                while (cursor.moveToNext() && galleryUris.size() < GALLERY_LIMIT) {
                    galleryUris.add(ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn)));
                }
            }
        } catch (Exception ignored) {
        }
        pickerAdapter.notifyDataSetChanged();
    }

    private void onThumbClick(Uri uri) {
        if (multiSelect) {
            if (selectedUris.contains(uri)) {
                selectedUris.remove(uri);
            } else if (selectedUris.size() < Constants.MAX_CAROUSEL_IMAGES) {
                selectedUris.add(uri);
            }
        } else {
            selectedUris.clear();
            selectedUris.add(uri);
        }
        setPreview(selectedUris.isEmpty() ? null : selectedUris.get(0));
        updatePickerActions();
        updateShareState();
        pickerAdapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------------
    // PAGE 9 — editor
    // ------------------------------------------------------------------

    private void setupEditorStep() {
        bindClick(R.id.btn_editor_close, v -> finish());
        bindClick(R.id.btn_editor_filters, v -> selectEditorTab(0));
        bindClick(R.id.btn_editor_aa, v -> ToastUtil.showNotAvailable(this));

        View.OnClickListener addMore = v -> pickMultipleMediaLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
        bindClick(R.id.btn_editor_gallery, addMore);
        bindClick(R.id.btn_editor_gallery_thumb, addMore);

        // Filter | Edit | Crop
        bindClick(R.id.btn_editor_tab_filter, v -> selectEditorTab(0));
        bindClick(R.id.btn_editor_tab_edit, v -> ToastUtil.showNotAvailable(this));
        bindClick(R.id.btn_editor_tab_crop, v -> ToastUtil.showNotAvailable(this));

        bindClick(R.id.btn_editor_next, v -> {
            refreshDetails();
            showStep(STEP_DETAILS);
        });

        int cardWidth = Math.round(getResources().getDisplayMetrics().widthPixels * 0.88f);
        int cardHeight = Math.round(cardWidth * 5f / 4f);
        editorCardAdapter = new EditorCardAdapter(cardWidth, cardHeight);
        LinearLayoutManager cardsManager = new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false);
        rvEditorCards.setLayoutManager(cardsManager);
        rvEditorCards.addItemDecoration(new GapDecoration(
                getResources().getDimensionPixelSize(R.dimen.editor_card_gap)));
        new PagerSnapHelper().attachToRecyclerView(rvEditorCards);
        rvEditorCards.setAdapter(editorCardAdapter);

        filterAdapter = new FilterAdapter();
        rvFilterStrip.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rvFilterStrip.setAdapter(filterAdapter);

    }

    private void selectEditorTab(int index) {
        underlineFilter.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        underlineEdit.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        underlineCrop.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        rvFilterStrip.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
    }

    private void refreshEditor() {
        editorCardAdapter.notifyDataSetChanged();
        filterAdapter.notifyDataSetChanged();
        if (!selectedUris.isEmpty()) {
            Glide.with(this).load(selectedUris.get(0)).centerCrop().into(ivEditorGalleryThumb);
        }
        selectEditorTab(0);
    }

    // ------------------------------------------------------------------
    // PAGE 10 — details + caption
    // ------------------------------------------------------------------

    private void setupDetailsStep() {
        bindClick(R.id.btn_details_back, v -> {
            refreshEditor();
            showStep(STEP_EDITOR);
        });

        // Tapping the caption area opens the caption screen
        if (etCaption != null) {
            etCaption.setOnClickListener(v -> {
                etCaptionScreen.setText(etCaption.getText());
                showStep(STEP_CAPTION);
                etCaptionScreen.requestFocus();
                etCaptionScreen.post(() -> {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(etCaptionScreen, InputMethodManager.SHOW_IMPLICIT);
                });
            });
        }

        detailsPhotoAdapter = new DetailsPhotoAdapter();
        rvDetailsPhotos.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rvDetailsPhotos.setAdapter(detailsPhotoAdapter);

        btnPublish.setOnClickListener(v -> publishPost());

        // Design-only rows
        int[] decorativeRows = {
                R.id.row_tag_people, R.id.row_add_music, R.id.row_ai_label,
                R.id.row_audience, R.id.row_also_share, R.id.row_more_options,
                R.id.btn_chip_poll, R.id.btn_chip_prompt, R.id.switch_ai_label,
                R.id.row_add_location
        };
        for (int id : decorativeRows) {
            final View row = findViewById(id);
            if (row == null) continue;
            if (Constants.DECORATIVE_UI) {
                row.setVisibility(View.VISIBLE);
                row.setOnClickListener(v -> ToastUtil.showNotAvailable(this));
            } else {
                row.setVisibility(View.GONE);
            }
        }
        if (tvLocationValue != null) tvLocationValue.setVisibility(View.GONE);
    }

    private void refreshDetails() {
        detailsPhotoAdapter.notifyDataSetChanged();
    }

    private void setupCaptionStep() {
        bindClick(R.id.btn_caption_ok, v -> {
            if (etCaption != null && etCaptionScreen != null) {
                etCaption.setText(etCaptionScreen.getText());
            }
            // Drop the field focus and the keyboard before returning
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && etCaptionScreen != null) {
                imm.hideSoftInputFromWindow(etCaptionScreen.getWindowToken(), 0);
                etCaptionScreen.clearFocus();
            }
            showStep(STEP_DETAILS);
        });
        bindClick(R.id.btn_caption_edit, v -> ToastUtil.showNotAvailable(this));

        int[] decorative = {R.id.btn_caption_chip_poll, R.id.btn_caption_chip_prompt};
        for (int id : decorative) {
            final View chip = findViewById(id);
            if (chip == null) continue;
            if (Constants.DECORATIVE_UI) {
                chip.setVisibility(View.VISIBLE);
                chip.setOnClickListener(v -> ToastUtil.showNotAvailable(this));
            } else {
                chip.setVisibility(View.GONE);
            }
        }

        // #hashtags typed in the caption use the mention colour
        etCaptionScreen.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                styleHashtags(s);
            }
        });
    }

    private void styleHashtags(Editable editable) {
        if (editable == null) return;
        for (ForegroundColorSpan span : editable.getSpans(0, editable.length(), ForegroundColorSpan.class)) {
            editable.removeSpan(span);
        }
        int color = ContextCompat.getColor(this, R.color.color_mention);
        Matcher matcher = HASHTAG_PATTERN.matcher(editable.toString());
        while (matcher.find()) {
            editable.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    // ------------------------------------------------------------------
    // Adapters
    // ------------------------------------------------------------------

    private class PickerGridAdapter extends RecyclerView.Adapter<PickerGridAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_picker_thumb, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Uri uri = galleryUris.get(position);
            Glide.with(CreatePostActivity.this).load(uri).centerCrop().into(holder.thumb);

            int index = selectedUris.indexOf(uri);
            boolean selected = index >= 0;

            holder.dim.setVisibility(!multiSelect && selected ? View.VISIBLE : View.GONE);
            holder.index.setVisibility(multiSelect && selected ? View.VISIBLE : View.GONE);
            holder.ring.setVisibility(multiSelect && !selected ? View.VISIBLE : View.GONE);
            if (multiSelect && selected) {
                holder.index.setText(String.valueOf(index + 1));
            }
            holder.itemView.setOnClickListener(v -> onThumbClick(uri));
        }

        @Override
        public int getItemCount() {
            return galleryUris.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final View dim;
            final View ring;
            final TextView index;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.iv_thumb);
                dim = itemView.findViewById(R.id.view_thumb_dim);
                ring = itemView.findViewById(R.id.view_thumb_ring);
                index = itemView.findViewById(R.id.tv_thumb_index);
            }
        }
    }

    private class EditorCardAdapter extends RecyclerView.Adapter<EditorCardAdapter.ViewHolder> {
        private final int cardWidth;
        private final int cardHeight;

        EditorCardAdapter(int cardWidth, int cardHeight) {
            this.cardWidth = cardWidth;
            this.cardHeight = cardHeight;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_editor_card, parent, false);
            v.setLayoutParams(new RecyclerView.LayoutParams(cardWidth, cardHeight));
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (position < 0 || position >= selectedUris.size()) return;
            Uri uri = selectedUris.get(position);
            Glide.with(CreatePostActivity.this).load(uri).centerCrop().into(holder.card);
            if (selectedFilter != 0 && selectedFilter < filterPresets.size()) {
                holder.card.setColorFilter(filterPresets.get(selectedFilter).getColorFilter(1f));
            } else {
                holder.card.clearColorFilter();
            }
            holder.remove.setOnClickListener(v -> {
                selectedUris.remove(uri);
                if (selectedUris.isEmpty()) {
                    showStep(STEP_PICKER);
                }
                notifyDataSetChanged();
                refreshEditor();
                return;
            });
            holder.expand.setOnClickListener(v -> {
                holder.fill = !holder.fill;
                holder.card.setScaleType(holder.fill
                        ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            });
        }

        @Override
        public int getItemCount() {
            return selectedUris.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView card;
            final View remove;
            final View expand;
            boolean fill = true;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.iv_editor_card);
                remove = itemView.findViewById(R.id.btn_editor_remove);
                expand = itemView.findViewById(R.id.btn_editor_expand);
            }
        }
    }

    private class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_filter_preset, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ColorMatrixPresets.FilterPreset preset = filterPresets.get(position);
            holder.name.setText(preset.name);
            if (!selectedUris.isEmpty()) {
                Glide.with(CreatePostActivity.this).load(selectedUris.get(0))
                        .centerCrop().into(holder.thumb);
            }
            if (position == 0) {
                holder.thumb.clearColorFilter();
            } else {
                holder.thumb.setColorFilter(preset.getColorFilter(1f));
            }
            holder.border.setVisibility(position == selectedFilter ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (position == selectedFilter) {
                    // The intensity slider does not exist in this codebase
                    ToastUtil.showNotAvailable(CreatePostActivity.this);
                    return;
                }
                selectedFilter = position;
                notifyDataSetChanged();
                editorCardAdapter.notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return filterPresets.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView name;
            final View border;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.iv_filter_thumb);
                name = itemView.findViewById(R.id.tv_filter_name);
                border = itemView.findViewById(R.id.view_filter_border);
            }
        }
    }

    private class DetailsPhotoAdapter extends RecyclerView.Adapter<DetailsPhotoAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_details_photo, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Uri uri = selectedUris.get(position);
            Glide.with(CreatePostActivity.this).load(uri).centerCrop().into(holder.photo);
            if (selectedFilter != 0 && selectedFilter < filterPresets.size()) {
                holder.photo.setColorFilter(filterPresets.get(selectedFilter).getColorFilter(1f));
            } else {
                holder.photo.clearColorFilter();
            }
        }

        @Override
        public int getItemCount() {
            return selectedUris.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView photo;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                photo = itemView.findViewById(R.id.iv_details_photo);
            }
        }
    }

    /** Keeps the gallery columns equal while leaving a gap between the cells. */
    private static class GridGapDecoration extends RecyclerView.ItemDecoration {
        private final int spanCount;
        private final int gap;

        GridGapDecoration(int spanCount, int gap) {
            this.spanCount = spanCount;
            this.gap = gap;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) return;
            int column = position % spanCount;
            outRect.left = gap * column / spanCount;
            outRect.right = gap * (spanCount - 1 - column) / spanCount;
            outRect.bottom = gap;
        }
    }

    /** Fixed gap between the editor cards. */
    private static class GapDecoration extends RecyclerView.ItemDecoration {
        private final int gap;

        GapDecoration(int gap) {
            this.gap = gap;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.right = gap;
        }
    }

    // ------------------------------------------------------------------
    // Existing pick / upload path (unchanged)
    // ------------------------------------------------------------------

    private void setupPickers() {
        pickMultipleMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(Constants.MAX_CAROUSEL_IMAGES),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        selectedUris.clear();
                        selectedUris.addAll(uris);
                        onMediaSelected();
                    }
                }
        );

        pickMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        selectedUris.clear();
                        selectedUris.add(uri);
                        onMediaSelected();
                    }
                }
        );
    }

    private void onMediaSelected() {
        if (selectedUris.isEmpty()) return;
        setPreview(selectedUris.get(0));
        updatePickerActions();
        pickerAdapter.notifyDataSetChanged();
        updateShareState();
    }

    /** Share is live as soon as there is at least one photo and nothing is uploading. */
    private void updateShareState() {
        boolean enabled = !selectedUris.isEmpty();
        btnPublish.setEnabled(enabled);
        btnPublish.setAlpha(enabled ? 1f : 0.4f);
    }

    private void publishPost() {
        if (selectedUris.isEmpty() || currentUserId == null) {
            toast("Please choose a photo after signing in.");
            return;
        }

        setLoading(true);
        uploadedPaths.clear();
        uploadedPublicUrls.clear();

        uploadMediaSequentially(0);
    }

    private void uploadMediaSequentially(int index) {
        if (index >= selectedUris.size()) {
            // All images uploaded; now create post
            createPostRecord();
            return;
        }

        Uri uri = selectedUris.get(index);
        byte[] jpegBytes;
        try {
            jpegBytes = ImageUtils.processUriToJpegBytes(this, uri);
        } catch (IOException e) {
            Log.e(TAG_API, "Failed to process image: " + e.getMessage(), e);
            fail("Failed to process image: " + e.getMessage());
            return;
        }

        if (jpegBytes == null || jpegBytes.length == 0) {
            fail("Image processing produced empty byte array");
            return;
        }

        String path = currentUserId + "/" + UUID.randomUUID() + ".jpg";
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), jpegBytes);
        Log.d(TAG_API, "uploaded " + jpegBytes.length + " bytes to posts/" + path);

        storageApi.uploadFile("posts", path, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "HTTP " + response.code() + " on uploadFile 'posts/" + path + "': " + err);
                    String msg;
                    if (response.code() == 403) msg = "403 Forbidden: Bucket policy error or invalid path.";
                    else if (response.code() == 404) msg = "404 Not Found: Bucket 'posts' does not exist.";
                    else if (response.code() == 401) msg = "401 Unauthorized: Invalid token.";
                    else msg = "Upload failed (HTTP " + response.code() + "): " + err;
                    fail(msg);
                    return;
                }

                String publicUrl = com.insta.clone.util.MediaUrl.normalize("posts", getPublicUrl("posts", path));
                Log.d(TAG_API, "HTTP 200 uploadFile posts/" + path + ". Verifying via HEAD...");

                okhttp3.Request headReq = new okhttp3.Request.Builder()
                        .url(publicUrl)
                        .head()
                        .build();

                okHttpClient.newCall(headReq).enqueue(new okhttp3.Callback() {
                    @Override
                    public void onResponse(@NonNull okhttp3.Call c, @NonNull okhttp3.Response headResponse) throws IOException {
                        int headCode = headResponse.code();
                        String contentType = headResponse.header("Content-Type");
                        String lenStr = headResponse.header("Content-Length");
                        long contentLength = -1;
                        try {
                            if (lenStr != null) {
                                contentLength = Long.parseLong(lenStr);
                            }
                        } catch (NumberFormatException ignored) {}
                        headResponse.close();

                        Log.d(TAG_API, "HEAD status=" + headCode + " Content-Type=" + contentType
                                + " Content-Length=" + contentLength + " expected=" + jpegBytes.length + " for " + publicUrl);

                        if (headCode == 200) {
                            runOnUiThread(() -> {
                                uploadedPaths.add(path);
                                uploadedPublicUrls.add(publicUrl);
                                verifyUrlWithGlide(publicUrl);
                                uploadMediaSequentially(index + 1);
                            });
                        } else {
                            runOnUiThread(() -> fail("HEAD verification failed (HTTP " + headCode + ") for " + publicUrl));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull okhttp3.Call c, @NonNull IOException e) {
                        Log.e(TAG_API, "HEAD verification failed: " + e.getMessage(), e);
                        runOnUiThread(() -> fail("HEAD verification failed: " + e.getMessage()));
                    }
                });
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "Network failure uploading image: " + t.getMessage(), t);
                fail("Network failure uploading image: " + t.getMessage());
            }
        });
    }

    private void verifyUrlWithGlide(String url) {
        Glide.with(getApplicationContext())
                .load(url)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG_GLIDE, "Failed to load public image from storage: " + url + " - " + e);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        Log.d(TAG_GLIDE, "Successfully loaded public image with Glide: " + url);
                        return false;
                    }
                })
                .preload();
    }

    private void createPostRecord() {
        if (uploadedPublicUrls.isEmpty()) {
            fail("No uploaded images found.");
            return;
        }

        String caption = etCaption != null ? etCaption.getText().toString().trim() : "";
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", currentUserId);
        body.put("caption", caption);
        body.put("thumbnail_url", uploadedPublicUrls.get(0));
        body.put("is_reel", false);

        restApi.createPost(body).enqueue(new Callback<List<PostDto>>() {
            @Override
            public void onResponse(Call<List<PostDto>> call, Response<List<PostDto>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "HTTP " + response.code() + " on createPost: " + err);
                    fail("Failed to create post (HTTP " + response.code() + "): " + err);
                    return;
                }

                String postId = response.body().get(0).getId();
                Log.d(TAG_API, "Created post with ID: " + postId);
                insertPostMedia(postId);
            }

            @Override
            public void onFailure(Call<List<PostDto>> call, Throwable t) {
                Log.e(TAG_API, "Network failure creating post: " + t.getMessage(), t);
                fail("Network error creating post: " + t.getMessage());
            }
        });
    }

    private void insertPostMedia(String postId) {
        List<Map<String, Object>> mediaList = new ArrayList<>();
        for (int i = 0; i < uploadedPublicUrls.size(); i++) {
            Map<String, Object> m = new HashMap<>();
            m.put("post_id", postId);
            m.put("url", uploadedPublicUrls.get(i));
            m.put("position", i);
            mediaList.add(m);
        }

        restApi.insertPostMedia(mediaList).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    String err = parseError(response);
                    Log.e(TAG_API, "HTTP " + response.code() + " on insertPostMedia: " + err);
                    fail("Failed to attach post media (HTTP " + response.code() + "): " + err);
                    return;
                }

                Log.d(TAG_API, "HTTP 200: Post media attached successfully.");
                setLoading(false);
                toast("Post shared!");
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "Network failure attaching media: " + t.getMessage(), t);
                fail("Network error attaching media: " + t.getMessage());
            }
        });
    }

    private String getPublicUrl(String bucket, String path) {
        String base = BuildConfig.SUPABASE_URL.trim();
        if (!base.endsWith("/")) base += "/";
        return base + "storage/v1/object/public/" + bucket + "/" + path;
    }

    private void fail(String message) {
        setLoading(false);
        // Clean up leftovers
        for (String p : uploadedPaths) {
            storageApi.deleteFile("posts", p).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {}
                @Override public void onFailure(Call<Void> c, Throwable t) {}
            });
        }
        uploadedPaths.clear();
        uploadedPublicUrls.clear();
        toast(message);
    }

    private void setLoading(boolean loading) {
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        // 40% alpha while uploading
        boolean enabled = !loading && !selectedUris.isEmpty();
        btnPublish.setEnabled(enabled);
        btnPublish.setAlpha(enabled ? 1f : 0.4f);
        View pickImage = findViewById(R.id.btn_pick_image);
        if (pickImage != null) pickImage.setEnabled(!loading);
    }

    private String parseError(Response<?> response) {
        try {
            if (response.errorBody() != null) return response.errorBody().string();
        } catch (Exception ignored) {}
        return "Unknown error";
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
```

---

# DESIGN CHECKLIST — PAGE 9 (POST EDITOR)

`1dp = 2.75px` on the verification device (1080 x 2400 @ 440dpi = 393dp wide). Bounds below
come from `adb shell uiautomator dump`.

| # | Requirement | Implementation | Status / measured evidence |
|---|---|---|---|
| 1 | Full-screen `#000000` | `create_step_editor` root `@color/black` | PASS — black in both device themes |
| 2 | `ic_close` 28dp white, 16dp start, 8dp below the status bar | `editor_icon` 28dp, `editor_top_bar_padding_horizontal` 16dp, `editor_top_bar_padding_top` 8dp | PASS — `btn_editor_close` `[44,132]-[121,209]` = **28.0dp** at 16dp |
| 3 | Top-right three white 28dp icons 20dp apart: `ic_filters` (opens the filter strip), `ic_gallery` (add more photos, existing picker), an 'Aa' icon (decorative) | `btn_editor_filters`, `btn_editor_gallery`, `btn_editor_aa` + `editor_icon_gap` 20dp | PASS — filters **28.0dp** `[695,132]`, gallery **28.0dp** `[827,132]` (gap 55px = 20dp), 'Aa' **28.0dp** `[959,132]` (gap 20dp), right edge 1036 = 16dp; filters selects the strip, gallery opens the existing picker, 'Aa' toasts |
| 4 | Centre: image card(s) in a horizontal pager; card width 88% of the screen, aspect 4:5, radius 12dp, next card peeking 12dp at the right | `rv_editor_cards` (horizontal + `PagerSnapHelper`), `EditorCardAdapter` sizes each card from `widthPixels * 0.88f` × 5/4, `ShapeAppearance.Insta.EditorCard` (12dp), `GapDecoration` 12dp | PASS — card `[44,231]-[994,1419]` = **345.5 x 432.0dp** (345.5/392.7 = **88%**, 432/345.5 = **1.25** = 4:5); the next card peeks at the right edge |
| 5 | Each card: 24dp `#99000000` circle with a white `ic_close` 14dp at its top-right (removes that image) | `btn_editor_remove` `editor_card_close_circle` 24dp + `bg_picker_overlay_circle`, `editor_card_inset` 8dp | PASS — **24.0dp** `[906,253]`, 8dp inside the card corner; tapping removes the image and, when the last one goes, returns to the picker |
| 6 | 36dp `#99000000` circle with `ic_expand` at its bottom-left (toggles fit/fill) | `btn_editor_expand` `picker_overlay_circle` 36dp | PASS — **36.0dp** `[66,1298]` (8dp inset); toggles `CENTER_CROP` ⇄ `FIT_CENTER` |
| 7 | Text row 'Filter \| Edit \| Crop' 13sp Bold caps white, selected underlined | `TextAppearance.Insta.EditorTab` + a 2dp underline per tab (`view_tab_underline_*`) | PASS — labels 13sp caps, `view_tab_underline_filter` `[44,1902]-[155,1908]` = **2.2dp** white under FILTER |
| 8 | Filter strip: items 76dp wide, thumbnail 72dp square radius 8dp (selected = 2dp white border), name 12sp white below, first item 'Normal' | `rv_filter_strip` + `FilterAdapter` from the **existing** `ColorMatrixPresets.getPresets()`; `item_filter_preset` (76/72dp + `bg_filter_selected_border`) | PASS — strip **96.0dp** tall, items **76dp**, thumbs **72.0dp**, names 12sp, 'Normal' first (then Clarendon, Gingham, Moon, Lark, …) with the 2dp border on the selection |
| 9 | Tapping the selected filter opens the existing intensity slider | none exists in this codebase | PARTIAL — the presets and their real `getColorFilter(intensity)` matrices are wired, but there is no intensity slider UI, so tapping the already-selected preset shows the shared "Not available" toast (NOTES 1) |
| 10 | 'Edit' shows the existing sliders in the same dark style | no slider panel exists | PARTIAL — decorative toast; the tab does not steal the selection state |
| 11 | 'Crop' opens the existing crop | there is no crop feature anywhere in the app | PARTIAL — decorative toast |
| 12 | Bottom-left 44dp rounded-8dp gallery thumbnail with a small + (add more photos, existing picker) | `btn_editor_gallery_thumb` + `bg_editor_gallery_thumb` + the first selected photo + `ic_add` badge | PASS — **44.0dp** `[44,2213]`; opens the existing `PickMultipleVisualMedia` launcher |
| 13 | Bottom-right pill 'Next' + `ic_arrow_forward`, 40dp, radius 20dp, bg `#0095F6`, white 14sp Bold, 16dp horizontal padding | `btn_editor_next` + `bg_editor_next_pill` + `editor_next_*` tokens | PASS — `[799,2219]-[1036,2329]` = 86.2 x **40.0dp**, right edge 16dp; steps to the details screen |
| 14 | The music strip in the mockup is replaced by the filter strip | only the filter strip exists; no music row was added | PASS — 8 presets, each with visual proof of the applied colour matrix on its own thumbnail |

---

# NOTES, DECISIONS AND LIMITATIONS

### 1. Filter / Edit / Crop: what is real and what is decorative
The **filter strip is fully real**: it is fed by the existing
`ColorMatrixPresets.getPresets()` (Normal, Clarendon, Gingham, Moon, Lark, Reyes, Juno,
Slumber) and the chosen matrix is applied through `ImageView#setColorFilter(...)` to every
editor card and to the details photo strip, so both the thumbnails and the photos visibly
change. That is the same source the app already had before this task.

What does **not** exist anywhere in the codebase: an intensity slider, an Edit slider panel
and a crop screen. The brief calls them "existing", so those three taps follow the shared
decorative contract — "Not available" toast under `Constants.DECORATIVE_UI` — and no invented
UI was added. If you want any of them built, they are self-contained new panels and I can add
them in a follow-up.

### 2. Why the cards are sized in Java
The card width is a percentage of the screen (88%) with a 4:5 height, which XML alone cannot
express for a RecyclerView item, so `EditorCardAdapter` sets the item's `LayoutParams` from
`getResources().getDisplayMetrics().widthPixels * 0.88f` and `width * 5 / 4`. Both values are
derived from the screen, not hard-coded, and the 12dp card gap comes from
`@dimen/editor_card_gap`.

### 3. The card pager is not a ViewPager2
A horizontal RecyclerView with a `PagerSnapHelper` gives the same paged feel as the mockup
while keeping the item-level controls (remove / fit-fill) inside the card, and it avoids
touching the existing `ViewPager2` used by the Reels screen.

---

# BUILD AND DEVICE VERIFICATION

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home \
  ./gradlew :app:assembleDebug          # BUILD SUCCESSFUL
adb install -r app/build/outputs/apk/debug/app-debug.apk   # Success
```

Verified on device `10BE2S1U640002B` (1080 x 2400 @ 440dpi):

* Full flow walked end to end: Create sheet → Post → picker → select → **Next** → editor.
* Editor geometry measured for every element in the checklist (values above are dp).
* The card is exactly 88% wide and 4:5; the next card peeks at the right edge.
* Filter presets visibly change the card and the preset thumbnails; selecting another preset
  re-renders all cards.
* Remove (×) on a card drops that image from the flow; the fit/fill circle toggles scaling.
* 'Aa', Edit, Crop and the already-selected preset show the shared "Not available" pill.
* Next → details; the back arrow on the details screen returns to the editor with the photos
  and filter intact.
* Light and dark themes both checked — the editor stays black, as designed.
