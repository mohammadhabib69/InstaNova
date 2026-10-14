# PAGE 3 — EXPLORE (idle Search tab) · PAGE 4 — SEARCH (focused + results) · Design-only changes

Design-only pass on the Explore / Search screen (`ExploreFragment`, also reached
through `SearchFragment`). No behaviour changes: ViewModels, repositories, API
calls, models, navigation targets and code-referenced view IDs are untouched.
Every color/size comes from tokens (`values/colors.xml`, `values/dimens.xml`,
`values-night/…`, `values/themes.xml`, theme attrs).

Complete code of every changed file follows (15 files: 4 token/style files,
1 color selector, 5 drawables, 4 layouts, 1 presentation class).

> `values/dimens.xml`, `values/colors.xml`, `values/strings.xml` and
> `values/themes.xml` are shared files — the full file is reproduced; only the
> `PAGE 3` / `PAGE 4` sections are new here.

---


## app/src/main/res/values/dimens.xml

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
</resources>
```

## app/src/main/res/values/colors.xml

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
</resources>
```

## app/src/main/res/values/strings.xml

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
</resources>
```

## app/src/main/res/values/themes.xml

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

## app/src/main/res/color/selector_search_tab_text.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 4 — Search tabs: selected = textPrimary, unselected = textSecondary.
    Both colors are config-aware PAGE 0 tokens.
-->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/color_text_primary" android:state_selected="true" />
    <item android:color="@color/color_text_secondary" />
</selector>
```

## app/src/main/res/drawable/bg_search_field.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 3 — Explore search field: surfaceVariant pill, 10dp radius.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/search_field_radius" />
    <solid android:color="@color/color_surface_variant" />
</shape>
```

## app/src/main/res/drawable/bg_chip_outline.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 3 — Explore filter chip, unselected: 1dp divider border, 8dp radius.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/explore_chip_radius" />
    <solid android:color="@color/transparent" />
    <stroke
        android:width="@dimen/divider_hairline"
        android:color="@color/color_divider" />
</shape>
```

## app/src/main/res/drawable/bg_chip_selected.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 3 — Explore filter chip, selected: textPrimary fill, 8dp radius.
    The label/icon switch to the background color in code.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="@dimen/explore_chip_radius" />
    <solid android:color="@color/color_text_primary" />
</shape>
```

## app/src/main/res/drawable/bg_badge_shadow.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 3 — soft dark halo behind the grid badges. The negative inset lets the
    shadow bleed a few dp past the badge icon so the white glyph stays readable
    on bright media.
-->
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:insetTop="@dimen/media_badge_shadow_inset"
    android:insetBottom="@dimen/media_badge_shadow_inset"
    android:insetLeft="@dimen/media_badge_shadow_inset"
    android:insetRight="@dimen/media_badge_shadow_inset">
    <shape android:shape="oval">
        <solid android:color="@color/color_badge_shadow" />
    </shape>
</inset>
```

## app/src/main/res/drawable/bg_circle_outline.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 4 — Search result leading circle (tags / places / recents):
    1dp divider border, transparent fill.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/transparent" />
    <stroke
        android:width="@dimen/divider_hairline"
        android:color="@color/color_divider" />
