# PAGE 2 — PROFILE · Design-only changes

Design-only pass on the Profile screen. No behaviour changes: ViewModels,
repositories, API calls, models, navigation targets and code-referenced view IDs
are untouched. Every color/size comes from tokens (`values/colors.xml`,
`values/dimens.xml`, `values-night/…`, theme attrs).

Complete code of every changed file follows (7 files: 2 token files, 2 new
resources, 3 layouts/classes).

> `values/dimens.xml` and `values/strings.xml` are shared token files — the full
> file is reproduced; only the `PAGE 2 — PROFILE` token block and the
> `Profile (PAGE 2)` strings are new here.

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

## app/src/main/res/color/selector_profile_tab_icon.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 2 — Profile tab icons: selected = textPrimary, unselected = textSecondary.
    Both colors are config-aware PAGE 0 tokens (values-night overrides them).
-->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/color_text_primary" android:state_selected="true" />
    <item android:color="@color/color_text_secondary" />
</selector>
```

## app/src/main/res/drawable/bg_highlight_ring.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 2 — Profile highlights row: 1dp divider ring around a highlight circle.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/transparent" />
    <stroke
        android:width="@dimen/profile_highlight_ring"
        android:color="@color/color_divider" />
</shape>
```

## app/src/main/res/layout/fragment_profile.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/themeBackground"
    android:orientation="vertical">

    <!-- ============================================================= -->
    <!-- Top bar — 56dp. Own profile: username + chevron (decorative),  -->
    <!-- right: create (+), settings (menu).                            -->
    <!-- ============================================================= -->
    <LinearLayout
        android:id="@+id/topBar"
        android:layout_width="match_parent"
        android:layout_height="@dimen/profile_top_bar_height"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/profile_top_bar_padding_horizontal"
        android:paddingEnd="@dimen/profile_top_bar_padding_horizontal">

        <TextView
            android:id="@+id/tv_username"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:maxLines="1"
            android:text=""
            android:textColor="?attr/themeTextPrimary"
            android:textSize="@dimen/profile_username_size"
            android:textStyle="bold" />

        <ImageView
            android:id="@+id/ivChevronDown"
            android:layout_width="@dimen/profile_chevron_size"
            android:layout_height="@dimen/profile_chevron_size"
            android:layout_marginStart="@dimen/profile_chevron_gap"
            android:src="@drawable/ic_chevron_down"
            app:tint="?attr/themeTextPrimary"
            android:contentDescription="@null" />

        <View
            android:layout_width="0dp"
            android:layout_height="1dp"
            android:layout_weight="1" />

        <ImageView
            android:id="@+id/iv_settings"
            android:layout_width="@dimen/profile_top_bar_icon"
            android:layout_height="@dimen/profile_top_bar_icon"
            android:layout_marginEnd="@dimen/profile_top_bar_icon_gap"
            android:src="@drawable/ic_add"
            app:tint="?attr/themeTextPrimary"
            android:contentDescription="Create" />

        <ImageView
            android:id="@+id/iv_menu"
            android:layout_width="@dimen/profile_top_bar_icon"
            android:layout_height="@dimen/profile_top_bar_icon"
            android:src="@drawable/ic_menu"
            app:tint="?attr/themeTextPrimary"
            android:contentDescription="Settings" />
    </LinearLayout>

    <!--
        Top bar — other user (design variant: back arrow, 20sp username, more).
        Not reachable today: the app has no other-user profile destination, so it
        stays gone until such a navigation target exists.
    -->
    <LinearLayout
        android:id="@+id/topBarOther"
        android:layout_width="match_parent"
        android:layout_height="@dimen/profile_top_bar_height"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="@dimen/profile_top_bar_padding_horizontal"
        android:paddingEnd="@dimen/profile_top_bar_padding_horizontal"
        android:visibility="gone">

        <ImageView
            android:id="@+id/ivArrowBack"
            android:layout_width="@dimen/profile_nav_icon"
            android:layout_height="@dimen/profile_nav_icon"
            android:src="@drawable/ic_arrow_back"
            app:tint="?attr/themeTextPrimary"
            android:contentDescription="Back" />

        <TextView
            android:id="@+id/tv_username_other"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/profile_chevron_gap"
            android:layout_weight="1"
            android:ellipsize="end"
            android:maxLines="1"
            android:text=""
            android:textColor="?attr/themeTextPrimary"
            android:textSize="@dimen/profile_username_other_size"
            android:textStyle="bold" />

        <ImageView
            android:id="@+id/iv_more_horiz_other"
            android:layout_width="@dimen/profile_nav_icon"
            android:layout_height="@dimen/profile_nav_icon"
            android:src="@drawable/ic_more_horiz"
            app:tint="?attr/themeTextPrimary"
            android:contentDescription="More options" />
    </LinearLayout>

    <!-- ============================================================= -->
    <!-- Header — avatar 86dp (gradient ring when there is a story) +  -->
    <!-- display name over three equal stat columns.                    -->
    <!-- ============================================================= -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="@dimen/profile_header_padding">

        <FrameLayout
            android:layout_width="@dimen/profile_avatar_frame"
            android:layout_height="@dimen/profile_avatar_frame">

            <com.insta.clone.ui.widget.StoryRingView
                android:id="@+id/profileAvatarRing"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                app:gapWidth="@dimen/profile_ring_gap"
                app:ringState="unseen"
                app:ringStrokeWidth="@dimen/profile_ring_stroke" />

            <de.hdodenhof.circleimageview.CircleImageView
                android:id="@+id/iv_avatar"
                android:layout_width="@dimen/profile_avatar_size"
                android:layout_height="@dimen/profile_avatar_size"
                android:layout_gravity="center"
                android:src="@drawable/ic_person_placeholder"
                android:contentDescription="Profile Picture" />
        </FrameLayout>

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/profile_header_gap"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tv_display_name"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:ellipsize="end"
                android:maxLines="1"
                android:text=""
                android:textColor="?attr/themeTextPrimary"
                android:textSize="@dimen/profile_display_name_size"
                android:textStyle="bold"
                android:visibility="gone" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/profile_stat_margin_top"
                android:orientation="horizontal">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:gravity="start"
                    android:orientation="vertical">

                    <TextView
                        android:id="@+id/tv_posts_count"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text=""
                        android:textColor="?attr/themeTextPrimary"
                        android:textSize="@dimen/profile_stat_number_size"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/profile_stat_posts"
                        android:textColor="?attr/themeTextPrimary"
                        android:textSize="@dimen/profile_stat_label_size" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:gravity="start"
                    android:orientation="vertical">

                    <TextView
                        android:id="@+id/tv_followers_count"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text=""
                        android:textColor="?attr/themeTextPrimary"
                        android:textSize="@dimen/profile_stat_number_size"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/profile_stat_followers"
                        android:textColor="?attr/themeTextPrimary"
                        android:textSize="@dimen/profile_stat_label_size" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:gravity="start"
                    android:orientation="vertical">

                    <TextView
                        android:id="@+id/tv_following_count"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text=""
                        android:textColor="?attr/themeTextPrimary"
                        android:textSize="@dimen/profile_stat_number_size"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/profile_stat_following"
                        android:textColor="?attr/themeTextPrimary"
                        android:textSize="@dimen/profile_stat_label_size" />
                </LinearLayout>
            </LinearLayout>
        </LinearLayout>
    </LinearLayout>

    <!-- Bio — 14sp, 4 lines max then "more" -->
    <TextView
        android:id="@+id/tv_bio"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/profile_bio_margin_top"
        android:ellipsize="end"
        android:maxLines="4"
        android:paddingStart="@dimen/profile_header_padding"
        android:paddingEnd="@dimen/profile_header_padding"
        android:text=""
        android:textColor="?attr/themeTextPrimary"
        android:textSize="@dimen/profile_bio_size" />

    <!-- ============================================================= -->
    <!-- Buttons — 34dp, radius 8dp, surfaceVariant, 14sp Bold          -->
    <!-- ============================================================= -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/profile_buttons_margin_top"
        android:orientation="horizontal"
        android:paddingStart="@dimen/profile_header_padding"
        android:paddingEnd="@dimen/profile_header_padding">

        <!-- Other user (design variant, not reachable) -->
        <TextView
            android:id="@+id/btnFollow"
            android:layout_width="0dp"
            android:layout_height="@dimen/profile_button_height"
            android:layout_weight="1"
            android:background="@drawable/bg_button_primary"
            android:gravity="center"
            android:text="@string/profile_follow"
            android:textColor="@color/white"
            android:textSize="@dimen/text_button"
            android:textStyle="bold"
            android:visibility="gone" />

        <TextView
            android:id="@+id/btnMessage"
            android:layout_width="0dp"
            android:layout_height="@dimen/profile_button_height"
            android:layout_marginStart="@dimen/profile_button_gap"
            android:layout_weight="1"
            android:background="@drawable/bg_button_secondary"
            android:gravity="center"
            android:text="@string/profile_message"
            android:textColor="?attr/themeTextPrimary"
            android:textSize="@dimen/text_button"
            android:textStyle="bold"
            android:visibility="gone" />

        <!-- Own profile -->
        <TextView
            android:id="@+id/btnEditProfile"
            android:layout_width="0dp"
            android:layout_height="@dimen/profile_button_height"
            android:layout_weight="1"
            android:background="@drawable/bg_button_secondary"
            android:gravity="center"
            android:text="@string/profile_edit"
            android:textColor="?attr/themeTextPrimary"
            android:textSize="@dimen/text_button"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/btnShareProfile"
            android:layout_width="0dp"
            android:layout_height="@dimen/profile_button_height"
            android:layout_marginStart="@dimen/profile_button_gap"
            android:layout_weight="1"
            android:background="@drawable/bg_button_secondary"
            android:gravity="center"
            android:text="@string/profile_share"
            android:textColor="?attr/themeTextPrimary"
            android:textSize="@dimen/text_button"
            android:textStyle="bold" />

        <!-- Decorative: suggest / add people -->
        <FrameLayout
            android:id="@+id/btnAddPerson"
            android:layout_width="@dimen/profile_button_square"
            android:layout_height="@dimen/profile_button_height"
            android:layout_marginStart="@dimen/profile_button_gap"
            android:background="@drawable/bg_button_secondary">

            <ImageView
                android:layout_width="@dimen/profile_button_icon"
                android:layout_height="@dimen/profile_button_icon"
                android:layout_gravity="center"
                android:src="@drawable/ic_add_person"
                app:tint="?attr/themeTextPrimary"
                android:contentDescription="@null" />
        </FrameLayout>
    </LinearLayout>

    <!-- ============================================================= -->
    <!-- Highlights — own list starts with "New" (decorative)           -->
    <!-- ============================================================= -->
    <LinearLayout
        android:id="@+id/highlightsRow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/profile_highlights_margin_top"
        android:orientation="horizontal"
        android:paddingStart="@dimen/profile_header_padding"
        android:paddingEnd="@dimen/profile_header_padding">

        <LinearLayout
            android:id="@+id/layoutHighlightNew"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:orientation="vertical">

            <FrameLayout
                android:layout_width="@dimen/profile_highlight_size"
                android:layout_height="@dimen/profile_highlight_size"
                android:background="@drawable/bg_highlight_ring">

                <ImageView
                    android:id="@+id/ivHighlightIcon"
                    android:layout_width="@dimen/profile_highlight_icon"
                    android:layout_height="@dimen/profile_highlight_icon"
                    android:layout_gravity="center"
                    android:src="@drawable/ic_add"
                    app:tint="?attr/themeTextPrimary"
                    android:contentDescription="@null" />
            </FrameLayout>

            <TextView
                android:id="@+id/tvHighlightTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/profile_highlight_title_margin_top"
                android:text="@string/profile_highlight_new"
                android:textColor="?attr/themeTextPrimary"
                android:textSize="@dimen/profile_highlight_title_size" />
        </LinearLayout>
    </LinearLayout>

    <!-- ============================================================= -->
    <!-- Tabs — 48dp, icons only, 4 equal columns                       -->
    <!-- ============================================================= -->
    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tab_layout_profile"
        android:layout_width="match_parent"
        android:layout_height="@dimen/profile_tabs_height"
        android:background="?attr/themeBackground"
        app:tabGravity="fill"
        app:tabIconTint="@color/selector_profile_tab_icon"
        app:tabIndicatorColor="?attr/themeTextPrimary"
        app:tabIndicatorFullWidth="false"
        app:tabIndicatorHeight="@dimen/profile_tab_indicator"
        app:tabMode="fixed"
        app:tabPaddingStart="@dimen/profile_tab_padding"
        app:tabPaddingEnd="@dimen/profile_tab_padding"
        app:tabRippleColor="@null">

        <com.google.android.material.tabs.TabItem
            android:id="@+id/tab_posts"
            android:icon="@drawable/ic_grid" />

        <com.google.android.material.tabs.TabItem
            android:id="@+id/tab_reels"
            android:icon="@drawable/ic_reels" />

        <com.google.android.material.tabs.TabItem
            android:id="@+id/tab_reposts"
            android:icon="@drawable/ic_repost" />

        <com.google.android.material.tabs.TabItem
            android:id="@+id/tab_tagged"
            android:icon="@drawable/ic_tagged" />
    </com.google.android.material.tabs.TabLayout>

    <!-- ============================================================= -->
    <!-- Grid — 3 columns, 1dp gaps, square cells + empty state         -->
    <!-- ============================================================= -->
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rv_media_grid"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

        <LinearLayout
            android:id="@+id/profileEmptyState"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:orientation="vertical"
            android:padding="@dimen/profile_empty_padding"
            android:visibility="gone">

            <ImageView
                android:id="@+id/ivEmptyIcon"
                android:layout_width="@dimen/profile_empty_icon"
                android:layout_height="@dimen/profile_empty_icon"
                android:src="@drawable/ic_repost"
                app:tint="?attr/themeTextSecondary"
                android:contentDescription="@null" />

            <TextView
                android:id="@+id/tvEmptyTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/profile_empty_margin_top"
                android:gravity="center"
                android:textColor="?attr/themeTextPrimary"
                android:textSize="@dimen/text_title" />
        </LinearLayout>
    </FrameLayout>

