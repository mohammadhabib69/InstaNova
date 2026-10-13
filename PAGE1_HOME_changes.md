# PAGE 1 — HOME · Design-only changes

Design-only pass on the Home feed. No behaviour changes: ViewModels, repositories,
API calls, models, navigation targets and code-referenced view IDs are untouched.
All colors/sizes come from tokens (`values/colors.xml`, `values/dimens.xml`, theme attrs).

Complete code of every changed file follows (12 files: 3 token files, 4 drawables/layouts,
1 new badge background × 2, 4 layouts, 3 presentation classes).

> Note on the three shared token files (`values/dimens.xml`, `values/colors.xml`,
> `values/strings.xml`): the full file is reproduced; only the `PAGE 1 — HOME`
> sections and the three home-only values (`story_tray_height` 98→108dp,
> `story_item_width` 76→84dp, `text_wordmark_home` 26sp→28sp) are new here.

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

## app/src/main/res/drawable/bg_story_add_badge_owner.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 1 — Home story tray: "Your story" add badge.
    Background is textPrimary with a background-colored 2dp border so the badge
    reads as a knock-out circle on top of the avatar.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/color_text_primary" />
    <stroke
        android:width="@dimen/story_add_badge_border"
        android:color="@color/color_background" />
</shape>
```

## app/src/main/res/drawable/bg_media_badge.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PAGE 1 — Home feed: media overlay badge (audio / repost attribution).
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/color_media_badge" />
</shape>
```

## app/src/main/res/layout/fragment_home.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/themeBackground">

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/appBarLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="?attr/themeBackground"
        app:elevation="0dp">

        <!-- HOME top bar — 48dp, hides on scroll down, returns on scroll up -->
        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="@dimen/home_top_bar_height"
            android:background="?attr/themeBackground"
            android:clipChildren="false"
            app:contentInsetStart="@dimen/home_top_bar_padding_horizontal"
            app:contentInsetEnd="@dimen/home_top_bar_padding_horizontal"
            app:layout_scrollFlags="scroll|enterAlways|snap">

            <androidx.constraintlayout.widget.ConstraintLayout
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:clipChildren="false">

                <!-- Left: create (+), opens the Create sheet -->
                <ImageView
                    android:id="@+id/btnCreate"
                    android:layout_width="@dimen/home_top_bar_icon_create"
                    android:layout_height="@dimen/home_top_bar_icon_create"
                    android:src="@drawable/ic_add"
                    app:tint="?attr/themeTextPrimary"
                    app:layout_constraintBottom_toBottomOf="parent"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toTopOf="parent"
                    android:contentDescription="Create post" />

                <!-- Center: wordmark -->
                <TextView
                    android:id="@+id/tvWordmark"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/wordmark"
                    android:textColor="?attr/themeTextPrimary"
                    android:textSize="@dimen/text_wordmark_home"
                    app:layout_constraintBottom_toBottomOf="parent"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toTopOf="parent" />

                <!-- Right: Activity (heart + unread dot) -->
                <FrameLayout
                    android:id="@+id/btnNotifications"
                    android:layout_width="@dimen/home_top_bar_icon_action"
                    android:layout_height="@dimen/home_top_bar_icon_action"
                    android:layout_marginEnd="@dimen/home_top_bar_icon_gap"
                    android:clipChildren="false"
                    app:layout_constraintBottom_toBottomOf="parent"
                    app:layout_constraintEnd_toStartOf="@id/btnDirect"
                    app:layout_constraintTop_toTopOf="parent">

                    <ImageView
                        android:layout_width="@dimen/home_top_bar_icon_action"
                        android:layout_height="@dimen/home_top_bar_icon_action"
                        android:layout_gravity="center"
                        android:src="@drawable/ic_heart"
                        app:tint="?attr/themeTextPrimary"
                        android:contentDescription="@string/notifications" />

                    <View
                        android:id="@+id/dotActivityBadge"
                        android:layout_width="@dimen/home_badge_dot_size"
                        android:layout_height="@dimen/home_badge_dot_size"
                        android:layout_gravity="top|end"
                        android:background="@drawable/bg_unread_dot"
                        android:visibility="gone" />
                </FrameLayout>

                <!-- Right: DM inbox (send + unread count) -->
                <FrameLayout
                    android:id="@+id/btnDirect"
                    android:layout_width="@dimen/home_top_bar_icon_action"
                    android:layout_height="@dimen/home_top_bar_icon_action"
                    android:clipChildren="false"
                    app:layout_constraintBottom_toBottomOf="parent"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintTop_toTopOf="parent">

                    <ImageView
                        android:layout_width="@dimen/home_top_bar_icon_action"
                        android:layout_height="@dimen/home_top_bar_icon_action"
                        android:layout_gravity="center"
                        android:src="@drawable/ic_send"
                        app:tint="?attr/themeTextPrimary"
                        android:contentDescription="@string/direct_messages" />

                    <TextView
                        android:id="@+id/tvDmBadge"
                        android:layout_width="wrap_content"
                        android:layout_height="@dimen/home_badge_count_height"
                        android:minWidth="@dimen/home_badge_count_height"
                        android:layout_gravity="top|end"
                        android:background="@drawable/bg_unread_dot"
                        android:gravity="center"
                        android:paddingStart="@dimen/home_badge_padding_horizontal"
                        android:paddingEnd="@dimen/home_badge_padding_horizontal"
                        android:textColor="@color/white"
                        android:textSize="@dimen/text_badge"
                        android:textStyle="bold"
                        android:visibility="gone" />
                </FrameLayout>

            </androidx.constraintlayout.widget.ConstraintLayout>

        </androidx.appcompat.widget.Toolbar>

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        android:id="@+id/swipeRefresh"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvFeed"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:overScrollMode="never" />

    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>

    <LinearLayout
        android:id="@+id/emptyState"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="@dimen/empty_state_padding"
        android:visibility="gone">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/welcome_to_instanova"
            android:textColor="?attr/themeTextPrimary"
            android:textSize="@dimen/text_title"
            android:textStyle="bold" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/empty_state_gap"
            android:text="@string/empty_state_subtitle"
            android:textColor="?attr/themeTextSecondary"
            android:textSize="@dimen/text_body" />
    </LinearLayout>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