</shape>
```

## app/src/main/res/layout/fragment_explore.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/themeBackground"
    android:orientation="vertical">

    <!-- ============================================================= -->
    <!-- Search row — idle (PAGE 3): centred hint + scan button.        -->
    <!-- Focused (PAGE 4): back arrow + field + clear.                  -->
    <!-- ============================================================= -->
    <LinearLayout
        android:id="@+id/searchRow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/explore_search_row_margin"
        android:paddingTop="@dimen/explore_search_row_margin"
        android:paddingEnd="@dimen/explore_search_row_margin"
        android:paddingBottom="@dimen/explore_search_row_margin">

        <ImageView
            android:id="@+id/ivSearchBack"
            android:layout_width="@dimen/search_back_icon_size"
            android:layout_height="@dimen/search_back_icon_size"
            android:src="@drawable/ic_arrow_back"
            app:tint="?attr/themeTextPrimary"
            android:visibility="gone"
            android:contentDescription="Back" />

        <LinearLayout
            android:id="@+id/layoutSearchField"
            android:layout_width="0dp"
            android:layout_height="@dimen/search_field_height_idle"
            android:layout_weight="1"
            android:background="@drawable/bg_search_field"
            android:gravity="center"
            android:orientation="horizontal"
            android:paddingStart="@dimen/search_field_padding_horizontal"
            android:paddingEnd="@dimen/search_field_padding_horizontal">

            <ImageView
                android:id="@+id/ivSearchIcon"
                android:layout_width="@dimen/search_field_icon_size"
                android:layout_height="@dimen/search_field_icon_size"
                android:layout_marginEnd="@dimen/search_field_icon_gap"
                android:src="@drawable/ic_search"
                app:tint="?attr/themeTextSecondary"
                android:contentDescription="@string/cd_search" />

            <EditText
                android:id="@+id/et_search"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="@null"
                android:hint="@string/search_hint"
                android:imeOptions="actionSearch"
                android:inputType="text"
                android:maxLines="1"
                android:padding="0dp"
                android:textColor="?attr/themeTextPrimary"
                android:textColorHint="?attr/themeTextSecondary"
                android:textSize="@dimen/search_field_text_size" />

            <ImageView
                android:id="@+id/ivSearchClear"
                android:layout_width="@dimen/search_field_close_size"
                android:layout_height="@dimen/search_field_close_size"
                android:layout_marginStart="@dimen/search_field_icon_gap"
                android:src="@drawable/ic_close"
                app:tint="?attr/themeTextPrimary"
                android:visibility="gone"
                android:contentDescription="Clear search" />
        </LinearLayout>

        <!-- Decorative: camera / scan lookup -->
        <FrameLayout
            android:id="@+id/btnSearchScan"
            android:layout_width="@dimen/search_scan_button_size"
            android:layout_height="@dimen/search_scan_button_size"
            android:layout_marginStart="@dimen/search_scan_gap">

            <ImageView
                android:layout_width="@dimen/search_scan_icon_size"
                android:layout_height="@dimen/search_scan_icon_size"
                android:layout_gravity="center"
                android:src="@drawable/ic_scan"
                app:tint="?attr/themeTextPrimary"
                android:contentDescription="@string/cd_search" />
        </FrameLayout>
    </LinearLayout>

    <!-- ============================================================= -->
    <!-- PAGE 3 — filter chips (horizontal scroll)                      -->
    <!-- ============================================================= -->
    <HorizontalScrollView
        android:id="@+id/chipsScroll"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/explore_chips_margin_top"
        android:clipToPadding="false"
        android:paddingStart="@dimen/explore_chips_padding_start"
        android:paddingEnd="@dimen/explore_chips_padding_start"
        android:scrollbars="none">

        <LinearLayout
            android:id="@+id/chipsRow"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/chipReels"
                style="@style/Widget.Insta.Chip"
                android:text="@string/explore_chip_reels"
                app:drawableStartCompat="@drawable/ic_reels" />

            <TextView
                android:id="@+id/chipShop"
                style="@style/Widget.Insta.Chip"
                android:text="@string/explore_chip_shop"
                app:drawableStartCompat="@drawable/ic_shopping_bag" />

            <TextView
                android:id="@+id/chipArchitecture"
                style="@style/Widget.Insta.Chip"
                android:text="@string/explore_tag_architecture" />

            <TextView
                android:id="@+id/chipTravel"
                style="@style/Widget.Insta.Chip"
                android:text="@string/explore_tag_travel" />

            <TextView
                android:id="@+id/chipDecor"
                style="@style/Widget.Insta.Chip"
                android:text="@string/explore_tag_decor" />

            <TextView
                android:id="@+id/chipFood"
                style="@style/Widget.Insta.Chip"
                android:text="@string/explore_tag_food" />

            <TextView
                android:id="@+id/chipArt"
                style="@style/Widget.Insta.Chip"
                android:text="@string/explore_tag_art" />

            <TextView
                android:id="@+id/chipNature"
                style="@style/Widget.Insta.Chip"
                android:text="@string/explore_tag_nature" />
        </LinearLayout>
    </HorizontalScrollView>

    <!-- ============================================================= -->
    <!-- PAGE 4 — result tabs + divider                                 -->
    <!-- ============================================================= -->
    <LinearLayout
        android:id="@+id/searchTabsContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:visibility="gone">

        <com.google.android.material.tabs.TabLayout
            android:id="@+id/search_tabs"
            android:layout_width="match_parent"
            android:layout_height="@dimen/search_tabs_height"
            android:background="?attr/themeBackground"
            app:tabGravity="fill"
            app:tabIndicatorColor="?attr/themeTextPrimary"
            app:tabIndicatorHeight="@dimen/search_tab_indicator"
            app:tabMode="fixed"
            app:tabRippleColor="@null"
            app:tabTextAppearance="@style/TextAppearance.Insta.SearchTab"
            app:tabTextColor="@color/selector_search_tab_text">

            <com.google.android.material.tabs.TabItem
                android:id="@+id/search_tab_top"
                android:text="@string/search_tab_top" />

            <com.google.android.material.tabs.TabItem
                android:id="@+id/search_tab_accounts"
                android:text="@string/search_tab_accounts" />

            <com.google.android.material.tabs.TabItem
                android:id="@+id/search_tab_audio"
                android:text="@string/search_tab_audio" />

            <com.google.android.material.tabs.TabItem
                android:id="@+id/search_tab_tags"
                android:text="@string/search_tab_tags" />

            <com.google.android.material.tabs.TabItem
                android:id="@+id/search_tab_places"
                android:text="@string/search_tab_places" />
        </com.google.android.material.tabs.TabLayout>

        <View
            android:layout_width="match_parent"
            android:layout_height="@dimen/divider_hairline"
            android:background="?attr/themeDivider" />
    </LinearLayout>

    <!-- ============================================================= -->
    <!-- Results: explore grid (PAGE 3) / search rows (PAGE 4)          -->
    <!-- ============================================================= -->
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rv_explore_grid"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

        <LinearLayout
            android:id="@+id/searchEmptyState"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:orientation="vertical"
            android:padding="@dimen/empty_state_padding"
            android:visibility="gone">

            <ImageView
                android:id="@+id/ivSearchEmptyIcon"
                android:layout_width="@dimen/search_empty_icon_size"
                android:layout_height="@dimen/search_empty_icon_size"
                android:src="@drawable/ic_search"
                app:tint="?attr/themeTextSecondary"
                android:contentDescription="@null" />

            <TextView
                android:id="@+id/tvSearchEmptyTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/search_empty_margin_top"
                android:text="@string/search_no_results"
                android:textColor="?attr/themeTextPrimary"
                android:textSize="@dimen/text_title" />
        </LinearLayout>
    </FrameLayout>

</LinearLayout>
```

## app/src/main/res/layout/item_search_row.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 4 — search result row (72dp, no divider): account / tag / place / recent.
    Account rows show the avatar, tag / place / recent rows show the outlined
    leading circle with their icon.
-->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="@dimen/search_row_height"
    android:background="?attr/themeBackground"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingStart="@dimen/search_row_padding_horizontal"
    android:paddingEnd="@dimen/search_row_padding_horizontal">

    <FrameLayout
        android:layout_width="@dimen/search_row_avatar_size"
        android:layout_height="@dimen/search_row_avatar_size">

        <de.hdodenhof.circleimageview.CircleImageView
            android:id="@+id/ivRowAvatar"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:src="@drawable/ic_person_placeholder" />

        <FrameLayout
            android:id="@+id/layoutRowCircle"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@drawable/bg_circle_outline"
            android:visibility="gone">

            <ImageView
                android:id="@+id/ivRowCircleIcon"
                android:layout_width="@dimen/search_row_icon_size"
                android:layout_height="@dimen/search_row_icon_size"
                android:layout_gravity="center"
                android:src="@drawable/ic_hashtag"
                app:tint="?attr/themeTextPrimary"
                android:contentDescription="@null" />
        </FrameLayout>
    </FrameLayout>

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="@dimen/search_row_gap"
        android:layout_weight="1"
        android:orientation="vertical">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/tvRowTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="?attr/themeTextPrimary"
                android:textSize="@dimen/search_row_text_size"
                android:textStyle="bold" />

            <ImageView
                android:id="@+id/ivRowVerified"
                android:layout_width="@dimen/search_row_verified_size"
                android:layout_height="@dimen/search_row_verified_size"
                android:layout_marginStart="@dimen/search_row_title_icon_gap"
                android:src="@drawable/ic_verified"
                android:visibility="gone"
                android:contentDescription="Verified" />
        </LinearLayout>

        <TextView
            android:id="@+id/tvRowSubtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/search_row_line_gap"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="?attr/themeTextSecondary"
            android:textSize="@dimen/search_row_text_size" />
    </LinearLayout>

    <ImageView
        android:id="@+id/ivRowTrailing"
        android:layout_width="@dimen/search_row_trailing_size"
        android:layout_height="@dimen/search_row_trailing_size"
        android:layout_marginStart="@dimen/search_row_gap"
        android:src="@drawable/ic_close"
        app:tint="?attr/themeTextPrimary"
        android:visibility="gone"
        android:contentDescription="Remove" />

