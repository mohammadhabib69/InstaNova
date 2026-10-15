# PAGE 8 — CREATE SHEET + PICKER — changed files

**Design only.** No behaviour changes to ViewModels, repositories, API calls, models,
navigation targets or the view ids used by code. Only XML, drawables, styles and
presentation Java changed; every colour and size comes from a PAGE 0 token or from a new
PAGE 8 token added below.

**References:** Image 9 (sheet and picker) and Image 8 (picker). Phone frames, arrows and
annotations are ignored.

The create flow is now one activity with four steps swapped in place (picker → editor →
details → caption), so **no new navigation target is introduced**: `CreatePostActivity`,
`CreateStoryActivity` and `CreateReelActivity` are still the only create destinations.

Read order: tokens → drawables → layouts → Java, then the DESIGN CHECKLIST and the
NOTES + device verification at the end.

---

## 1. Token — global dimens (PAGE 8 block)

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

## 2. Token — global colors (PAGE 8 tokens)

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

    <!-- ============================================================= -->
    <!-- PAGE 8/9 — CREATE FLOW (always-dark screens)                   -->
    <!-- ============================================================= -->
    <!-- Centred POST / STORY / REEL switch -->
    <color name="color_scrim_strong">#CC000000</color>
    <!-- 40% white crop grid drawn over the picker/editor preview -->
    <color name="color_grid_line">#66FFFFFF</color>
    <!-- 50% dim over the selected thumbnail in single-select mode -->
    <color name="color_thumb_dim">#80000000</color>
</resources>
```

## 3. Token — strings (PAGE 8 strings)

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

## 4. Token — styles (PAGE 8 text appearances)

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

## 5. NEW color selector — picker Next enabled/disabled

**Path:** `app/src/main/res/color/selector_picker_action.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 8 — Picker 'Next' action: brand blue when enabled, PAGE 0 disabled
    blue while nothing is selected.
-->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/color_primary" android:state_enabled="true" />
    <item android:color="@color/color_primary_disabled" />
</selector>
```

## 6. NEW drawable — create sheet drag handle

**Path:** `app/src/main/res/drawable/bg_sheet_drag_handle.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 8 — Create sheet drag handle: 36x4dp, #A8A8A8, centred 8dp from the top.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="2dp" />
    <solid android:color="@color/color_icon_muted" />
</shape>
```

## 7. NEW drawable — SELECT MULTIPLE pill

**Path:** `app/src/main/res/drawable/bg_picker_pill.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 8 — Picker 'SELECT MULTIPLE' pill: surfaceVariant, fully rounded.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/picker_select_pill_radius" />
    <solid android:color="@color/color_surface_variant" />
</shape>
```

## 8. NEW drawable — POST/STORY/REEL pill

**Path:** `app/src/main/res/drawable/bg_picker_mode_pill.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 8 — floating POST / STORY / REEL switch: #CC000000, fully rounded.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/picker_mode_pill_radius" />
    <solid android:color="@color/color_scrim_strong" />
</shape>
```

## 9. NEW drawable — 36dp overlay circle

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

## 10. NEW drawable — selected index badge

**Path:** `app/src/main/res/drawable/bg_picker_selected_circle.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- PAGE 8 — multi-select index badge on a chosen thumbnail (brand blue). -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/color_primary" />
    <stroke
        android:width="@dimen/picker_thumb_badge_border"
        android:color="@color/white" />
</shape>
```

## 11. NEW drawable — unselected outline badge

**Path:** `app/src/main/res/drawable/bg_picker_rest_circle.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 8 — empty 22dp circle shown on the thumbnails that are not selected
    while multi-select is active: transparent fill, 1.5dp white outline.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/transparent" />
    <stroke
        android:width="@dimen/picker_thumb_badge_border_rest"
        android:color="@color/white" />
</shape>
```

## 12. NEW drawable — 50% thumb dim

**Path:** `app/src/main/res/drawable/bg_picker_thumb_dim.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 8 — 50% dim laid over the thumbnail that is currently selected in
    single-select mode.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/color_thumb_dim" />
</shape>
```

## 13. NEW drawable — 32dp surface circle

**Path:** `app/src/main/res/drawable/bg_picker_circle_surface.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 8 — 32dp circular button behind the camera / multi-select controls
    in the Recents row (surfaceVariant).
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/color_surface_variant" />
</shape>
```

## 14. NEW drawable — active multi-select circle

**Path:** `app/src/main/res/drawable/bg_picker_multi_active.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 8 — 'SELECT MULTIPLE' pill while multi-select is active: it collapses
    into a 32dp brand-blue circle holding a white icon.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/color_primary" />
</shape>
```

## 15. NEW drawable — ic_live

**Path:** `app/src/main/res/drawable/ic_live.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- PAGE 8 — Live: centre dot with broadcast arcs. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/color_text_primary"
        android:pathData="M12,10a2,2 0 1,0 0,4a2,2 0 1,0 0,-4z" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="@color/color_text_primary"
        android:strokeWidth="1.5"
        android:strokeLineCap="round"
        android:pathData="M8.2,8.2a5.4,5.4 0 0,0 0,7.6" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="@color/color_text_primary"
        android:strokeWidth="1.5"
        android:strokeLineCap="round"
        android:pathData="M15.8,8.2a5.4,5.4 0 0,1 0,7.6" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="@color/color_text_primary"
        android:strokeWidth="1.5"
        android:strokeLineCap="round"
        android:pathData="M5.4,5.4a9.3,9.3 0 0,0 0,13.2" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="@color/color_text_primary"
        android:strokeWidth="1.5"
        android:strokeLineCap="round"
        android:pathData="M18.6,5.4a9.3,9.3 0 0,1 0,13.2" />
</vector>
```

## 16. NEW drawable — ic_sparkle

**Path:** `app/src/main/res/drawable/ic_sparkle.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- PAGE 8 — Made for you: four-point sparkle with a smaller companion. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/color_text_primary"
        android:pathData="M9.5,3.5l1.7,4.6l4.6,1.7l-4.6,1.7l-1.7,4.6l-1.7,-4.6l-4.6,-1.7l4.6,-1.7z" />
    <path
        android:fillColor="@color/color_text_primary"
        android:pathData="M17,13.5l0.9,2.4l2.4,0.9l-2.4,0.9l-0.9,2.4l-0.9,-2.4l-2.4,-0.9l2.4,-0.9z" />
</vector>
```

## 17. Layout — Create sheet

**Path:** `app/src/main/res/layout/sheet_create.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 8A — Create sheet, opened by the '+' on Home and Profile.
    Reel / Post / Story / Story highlight open the existing flows;
    Live and Made for you are decorative (Constants.DECORATIVE_UI).