</LinearLayout>
```

## app/src/main/res/layout/item_saved_media.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    Grid cell (Profile / Explore / Saved): square media with optional
    carousel (ic_multi_image) and reel (ic_play) badges, white 18dp top-right.
    The badges are hidden unless the hosting adapter turns them on.
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
        android:src="@drawable/ic_play"
        app:tint="@color/white"
        android:visibility="gone"
        android:contentDescription="Reel" />

</FrameLayout>
```

## app/src/main/java/com/insta/clone/ui/profile/ProfileFragment.java

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
import com.insta.clone.ui.create.CreatePostActivity;
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
            ivCreate.setOnClickListener(v -> startActivity(new Intent(requireContext(), CreatePostActivity.class)));
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

# DESIGN CHECKLIST

| # | Requirement (PAGE 2 — PROFILE) | Implementation | Status |
|---|---|---|---|
| 1 | Top bar 56dp | `fragment_profile.xml` `topBar` @ `@dimen/profile_top_bar_height` | done |
| 2 | Own: username 22sp Bold + `ic_chevron_down` 16dp (decorative) | `tv_username` @ `profile_username_size`; `ivChevronDown` 16dp → `wireDecorative(..., hideWhenOff=true)` | done |
| 3 | Right `ic_add` (Create) and `ic_menu` (Settings), 28dp, 24dp apart, 16dp end padding | `iv_settings`→`CreatePostActivity`, `iv_menu`→`SettingsActivity` (existing targets kept); `profile_top_bar_icon` 28dp, `profile_top_bar_icon_gap` 24dp, bar paddingEnd 16dp | done |
| 4 | Other user: `ic_arrow_back`, username 20sp Bold, right `ic_more_horiz` | `topBarOther` + `ivArrowBack`/`tv_username_other`(20sp bold)/`iv_more_horiz_other` — rendered, `gone` (no other-user destination exists) | done |
| 5 | Header 16dp padding; avatar 86dp left with 3dp gradient ring when there is an active story (no ring otherwise, no plus badge) | header `profile_header_padding`; `StoryRingView` (stroke 3dp, gap 2dp) in a 96dp frame + 86dp `CircleImageView` `iv_avatar`; no badge | done |
| 6 | 16dp gap; display name 16sp Bold on top | `profile_header_gap` 16dp; `tv_display_name` @ `profile_display_name_size` (16sp bold, hidden when empty) | done |
| 7 | Three equal stat columns: number 17sp Bold over 14sp regular label ('posts','followers','following') | 3 × `layout_weight=1` columns, `tv_*_count` 17sp bold + 14sp labels (lowercase strings) | done |
| 8 | Numbers formatted 84 / 1,134 / 12.3K / 1.2M | `formatStatCount()`: comma grouping < 10 000, then K/M with one decimal (trailing `.0` stripped) | done |
| 9 | Below the avatar: bio 14sp (4 lines max, then 'more'), 8dp gap | `tv_bio` @ `profile_bio_size`, `maxLines=4` + `ellipsize=end`, marginTop 8dp | done |
| 10 | Buttons row 12dp below, 8dp gaps, height 34dp, radius 8dp, bg surfaceVariant, text 14sp Bold textPrimary | row marginTop 12dp; buttons 34dp; `bg_button_secondary` (surfaceVariant + 8dp radius); `profile_button_gap` 8dp; `text_button` + bold + `?attr/themeTextPrimary` | done |
| 11 | Own: 'Edit profile' (w1), 'Share profile' (w1), 34dp `ic_add_person` button (decorative) | `btnEditProfile`/`btnShareProfile` weight 1; `btnAddPerson` 34dp + `ic_add_person` → decorative | done |
| 12 | Other user: 'Follow' (bg primary, white) / 'Following' (surfaceVariant) + 'Message' (surfaceVariant) + same `ic_add_person` | `btnFollow` (`bg_button_primary`, white text) + `btnMessage` (`bg_button_secondary`) — rendered, `gone` | done |
| 13 | Highlights row between buttons and tabs (circle 64dp, 1dp divider ring, title 12sp; own list starts with 'New') | `highlightsRow` sits between the buttons and the tabs: 64dp circle + `bg_highlight_ring` (1dp `color_divider`), `ic_add`, 'New' 12sp; decorative | done |
| 14 | Tabs 48dp, icons only, 4 equal columns: `ic_grid`, `ic_reels`, `ic_repost`, `ic_tagged` | `tab_layout_profile` 48dp, `tabMode=fixed` + `tabGravity=fill`, 4 `TabItem`s | done |
| 15 | Selected = textPrimary icon + 1.5dp textPrimary underline; unselected = textSecondary | `selector_profile_tab_icon` (state_selected), `tabIndicatorColor=?attr/themeTextPrimary`, `tabIndicatorHeight` 1.5dp, `tabIndicatorFullWidth=false` | done |
| 16 | Tabs 3 and 4 decorative, empty state ('No reposts yet' / 'No photos of you yet') | tab listener → toast (`DECORATIVE_UI`) + `showEmptyState()` sets the per-tab icon/title; tabs 1–2 keep their existing data paths | done |
| 17 | Grid 3 columns, 1dp gaps, square cells | `GridLayoutManager(3)` + `GridGapDecoration` (3px = 1.09dp at 440dpi) over square `SquareImageView` cells | done |
| 18 | `ic_multi_image` (white 18dp, top-right) for carousels and `ic_play` for reels | `ivMultiBadge` / `ivReelBadge` in `item_saved_media` (18dp, white, 6dp, top|end); visibility set in the profile grid adapter from real post data | done |
| 19 | Empty states unchanged but restyled; bottom nav from PAGE 0 with Profile selected | `profileEmptyState` uses `text_title` + theme tokens; `activity_main.xml` / nav graph / `MainActivity` untouched | done |