## app/src/main/res/layout/item_story_tray.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="@dimen/story_item_width"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical">

    <!-- Ring outer 76dp, avatar 64dp (64 + 2 * (2dp stroke + 4dp gap) = 76dp) -->
    <FrameLayout
        android:layout_width="@dimen/story_ring_outer_size"
        android:layout_height="@dimen/story_ring_outer_size">

        <com.insta.clone.ui.widget.StoryRingView
            android:id="@+id/storyRingView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            app:gapWidth="@dimen/story_ring_gap_item"
            app:ringState="unseen"
            app:ringStrokeWidth="@dimen/story_ring_stroke_item" />

        <!-- "Your story" add badge: 22dp, bg textPrimary, ic_add in bg color,
             2dp background-colored border -->
        <FrameLayout
            android:id="@+id/ivAddBadge"
            android:layout_width="@dimen/story_add_badge_size"
            android:layout_height="@dimen/story_add_badge_size"
            android:layout_gravity="bottom|end"
            android:background="@drawable/bg_story_add_badge_owner"
            android:visibility="gone">

            <ImageView
                android:layout_width="@dimen/story_add_badge_icon"
                android:layout_height="@dimen/story_add_badge_icon"
                android:layout_gravity="center"
                android:src="@drawable/ic_add"
                app:tint="@color/color_background"
                android:contentDescription="Add story" />
        </FrameLayout>
    </FrameLayout>

    <!-- Unseen -> textPrimary, seen -> textSecondary (set in the adapter) -->
    <TextView
        android:id="@+id/tvUsername"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/story_label_margin_top"
        android:ellipsize="end"
        android:gravity="center"
        android:maxLines="1"
        android:textColor="?attr/themeTextPrimary"
        android:textSize="@dimen/text_meta" />