-->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_bottom_sheet"
    android:orientation="vertical"
    android:paddingBottom="@dimen/text_title">

    <View
        android:layout_width="@dimen/create_sheet_handle_width"
        android:layout_height="@dimen/create_sheet_handle_height"
        android:layout_gravity="center_horizontal"
        android:layout_marginTop="@dimen/create_sheet_handle_margin_top"
        android:background="@drawable/bg_sheet_drag_handle" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:paddingTop="@dimen/create_sheet_title_padding_vertical"
        android:paddingBottom="@dimen/create_sheet_title_padding_vertical"
        android:text="@string/create_title"
        android:textColor="?attr/themeTextPrimary"
        android:textSize="@dimen/text_title"
        android:textStyle="bold" />

    <!-- Reel -->
    <LinearLayout
        android:id="@+id/row_create_reel"
        android:layout_width="match_parent"
        android:layout_height="@dimen/create_sheet_row_height"
        android:background="?attr/selectableItemBackground"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/create_sheet_row_padding_horizontal"
        android:paddingEnd="@dimen/create_sheet_row_padding_horizontal">

        <ImageView
            android:layout_width="@dimen/create_sheet_row_icon"
            android:layout_height="@dimen/create_sheet_row_icon"
            android:contentDescription="@string/create_reel"
            android:src="@drawable/ic_reels"
            app:tint="?attr/themeTextPrimary" />

        <TextView
            style="@style/TextAppearance.Insta.CreateRow"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/create_sheet_row_icon_gap"
            android:layout_weight="1"
            android:text="@string/create_reel" />
    </LinearLayout>

    <!-- Post -->
    <LinearLayout
        android:id="@+id/row_create_post"
        android:layout_width="match_parent"
        android:layout_height="@dimen/create_sheet_row_height"
        android:background="?attr/selectableItemBackground"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/create_sheet_row_padding_horizontal"
        android:paddingEnd="@dimen/create_sheet_row_padding_horizontal">

        <ImageView
            android:layout_width="@dimen/create_sheet_row_icon"
            android:layout_height="@dimen/create_sheet_row_icon"
            android:contentDescription="@string/create_post"
            android:src="@drawable/ic_grid"
            app:tint="?attr/themeTextPrimary" />

        <TextView
            style="@style/TextAppearance.Insta.CreateRow"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/create_sheet_row_icon_gap"
            android:layout_weight="1"
            android:text="@string/create_post" />
    </LinearLayout>

    <!-- Story -->
    <LinearLayout
        android:id="@+id/row_create_story"
        android:layout_width="match_parent"
        android:layout_height="@dimen/create_sheet_row_height"
        android:background="?attr/selectableItemBackground"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/create_sheet_row_padding_horizontal"
        android:paddingEnd="@dimen/create_sheet_row_padding_horizontal">

        <ImageView
            android:layout_width="@dimen/create_sheet_row_icon"
            android:layout_height="@dimen/create_sheet_row_icon"
            android:contentDescription="@string/create_story"
            android:src="@drawable/ic_add"
            app:tint="?attr/themeTextPrimary" />

        <TextView
            style="@style/TextAppearance.Insta.CreateRow"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/create_sheet_row_icon_gap"
            android:layout_weight="1"
            android:text="@string/create_story" />
    </LinearLayout>

    <!-- Story highlight -->
    <LinearLayout
        android:id="@+id/row_create_highlight"
        android:layout_width="match_parent"
        android:layout_height="@dimen/create_sheet_row_height"
        android:background="?attr/selectableItemBackground"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/create_sheet_row_padding_horizontal"
        android:paddingEnd="@dimen/create_sheet_row_padding_horizontal">

        <ImageView
            android:layout_width="@dimen/create_sheet_row_icon"
            android:layout_height="@dimen/create_sheet_row_icon"
            android:contentDescription="@string/create_story_highlight"
            android:src="@drawable/ic_highlight"
            app:tint="?attr/themeTextPrimary" />

        <TextView
            style="@style/TextAppearance.Insta.CreateRow"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/create_sheet_row_icon_gap"
            android:layout_weight="1"
            android:text="@string/create_story_highlight" />
    </LinearLayout>

    <!-- Live (decorative) -->
    <LinearLayout
        android:id="@+id/row_create_live"
        android:layout_width="match_parent"
        android:layout_height="@dimen/create_sheet_row_height"
        android:background="?attr/selectableItemBackground"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/create_sheet_row_padding_horizontal"
        android:paddingEnd="@dimen/create_sheet_row_padding_horizontal">

        <ImageView
            android:layout_width="@dimen/create_sheet_row_icon"
            android:layout_height="@dimen/create_sheet_row_icon"
            android:contentDescription="@string/create_live"
            android:src="@drawable/ic_live"
            app:tint="?attr/themeTextPrimary" />

        <TextView
            style="@style/TextAppearance.Insta.CreateRow"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/create_sheet_row_icon_gap"
            android:layout_weight="1"
            android:text="@string/create_live" />
    </LinearLayout>

    <!-- Made for you (decorative) -->
    <LinearLayout
        android:id="@+id/row_create_made_for_you"
        android:layout_width="match_parent"
        android:layout_height="@dimen/create_sheet_row_height"
        android:background="?attr/selectableItemBackground"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/create_sheet_row_padding_horizontal"
        android:paddingEnd="@dimen/create_sheet_row_padding_horizontal">

        <ImageView
            android:layout_width="@dimen/create_sheet_row_icon"
            android:layout_height="@dimen/create_sheet_row_icon"
            android:contentDescription="@string/create_made_for_you"
            android:src="@drawable/ic_sparkle"
            app:tint="?attr/themeTextPrimary" />

        <TextView
            style="@style/TextAppearance.Insta.CreateRow"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/create_sheet_row_icon_gap"
            android:layout_weight="1"
            android:text="@string/create_made_for_you" />
    </LinearLayout>

</LinearLayout>
```

## 18. Layout — create flow host (4 steps)

**Path:** `app/src/main/res/layout/activity_create_post.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    Create flow host (PAGE 8 → 10). One activity, four steps swapped in place,
    so no new navigation target is introduced:

      step_picker  — PAGE 8B, the picker (also opened by the Create tab)
      step_editor  — PAGE 9,  the post editor behind 'Next'
      step_details — PAGE 10A, the new post details behind the editor 'Next'
      step_caption — PAGE 10B, the caption screen

    All ids consumed by CreatePostActivity are preserved: iv_preview,
    btn_pick_image, et_caption, btn_publish and progress.
-->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/black">

    <include
        android:id="@+id/step_picker"
        layout="@layout/create_step_picker" />

    <include
        android:id="@+id/step_editor"
        layout="@layout/create_step_editor" />

    <include
        android:id="@+id/step_details"
        layout="@layout/create_step_details" />

    <include
        android:id="@+id/step_caption"
        layout="@layout/create_step_caption" />

    <ProgressBar
        android:id="@+id/progress"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:visibility="gone" />

</FrameLayout>
```

## 19. Layout — picker step

**Path:** `app/src/main/res/layout/create_step_picker.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 8B — Picker (step 1 of the create flow).

    Full-screen always-dark screen: 56dp bar, 1:1 preview of the current
    selection, the Recents tool row, the 4-column gallery grid and the
    floating POST / STORY / REEL switch.

    iv_preview and btn_pick_image keep their ids so the existing Glide preview
    and the existing system picker launcher stay wired.