## Notes / assumptions

- **Other-user variant** (top bar + Follow/Message buttons) is fully described in XML but stays `gone`: the app has no other-user profile destination, and creating one would change navigation.
- **Avatar ring** is not data-backed (`Profile`/`Post` carry no story state and repositories must stay untouched), so the gradient ring always renders — as the reference image shows for this profile.
- **Highlights row**: the reference image does not show it (that account has no highlights); it is implemented to the written spec (64dp circle, 1dp divider ring, 12sp title, own list starts with 'New') and is decorative.
- **Saved tab replaced**: the old third tab (saved posts) is replaced by the specified repost tab. `PostRepository.getSavedPosts()`, `SavedFragment` and the `navigation_shop` destination are untouched, but no profile tab loads saved posts any more.
- **Display name** moved out of the bio into the header (16sp Bold); the bio shows the bio text only.
- **Grid gaps**: 1dp = 2.75px at 440dpi, implemented as 3px total (1.09dp) — the closest achievable pixel value.
- **Tab glyphs** use the in-repo assets named in the spec; the reference image uses Instagram's own variants (rounded-rect play icon, person-in-frame tagged icon, 3×3 grid).
- **Decorative elements** follow `Constants.DECORATIVE_UI`: chevron, `ic_add_person`, the highlights 'New' entry and the repost/tagged tabs toast "Not available" (the spec-marked ones disappear when the flag is off); 'Edit profile'/'Share profile' also toast but stay visible.

## Verification run

- `./gradlew :app:assembleDebug` (JDK 17) → BUILD SUCCESSFUL (resources resolve, view-binding types match, Java compiles).
- Installed on the device (1080×2400 @440dpi = 393dp) and inspected the live Profile screen in dark and light themes. Measured: top bar 56dp below the status bar, icons 28dp with 24dp gap and 16dp end padding, avatar frame 96dp with an 86dp circle-cropped photo, stat columns 83dp each (17sp/14sp), buttons 34dp with 8dp gaps, tabs 48dp with a 1.5dp selected underline, grid cells 357px square with 3px gutters.
- Functional checks: reels tab renders `ic_play` badges on every cell; reposts tab renders "No reposts yet"; tagged tab renders "No photos of you yet"; both decorative tabs show the "Not available" toast.