</LinearLayout>
```

## app/src/main/res/layout/item_feed_post.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/themeBackground"
    android:orientation="vertical"
    android:paddingBottom="@dimen/post_spacing_bottom">

    <!-- Post header — 56dp: avatar 32dp, 10dp gap, username 14sp Medium, 24dp more -->
    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="@dimen/post_header_height"
        android:paddingStart="@dimen/post_padding_horizontal"
        android:paddingEnd="@dimen/post_padding_horizontal">

        <de.hdodenhof.circleimageview.CircleImageView
            android:id="@+id/ivAvatar"
            android:layout_width="@dimen/post_header_avatar_size"
            android:layout_height="@dimen/post_header_avatar_size"
            android:src="@drawable/ic_person_placeholder"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <LinearLayout
            android:id="@+id/layoutAuthor"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/post_header_avatar_gap"
            android:orientation="vertical"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toStartOf="@id/ivOptions"
            app:layout_constraintStart_toEndOf="@id/ivAvatar"
            app:layout_constraintTop_toTopOf="parent">

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <TextView
                    android:id="@+id/tvUsername"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textAppearance="@style/TextAppearance.Insta.Username" />

                <com.insta.clone.ui.widget.VerifiedBadgeView
                    android:id="@+id/badgeVerified"
                    android:layout_width="@dimen/badge_verified_size"
                    android:layout_height="@dimen/badge_verified_size"
                    android:layout_marginStart="@dimen/post_line_gap"
                    android:visibility="gone" />
            </LinearLayout>

            <TextView
                android:id="@+id/tvLocation"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="?attr/themeTextSecondary"
                android:textSize="@dimen/text_meta"
                android:visibility="gone" />
        </LinearLayout>

        <ImageView
            android:id="@+id/ivOptions"
            android:layout_width="@dimen/post_icon_more"
            android:layout_height="@dimen/post_icon_more"
            android:src="@drawable/ic_more_horiz"
            app:tint="?attr/themeTextPrimary"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toTopOf="parent"
            android:contentDescription="Options" />

    </androidx.constraintlayout.widget.ConstraintLayout>

    <!-- Post media — full width, 1:1, edge to edge -->
    <com.insta.clone.ui.widget.DoubleTapHeartLayout
        android:id="@+id/doubleTapLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <!-- Single Image View -->
        <ImageView
            android:id="@+id/ivSingleMedia"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scaleType="centerCrop"
            android:adjustViewBounds="false"
            android:contentDescription="Post media" />

        <!-- Carousel ViewPager2 -->
        <androidx.viewpager2.widget.ViewPager2
            android:id="@+id/viewPagerCarousel"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:visibility="gone" />

        <!-- Carousel position chip (1/N) -->
        <TextView
            android:id="@+id/ivCarouselBadge"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="top|end"
            android:layout_margin="@dimen/post_media_overlay_margin"
            android:background="@drawable/bg_pill_surface"
            android:paddingStart="@dimen/post_chip_padding_horizontal"
            android:paddingTop="@dimen/post_chip_padding_vertical"
            android:paddingEnd="@dimen/post_chip_padding_horizontal"
            android:paddingBottom="@dimen/post_chip_padding_vertical"
            android:textColor="@color/white"
            android:textSize="@dimen/text_meta"
            android:visibility="gone"
            android:contentDescription="Multiple photos" />

        <!-- Audio attribution overlay (decorative) -->
        <LinearLayout
            android:id="@+id/layoutMediaAttribution"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|start"
            android:layout_margin="@dimen/post_media_overlay_margin"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:visibility="gone">

            <FrameLayout
                android:layout_width="@dimen/post_media_avatar_size"
                android:layout_height="@dimen/post_media_avatar_size">

                <de.hdodenhof.circleimageview.CircleImageView
                    android:id="@+id/ivMediaAvatar"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:src="@drawable/ic_person_placeholder"
                    app:civ_border_width="@dimen/post_media_avatar_border"
                    app:civ_border_color="@color/white" />

                <FrameLayout
                    android:id="@+id/ivMediaBadge"
                    android:layout_width="@dimen/post_media_badge_size"
                    android:layout_height="@dimen/post_media_badge_size"
                    android:layout_gravity="bottom|end"
                    android:background="@drawable/bg_media_badge">

                    <ImageView
                        android:layout_width="@dimen/post_media_badge_icon"
                        android:layout_height="@dimen/post_media_badge_icon"
                        android:layout_gravity="center"
                        android:src="@drawable/ic_repost"
                        app:tint="@color/white"
                        android:contentDescription="@null" />
                </FrameLayout>
            </FrameLayout>
        </LinearLayout>

        <!-- Reel Play / Audio Toggle Overlay -->
        <ImageView
            android:id="@+id/ivAudioToggle"
            android:layout_width="@dimen/post_media_control_icon"
            android:layout_height="@dimen/post_media_control_icon"
            android:layout_gravity="bottom|end"
            android:layout_margin="@dimen/post_media_overlay_margin"
            android:background="@drawable/bg_pill_surface"
            android:padding="@dimen/post_media_control_padding"
            android:src="@drawable/ic_volume_off"
            app:tint="@color/white"
            android:visibility="gone"
            android:contentDescription="Toggle sound" />

    </com.insta.clone.ui.widget.DoubleTapHeartLayout>

    <!-- Carousel dots -->
    <com.insta.clone.ui.widget.DotsIndicatorView
        android:id="@+id/dotsIndicator"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal"
        android:layout_marginTop="@dimen/post_dots_margin_top" />

    <!-- Action row — 48dp, icons 26dp, 16dp apart, bookmark at the far right -->
    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="@dimen/post_action_row_height"
        android:paddingStart="@dimen/post_padding_horizontal"
        android:paddingEnd="@dimen/post_padding_horizontal">

        <LinearLayout
            android:id="@+id/layoutLeftActions"
            android:layout_width="wrap_content"
            android:layout_height="match_parent"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent">

            <ImageView
                android:id="@+id/ivLike"
                android:layout_width="@dimen/post_action_icon"
                android:layout_height="@dimen/post_action_icon"
                android:src="@drawable/ic_heart"
                app:tint="?attr/themeTextPrimary"
                android:contentDescription="Like" />

            <TextView
                android:id="@+id/tvLikeCount"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="@dimen/post_count_gap"
                android:textColor="?attr/themeTextPrimary"
                android:textSize="@dimen/text_body"
                android:visibility="gone" />

            <ImageView
                android:id="@+id/ivComment"
                android:layout_width="@dimen/post_action_icon"
                android:layout_height="@dimen/post_action_icon"
                android:layout_marginStart="@dimen/post_action_gap"
                android:src="@drawable/ic_comment"
                app:tint="?attr/themeTextPrimary"
                android:contentDescription="Comment" />

            <TextView
                android:id="@+id/tvCommentCount"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="@dimen/post_count_gap"
                android:textColor="?attr/themeTextPrimary"
                android:textSize="@dimen/text_body"
                android:visibility="gone" />

            <ImageView
                android:id="@+id/ivRepost"
                android:layout_width="@dimen/post_action_icon"
                android:layout_height="@dimen/post_action_icon"
                android:layout_marginStart="@dimen/post_action_gap"
                android:src="@drawable/ic_repost"
                app:tint="?attr/themeTextPrimary"
                android:contentDescription="Repost" />

            <ImageView
                android:id="@+id/ivShare"
                android:layout_width="@dimen/post_action_icon"
                android:layout_height="@dimen/post_action_icon"
                android:layout_marginStart="@dimen/post_action_gap"
                android:rotation="-20"
                android:src="@drawable/ic_send"
                app:tint="?attr/themeTextPrimary"
                android:contentDescription="Share" />
        </LinearLayout>

        <ImageView
            android:id="@+id/ivBookmark"
            android:layout_width="@dimen/post_action_icon"
            android:layout_height="@dimen/post_action_icon"
            android:src="@drawable/ic_bookmark_outline"
            app:tint="?attr/themeTextPrimary"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toTopOf="parent"
            android:contentDescription="Save" />

    </androidx.constraintlayout.widget.ConstraintLayout>

    <!-- Likes line ("Liked by <user> and <n> others"), hidden at 0 likes -->
    <TextView
        android:id="@+id/tvLikesCount"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/post_line_gap"
        android:paddingStart="@dimen/post_padding_horizontal"
        android:paddingEnd="@dimen/post_padding_horizontal"
        android:textColor="?attr/themeTextPrimary"
        android:textSize="@dimen/text_body"
        android:visibility="gone" />

    <!-- Caption — bold username + text, 2 lines + "more" -->
    <com.insta.clone.ui.widget.ExpandableCaptionTextView
        android:id="@+id/tvCaption"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/post_line_gap_tight"
        android:ellipsize="end"
        android:maxLines="2"
        android:paddingStart="@dimen/post_padding_horizontal"
        android:paddingEnd="@dimen/post_padding_horizontal"
        android:textColor="?attr/themeTextPrimary"
        android:textSize="@dimen/text_body" />

    <!-- "View all X comments" -->
    <TextView
        android:id="@+id/tvViewComments"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/post_line_gap"
        android:paddingStart="@dimen/post_padding_horizontal"
        android:paddingEnd="@dimen/post_padding_horizontal"
        android:textColor="?attr/themeTextSecondary"
        android:textSize="@dimen/text_body" />

    <!-- Relative timestamp -->
    <TextView
        android:id="@+id/tvTimestamp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/post_line_gap_tight"
        android:paddingStart="@dimen/post_padding_horizontal"
        android:paddingEnd="@dimen/post_padding_horizontal"
        android:textColor="?attr/themeTextSecondary"
        android:textSize="@dimen/text_meta" />

</LinearLayout>
```