-->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/black">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <!-- 56dp bar: close | 'New post' | Next -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="@dimen/picker_top_bar_height"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingStart="@dimen/picker_top_bar_padding_horizontal"
            android:paddingEnd="@dimen/picker_top_bar_padding_horizontal">

            <ImageView
                android:id="@+id/btn_picker_close"
                android:layout_width="@dimen/picker_close_icon"
                android:layout_height="@dimen/picker_close_icon"
                android:contentDescription="@string/not_available"
                android:src="@drawable/ic_close"
                app:tint="@color/white" />

            <TextView
                android:id="@+id/tv_picker_title"
                style="@style/TextAppearance.Insta.PickerBar"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="@dimen/create_sheet_row_icon_gap"
                android:text="@string/picker_new_post" />

            <View
                android:layout_width="0dp"
                android:layout_height="1dp"
                android:layout_weight="1" />

            <TextView
                android:id="@+id/btn_picker_next"
                style="@style/TextAppearance.Insta.PickerBar"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="?attr/selectableItemBackground"
                android:enabled="false"
                android:text="@string/picker_next"
                android:textColor="@color/selector_picker_action" />
        </LinearLayout>

        <!-- Full-width 1:1 preview of the current selection -->
        <FrameLayout
            android:id="@+id/layout_picker_preview"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <!-- Kept visible so the 1:1 area is reserved before a pick -->
            <com.insta.clone.ui.widget.SquareImageView
                android:id="@+id/iv_preview"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="@color/black"
                android:contentDescription="@string/not_available"
                android:scaleType="centerCrop" />

            <!-- 3x3 rule-of-thirds guides, shown while dragging / zooming -->
            <androidx.constraintlayout.widget.ConstraintLayout
                android:id="@+id/layout_picker_grid_lines"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:visibility="gone">

                <androidx.constraintlayout.widget.Guideline
                    android:id="@+id/guideline_picker_v1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    app:layout_constraintGuide_percent="0.3333" />

                <androidx.constraintlayout.widget.Guideline
                    android:id="@+id/guideline_picker_v2"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    app:layout_constraintGuide_percent="0.6666" />

                <androidx.constraintlayout.widget.Guideline
                    android:id="@+id/guideline_picker_h1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    app:layout_constraintGuide_percent="0.3333" />

                <androidx.constraintlayout.widget.Guideline
                    android:id="@+id/guideline_picker_h2"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    app:layout_constraintGuide_percent="0.6666" />

                <View
                    android:layout_width="@dimen/picker_grid_line_width"
                    android:layout_height="0dp"
                    android:background="@color/color_grid_line"
                    app:layout_constraintBottom_toBottomOf="parent"
                    app:layout_constraintEnd_toEndOf="@id/guideline_picker_v1"
                    app:layout_constraintTop_toTopOf="parent" />

                <View
                    android:layout_width="@dimen/picker_grid_line_width"
                    android:layout_height="0dp"
                    android:background="@color/color_grid_line"
                    app:layout_constraintBottom_toBottomOf="parent"
                    app:layout_constraintEnd_toEndOf="@id/guideline_picker_v2"
                    app:layout_constraintTop_toTopOf="parent" />

                <View
                    android:layout_width="0dp"
                    android:layout_height="@dimen/picker_grid_line_width"
                    android:background="@color/color_grid_line"
                    app:layout_constraintBottom_toBottomOf="@id/guideline_picker_h1"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent" />

                <View
                    android:layout_width="0dp"
                    android:layout_height="@dimen/picker_grid_line_width"
                    android:background="@color/color_grid_line"
                    app:layout_constraintBottom_toBottomOf="@id/guideline_picker_h2"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent" />
            </androidx.constraintlayout.widget.ConstraintLayout>

            <!-- 36dp fit / fill toggle -->
            <ImageView
                android:id="@+id/btn_picker_expand"
                android:layout_width="@dimen/picker_overlay_circle"
                android:layout_height="@dimen/picker_overlay_circle"
                android:layout_gravity="bottom|start"
                android:layout_margin="@dimen/picker_overlay_margin"
                android:background="@drawable/bg_picker_overlay_circle"
                android:contentDescription="@string/picker_fit_toggle"
                android:padding="@dimen/picker_overlay_padding"
                android:src="@drawable/ic_expand"
                app:tint="@color/white" />
        </FrameLayout>

        <!-- Recents tool row -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="@dimen/picker_recents_row_height"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingStart="@dimen/picker_top_bar_padding_horizontal"
            android:paddingEnd="@dimen/picker_top_bar_padding_horizontal">

            <LinearLayout
                android:id="@+id/btn_picker_album"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/picker_recents"
                    android:textColor="@color/white"
                    android:textSize="@dimen/text_title"
                    android:textStyle="bold" />

                <ImageView
                    android:layout_width="@dimen/profile_chevron_size"
                    android:layout_height="@dimen/profile_chevron_size"
                    android:layout_marginStart="@dimen/picker_row_gap"
                    android:contentDescription="@string/picker_album_source"
                    android:src="@drawable/ic_chevron_down"
                    app:tint="@color/white" />
            </LinearLayout>

            <View
                android:layout_width="0dp"
                android:layout_height="1dp"
                android:layout_weight="1" />

            <!-- SELECT MULTIPLE pill -->
            <LinearLayout
                android:id="@+id/btn_picker_multi"
                android:layout_width="wrap_content"
                android:layout_height="@dimen/picker_select_pill_height"
                android:background="@drawable/bg_picker_pill"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingStart="@dimen/picker_select_pill_padding_horizontal"
                android:paddingEnd="@dimen/picker_select_pill_padding_horizontal">

                <ImageView
                    android:id="@+id/iv_multi_icon"
                    android:layout_width="@dimen/picker_select_pill_icon"
                    android:layout_height="@dimen/picker_select_pill_icon"
                    android:contentDescription="@string/picker_select_multiple"
                    android:src="@drawable/ic_multi_image"
                    app:tint="?attr/themeTextPrimary" />

                <TextView
                    android:id="@+id/tv_multi_label"
                    style="@style/TextAppearance.Insta.PickerPill"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="@dimen/picker_row_gap"
                    android:text="@string/picker_select_multiple" />
            </LinearLayout>

            <!-- Existing camera / photo-source flow -->
            <ImageView
                android:id="@+id/btn_pick_image"
                android:layout_width="@dimen/picker_circle_button"
                android:layout_height="@dimen/picker_circle_button"
                android:layout_marginStart="@dimen/picker_recents_gap"
                android:background="@drawable/bg_picker_circle_surface"
                android:contentDescription="@string/inbox_camera"
                android:padding="@dimen/picker_circle_button_padding"
                android:src="@drawable/ic_camera"
                app:tint="?attr/themeTextPrimary" />
        </LinearLayout>

        <!-- 4-column gallery grid -->
        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rv_picker_grid"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:overScrollMode="never" />
    </LinearLayout>

    <!-- Floating POST / STORY / REEL switch -->
    <LinearLayout
        android:id="@+id/layout_picker_mode"
        android:layout_width="wrap_content"
        android:layout_height="@dimen/picker_mode_pill_height"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="@dimen/picker_mode_margin_bottom"
        android:background="@drawable/bg_picker_mode_pill"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/picker_mode_pill_padding_horizontal"
        android:paddingEnd="@dimen/picker_mode_pill_padding_horizontal">

        <TextView
            android:id="@+id/tv_mode_post"
            style="@style/TextAppearance.Insta.PickerMode"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:letterSpacing="0.1"
            android:text="@string/picker_mode_post"
            android:textColor="@color/white" />

        <TextView
            android:id="@+id/tv_mode_story"
            style="@style/TextAppearance.Insta.PickerMode"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/picker_mode_pill_gap"
            android:letterSpacing="0.1"
            android:text="@string/picker_mode_story" />

        <TextView
            android:id="@+id/tv_mode_reel"
            style="@style/TextAppearance.Insta.PickerMode"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/picker_mode_pill_gap"
            android:letterSpacing="0.1"
            android:text="@string/picker_mode_reel" />
    </LinearLayout>

</FrameLayout>
```

## 20. Layout — gallery tile

**Path:** `app/src/main/res/layout/item_picker_thumb.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 8B — one gallery tile: square thumbnail plus the selection chrome.
    Single mode: the chosen tile is dimmed 50%.
    Multi mode: the chosen tile shows a 22dp blue circle with its index,
    the others show an empty 22dp circle with a 1.5dp white outline.
-->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <com.insta.clone.ui.widget.SquareImageView
        android:id="@+id/iv_thumb"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:contentDescription="@string/not_available"
        android:scaleType="centerCrop" />

    <!-- 50% dim while this tile is the single selection -->
    <View
        android:id="@+id/view_thumb_dim"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@drawable/bg_picker_thumb_dim"
        android:visibility="gone" />

    <!-- Multi-select index badge -->
    <TextView
        android:id="@+id/tv_thumb_index"
        android:layout_width="@dimen/picker_thumb_badge"
        android:layout_height="@dimen/picker_thumb_badge"
        android:layout_gravity="top|end"
        android:layout_margin="@dimen/picker_recents_gap"
        android:background="@drawable/bg_picker_selected_circle"
        android:gravity="center"
        android:textColor="@color/white"
        android:textSize="@dimen/picker_thumb_badge_text_size"
        android:textStyle="bold"
        android:visibility="gone" />

    <!-- Empty outline shown on the unselected tiles in multi mode -->
    <View
        android:id="@+id/view_thumb_ring"
        android:layout_width="@dimen/picker_thumb_badge"
        android:layout_height="@dimen/picker_thumb_badge"
        android:layout_gravity="top|end"
        android:layout_margin="@dimen/picker_recents_gap"
        android:background="@drawable/bg_picker_rest_circle"
        android:visibility="gone" />