</LinearLayout>
```

## app/src/main/res/layout/item_search_recent_header.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 4 — "Recent" header of the empty-query search state: 16sp Bold title
    with the blue "Clear all" action on the right.
-->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="@dimen/search_recent_header_height"
    android:background="?attr/themeBackground"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingStart="@dimen/search_row_padding_horizontal"
    android:paddingEnd="@dimen/search_row_padding_horizontal">

    <TextView
        android:id="@+id/tvRecentTitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="@string/search_recent"
        android:textColor="?attr/themeTextPrimary"
        android:textSize="@dimen/search_page_title_size"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/tvRecentClearAll"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/search_clear_all"
        android:textColor="@color/color_primary"
        android:textSize="@dimen/search_clear_all_size" />

</LinearLayout>
```

## app/src/main/res/layout/item_saved_media.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    Grid cell (Profile / Explore / Saved): square media with optional
    carousel (ic_multi_image) and reel (ic_play) badges — white 18dp with a soft
    dark halo, top-right. The badges stay hidden unless the hosting adapter turns
    them on.
-->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <com.insta.clone.ui.widget.SquareImageView
        android:id="@+id/ivMedia"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:adjustViewBounds="false"
        android:background="@color/color_surface_variant"
        android:scaleType="centerCrop"
        android:contentDescription="Post media" />

    <ImageView
        android:id="@+id/ivMultiBadge"
        android:layout_width="@dimen/profile_grid_badge"
        android:layout_height="@dimen/profile_grid_badge"
        android:layout_gravity="top|end"
        android:layout_margin="@dimen/profile_grid_badge_margin"
        android:background="@drawable/bg_badge_shadow"
        android:src="@drawable/ic_multi_image"
        app:tint="@color/white"
        android:visibility="gone"
        android:contentDescription="Multiple photos" />

    <ImageView
        android:id="@+id/ivReelBadge"
        android:layout_width="@dimen/profile_grid_badge"
        android:layout_height="@dimen/profile_grid_badge"
        android:layout_gravity="top|end"
        android:layout_margin="@dimen/profile_grid_badge_margin"
        android:background="@drawable/bg_badge_shadow"
        android:src="@drawable/ic_play"
        app:tint="@color/white"
        android:visibility="gone"
        android:contentDescription="Reel" />

</FrameLayout>
```

## app/src/main/java/com/insta/clone/ui/explore/ExploreFragment.java

```java
package com.insta.clone.ui.explore;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.insta.clone.R;
import com.insta.clone.data.local.RecentSearchStore;
import com.insta.clone.data.model.Post;
import com.insta.clone.data.model.Profile;
import com.insta.clone.data.repository.ReelRepository;
import com.insta.clone.data.repository.SearchRepository;
import com.insta.clone.ui.post.PostDetailActivity;
import com.insta.clone.util.Constants;
import com.insta.clone.util.Resource;
import com.insta.clone.util.ToastUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Explore (idle Search tab, PAGE 3) and Search (focused + results, PAGE 4).
 *
 * <p>Design-only pass: the existing search / explore repository calls are reused
 * unchanged; this class only owns presentation and the two screen states.
 */
public class ExploreFragment extends Fragment {

    /* PAGE 4 result tabs */
    private static final int TAB_TOP = 0;
    private static final int TAB_ACCOUNTS = 1;
    private static final int TAB_AUDIO = 2;
    private static final int TAB_TAGS = 3;
    private static final int TAB_PLACES = 4;

    /* PAGE 3 filter chips */
    private static final int CHIP_REELS = 0;
    private static final int CHIP_SHOP = 1;
    private static final int CHIP_FIRST_TAG = 2;
    private static final int CHIP_NONE = -1;

    private static final int[] CHIP_IDS = {
            R.id.chipReels, R.id.chipShop, R.id.chipArchitecture, R.id.chipTravel,
            R.id.chipDecor, R.id.chipFood, R.id.chipArt, R.id.chipNature
    };

    private static final int[] CHIP_TAG_RES = {
            0, 0, R.string.explore_tag_architecture, R.string.explore_tag_travel,
            R.string.explore_tag_decor, R.string.explore_tag_food,
            R.string.explore_tag_art, R.string.explore_tag_nature
    };

    private View rootView;
    private LinearLayout searchRow;
    private EditText etSearch;
    private RecyclerView rvExploreGrid;
    private View layoutSearchField;
    private View btnSearchScan;
    private View chipsScroll;
    private View searchTabsContainer;
    private View searchEmptyState;
    private ImageView ivSearchBack;
    private ImageView ivSearchClear;
    private ImageView ivSearchEmptyIcon;
    private TextView tvSearchEmptyTitle;
    private TabLayout searchTabs;

    private SearchRepository searchRepository;
    private ReelRepository reelRepository;
    private RecentSearchStore recentSearchStore;

    private final List<Post> explorePosts = new ArrayList<>();
    private final List<SearchRow> searchRows = new ArrayList<>();
    private final List<Profile> accountResults = new ArrayList<>();
    private int tagResultCount = 0;

    private ExploreGridAdapter gridAdapter;
    private SearchRowAdapter rowAdapter;
    private GridGapDecoration gridGapDecoration;

    private boolean isSearching = false;
    private int activeChip = CHIP_NONE;
    private int searchTab = TAB_TOP;
    private boolean settingText = false;