## app/src/main/res/layout/item_all_caught_up.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/themeBackground"
    android:gravity="center"
    android:orientation="vertical"
    android:paddingTop="@dimen/caught_up_padding_vertical"
    android:paddingBottom="@dimen/caught_up_padding_vertical"
    android:paddingStart="@dimen/empty_state_padding"
    android:paddingEnd="@dimen/empty_state_padding">

    <ImageView
        android:layout_width="@dimen/caught_up_icon_size"
        android:layout_height="@dimen/caught_up_icon_size"
        android:src="@drawable/ic_caught_up"
        android:contentDescription="All caught up" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/caught_up_gap"
        android:text="@string/all_caught_up"
        android:textColor="?attr/themeTextPrimary"
        android:textSize="@dimen/text_title"
        android:textStyle="bold" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/caught_up_gap_tight"
        android:gravity="center"
        android:text="@string/all_caught_up_subtitle"
        android:textColor="?attr/themeTextSecondary"
        android:textSize="@dimen/text_meta" />

</LinearLayout>
```

## app/src/main/java/com/insta/clone/ui/home/FeedPostAdapter.java

```java
package com.insta.clone.ui.home;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.insta.clone.R;
import com.insta.clone.data.model.Post;
import com.insta.clone.databinding.ItemFeedPostBinding;
import com.insta.clone.util.Constants;
import com.insta.clone.util.GlideUtils;
import com.insta.clone.util.SpanUtils;
import com.insta.clone.util.TimeUtils;
import com.insta.clone.util.ToastUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Recycler adapter for Main Home Feed posts (single image, carousels, reels preview).
 */
public class FeedPostAdapter extends ListAdapter<Post, FeedPostAdapter.ViewHolder> {

    public interface OnPostInteractionListener {
        void onLikeClick(Post post, int position);
        void onCommentClick(Post post);
        void onShareClick(Post post);
        void onSaveClick(Post post, int position);
        void onUserClick(String userId, String username);
        void onOptionsClick(Post post, int position);
        void onTagClick(String tag);
    }

    private static boolean isAudioMuted = true;
    private final OnPostInteractionListener listener;