</FrameLayout>
```

## 21. Java — CreateSheet (new)

**Path:** `app/src/main/java/com/insta/clone/ui/create/CreateSheet.java`

```java
package com.insta.clone.ui.create;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.clone.R;
import com.insta.clone.util.Constants;
import com.insta.clone.util.ToastUtil;

/**
 * PAGE 8A — the Create sheet.
 *
 * Presentation only: every row launches an existing flow, so there is no new
 * navigation target. Story highlight / Live / Made for you have no backing
 * feature in this codebase and follow the shared Constants.DECORATIVE_UI
 * contract (visible + "Not available" toast, hidden when the flag is off).
 */
public final class CreateSheet {

    private CreateSheet() {}

    public static void show(Context context) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.InstagramBottomSheetDialog);
        View sheet = LayoutInflater.from(context).inflate(R.layout.sheet_create, null);
        dialog.setContentView(sheet);

        // Existing flows
        View reel = sheet.findViewById(R.id.row_create_reel);
        View post = sheet.findViewById(R.id.row_create_post);
        View story = sheet.findViewById(R.id.row_create_story);
        View highlight = sheet.findViewById(R.id.row_create_highlight);

        reel.setOnClickListener(v -> {
            dialog.dismiss();
            context.startActivity(new Intent(context, CreateReelActivity.class));
        });

        post.setOnClickListener(v -> {
            dialog.dismiss();
            context.startActivity(new Intent(context, CreatePostActivity.class));
        });

        story.setOnClickListener(v -> {
            dialog.dismiss();
            context.startActivity(new Intent(context, CreateStoryActivity.class));
        });

        // "Story highlight" — there is no New highlight flow in the app
        highlight.setOnClickListener(v -> ToastUtil.showNotAvailable(context));

        // Design-only rows
        int[] decorative = {R.id.row_create_live, R.id.row_create_made_for_you};
        for (int id : decorative) {
            View row = sheet.findViewById(id);
            if (row == null) continue;
            if (Constants.DECORATIVE_UI) {
                row.setVisibility(View.VISIBLE);
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    ToastUtil.showNotAvailable(context);
                });
            } else {
                row.setVisibility(View.GONE);
            }
        }

        dialog.show();
    }
}
```

## 22. Java — CreatePostActivity (picker step)

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

## 23. Java — HomeFragment (the + opens the sheet)

**Path:** `app/src/main/java/com/insta/clone/ui/home/HomeFragment.java`

```java
package com.insta.clone.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.clone.InstaApp;
import com.insta.clone.R;
import com.insta.clone.data.model.Post;
import com.insta.clone.data.model.StoryGroup;
import com.insta.clone.databinding.DialogPostOptionsBinding;
import com.insta.clone.databinding.FragmentHomeBinding;
import com.insta.clone.ui.activity.ActivityFragment;
import com.insta.clone.ui.chat.InboxActivity;
import com.insta.clone.ui.comment.CommentsBottomSheet;
import com.insta.clone.ui.create.CreateStoryActivity;
import com.insta.clone.ui.create.CreateSheet;
import com.insta.clone.ui.story.StoryViewerActivity;
import com.insta.clone.ui.auth.AuthActivity;
import com.insta.clone.data.repository.ChatRepository;
import com.insta.clone.util.Resource;
import java.util.Objects;

/**
 * Main Home feed screen hosting Story Tray, Feed Posts, and All Caught Up footer.
 */
public class HomeFragment extends Fragment implements FeedPostAdapter.OnPostInteractionListener, StoryTrayAdapter.OnStoryClickListener {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;

    private StoryTrayAdapter storyTrayAdapter;
    private FeedPostAdapter feedPostAdapter;
    private CaughtUpAdapter caughtUpAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // Wordmark styling
        binding.tvWordmark.setTypeface(InstaApp.getWordmarkTypeface(requireContext()));