    public ExploreFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_explore, container, false);
        rootView = view;

        searchRow = view.findViewById(R.id.searchRow);
        etSearch = view.findViewById(R.id.et_search);
        rvExploreGrid = view.findViewById(R.id.rv_explore_grid);
        layoutSearchField = view.findViewById(R.id.layoutSearchField);
        btnSearchScan = view.findViewById(R.id.btnSearchScan);
        chipsScroll = view.findViewById(R.id.chipsScroll);
        searchTabsContainer = view.findViewById(R.id.searchTabsContainer);
        searchEmptyState = view.findViewById(R.id.searchEmptyState);
        ivSearchBack = view.findViewById(R.id.ivSearchBack);
        ivSearchClear = view.findViewById(R.id.ivSearchClear);
        ivSearchEmptyIcon = view.findViewById(R.id.ivSearchEmptyIcon);
        tvSearchEmptyTitle = view.findViewById(R.id.tvSearchEmptyTitle);
        searchTabs = view.findViewById(R.id.search_tabs);

        searchRepository = new SearchRepository(requireContext());
        reelRepository = new ReelRepository(requireContext());
        recentSearchStore = new RecentSearchStore(requireContext());

        gridAdapter = new ExploreGridAdapter(explorePosts, post -> {
            Intent intent = new Intent(requireContext(), PostDetailActivity.class);
            intent.putExtra("post", post);
            intent.putExtra("post_id", post.getId());
            startActivity(intent);
        });

        rowAdapter = new SearchRowAdapter(searchRows, new SearchRowAdapter.Listener() {
            @Override
            public void onAccountClick(Profile profile) {
                Toast.makeText(requireContext(), "Selected @" + profile.getUsername(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRecentClick(String query) {
                setQueryText(query);
                runSearch(query);
            }

            @Override
            public void onRecentRemove(String query) {
                recentSearchStore.removeSearch(query);
                showRecentRows();
            }

            @Override
            public void onClearAllRecents() {
                recentSearchStore.clearAll();
                showRecentRows();
            }

            @Override
            public void onPlaceClick() {
                if (Constants.DECORATIVE_UI) ToastUtil.showNotAvailable(requireContext());
            }
        });

        gridGapDecoration = new GridGapDecoration(
                getResources().getDimensionPixelSize(R.dimen.explore_grid_gap));

        setupChips(view);
        setupTabs();
        setupSearchField();
        setupEvents(view);

        // PAGE 3/4: keep the search row clear of the status bar (edge-to-edge window).
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);

        applyMode(false);
        showExploreGrid();
        loadExploreFeed();

        return view;
    }

    /* ------------------------------------------------------------------ */
    /* PAGE 3 — chips                                                      */
    /* ------------------------------------------------------------------ */

    private void setupChips(View root) {
        for (int i = 0; i < CHIP_IDS.length; i++) {
            final int chipIndex = i;
            TextView chip = root.findViewById(CHIP_IDS[i]);
            if (chip == null) continue;
            chip.setOnClickListener(v -> onChipClick(chipIndex));
        }
        // Shop is design-only.
        if (!Constants.DECORATIVE_UI) {
            View shop = root.findViewById(R.id.chipShop);
            if (shop != null) shop.setVisibility(View.GONE);
        }
        applyChipSelection();
    }

    private void onChipClick(int chipIndex) {
        if (chipIndex == CHIP_SHOP) {
            if (Constants.DECORATIVE_UI) ToastUtil.showNotAvailable(requireContext());
            return;
        }
        activeChip = (activeChip == chipIndex) ? CHIP_NONE : chipIndex;
        applyChipSelection();
        loadChipFeed();
    }

    private void applyChipSelection() {
        for (int i = 0; i < CHIP_IDS.length; i++) {
            TextView chip = rootView == null ? null : rootView.findViewById(CHIP_IDS[i]);
            if (chip == null) continue;
            boolean selected = (i == activeChip);
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_outline);
            chip.setTextColor(selected
                    ? ContextCompat.getColor(requireContext(), R.color.color_background)
                    : ContextCompat.getColor(requireContext(), R.color.color_text_primary));
            if (CHIP_TAG_RES[i] == 0 && i != CHIP_SHOP) {
                // Chips with a leading icon (Reels) follow the same tint.
                TextViewCompat.setCompoundDrawableTintList(chip, android.content.res.ColorStateList.valueOf(
                        selected
                                ? ContextCompat.getColor(requireContext(), R.color.color_background)
                                : ContextCompat.getColor(requireContext(), R.color.color_text_primary)));
            }
        }
    }

    private void loadChipFeed() {
        if (activeChip == CHIP_REELS) {
            // Existing is_reel query.
            reelRepository.getReels(30, 0).observe(getViewLifecycleOwner(), resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                    explorePosts.clear();
                    explorePosts.addAll(resource.data);
                    gridAdapter.notifyDataSetChanged();
                }
            });
            return;
        }
        if (activeChip >= CHIP_FIRST_TAG) {
            // Existing tag search.
            String tag = getString(CHIP_TAG_RES[activeChip]);
            searchRepository.searchTags(tag).observe(getViewLifecycleOwner(), resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                    explorePosts.clear();
                    explorePosts.addAll(resource.data);
                    gridAdapter.notifyDataSetChanged();
                }
            });
            return;
        }
        loadExploreFeed();
    }

    /* ------------------------------------------------------------------ */
    /* PAGE 4 — tabs                                                       */
    /* ------------------------------------------------------------------ */

    private void setupTabs() {
        searchTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                searchTab = tab.getPosition();
                if (searchTab == TAB_AUDIO || searchTab == TAB_PLACES) {
                    if (Constants.DECORATIVE_UI) ToastUtil.showNotAvailable(requireContext());
                    searchRows.clear();
                    rowAdapter.notifyDataSetChanged();
                    showEmptyState(true);
                    return;
                }
                String query = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
                if (query.isEmpty()) {
                    showRecentRows();
                } else {
                    runSearch(query);
                }
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}

            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    /* ------------------------------------------------------------------ */
    /* Search field + state                                                */
    /* ------------------------------------------------------------------ */

    private void setupSearchField() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (settingText) return;
                String q = s != null ? s.toString().trim() : "";
                ivSearchClear.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                if (q.isEmpty()) {
                    if (isSearching) {
                        showRecentRows();
                    } else {
                        showExploreGrid();
                    }
                } else {
                    runSearch(q);
                }
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
                if (!q.isEmpty()) {
                    recentSearchStore.addSearch(q);
                    runSearch(q);
                }
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void setupEvents(View root) {
        // Tapping the idle field opens the search screen (PAGE 4).
        layoutSearchField.setOnClickListener(v -> enterSearchMode());
        etSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !isSearching) enterSearchMode();
        });
        ivSearchBack.setOnClickListener(v -> exitSearchMode());
        ivSearchClear.setOnClickListener(v -> {
            setQueryText("");
            if (isSearching) {
                showRecentRows();
            } else {
                showExploreGrid();
            }
        });
        btnSearchScan.setOnClickListener(v -> {
            if (Constants.DECORATIVE_UI) ToastUtil.showNotAvailable(requireContext());
        });
        if (!Constants.DECORATIVE_UI) btnSearchScan.setVisibility(View.GONE);
    }

    private void enterSearchMode() {
        if (isSearching) return;
        applyMode(true);
        etSearch.requestFocus();
        showKeyboard();
        showRecentRows();
    }

    private void exitSearchMode() {
        if (!isSearching) return;
        setQueryText("");
        etSearch.clearFocus();
        hideKeyboard();
        applyMode(false);
        loadChipFeed();
    }

    /**
     * PAGE 3 <-> PAGE 4 chrome. Idle: 38dp field with 12dp margins, chips, grid.
     * Focused: 56dp row, back arrow, 40dp field, tabs, rows.
     */
    private void applyMode(boolean searching) {
        isSearching = searching;

        int rowMargin = getResources().getDimensionPixelSize(R.dimen.explore_search_row_margin);
        int rowPaddingEnd = getResources().getDimensionPixelSize(R.dimen.search_row_padding_end);
        int backPaddingStart = getResources().getDimensionPixelSize(R.dimen.search_back_padding_start);
        int fieldMarginStart = getResources().getDimensionPixelSize(R.dimen.search_field_margin_start);

        ViewGroup.LayoutParams rowParams = searchRow.getLayoutParams();
        rowParams.height = searching
                ? getResources().getDimensionPixelSize(R.dimen.search_top_row_height)
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        searchRow.setLayoutParams(rowParams);
        searchRow.setPadding(
                searching ? backPaddingStart : rowMargin,
                searching ? 0 : rowMargin,
                searching ? rowPaddingEnd : rowMargin,
                searching ? 0 : rowMargin);

        ViewGroup.LayoutParams fieldParams = layoutSearchField.getLayoutParams();
        fieldParams.height = getResources().getDimensionPixelSize(searching
                ? R.dimen.search_field_height_active
                : R.dimen.search_field_height_idle);
        ((LinearLayout.LayoutParams) fieldParams).setMarginStart(searching ? fieldMarginStart : 0);
        layoutSearchField.setLayoutParams(fieldParams);
        ((LinearLayout) layoutSearchField).setGravity(searching ? android.view.Gravity.START : android.view.Gravity.CENTER);

        etSearch.setLayoutParams(searching
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ivSearchBack.setVisibility(searching ? View.VISIBLE : View.GONE);
        btnSearchScan.setVisibility(searching || !Constants.DECORATIVE_UI ? View.GONE : View.VISIBLE);
        chipsScroll.setVisibility(searching ? View.GONE : View.VISIBLE);
        searchTabsContainer.setVisibility(searching ? View.VISIBLE : View.GONE);

        if (!searching) {
            ivSearchClear.setVisibility(View.GONE);
            showEmptyState(false);
        } else {
            ivSearchClear.setVisibility(etSearch.getText() != null && etSearch.getText().length() > 0
                    ? View.VISIBLE : View.GONE);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Data                                                                */
    /* ------------------------------------------------------------------ */

    private void showExploreGrid() {
        showEmptyState(false);
        rvExploreGrid.setAdapter(gridAdapter);
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
        rvExploreGrid.setLayoutManager(layoutManager);
        if (rvExploreGrid.getItemDecorationCount() == 0) {
            rvExploreGrid.addItemDecoration(gridGapDecoration);
        }
    }

    private void showSearchRows() {
        rvExploreGrid.setAdapter(rowAdapter);
        rvExploreGrid.setLayoutManager(new LinearLayoutManager(getContext()));
        if (rvExploreGrid.getItemDecorationCount() > 0) {
            rvExploreGrid.removeItemDecoration(gridGapDecoration);
        }
    }

    private void loadExploreFeed() {
        searchRepository.getExplorePosts(30, 0).observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                explorePosts.clear();
                explorePosts.addAll(resource.data);
                if (!isSearching) gridAdapter.notifyDataSetChanged();
            }
        });
    }

    /** Runs the existing account / tag search for the selected tab. */
    private void runSearch(String query) {
        if (!isSearching) applyMode(true);
        showSearchRows();

        if (searchTab == TAB_AUDIO || searchTab == TAB_PLACES) {
            searchRows.clear();
            rowAdapter.notifyDataSetChanged();
            showEmptyState(true);
            return;
        }

        boolean wantAccounts = (searchTab == TAB_TOP || searchTab == TAB_ACCOUNTS);
        boolean wantTags = (searchTab == TAB_TOP || searchTab == TAB_TAGS);

        accountResults.clear();
        tagResultCount = 0;
        rebuildRows();
        showEmptyState(false);

        if (wantAccounts) {
            searchRepository.searchUsers(query).observe(getViewLifecycleOwner(), resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS) {
                    accountResults.clear();
                    if (resource.data != null) accountResults.addAll(resource.data);
                    rebuildRows();
                }
            });
        }
        if (wantTags) {
            searchRepository.searchTags(query).observe(getViewLifecycleOwner(), resource -> {
                if (resource != null && resource.status == Resource.Status.SUCCESS) {
                    tagResultCount = resource.data == null ? 0 : resource.data.size();
                    rebuildRows();
                }
            });
        }
    }

    private void rebuildRows() {
        searchRows.clear();
        for (Profile profile : accountResults) {
            searchRows.add(SearchRow.account(profile));
        }
        if (searchTab == TAB_TOP || searchTab == TAB_TAGS) {
            String query = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
            if (!query.isEmpty()) {
                searchRows.add(SearchRow.tag(
                        query.replace("#", ""),
                        getString(R.string.tag_posts_count, formatTagCount(tagResultCount))));
            }
        }
        rowAdapter.notifyDataSetChanged();
        showEmptyState(searchRows.isEmpty());
    }

    /** PAGE 4 — focused with an empty query: existing recents logic. */
    private void showRecentRows() {
        showSearchRows();
        if (searchTab == TAB_AUDIO || searchTab == TAB_PLACES) {
            searchRows.clear();
            rowAdapter.notifyDataSetChanged();
            showEmptyState(true);
            return;
        }
        searchRows.clear();
        List<String> recents = recentSearchStore.getRecentSearches();
        if (!recents.isEmpty()) {
            searchRows.add(SearchRow.header());
            for (String query : recents) {
                searchRows.add(SearchRow.recent(query));
            }
        }
        rowAdapter.notifyDataSetChanged();
        // No recents yet -> nothing to show, and no "No results" notice either.
        showEmptyState(false);
    }

    private void showEmptyState(boolean show) {
        if (searchEmptyState == null) return;
        searchEmptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            boolean decorative = searchTab == TAB_AUDIO || searchTab == TAB_PLACES;
            ivSearchEmptyIcon.setImageResource(decorative
                    ? (searchTab == TAB_AUDIO ? R.drawable.ic_reels : R.drawable.ic_location)
                    : R.drawable.ic_search);
            tvSearchEmptyTitle.setText(R.string.search_no_results);
        }
    }

    private void setQueryText(String text) {
        settingText = true;
        etSearch.setText(text);
        etSearch.setSelection(etSearch.getText().length());
        settingText = false;
        ivSearchClear.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Counts for tag rows. The app has no tag counter (the existing tag search
     * returns the matching posts), so the count is the number of results that
     * query returned.
     */
    private String formatTagCount(int count) {
        if (count < 100) {
            return getString(R.string.tag_count_fewer_than_100);
        }
        if (count < 1000) {
            return String.format(Locale.US, "%d+", (count / 100) * 100);
        }
        if (count < 1000000) {
            return String.format(Locale.US, "%dK", count / 1000);
        }
        return String.format(Locale.US, "%.1fM", count / 1000000.0);
    }

    private void showKeyboard() {
        etSearch.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isSearching) loadExploreFeed();
    }

    /* ------------------------------------------------------------------ */
    /* Rows                                                                */
    /* ------------------------------------------------------------------ */

    /** One line of the PAGE 4 result list. */
    static class SearchRow {
        static final int ACCOUNT = 0;
        static final int TAG = 1;
        static final int PLACE = 2;
        static final int RECENT = 3;
        static final int HEADER = 4;

        final int type;
        final Profile profile;
        final String title;
        final String subtitle;

        private SearchRow(int type, Profile profile, String title, String subtitle) {
            this.type = type;
            this.profile = profile;
            this.title = title;
            this.subtitle = subtitle;
        }

        static SearchRow account(Profile profile) {
            return new SearchRow(ACCOUNT, profile, profile.getUsername(), profile.getFullName());
        }

        static SearchRow tag(String tag, String subtitle) {
            return new SearchRow(TAG, null, "#" + tag, subtitle);
        }

        static SearchRow recent(String query) {
            return new SearchRow(RECENT, null, query, null);
        }

        static SearchRow header() {
            return new SearchRow(HEADER, null, null, null);
        }
    }

    static class SearchRowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        interface Listener {
            void onAccountClick(Profile profile);
            void onRecentClick(String query);
            void onRecentRemove(String query);
            void onClearAllRecents();
            void onPlaceClick();
        }

        private final List<SearchRow> items;
        private final Listener listener;

        SearchRowAdapter(List<SearchRow> items, Listener listener) {
            this.items = items;
            this.listener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == SearchRow.HEADER) {
                return new HeaderHolder(inflater.inflate(R.layout.item_search_recent_header, parent, false));
            }
            return new RowHolder(inflater.inflate(R.layout.item_search_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SearchRow row = items.get(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).tvClearAll.setOnClickListener(v -> {
                    if (listener != null) listener.onClearAllRecents();
                });
                return;
            }
            RowHolder h = (RowHolder) holder;

            // Reset the recycled row.
            h.ivAvatar.setVisibility(View.VISIBLE);
            h.layoutCircle.setVisibility(View.GONE);
            h.ivVerified.setVisibility(View.GONE);
            h.ivTrailing.setVisibility(View.GONE);
            h.tvSubtitle.setVisibility(View.GONE);
            h.tvSubtitle.setText("");
            h.tvTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

            switch (row.type) {
                case SearchRow.ACCOUNT:
                    h.tvTitle.setText(row.title);
                    if (row.subtitle != null && !row.subtitle.isEmpty()) {
                        h.tvSubtitle.setText(row.subtitle);
                        h.tvSubtitle.setVisibility(View.VISIBLE);
                    }
                    if (row.profile != null && row.profile.isVerified()) {
                        h.ivVerified.setVisibility(View.VISIBLE);
                    }
                    if (row.profile != null && row.profile.getAvatarUrl() != null
                            && !row.profile.getAvatarUrl().isEmpty()) {
                        com.insta.clone.util.GlideUtils.loadAvatar(h.ivAvatar, row.profile.getAvatarUrl());
                    } else {
                        h.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
                    }
                    h.itemView.setOnClickListener(v -> {
                        if (listener != null && row.profile != null) listener.onAccountClick(row.profile);
                    });
                    break;

                case SearchRow.TAG:
                    showCircleRow(h, R.drawable.ic_hashtag);
                    h.tvTitle.setText(row.title);
                    h.tvSubtitle.setText(row.subtitle);
                    h.tvSubtitle.setVisibility(View.VISIBLE);
                    h.itemView.setOnClickListener(null);
                    break;

                case SearchRow.PLACE:
                    showCircleRow(h, R.drawable.ic_location);
                    h.tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    h.tvTitle.setText(row.title);
                    h.tvSubtitle.setText(row.subtitle);
                    h.tvSubtitle.setVisibility(View.VISIBLE);
                    h.itemView.setOnClickListener(v -> {
                        if (listener != null) listener.onPlaceClick();
                    });
                    break;

                case SearchRow.RECENT:
                    showCircleRow(h, R.drawable.ic_search);
                    h.tvTitle.setText(row.title);
                    h.ivTrailing.setVisibility(View.VISIBLE);
                    h.itemView.setOnClickListener(v -> {
                        if (listener != null) listener.onRecentClick(row.title);
                    });
                    h.ivTrailing.setOnClickListener(v -> {
                        if (listener != null) listener.onRecentRemove(row.title);
                    });
                    break;

                default:
                    break;
            }
        }

        private void showCircleRow(RowHolder h, int iconRes) {
            h.ivAvatar.setVisibility(View.GONE);
            h.layoutCircle.setVisibility(View.VISIBLE);
            h.ivCircleIcon.setImageResource(iconRes);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class RowHolder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final View layoutCircle;
            final ImageView ivCircleIcon;
            final TextView tvTitle;
            final ImageView ivVerified;
            final TextView tvSubtitle;
            final ImageView ivTrailing;

            RowHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivRowAvatar);
                layoutCircle = itemView.findViewById(R.id.layoutRowCircle);
                ivCircleIcon = itemView.findViewById(R.id.ivRowCircleIcon);
                tvTitle = itemView.findViewById(R.id.tvRowTitle);
                ivVerified = itemView.findViewById(R.id.ivRowVerified);
                tvSubtitle = itemView.findViewById(R.id.tvRowSubtitle);
                ivTrailing = itemView.findViewById(R.id.ivRowTrailing);
            }
        }

        static class HeaderHolder extends RecyclerView.ViewHolder {
            final TextView tvClearAll;

            HeaderHolder(@NonNull View itemView) {
                super(itemView);
                tvClearAll = itemView.findViewById(R.id.tvRecentClearAll);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Grid                                                                */
    /* ------------------------------------------------------------------ */

    /** PAGE 3 grid gutters: 1dp split across neighbours, columns stay equal. */
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

    static class ExploreGridAdapter extends RecyclerView.Adapter<ExploreGridAdapter.Holder> {
        private final List<Post> items;
        private final OnPostClickListener listener;

        interface OnPostClickListener { void onPostClick(Post post); }

        ExploreGridAdapter(List<Post> items, OnPostClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_media, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Post p = items.get(position);
            String url = p.getThumbnailUrl();
            if (url == null || url.isEmpty()) url = p.getFirstMediaUrl();

            com.insta.clone.util.GlideUtils.load(holder.ivMedia, "posts", url);

            // PAGE 3: carousel / reel markers (real post data, not decoration).
            boolean carousel = p.getMediaUrls() != null && p.getMediaUrls().size() > 1;
            holder.ivMultiBadge.setVisibility(carousel ? View.VISIBLE : View.GONE);
            holder.ivReelBadge.setVisibility(p.isReel() ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onPostClick(p);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView ivMedia;
            final ImageView ivMultiBadge;
            final ImageView ivReelBadge;
            Holder(@NonNull View itemView) {
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

# DESIGN CHECKLIST

## PAGE 3 — EXPLORE (idle Search tab)

| # | Requirement | Implementation | Status |
|---|---|---|---|
| 1 | Top row 12dp margins | `searchRow` padding 12dp (`explore_search_row_margin`) in the idle state | done |
| 2 | Search field height 38dp, radius 10dp, bg surfaceVariant | `layoutSearchField` @ `search_field_height_idle` (38dp), `@drawable/bg_search_field` (`search_field_radius` 10dp + `color_surface_variant`) | done |
| 3 | ic_search 18dp + hint 'Search' 16sp textSecondary centered | `ivSearchIcon` 18dp (`search_field_icon_size`) tinted textSecondary; `et_search` hint @ 16sp `textSecondary`, field `gravity=center` | done |
| 4 | 38dp icon button ic_scan to its right (decorative) | `btnSearchScan` 38dp (`search_scan_button_size`) + 24dp `ic_scan`; decorative toast (`DECORATIVE_UI`, hidden when off) | done |
| 5 | Tapping the field opens the existing search screen (PAGE 4) | field click / `et_search` focus → `enterSearchMode()` (56dp row, back arrow, 40dp field, tabs, keyboard) | done |
| 6 | Chips row: horizontal scroll, 12dp start, 8dp gaps, 8dp top margin | `chipsScroll` (HorizontalScrollView, `explore_chips_padding_start` 12dp, marginTop 8dp), chips separated by `explore_chip_gap` 8dp | done |
| 7 | Chips: height 34dp, radius 8dp, 1dp divider border, 14sp Medium, 12dp horizontal padding | `Widget.Insta.Chip` style + `bg_chip_outline` (1dp `color_divider`) — all from tokens | done |
| 8 | 'Reels' chip: ic_reels 16dp + text, filters the grid to reels with the existing is_reel query | `chipReels` (16dp `explore_chip_icon_size` compound drawable) → `ReelRepository.getReels()` (`is_reel=eq.true`) | done |
| 9 | 'Shop' chip: ic_shopping_bag + text, decorative | `chipShop` → toast only, never selected (hidden when the flag is off) | done |
| 10 | Architecture / Travel / Decor / Food / Art / Nature run the existing tag search | `chipArchitecture…chipNature` → `SearchRepository.searchTags(tag)` into the grid; tapping again deselects | done |
| 11 | Selected chip = textPrimary fill with background-colored text | `bg_chip_selected` + `color_text_primary` / `color_background` label + compound-drawable tint (`applyChipSelection()`) | done |
| 12 | Grid: uniform 3 columns, 1dp gaps, square tiles edge to edge (replaces the big-tile pattern) | `GridLayoutManager(3)` without span lookup + `GridGapDecoration` (`explore_grid_gap` 1dp = 3px at 440dpi) + square `SquareImageView` cells | done |
| 13 | Badges top-right: ic_multi_image for carousels, ic_play for reels, white 18dp with a soft dark shadow | `ivMultiBadge` / `ivReelBadge` 18dp `@color/white` + `bg_badge_shadow` (negative-inset oval, `color_badge_shadow`); visibility from real post data | done |
| 14 | Shimmer tiles, pull-to-refresh and infinite scroll stay as they are | untouched — the feed load path (`getExplorePosts(30, 0)`) is unchanged | done |

## PAGE 4 — SEARCH (focused + results)

| # | Requirement | Implementation | Status |
|---|---|---|---|
| 15 | Top row 56dp | `searchRow` height `search_top_row_height` (56dp) in the search state, vertical padding 0 | done |
| 16 | ic_arrow_back 24dp (16dp start) | `ivSearchBack` 24dp, row paddingStart `search_back_padding_start` (16dp) | done |
| 17 | Search field height 40dp, radius 10dp, bg surfaceVariant, 16sp text | `layoutSearchField` @ `search_field_height_active` (40dp), same `bg_search_field`, `et_search` 16sp | done |
| 18 | ic_close 20dp inside its right edge when text exists | `ivSearchClear` 20dp (`search_field_close_size`), inside the field, VISIBLE only when the query is non-empty | done |
| 19 | 12dp margins | row paddingEnd `search_row_padding_end` (12dp) + field marginStart `search_field_margin_start` (12dp) | done |
| 20 | Keyboard action = search | `imeOptions=actionSearch` + `setOnEditorActionListener` → stores the recent + runs the search + hides the keyboard | done |
| 21 | Tabs row 44dp: Top / Accounts / Audio / Tags / Places, 5 equal text tabs, 14sp Medium | `search_tabs` 44dp, `tabMode=fixed` + `tabGravity=fill`, 5 `TabItem`s, `TextAppearance.Insta.SearchTab` (14sp Medium) | done |
| 22 | Selected = textPrimary + 2dp underline spanning the tab; unselected = textSecondary | `selector_search_tab_text` + `tabIndicatorColor=?attr/themeTextPrimary` + `tabIndicatorHeight` 2dp + `tabIndicatorFullWidth` (default = spanning) | done |
| 23 | 1dp divider under the row | `View` @ `divider_hairline` / `?attr/themeDivider` under the TabLayout | done |
| 24 | Existing logic: Top = accounts + tags, Accounts, Tags | `runSearch()`: TAB_TOP runs both `searchUsers` + `searchTags` (accounts first, then the tag row), TAB_ACCOUNTS / TAB_TAGS run one each | done |
| 25 | Audio and Places are decorative and show a centered empty state 'No results' | tab 2 / 4 → toast (`DECORATIVE_UI`) + `searchEmptyState` with the tab's icon and "No results" | done |
| 26 | Rows 72dp, 16dp horizontal padding, no dividers | `item_search_row` @ `search_row_height` (72dp) / `search_row_padding_horizontal` (16dp), no divider views | done |
| 27 | Account row: avatar 56dp, username 14sp Bold (+ ic_verified 14dp), second line full name 14sp textSecondary | `ivRowAvatar` 56dp, `tvRowTitle` 14sp bold, `ivRowVerified` 14dp shown when `profile.isVerified()`, `tvRowSubtitle` 14sp textSecondary | done |
| 28 | Tag row: 56dp circle (1dp divider border) with ic_hashtag 24dp, '#tag' 14sp Bold, '{count} posts' 14sp textSecondary (113K / 21K / 500+ / 'Fewer than 100') | `layoutRowCircle` + `bg_circle_outline` + 24dp `ic_hashtag`; `formatTagCount()` implements the four tiers; `tag_posts_count` string | done |
| 29 | Place row (decorative): 56dp circle with ic_location, name 14sp Medium, subtitle 14sp textSecondary | `SearchRow.PLACE` branch (medium typeface + `ic_location`) + decorative toast | done |
| 30 | Focused with an empty query: 'Recent' 16sp Bold header + blue 'Clear all' 14sp | `item_search_recent_header` (`search_page_title_size`, `TVClearAll` @ `search_clear_all_size` in `color_primary`) | done |
| 31 | Recent rows: same row style, ic_close 20dp at the end, existing recents logic | `SearchRow.RECENT` uses the same 72dp row with a magnifier circle + `ivRowTrailing`; backed by `RecentSearchStore` (get/add/remove/clearAll) | done |

## Notes / assumptions

- **Tag counts**: the app has no tag counter (no tags table/API and repositories must stay untouched), so the second line uses the number of posts the *existing* `searchTags` query returned. With the current data that is always < 100, hence "Fewer than 100 posts"; the 500+/21K/113K tiers are implemented in `formatTagCount()` for when a real count is available.
- **Reference icon differences**: the reference screenshot's first chip reads "IGTV" (the pre-2021 label); the written spec names `Reels` with `ic_reels` and the is_reel filter, so the spec label/asset is used. Its tab glyphs are Instagram's current variants.
- **Decorative elements** follow `Constants.DECORATIVE_UI`: the scan button, the Shop chip and the Audio/Places tabs toast "Not available"; the scan button and Shop chip are hidden when the flag is off (the 5 tabs stay so the tab row keeps its 5 equal columns).
- **Place rows** are only reachable from the decorative Places tab (so the toast is their only action); tag rows are display-only in this design pass.
- **Status-bar clearance**: the window is edge-to-edge (PAGE 0), so the fragment applies the top system-bar inset — otherwise the 56dp / 38dp rows sit under the status bar.
- **Superseded from PAGE 2**: `item_saved_media` badges now carry the soft dark halo (`bg_badge_shadow`) requested for the grid badges; the 18dp white icon size is unchanged.

## Verification run

- `./gradlew :app:assembleDebug` (JDK 17) → BUILD SUCCESSFUL (all resource references resolve, view-binding types match, Java compiles).
- Installed on the device (1080×2400 @440dpi = 393dp) and inspected both states in dark **and** light themes. Measured: idle row 62dp (38dp field + 12dp margins) with the scan button 38dp at 12dp end padding; chips 34dp tall starting at 12dp with 8dp gaps and 8dp above; grid cells 357px square with 3px (1.09dp) gutters edge-to-edge; focused row 56dp below the status bar, back arrow 24dp at 16dp, field 40dp ending 12dp from the right, clear icon 20dp inside the field edge, tabs 44dp + 1dp divider with 5 equal 216px columns; result rows 72dp with a 56dp leading circle at 16dp.
- Functional checks: Reels chip selects (textPrimary fill, dark label+icon) and reloads the grid through the is_reel query; tag chips run the tag search; typing shows account/tag rows; Tags tab shows the tag row; Audio tab shows the centered "No results"; back arrow returns to the idle chrome; the IME search action stores a recent and the empty-query state renders the "Recent" header, the stored rows with their inline close buttons and the blue "Clear all".