    public FeedPostAdapter(OnPostInteractionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Post> DIFF_CALLBACK = new DiffUtil.ItemCallback<Post>() {
        @Override
        public boolean areItemsTheSame(@NonNull Post oldItem, @NonNull Post newItem) {
            return oldItem != null && newItem != null && Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Post oldItem, @NonNull Post newItem) {
            return Objects.equals(oldItem, newItem)
                    && Objects.equals(oldItem.getMediaUrls(), newItem.getMediaUrls())
                    && Objects.equals(oldItem.getThumbnailUrl(), newItem.getThumbnailUrl())
                    && Objects.equals(oldItem.getAvatarUrl(), newItem.getAvatarUrl())
                    && oldItem.isLikedByMe() == newItem.isLikedByMe()
                    && oldItem.isSavedByMe() == newItem.isSavedByMe()
                    && oldItem.getLikesCount() == newItem.getLikesCount()
                    && oldItem.getCommentsCount() == newItem.getCommentsCount();
        }
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFeedPostBinding binding = ItemFeedPostBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Post post = getItem(position);
        holder.bind(post, position, listener);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.clearCarouselCallback();
        super.onViewRecycled(holder);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemFeedPostBinding binding;
        private ViewPager2.OnPageChangeCallback carouselPageChangeCallback;

        ViewHolder(ItemFeedPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Post post, int position, OnPostInteractionListener listener) {
            Context context = binding.getRoot().getContext();
            binding.getRoot().setVisibility(View.VISIBLE);
            clearCarouselCallback();
            binding.viewPagerCarousel.setAdapter(null);

            if (post == null) {
                binding.getRoot().setVisibility(View.GONE);
                return;
            }

            // 1. Author info
            binding.tvUsername.setText(post.getUsername() == null ? "" : post.getUsername());
            binding.badgeVerified.setVerified(post.isVerified());

            if (post.getLocation() != null && !post.getLocation().isEmpty()) {
                binding.tvLocation.setText(post.getLocation());
                binding.tvLocation.setVisibility(View.VISIBLE);
            } else {
                binding.tvLocation.setVisibility(View.GONE);
            }

            com.insta.clone.util.GlideUtils.loadAvatar(binding.ivAvatar, post.getAvatarUrl());

            View.OnClickListener userClickListener = v -> {
                if (listener != null) listener.onUserClick(post.getUserId(), post.getUsername());
            };
            binding.ivAvatar.setOnClickListener(userClickListener);
            binding.layoutAuthor.setOnClickListener(userClickListener);

            // 2. Media handling (Carousel vs Single Media)
            List<String> mediaUrls = validMediaUrls(post.getMediaUrls());
            if (mediaUrls != null && mediaUrls.size() > 1) {
                // Multi-image carousel
                binding.ivSingleMedia.setVisibility(View.GONE);
                binding.viewPagerCarousel.setVisibility(View.VISIBLE);
                binding.ivCarouselBadge.setVisibility(View.VISIBLE);
                binding.dotsIndicator.setVisibility(View.VISIBLE);
                binding.dotsIndicator.setCount(mediaUrls.size());

                final int pageCount = mediaUrls.size();
                binding.ivCarouselBadge.setText(context.getString(R.string.carousel_position, 1, pageCount));

                CarouselPagerAdapter carouselAdapter = new CarouselPagerAdapter(mediaUrls);
                binding.viewPagerCarousel.setAdapter(carouselAdapter);
                carouselPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int pagePos) {
                        binding.dotsIndicator.setSelectedPosition(pagePos);
                        binding.ivCarouselBadge.setText(
                                context.getString(R.string.carousel_position, pagePos + 1, pageCount));
                    }
                };
                binding.viewPagerCarousel.registerOnPageChangeCallback(carouselPageChangeCallback);
            } else {
                // Single image or reel preview
                binding.ivSingleMedia.setVisibility(View.VISIBLE);
                binding.viewPagerCarousel.setVisibility(View.GONE);
                binding.ivCarouselBadge.setVisibility(View.GONE);
                binding.dotsIndicator.setVisibility(View.GONE);

                String singleUrl = (mediaUrls != null && !mediaUrls.isEmpty()) ? mediaUrls.get(0) : post.getThumbnailUrl();
                com.insta.clone.util.GlideUtils.load(binding.ivSingleMedia, "posts", singleUrl);
            }

            // Audio indicator for reels
            if (post.isReel()) {
                binding.ivAudioToggle.setVisibility(View.VISIBLE);
                updateAudioIcon();
                binding.ivAudioToggle.setOnClickListener(v -> {
                    isAudioMuted = !isAudioMuted;
                    updateAudioIcon();
                });
            } else {
                binding.ivAudioToggle.setVisibility(View.GONE);
            }

            // Audio / repost attribution overlay (design-only, decorative)
            if (post.isReel() && Constants.DECORATIVE_UI) {
                binding.layoutMediaAttribution.setVisibility(View.VISIBLE);
                GlideUtils.loadAvatar(binding.ivMediaAvatar, post.getAvatarUrl());
                binding.layoutMediaAttribution.setOnClickListener(v -> ToastUtil.showNotAvailable(context));
            } else {
                binding.layoutMediaAttribution.setVisibility(View.GONE);
                binding.layoutMediaAttribution.setOnClickListener(null);
            }

            // 3. Double-tap to like
            binding.doubleTapLayout.setOnDoubleTapLikeListener(() -> {
                if (!post.isLikedByMe()) {
                    animateLikeButton();
                    if (listener != null) listener.onLikeClick(post, position);
                }
            });

            // 4. Like button state
            updateLikeButton(post.isLikedByMe());
            binding.ivLike.setOnClickListener(v -> {
                animateLikeButton();
                if (listener != null) listener.onLikeClick(post, position);
            });

            // 5. Save button state
            updateSaveButton(post.isSavedByMe());
            binding.ivBookmark.setOnClickListener(v -> {
                if (listener != null) listener.onSaveClick(post, position);
            });

            // 6. Comments click
            binding.ivComment.setOnClickListener(v -> {
                if (listener != null) listener.onCommentClick(post);
            });
            binding.tvViewComments.setOnClickListener(v -> {
                if (listener != null) listener.onCommentClick(post);
            });

            // 7. Share click
            binding.ivShare.setOnClickListener(v -> {
                if (listener != null) listener.onShareClick(post);
            });

            // 7b. Repost (design-only, decorative)
            if (Constants.DECORATIVE_UI) {
                binding.ivRepost.setVisibility(View.VISIBLE);
                binding.ivRepost.setOnClickListener(v -> ToastUtil.showNotAvailable(context));
            } else {
                binding.ivRepost.setVisibility(View.GONE);
                binding.ivRepost.setOnClickListener(null);
            }

            // 8. 3-dots options
            binding.ivOptions.setOnClickListener(v -> {
                if (listener != null) listener.onOptionsClick(post, position);
            });

            // 9. Likes line: "Liked by <user> and <n> others" (hidden at 0)
            if (post.getLikesCount() > 0) {
                binding.tvLikesCount.setText(buildLikesLine(context, post));
                binding.tvLikesCount.setVisibility(View.VISIBLE);
            } else {
                binding.tvLikesCount.setText(null);
                binding.tvLikesCount.setVisibility(View.GONE);
            }

            // 9b. Action-row counts (abbreviated, hidden at 0)
            if (post.getLikesCount() > 0) {
                binding.tvLikeCount.setText(abbreviate(post.getLikesCount()));
                binding.tvLikeCount.setVisibility(View.VISIBLE);
            } else {
                binding.tvLikeCount.setVisibility(View.GONE);
            }

            if (post.getCommentsCount() > 0) {
                binding.tvCommentCount.setText(abbreviate(post.getCommentsCount()));
                binding.tvCommentCount.setVisibility(View.VISIBLE);
            } else {
                binding.tvCommentCount.setVisibility(View.GONE);
            }

            // 10. Caption
            binding.tvCaption.setCaption(
                    post.getUsername(),
                    post.getCaption(),
                    new SpanUtils.OnSpanClickListener() {
                        @Override public void onTagClick(String tag) {
                            if (listener != null) listener.onTagClick(tag);
                        }
                        @Override public void onMentionClick(String mentionUsername) {
                            if (listener != null) listener.onUserClick(null, mentionUsername);
                        }
                    },
                    username -> {
                        if (listener != null) listener.onUserClick(post.getUserId(), username);
                    }
            );

            // 11. Comments count text
            if (post.getCommentsCount() > 0) {
                binding.tvViewComments.setText(context.getString(R.string.view_all_comments, post.getCommentsCount()));
                binding.tvViewComments.setVisibility(View.VISIBLE);
            } else {
                binding.tvViewComments.setVisibility(View.GONE);
            }

            // 12. Timestamp
            binding.tvTimestamp.setText(TimeUtils.formatRelativeTime(post.getCreatedAt()));
        }