        // PAGE 1: keep the 48dp home top bar clear of the status bar. The window is
        // edge-to-edge (PAGE 0), so only the top inset is applied here.
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.appBarLayout);

        // Header click listeners
        binding.btnNotifications.setOnClickListener(v -> {
            // Open activity screen
            ActivityFragment activityFragment = new ActivityFragment();
            activityFragment.show(getParentFragmentManager(), "ActivityFragment");
        });

        binding.btnDirect.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), InboxActivity.class);
            startActivity(intent);
        });
        // PAGE 8A — the '+' opens the Create sheet, whose rows launch the existing flows
        binding.btnCreate.setOnClickListener(v -> CreateSheet.show(requireContext()));

        setupRecyclerView();

        // Swipe-to-refresh
        binding.swipeRefresh.setColorSchemeResources(R.color.color_primary);
        binding.swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());

        // Observe ViewModel data
        viewModel.getIsRefreshing().observe(getViewLifecycleOwner(), refreshing ->
                { if (binding != null) binding.swipeRefresh.setRefreshing(Boolean.TRUE.equals(refreshing)); }
        );

        viewModel.getStoryGroups().observe(getViewLifecycleOwner(), groups ->
                { if (storyTrayAdapter != null) storyTrayAdapter.submitList(groups); }
        );

        viewModel.getPosts().observe(getViewLifecycleOwner(), posts ->
                { if (feedPostAdapter != null) feedPostAdapter.submitList(posts); if (binding != null) binding.emptyState.setVisibility(posts == null || posts.isEmpty() ? View.VISIBLE : View.GONE); }
        );

        viewModel.getIsAllCaughtUp().observe(getViewLifecycleOwner(), caughtUp ->
                { if (caughtUpAdapter != null) caughtUpAdapter.setVisible(Boolean.TRUE.equals(caughtUp)); }
        );

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.trim().isEmpty() && isAdded()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getSessionExpired().observe(getViewLifecycleOwner(), expired -> {
            if (Boolean.TRUE.equals(expired) && isAdded()) {
                Intent intent = new Intent(requireContext(), AuthActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                requireActivity().finish();
            }
        });

        // Start only after observers and adapters are ready. The ViewModel makes this idempotent.
        viewModel.loadInitial();
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        binding.rvFeed.setLayoutManager(layoutManager);

        // Horizontal Story Tray in a simple wrapping RecyclerView adapter
        storyTrayAdapter = new StoryTrayAdapter(this);
        StoryTrayHeaderAdapter headerAdapter = new StoryTrayHeaderAdapter(storyTrayAdapter);

        feedPostAdapter = new FeedPostAdapter(this);
        caughtUpAdapter = new CaughtUpAdapter();

        ConcatAdapter concatAdapter = new ConcatAdapter(headerAdapter, feedPostAdapter, caughtUpAdapter);
        binding.rvFeed.setAdapter(concatAdapter);

        // Keyset Pagination: trigger when 3 items from bottom
        binding.rvFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    int visibleCount = layoutManager.getChildCount();
                    int totalCount = layoutManager.getItemCount();
                    int pastVisible = layoutManager.findFirstVisibleItemPosition();

                    if (totalCount > 0 && (visibleCount + pastVisible) >= totalCount - 3) {
                        viewModel.loadMore();
                    }
                }
            }
        });
    }

    public void scrollToTop() {
        if (binding != null && binding.rvFeed != null) {
            binding.rvFeed.smoothScrollToPosition(0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.refresh();
        updateUnreadBadges();
    }

    private void updateUnreadBadges() {
        if (!isAdded() || getContext() == null) return;
        ChatRepository.getInstance(requireContext()).getUnreadCounts().observe(getViewLifecycleOwner(), res -> {
            if (binding != null && res != null && res.status == Resource.Status.SUCCESS && res.data != null) {
                int dms = res.data.getDirectMessages();
                int notifs = res.data.getNotifications();
                if (dms > 0) {
                    binding.tvDmBadge.setVisibility(View.VISIBLE);
                    binding.tvDmBadge.setText(dms > 99 ? "99+" : String.valueOf(dms));
                } else {
                    binding.tvDmBadge.setVisibility(View.GONE);
                }
                binding.dotActivityBadge.setVisibility(notifs > 0 ? View.VISIBLE : View.GONE);
            }
        });
    }

    // --- Story Tray Callbacks ---
    @Override
    public void onStoryClick(StoryGroup group, int position) {
        if (group == null || group.getUserId() == null || group.getUserId().trim().isEmpty() || !isAdded()) return;
        Intent intent = new Intent(requireContext(), StoryViewerActivity.class);
        intent.putExtra("user_id", group.getUserId());
        intent.putExtra("username", group.getUsername());
        startActivity(intent);
    }

    @Override
    public void onAddStoryClick() {
        if (!isAdded()) return;
        Intent intent = new Intent(requireContext(), CreateStoryActivity.class);
        startActivity(intent);
    }

    // --- Post Interaction Callbacks ---
    @Override
    public void onLikeClick(Post post, int position) {
        viewModel.toggleLike(post, position);
    }

    @Override
    public void onCommentClick(Post post) {
        if (post == null || post.getId() == null || post.getId().trim().isEmpty() || !isAdded()) return;
        CommentsBottomSheet sheet = CommentsBottomSheet.newInstance(post.getId(), post.getCommentsCount());
        sheet.show(getChildFragmentManager(), "CommentsBottomSheet");
    }

    @Override
    public void onShareClick(Post post) {
        if (post == null || !isAdded()) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out this post by @" + post.getUsername() + " on Instagram!");
        startActivity(Intent.createChooser(shareIntent, "Share Post"));
    }

    @Override
    public void onSaveClick(Post post, int position) {
        viewModel.toggleSave(post, position);
    }

    @Override
    public void onUserClick(String userId, String username) {
        // Switch to Profile tab or open user profile
        if (isAdded()) Toast.makeText(requireContext(), "Opening @" + (username == null ? "user" : username), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTagClick(String tag) {
        if (isAdded()) Toast.makeText(requireContext(), "Searching #" + (tag == null ? "" : tag), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onOptionsClick(Post post, int position) {
        if (post == null || !isAdded()) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.InstagramBottomSheetDialog);
        DialogPostOptionsBinding dialogBinding = DialogPostOptionsBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        boolean isMyPost = Objects.equals(post.getUserId(), viewModel.getCurrentUserId());

        if (isMyPost) {
            dialogBinding.btnDeletePost.setVisibility(View.VISIBLE);
            dialogBinding.btnDeletePost.setOnClickListener(v -> {
                dialog.dismiss();
                viewModel.deletePost(post);
                Toast.makeText(requireContext(), "Post deleted", Toast.LENGTH_SHORT).show();
            });
        } else {
            dialogBinding.btnUnfollow.setVisibility(View.VISIBLE);
            dialogBinding.btnUnfollow.setText("Unfollow @" + post.getUsername());
            dialogBinding.btnUnfollow.setOnClickListener(v -> {
                dialog.dismiss();
                viewModel.unfollowUser(post.getUserId());
                Toast.makeText(requireContext(), "Unfollowed @" + post.getUsername(), Toast.LENGTH_SHORT).show();
            });
        }

        dialogBinding.btnSharePost.setOnClickListener(v -> {
            dialog.dismiss();
            onShareClick(post);
        });

        dialogBinding.btnCopyLink.setOnClickListener(v -> {
            dialog.dismiss();
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Post Link", "https://instagram.com/p/" + post.getId());
            if (cm != null) cm.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    @Override
    public void onDestroyView() {
        if (binding != null) {
            binding.rvFeed.clearOnScrollListeners();
            binding.rvFeed.setAdapter(null);
        }
        storyTrayAdapter = null;
        feedPostAdapter = null;
        caughtUpAdapter = null;
        super.onDestroyView();
        binding = null;
    }

    /**
     * Header wrapper adapter holding horizontal Story Tray inside ConcatAdapter.
     */
    static class StoryTrayHeaderAdapter extends RecyclerView.Adapter<StoryTrayHeaderAdapter.ViewHolder> {
        private final StoryTrayAdapter trayAdapter;

        StoryTrayHeaderAdapter(StoryTrayAdapter trayAdapter) {
            this.trayAdapter = trayAdapter;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            Resources res = context.getResources();

            RecyclerView rv = new RecyclerView(context);
            rv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    res.getDimensionPixelSize(R.dimen.story_tray_height)
            ));
            rv.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            rv.setClipToPadding(false);
            int gutter = res.getDimensionPixelSize(R.dimen.story_tray_padding_horizontal);
            rv.setPadding(gutter, 0, gutter, 0);
            rv.setAdapter(trayAdapter);

            // 1dp divider under the tray.
            View divider = new View(context);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    res.getDimensionPixelSize(R.dimen.divider_hairline)
            ));
            divider.setBackgroundColor(themeColor(context, R.attr.themeDivider, R.color.color_divider));

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            container.addView(rv);
            container.addView(divider);
            return new ViewHolder(container);
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {}
        @Override public int getItemCount() { return 1; }

        /** Resolves a PAGE 0 semantic theme attribute to a concrete color. */
        private static int themeColor(Context context, int attr, int fallbackColorRes) {
            try {
                TypedValue tv = new TypedValue();
                if (context.getTheme().resolveAttribute(attr, tv, true)) {
                    if (tv.resourceId != 0) {
                        return ContextCompat.getColor(context, tv.resourceId);
                    }
                    return tv.data;
                }
            } catch (Exception ignored) {
                // Fall through to the token default.
            }
            return ContextCompat.getColor(context, fallbackColorRes);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(@NonNull View itemView) { super(itemView); }
        }
    }
}
```

## 24. Java — ProfileFragment (the + opens the sheet)

**Path:** `app/src/main/java/com/insta/clone/ui/profile/ProfileFragment.java`

```java
package com.insta.clone.ui.profile;

import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.insta.clone.BuildConfig;
import com.insta.clone.R;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Post;
import com.insta.clone.data.model.Profile;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.api.StorageApi;
import com.insta.clone.data.remote.dto.ProfileDto;
import com.insta.clone.data.repository.PostRepository;
import com.insta.clone.data.repository.ProfileRepository;
import com.insta.clone.data.repository.ReelRepository;
import com.insta.clone.ui.create.CreateSheet;
import com.insta.clone.ui.post.PostDetailActivity;
import com.insta.clone.ui.settings.SettingsActivity;
import com.insta.clone.util.Constants;
import com.insta.clone.util.ImageUtils;
import com.insta.clone.util.Resource;
import com.insta.clone.util.ToastUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * User Profile Fragment with stats, bio, avatar upload, tabbed media grid, and Settings entry.
 */
public class ProfileFragment extends Fragment {

    private static final String TAG_API = "InstaApi";
    private static final String TAG_GLIDE = "InstaGlide";

    /** PAGE 2 tab positions: 0 = grid, 1 = reels, 2 = reposts, 3 = tagged. */
    private static final int TAB_REPOSTS = 2;
    private static final int TAB_TAGGED = 3;

    private ImageView ivAvatar;
    private TextView tvUsername, tvPostsCount, tvFollowersCount, tvFollowingCount, tvBio;
    private TextView tvDisplayName, tvEmptyTitle;
    private View profileEmptyState;
    private ImageView ivEmptyIcon;
    private TabLayout tabLayout;
    private RecyclerView rvMediaGrid;

    private ProfileRepository profileRepository;
    private PostRepository postRepository;
    private ReelRepository reelRepository;
    private StorageApi storageApi;
    private RestApi restApi;
    private okhttp3.OkHttpClient okHttpClient;
    private SessionManager sessionManager;
    private String currentUserId;

    private final List<Post> gridPosts = new ArrayList<>();
    private MediaGridAdapter gridAdapter;

    private ActivityResultLauncher<PickVisualMediaRequest> avatarPickerLauncher;

    public ProfileFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        ivAvatar = view.findViewById(R.id.iv_avatar);
        tvUsername = view.findViewById(R.id.tv_username);
        tvPostsCount = view.findViewById(R.id.tv_posts_count);
        tvFollowersCount = view.findViewById(R.id.tv_followers_count);
        tvFollowingCount = view.findViewById(R.id.tv_following_count);
        tvBio = view.findViewById(R.id.tv_bio);
        tvDisplayName = view.findViewById(R.id.tv_display_name);
        profileEmptyState = view.findViewById(R.id.profileEmptyState);
        ivEmptyIcon = view.findViewById(R.id.ivEmptyIcon);
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle);
        tabLayout = view.findViewById(R.id.tab_layout_profile);
        rvMediaGrid = view.findViewById(R.id.rv_media_grid);

        // PAGE 2: keep the 56dp top bar clear of the status bar (edge-to-edge window).
        // The inset goes on the fragment root so the bar keeps its 56dp height.
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);

        // PAGE 2: design-only elements -> "Not available" toast (DECORATIVE_UI).
        // hideWhenOff = the element itself is decorative in the reference design.
        wireDecorative(view.findViewById(R.id.ivChevronDown), true);
        wireDecorative(view.findViewById(R.id.btnAddPerson), true);
        wireDecorative(view.findViewById(R.id.layoutHighlightNew), true);
        wireDecorative(view.findViewById(R.id.btnEditProfile), false);
        wireDecorative(view.findViewById(R.id.btnShareProfile), false);
        wireDecorative(view.findViewById(R.id.btnFollow), false);
        wireDecorative(view.findViewById(R.id.btnMessage), false);

        sessionManager = SessionManager.getInstance(requireContext());
        currentUserId = sessionManager.getUserId();

        profileRepository = new ProfileRepository(requireContext());
        postRepository = new PostRepository(requireContext());
        reelRepository = new ReelRepository(requireContext());
        RetrofitClient client = RetrofitClient.getInstance(requireContext());
        storageApi = client.getStorageApi();
        restApi = client.getRestApi();
        okHttpClient = client.getOkHttpClient();

        // Top bar buttons
        View ivCreate = view.findViewById(R.id.iv_settings); // Add box icon
        if (ivCreate != null) {
            // PAGE 8A — the '+' opens the Create sheet, whose rows launch the existing flows
            ivCreate.setOnClickListener(v -> CreateSheet.show(requireContext()));
        }

        View ivMenu = view.findViewById(R.id.iv_menu);
        if (ivMenu != null) {
            ivMenu.setOnClickListener(v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
        }

        // Avatar change
        setupAvatarPicker();
        ivAvatar.setOnClickListener(v -> {
            avatarPickerLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        // Grid setup (3 columns, 1dp gutters)
        rvMediaGrid.setLayoutManager(new GridLayoutManager(getContext(), 3));
        rvMediaGrid.addItemDecoration(new GridGapDecoration(
                getResources().getDimensionPixelSize(R.dimen.profile_grid_gap)));
        gridAdapter = new MediaGridAdapter(gridPosts, post -> {
            Intent intent = new Intent(requireContext(), PostDetailActivity.class);
            intent.putExtra("post", post);
            intent.putExtra("post_id", post.getId());
            startActivity(intent);
        });
        rvMediaGrid.setAdapter(gridAdapter);

        // Tab selection listener
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                // PAGE 2: reposts / tagged tabs are design-only (no feature behind them).
                if (Constants.DECORATIVE_UI
                        && (position == TAB_REPOSTS || position == TAB_TAGGED)) {
                    ToastUtil.showNotAvailable(requireContext());
                }
                loadTabContent(position);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadProfileData();
        loadTabContent(0);

        return view;
    }

    /**
     * PAGE 2 — design-only element. When {@link Constants#DECORATIVE_UI} is on the
     * element surfaces the shared "Not available" pill toast; when it is off the
     * element either disappears (hideWhenOff) or stays inert.
     */
    private void wireDecorative(@Nullable View view, boolean hideWhenOff) {
        if (view == null) return;
        if (Constants.DECORATIVE_UI) {
            view.setOnClickListener(v -> ToastUtil.showNotAvailable(requireContext()));
        } else if (hideWhenOff) {
            view.setVisibility(View.GONE);
        }
    }

    private void setupAvatarPicker() {
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        uploadAvatar(uri);
                    }
                }
        );
    }

    private void uploadAvatar(Uri uri) {
        if (currentUserId == null) return;
        Toast.makeText(requireContext(), "Updating profile photo…", Toast.LENGTH_SHORT).show();

        byte[] jpegBytes;
        try {
            jpegBytes = ImageUtils.processUriToJpegBytes(requireContext(), uri);
        } catch (IOException e) {
            Log.e(TAG_API, "Failed to process avatar: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Failed to process photo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        if (jpegBytes == null || jpegBytes.length == 0) {
            Toast.makeText(requireContext(), "Processed photo was empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String path = currentUserId + "/" + UUID.randomUUID() + ".jpg";
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), jpegBytes);
        Log.d(TAG_API, "uploaded " + jpegBytes.length + " bytes to avatars/" + path);

        storageApi.uploadFile("avatars", path, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e(TAG_API, "HTTP " + response.code() + " uploading avatar");
                    Toast.makeText(requireContext(), "Avatar upload failed (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    return;
                }

                String base = BuildConfig.SUPABASE_URL.trim();
                if (!base.endsWith("/")) base += "/";
                String publicUrl = com.insta.clone.util.MediaUrl.normalize("avatars", base + "storage/v1/object/public/avatars/" + path);
                Log.d(TAG_API, "HTTP 200 uploadFile avatars/" + path + ". Verifying via HEAD...");

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
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                Map<String, Object> update = new HashMap<>();
                                update.put("avatar_url", publicUrl);
                                restApi.updateProfile("eq." + currentUserId, update).enqueue(new Callback<List<ProfileDto>>() {
                                    @Override
                                    public void onResponse(Call<List<ProfileDto>> patchCall, Response<List<ProfileDto>> r) {
                                        if (r.isSuccessful()) {
                                            Toast.makeText(requireContext(), "Profile photo updated!", Toast.LENGTH_SHORT).show();
                                            sessionManager.saveSession(
                                                    sessionManager.getAccessToken(),
                                                    sessionManager.getRefreshToken(),
                                                    sessionManager.getUserId(),
                                                    sessionManager.getUsername(),
                                                    publicUrl
                                            );
                                            if (isAdded()) {
                                                com.insta.clone.util.GlideUtils.loadAvatar(ivAvatar, publicUrl);
                                            }
                                        } else {
                                            Log.e(TAG_API, "Failed to patch profile avatar: " + r.code());
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<List<ProfileDto>> patchCall, Throwable t) {
                                        Log.e(TAG_API, "Network error updating profile: " + t.getMessage());
                                    }
                                });
                            });
                        } else {
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "HEAD verification failed (HTTP " + headCode + ")", Toast.LENGTH_SHORT).show());
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull okhttp3.Call c, @NonNull IOException e) {
                        Log.e(TAG_API, "HEAD verification error: " + e.getMessage(), e);
                    }
                });
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG_API, "Network error uploading avatar: " + t.getMessage());
                Toast.makeText(requireContext(), "Upload network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadProfileData() {
        profileRepository.getProfile(currentUserId).observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                Profile profile = resource.data;
                tvUsername.setText(profile.getUsername());
                tvPostsCount.setText(formatStatCount(profile.getPostsCount()));
                tvFollowersCount.setText(formatStatCount(profile.getFollowersCount()));
                tvFollowingCount.setText(formatStatCount(profile.getFollowingCount()));

                // PAGE 2: the display name sits above the stats, the bio holds the bio.
                String displayName = profile.getFullName() == null ? "" : profile.getFullName().trim();
                if (displayName.isEmpty()) {
                    tvDisplayName.setText("");
                    tvDisplayName.setVisibility(View.GONE);
                } else {
                    tvDisplayName.setText(displayName);
                    tvDisplayName.setVisibility(View.VISIBLE);
                }
                String bio = profile.getBio() == null ? "" : profile.getBio().trim();
                tvBio.setText(bio);

                if (isAdded()) {
                    com.insta.clone.util.GlideUtils.loadAvatar(ivAvatar, profile.getAvatarUrl());
                }
            }
        });
    }

    /** 84 / 1,134 / 12.3K / 1.2M (PAGE 2 header format). */
    private String formatStatCount(int count) {
        if (count < 10_000) {
            return String.format(Locale.US, "%,d", count);
        }
        if (count < 1_000_000) {
            return trimTrailingZero(count / 1000.0) + "K";
        }
        return trimTrailingZero(count / 1_000_000.0) + "M";
    }

    private String trimTrailingZero(double value) {
        String text = String.format(Locale.US, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private void showEmptyState(int tabPosition) {
        if (profileEmptyState == null || ivEmptyIcon == null || tvEmptyTitle == null) return;
        boolean reposts = tabPosition == TAB_REPOSTS;
        ivEmptyIcon.setImageResource(reposts ? R.drawable.ic_repost : R.drawable.ic_tagged);
        tvEmptyTitle.setText(reposts ? R.string.profile_no_reposts : R.string.profile_no_photos_of_you);
        profileEmptyState.setVisibility(View.VISIBLE);
    }

    private void hideEmptyState() {
        if (profileEmptyState != null) profileEmptyState.setVisibility(View.GONE);
    }

    private void loadTabContent(int tabPosition) {
        if (tabPosition == TAB_REPOSTS || tabPosition == TAB_TAGGED) {
            // PAGE 2: design-only tabs show their empty state instead of feed data.
            gridPosts.clear();
            gridAdapter.notifyDataSetChanged();
            showEmptyState(tabPosition);
            return;
        }

        hideEmptyState();

        if (tabPosition == 0) {
            // Posts Grid: v_posts?user_id=eq.{id}&is_reel=eq.false
            postRepository.getUserPosts(currentUserId).observe(getViewLifecycleOwner(), resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                    gridPosts.clear();
                    gridPosts.addAll(resource.data);
                    gridAdapter.notifyDataSetChanged();
                }
            });
        } else if (tabPosition == 1) {
            // Reels Grid: v_posts?user_id=eq.{id}&is_reel=eq.true
            reelRepository.getUserReels(currentUserId).observe(getViewLifecycleOwner(), resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                    gridPosts.clear();
                    gridPosts.addAll(resource.data);
                    gridAdapter.notifyDataSetChanged();
                }
            });
        }
        // Tabs 3 and 4 (reposts / tagged) are design-only and never reach here.
    }

    /**
     * PAGE 2 grid gutters: the 1dp gap is split across neighbours (3px total at
     * 440dpi = 1.09dp, the closest achievable value). Equal insets keep the three
     * columns the same width.
     */
    static class GridGapDecoration extends RecyclerView.ItemDecoration {
        private final int leadingGap;
        private final int trailingGap;

        GridGapDecoration(int gapPx) {
            this.leadingGap = gapPx / 2;
            this.trailingGap = gapPx - leadingGap;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.set(leadingGap, leadingGap, trailingGap, trailingGap);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfileData();
        if (tabLayout != null) {
            loadTabContent(tabLayout.getSelectedTabPosition());
        }
    }

    /**
     * Adapter for 3-column post grid in Profile.
     */
    static class MediaGridAdapter extends RecyclerView.Adapter<MediaGridAdapter.GridViewHolder> {
        private final List<Post> items;
        private final OnPostClickListener listener;

        interface OnPostClickListener {
            void onPostClick(Post post);
        }

        MediaGridAdapter(List<Post> items, OnPostClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public GridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_media, parent, false);
            return new GridViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GridViewHolder holder, int position) {
            Post post = items.get(position);
            String url = post.getThumbnailUrl();
            if (url == null || url.isEmpty()) {
                url = post.getFirstMediaUrl();
            }

            com.insta.clone.util.GlideUtils.load(holder.ivMedia, "posts", url);

            // PAGE 2: carousel / reel markers (real post data, not decoration).
            boolean carousel = post.getMediaUrls() != null && post.getMediaUrls().size() > 1;
            holder.ivMultiBadge.setVisibility(carousel ? View.VISIBLE : View.GONE);
            holder.ivReelBadge.setVisibility(post.isReel() ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onPostClick(post);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class GridViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivMedia;
            final ImageView ivMultiBadge;
            final ImageView ivReelBadge;
            GridViewHolder(@NonNull View itemView) {
                super(itemView);
                ivMedia = itemView.findViewById(R.id.ivMedia);
                ivMultiBadge = itemView.findViewById(R.id.ivMultiBadge);
                ivReelBadge = itemView.findViewById(R.id.ivReelBadge);
            }
        }
    }
}
```

---

# DESIGN CHECKLIST — PAGE 8A (CREATE SHEET)

`1dp = 2.75px` on the verification device (1080 x 2400 @ 440dpi = 393dp wide). Bounds below
come from `adb shell uiautomator dump`.

| # | Requirement | Implementation | Status / measured evidence |
|---|---|---|---|
| 1 | Bottom sheet, bg = sheet token, top radius 16dp | `BottomSheetDialog` + `InstagramBottomSheetDialog` style with `@drawable/bg_bottom_sheet` (`@color/color_sheet`, `@dimen/radius_sheet` = 16dp) | PASS — sheet opens over Home, panel top ≈ 446dp, rounded top corners |
| 2 | Drag handle 36 x 4dp `#A8A8A8`, centred 8dp from the top | `create_sheet_handle_width/height/margin_top` + new `bg_sheet_drag_handle` (`@color/color_icon_muted`) | PASS — handle centred, 8dp below the panel top |
| 3 | Title 'Create' 16sp Bold centred | `@dimen/text_title` + `textStyle="bold"` | PASS — centred above the rows |
| 4 | Rows 56dp, 16dp padding, icon 24dp + 16dp gap + label 16sp | `create_sheet_row_*` tokens + `TextAppearance.Insta.CreateRow` | PASS — all six rows measured **56.0dp** tall, `[0,1383]-[1080,1537]` … `[0,2153]-[1080,2307]` |
| 5 | 'Reel' (`ic_reels`) opens its existing flow | `row_create_reel` → `CreateReelActivity` | PASS — existing navigation target, untouched |
| 6 | 'Post' (`ic_grid`) opens its existing flow | `row_create_post` → `CreatePostActivity` | PASS — verified on device (opens the picker) |
| 7 | 'Story' (`ic_add`) opens its existing flow | `row_create_story` → `CreateStoryActivity` | PASS — existing navigation target |
| 8 | 'Story highlight' (`ic_highlight`) opens the existing New highlight flow | `row_create_highlight` | PARTIAL — there is **no** New highlight flow in this codebase (the profile's `layoutHighlightNew` is itself decorative), so the row is decorative (NOTES 1) |
| 9 | 'Live' and 'Made for you' are decorative | `row_create_live` (`ic_live`, new), `row_create_made_for_you` (`ic_sparkle`, new) | PASS — `Constants.DECORATIVE_UI` + "Not available" toast, hidden when the flag is off |
| 10 | Opened by the '+' on Home and Profile | `HomeFragment.btnCreate` and `ProfileFragment.ivCreate` call `CreateSheet.show(...)` | PASS — verified on device from Home (NOTES 2) |

---

# DESIGN CHECKLIST — PAGE 8B (PICKER)

| # | Requirement | Implementation | Status / measured evidence |
|---|---|---|---|
| 11 | Full screen picker; the bottom-nav Create tab opens it too | `create_step_picker` inside `CreatePostActivity`; `MainActivity`'s Create destination still launches `CreatePostActivity` | PASS — unchanged nav graph, the tab lands on step 1 of the flow |
| 12 | Top bar 56dp: `ic_close`, 'New post' 16sp Bold left-aligned after the icon, right 'Next' 16sp Bold `#0095F6` (disabled when nothing is selected) | `picker_top_bar_height` 56dp, `TextAppearance.Insta.PickerBar`, new `selector_picker_action` (`@color/color_primary` / `@color/color_primary_disabled`) | PASS — close **24.0dp** at 16dp, title left-aligned after it, `btn_picker_next` right edge 1036 = **16dp**, disabled → `#4D0095F6`, enabled → `#0095F6` (observed bright after selecting) |
| 13 | Preview: full-width square 1:1 of the selected image | `SquareImageView` id `iv_preview` in `layout_picker_preview` | PASS — `iv_preview` `[0,264]-[1080,1344]` = **392.7 x 392.7dp** |
| 14 | While dragging/zooming show 3x3 white 40% grid lines | `layout_picker_grid_lines` — 4 ConstraintLayout guidelines at 33.33% / 66.66% + 1dp lines in `@color/color_grid_line` (`#66FFFFFF`); shown on ACTION_DOWN, hidden on UP | PASS — lines appear over the preview while touching and clear on release (both states photographed) |
| 15 | 36dp circle `#99000000` bottom-left with `ic_expand` (toggles fit/fill) | `btn_picker_expand` `picker_overlay_circle` 36dp + `bg_picker_overlay_circle` (`@color/color_scrim`), 12dp margin | PASS — **36.0dp** at `[33,1212]` (12dp inset, bottom-left); toggles `CENTER_CROP` ⇄ `FIT_CENTER` |
| 16 | Row 52dp: 'Recents' 16sp Bold + `ic_chevron_down` (existing album source) | `picker_recents_row_height` 52dp, `btn_picker_album` | PARTIAL — 16sp bold + chevron render at 16dp start; there is no album browser in the app, so the row is decorative (NOTES 3) |
| 17 | Right: 'SELECT MULTIPLE' pill 32dp, radius 16dp, surfaceVariant, `ic_multi_image` 16dp + 12sp Bold caps | `bg_picker_pill` + `TextAppearance.Insta.PickerPill` | PASS — pill **32.0dp** tall (`[520,1371]-[926,1459]`) |
| 18 | When active it becomes a 32dp circle `#0095F6` with a white icon | `setMultiSelect()` → `bg_picker_multi_active`, width/height 32dp, label hidden, icon tinted white | PASS — collapses to a blue circle with a white icon on device |
| 19 | Next to it a 32dp circle surfaceVariant with `ic_camera` (existing camera) | `btn_pick_image` (id preserved) + `bg_picker_circle_surface` | PASS — **32.0dp** at the 16dp end edge; opens the existing `PickMultipleVisualMedia` launcher |
| 20 | Gallery grid: 4 columns, 1dp gaps, square thumbs | `GridLayoutManager(4)` + `GridGapDecoration` (`picker_grid_gap`), `SquareImageView` tiles | PASS — 4 columns, tiles 97.5dp square, 1–2px gutters (equal-width decoration) |
| 21 | Single mode: the selected thumb is dimmed 50% | `view_thumb_dim` + `bg_picker_thumb_dim` (`@color/color_thumb_dim` = `#80000000`) | PASS — single tap dims the chosen tile |
| 22 | Multi mode: each selected thumb shows a 22dp `#0095F6` circle with a 2dp white border and a white 12sp Bold index (1 to 10); unselected show an empty 22dp circle with a 1.5dp white border | `tv_thumb_index` + `bg_picker_selected_circle`; `view_thumb_ring` + `bg_picker_rest_circle` | PASS — indices **1, 2, 3** rendered on the three tapped tiles, empty white outlines on the rest (`picker_thumb_badge` 22dp) |
| 23 | Bottom mode switch: centred pill 40dp, radius 20dp, `#CC000000`, 16dp above the bottom inset, 'POST','STORY','REEL' 13sp Bold caps, letter-spacing 0.1em, selected white, others `#A8A8A8`, LIVE omitted | `layout_picker_mode` + `bg_picker_mode_pill` (`@color/color_scrim_strong`), `TextAppearance.Insta.PickerMode` | PASS — pill `[301,2197]-[779,2307]` = 173.8 x **40.0dp** centred, bottom exactly **16dp** above the content edge; POST white / STORY REEL `#A8A8A8`; **LIVE omitted**; STORY and REEL open the existing story/reel activities |
| 24 | Dark theme screen background `#000000` | root `@color/black` | PASS — black in **both** themes (verified in light and dark device themes, like Instagram's own create flow) |

---

# NOTES, DECISIONS AND LIMITATIONS

### 1. 'Story highlight' has no flow to open
The brief says it opens "the existing New highlight flow". There is no such flow:
`HighlightRepository` exists (data only) and the profile's "New" highlight is wired as
`wireDecorative(...)` in `ProfileFragment`. The row is therefore decorative, consistent with
the project's design-only rule.

### 2. The '+' now opens the sheet instead of the picker directly
Home and Profile used to launch `CreatePostActivity` straight away. The brief makes the sheet
the entry point, so those two listeners now call `CreateSheet.show(...)`; the sheet's 'Post'
row opens the same `CreatePostActivity`, so no destination was added or removed. The
bottom-navigation Create tab still goes straight to the picker, exactly as the brief asks.

### 3. The gallery grid needs a media source — and this repo has none
The app deliberately uses the Android photo picker (`PickVisualMedia`), so there was **no**
in-app gallery to fill the 4-column grid. The grid is implemented as a real RecyclerView with
a complete adapter (thumbnail + dim + index + outline states) fed by a read-only MediaStore
query:

* `READ_MEDIA_IMAGES` is **already declared** in the manifest — no permission was added;
* the query runs only when the permission is already granted, so **no permission prompt was
  introduced** (adding one would be new user-facing behaviour, which design-only forbids);
* for verification the permission was granted from adb
  (`adb shell pm grant com.insta.clone android.permission.READ_MEDIA_IMAGES`), which is why
  the device photos appear in the screenshots. Without it the grid is simply empty and the
  existing system picker (the camera circle, id `btn_pick_image`) remains the selection path.
* The album dropdown ('Recents') likewise has no album browser to open, so it is decorative.

Everything else selected on this screen is real: the 1:1 preview, the fit/fill toggle, the
single/multi selection states, the index badges and the Next gate all work on live data.

### 4. One activity, four steps
`activity_create_post.xml` is now a host with four `<include>`d step layouts, and
`CreatePostActivity` shows one at a time. All ids the activity used before are preserved —
`iv_preview`, `btn_pick_image`, `et_caption`, `btn_publish` and `progress` — with
`btn_pick_image` moving to the camera circle (same "choose a photo" meaning) and `et_caption`
staying an `EditText` so the existing caption read path still compiles.

### 5. The mode switch reuses existing destinations
'STORY' opens `CreateStoryActivity` and 'REEL' opens `CreateReelActivity` — the same targets
the Create sheet uses. 'POST' is the current screen and stays selected.

---

# BUILD AND DEVICE VERIFICATION

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home \
  ./gradlew :app:assembleDebug          # BUILD SUCCESSFUL
adb install -r app/build/outputs/apk/debug/app-debug.apk   # Success
adb shell pm grant com.insta.clone android.permission.READ_MEDIA_IMAGES   # for the grid
```

Verified on device `10BE2S1U640002B` (1080 x 2400 @ 440dpi):

* Home top-bar '+' → Create sheet; all six rows measured 56.0dp; 'Post' opens the picker.
* Picker: 56dp bar, 392.7dp square preview, 36dp fit/fill circle, 52dp Recents row, 32dp pill
  and camera circle, 4-column grid with device photos, 40dp mode pill 16dp above the bottom.
* Multi-select: pill collapses to a blue circle, three tiles show blue index badges, the rest
  show white outlines; 'Next' turns `#0095F6` once something is selected.
* Rule-of-thirds lines appear while touching the preview and clear on release.
* Both themes checked; the create screens stay black in both, as designed.