        private void updateLikeButton(boolean liked) {
            Context c = binding.getRoot().getContext();
            if (liked) {
                // Liked heart: ic_heart_filled (tinted with @color/color_like).
                binding.ivLike.setImageResource(R.drawable.ic_heart_filled);
                binding.ivLike.setImageTintList(null);
                binding.ivLike.clearColorFilter();
            } else {
                binding.ivLike.setImageResource(R.drawable.ic_heart);
                binding.ivLike.setColorFilter(ContextCompat.getColor(c, R.color.color_text_primary));
            }
        }

        /** "Liked by <user>" / "Liked by <user> and <n> others" with bold usernames. */
        private CharSequence buildLikesLine(Context context, Post post) {
            String username = post.getUsername() == null ? "" : post.getUsername();
            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append(context.getString(R.string.liked_by)).append(' ');

            int nameStart = sb.length();
            sb.append(username);
            sb.setSpan(new StyleSpan(Typeface.BOLD), nameStart, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            int others = post.getLikesCount() - 1;
            if (others > 0) {
                sb.append(' ').append(context.getString(R.string.and)).append(' ');
                int othersStart = sb.length();
                sb.append(abbreviate(others)).append(' ').append(context.getString(R.string.others));
                sb.setSpan(new StyleSpan(Typeface.BOLD), othersStart, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return sb;
        }

        /** 999 -> "999", 1200 -> "1.2K", 12000 -> "12K", 1200000 -> "1.2M". */
        private String abbreviate(int count) {
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

        private void animateLikeButton() {
            binding.ivLike.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .setDuration(150)
                    .setInterpolator(new OvershootInterpolator())
                    .withEndAction(() -> {
                        binding.ivLike.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100)
                                .start();
                    }).start();
        }

        private void updateSaveButton(boolean saved) {
            Context c = binding.getRoot().getContext();
            if (saved) {
                binding.ivBookmark.setImageResource(R.drawable.ic_bookmark_filled);
            } else {
                binding.ivBookmark.setImageResource(R.drawable.ic_bookmark_outline);
            }
            binding.ivBookmark.setColorFilter(ContextCompat.getColor(c, R.color.color_text_primary));
        }

        private void updateAudioIcon() {
            binding.ivAudioToggle.setImageResource(isAudioMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
        }

        void clearCarouselCallback() {
            if (carouselPageChangeCallback != null) {
                binding.viewPagerCarousel.unregisterOnPageChangeCallback(carouselPageChangeCallback);
                carouselPageChangeCallback = null;
            }
        }

        private static List<String> validMediaUrls(List<String> urls) {
            List<String> valid = new ArrayList<>();
            if (urls == null) return valid;
            for (String url : urls) {
                if (url != null && !url.trim().isEmpty()) valid.add(url);
            }
            return valid;
        }
    }

    /**
     * Inner adapter for carousel pages.
     */
    static class CarouselPagerAdapter extends RecyclerView.Adapter<CarouselPagerAdapter.PageViewHolder> {
        private final List<String> urls;

        CarouselPagerAdapter(List<String> urls) {
            this.urls = urls;
        }

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setAdjustViewBounds(false);
            return new PageViewHolder(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            com.insta.clone.util.GlideUtils.load(holder.imageView, "posts", urls.get(position));
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        static class PageViewHolder extends RecyclerView.ViewHolder {
            final ImageView imageView;
            PageViewHolder(ImageView iv) {
                super(iv);
                this.imageView = iv;
            }
        }
    }
}
```

## app/src/main/java/com/insta/clone/ui/home/StoryTrayAdapter.java

```java
package com.insta.clone.ui.home;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.clone.R;
import com.insta.clone.data.model.StoryGroup;
import com.insta.clone.databinding.ItemStoryTrayBinding;
import com.insta.clone.ui.widget.StoryRingView;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for horizontal Story Tray displayed at top of Home feed.
 */
public class StoryTrayAdapter extends RecyclerView.Adapter<StoryTrayAdapter.ViewHolder> {

    private final List<StoryGroup> items = new ArrayList<>();
    private final OnStoryClickListener listener;

    public interface OnStoryClickListener {
        void onStoryClick(StoryGroup group, int position);
        void onAddStoryClick();
    }

    public StoryTrayAdapter(OnStoryClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<StoryGroup> newItems) {
        items.clear();
        if (newItems != null) {
            for (StoryGroup group : newItems) {
                if (group != null) items.add(group);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStoryTrayBinding binding = ItemStoryTrayBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StoryGroup group = items.get(position);
        holder.bind(group, position, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemStoryTrayBinding binding;

        ViewHolder(ItemStoryTrayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(StoryGroup group, int position, OnStoryClickListener listener) {
            if (group == null) {
                binding.getRoot().setVisibility(View.GONE);
                return;
            }
            binding.getRoot().setVisibility(View.VISIBLE);
            Context context = binding.getRoot().getContext();
            boolean owner = group.isOwner();
            boolean unseen = group.hasUnseen();
            String username = group.getUsername() == null ? "" : group.getUsername();
            binding.tvUsername.setText(owner ? context.getString(R.string.your_story) : username);
            binding.storyRingView.loadAvatar(group.getAvatarUrl());

            if (owner) {
                // Active story -> gradient ring, no badge. No active story ->
                // grey ring + the 22dp "add" badge on the avatar corner.
                binding.storyRingView.setRingState(unseen
                        ? StoryRingView.RingState.UNSEEN
                        : StoryRingView.RingState.SEEN);
                binding.ivAddBadge.setVisibility(unseen ? View.GONE : View.VISIBLE);
            } else {
                binding.ivAddBadge.setVisibility(View.GONE);
                binding.storyRingView.setRingState(unseen
                        ? StoryRingView.RingState.UNSEEN
                        : StoryRingView.RingState.SEEN);
            }

            // Unseen -> textPrimary, seen -> textSecondary ("Your story" stays
            // textPrimary so it never greys out).
            boolean secondaryLabel = !owner && !unseen;
            binding.tvUsername.setTextColor(themeColor(context,
                    secondaryLabel ? R.attr.themeTextSecondary : R.attr.themeTextPrimary,
                    secondaryLabel ? R.color.color_text_secondary : R.color.color_text_primary));

            binding.getRoot().setOnClickListener(v -> {
                if (group.isOwner() && !group.hasUnseen()) {
                    if (listener != null) listener.onAddStoryClick();
                } else {
                    if (listener != null) listener.onStoryClick(group, position);
                }
            });
        }

        /** Resolves a PAGE 0 semantic theme attribute to a concrete color. */
        private int themeColor(Context context, int attr, int fallbackColorRes) {
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
    }
}
```

## app/src/main/java/com/insta/clone/ui/home/HomeFragment.java

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
import com.insta.clone.ui.create.CreatePostActivity;
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
        binding.btnCreate.setOnClickListener(v -> startActivity(new Intent(requireContext(), CreatePostActivity.class)));

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

---

# DESIGN CHECKLIST

| # | Requirement (PAGE 1 — HOME) | Implementation | Status |
|---|---|---|---|
| 1 | Top bar 48dp, hides on scroll down / returns on scroll up | `fragment_home.xml` toolbar `@dimen/home_top_bar_height` (48dp), existing `scroll\|enterAlways\|snap` kept | done |
| 2 | Left `ic_add` 28dp → opens Create | `btnCreate`, `@drawable/ic_add`, `@dimen/home_top_bar_icon_create`; id + existing listener untouched | done |
| 3 | Center wordmark, 28dp tall | `tvWordmark` @ `@dimen/text_wordmark_home` (28sp), centred (start/end to parent); cursive brand typeface path in `HomeFragment` preserved | done |
| 4 | Right `ic_heart` 26dp, red 8dp dot when unread | `btnNotifications` 26dp, `@drawable/ic_heart` tint `?attr/themeTextPrimary`, `dotActivityBadge` 8dp `@drawable/bg_unread_dot` (existing unread logic) | done |
| 5 | Right `ic_send` 26dp, red 16dp count badge when unread | `btnDirect` 26dp, `@drawable/ic_send`; `tvDmBadge` 16dp `bg_unread_dot`, `text_badge` (10sp) white bold (existing unread logic) | done |
| 6 | Icons 20dp apart, 16dp end padding | `layout_marginEnd=@dimen/home_top_bar_icon_gap` (20dp); toolbar contentInset start/end 16dp | done |
| 7 | Story tray 108dp, 12dp start padding | `story_tray_height` 108dp; header adapter gutter `story_tray_padding_horizontal` (12dp) | done |
| 8 | Items 84dp wide | `story_item_width` 84dp | done |
| 9 | Avatar 64dp inside a ring, outer 76dp | ring box `story_ring_outer_size` 76dp, ring stroke 2dp + gap 4dp → avatar `story_avatar_size` 64dp | done |
| 10 | Label 12sp textPrimary, centred, 1 line, ellipsized | `tvUsername` `text_meta` (12sp), `gravity=center`, `maxLines=1`, `ellipsize=end`, colour from token in adapter | done |
| 11 | "Your story" 22dp badge bottom-right (bg textPrimary, `ic_add` in bg colour, 2dp bg border) | `ivAddBadge` 22dp + `bg_story_add_badge_owner` (solid `color_text_primary`, 2dp stroke `color_background`), `ic_add` 12dp tinted `color_background` | done |
| 12 | Active story → gradient ring, no badge | `StoryTrayAdapter`: unseen → `UNSEEN` + badge `GONE` | done |
| 13 | Unseen = gradient ring; seen = grey ring + label textSecondary | `StoryTrayAdapter` ring state + `themeTextSecondary`/`themeTextPrimary` label; gradient/grey from PAGE 0 tokens | done |
| 14 | 1dp divider under the tray | header adapter wraps tray RecyclerView + divider (`divider_hairline`, `?attr/themeDivider`) | done |
| 15 | Post header 56dp, avatar 32dp, 12dp start | `post_header_height`, `post_header_avatar_size`, `post_padding_horizontal` | done |
| 16 | 10dp gap, username 14sp Medium | `post_header_avatar_gap`; `tvUsername` `TextAppearance.Insta.Username` (sans-serif-medium 14sp) | done |
| 17 | Right `ic_more_horiz` 24dp, 12dp end | `ivOptions` `post_icon_more` 24dp inside parent padding end 12dp | done |
| 18 | Media full width 1:1, edge to edge | `DoubleTapHeartLayout` (1:1 by width) + `ivSingleMedia` centerCrop | done |
| 19 | Carousel keeps the 1/N chip and dots | `ivCarouselBadge` → pill chip `%1$d/%2$d` updated in `onPageSelected`; `dotsIndicator` kept, moved under the media | done |
| 20 | Action row 48dp, 12dp start, icons 26dp | `post_action_row_height`, `post_padding_horizontal`, `post_action_icon` | done |
| 21 | heart+likes, 16dp, comment+comments, 16dp, repost (decorative), 16dp, send, bookmark far right | `ivLike`+`tvLikeCount`, `ivComment`+`tvCommentCount` (`post_action_gap` 16dp), `ivRepost` (DECORATIVE_UI → toast "Not available"), `ivShare` (existing share sheet), `ivBookmark` end | done |
| 22 | Counts 14sp textPrimary, 6dp from icon, abbreviated, hidden at 0 | `tvLikeCount`/`tvCommentCount` `text_body` + `post_count_gap`; `abbreviate()` → 1.2K / 12K / 1.2M; `GONE` at 0 | done |
| 23 | Liked heart = `ic_heart_filled` in #FF3040 | `updateLikeButton()` → `ic_heart_filled` (`color_like` #FF3040); unliked `ic_heart` tinted textPrimary | done |
| 24 | Likes line "Liked by **user** and **{n-1} others**", 1 like → "Liked by **user**", hidden at 0 | `tvLikesCount` + `buildLikesLine()` (`StyleSpan` bold; strings `liked_by`/`and`/`others`), 14sp | done |
| 25 | Caption bold username + text, 14sp, 2 lines + "more" | `tvCaption` `maxLines=2` + `ellipsize=end`; widget truncation logic unchanged | done |
| 26 | "View all N comments" 14sp textSecondary | `tvViewComments` `text_body` + `?attr/themeTextSecondary` | done |
| 27 | Timestamp 12sp textSecondary | `tvTimestamp` `text_meta` + `?attr/themeTextSecondary` (old 10sp/allCaps/letterSpacing removed) | done |
| 28 | 12dp between posts, no dividers | root `paddingBottom=post_spacing_bottom` (12dp); no divider views | done |
| 29 | Skeletons / pull-to-refresh / "You're all caught up" / empty state restyled only | `item_all_caught_up` tokenised (icon 56dp, gaps 16/6dp, padding 40/24dp); empty state uses `text_title`/`text_body`/`empty_state_padding` + `@string/empty_state_subtitle`; SwipeRefreshLayout keeps `color_primary`. No skeleton views exist in the Home feed | done |
| 30 | Bottom navigation from PAGE 0, Home selected | untouched (`activity_main.xml`, `menu/bottom_nav_menu.xml`, `MainActivity`) | done |
| 31 | Media overlay visible in the screenshot (avatar + violet music/repost badge) | `layoutMediaAttribution` bottom-start: 32dp avatar with 1.5dp white border + 18dp badge (`color_media_badge`) with `ic_repost`; decorative → toast; reels only | done |
| 32 | 48dp bar must sit below the status bar (mock) | `HomeFragment` applies the system-bars top inset as app-bar padding (PAGE 0 window is edge-to-edge) | done |

## Notes / assumptions

- **Header avatar ring**: "ring only if that user has an active story" is not data-backed — `Post` has no story field and models are frozen, so the 32dp header avatar renders plain (as in the mock).
- **Tray item width** follows the written spec (84dp); the mock's own tray spacing reads ~100dp. Spec value used.
- **Wordmark** uses the existing `tvWordmark` TextView (cursive brand typeface) at the 28dp token rather than swapping in an `ImageView`, keeping the id/type contract `HomeFragment` depends on.
- **"Your story"** keeps a textPrimary label (mock), even though its ring is the grey seen ring.
- **Decorative elements** (repost button, media attribution overlay) follow `Constants.DECORATIVE_UI`: toast "Not available" when true, hidden when false.
- **Not verified visually**: carousel 1/N chip + dots (no multi-image post in the test feed) — verified by build + code path only.

## Verification run

- `./gradlew :app:assembleDebug` (JDK 17) → BUILD SUCCESSFUL: every resource reference resolves, view-binding types match the new layouts, Java compiles.
- Installed on device (1080×2400 @440dpi = 393dp) and inspected the live Home screen in dark and light themes. Measured: toolbar 48dp, create icon 28dp, action icons 26dp, action row 48dp, header 56dp, header avatar 32dp, more 24dp, media 1080×1080 (1:1, edge-to-edge), ring outer 76dp, tray item pitch 84dp, gaps 20/16/10/6/4dp, likes/caption/timestamp 14/14/12sp.
