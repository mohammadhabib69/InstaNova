package com.insta.nova.util;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.insta.nova.R;

import java.util.Locale;

/**
 * Android plurals and quantity helper for InstaNova.
 * Provides type-safe singular/plural formatting for likes, liked-by tail,
 * comments, followers, posts, answers, requests, minutes, hours, days, members, donations.
 * Includes pure-locale fallbacks for robust execution in plain JUnit tests without Android Context.
 */
public final class Plural {

    private Plural() {}

    private static boolean isBangla(Locale locale) {
        if (locale == null) locale = Locale.getDefault();
        return "bn".equalsIgnoreCase(locale.getLanguage());
    }

    private static String num(int count, Locale locale) {
        if (isBangla(locale)) {
            return NumberFormat.toBanglaDigits(String.valueOf(count));
        }
        return String.valueOf(count);
    }

    /* ================================================================== */
    /* 1. Likes                                                           */
    /* ================================================================== */

    public static String likes(@Nullable Context context, int count) {
        if (context != null) {
            try {
                return context.getResources().getQuantityString(R.plurals.likes, count, count);
            } catch (Exception ignored) {}
        }
        return likes(count, Locale.getDefault());
    }

    public static String likes(int count, @NonNull Locale locale) {
        if (isBangla(locale)) {
            return num(count, locale) + "টি লাইক";
        }
        return count == 1 ? "1 like" : count + " likes";
    }

    /* ================================================================== */
    /* 2. Liked-by tail ("and 1 other" / "and %d others")                 */
    /* ================================================================== */

    public static String likedByTail(@Nullable Context context, int othersCount) {
        if (context != null) {
            try {
                return context.getResources().getQuantityString(R.plurals.liked_by_tail, othersCount, othersCount);
            } catch (Exception ignored) {}
        }
        return likedByTail(othersCount, Locale.getDefault());
    }

    public static String likedByTail(int othersCount, @NonNull Locale locale) {
        if (isBangla(locale)) {
            return "এবং অন্য " + num(othersCount, locale) + " জন";
        }
        return othersCount == 1 ? "and 1 other" : "and " + othersCount + " others";
    }

    /* ================================================================== */
    /* 3. Comments ("View 1 comment" / "View all %d comments")            */
    /* ================================================================== */

    public static String comments(@Nullable Context context, int count) {
        if (context != null) {
            try {
                return context.getResources().getQuantityString(R.plurals.comments_view, count, count);
            } catch (Exception ignored) {}
        }
        return comments(count, Locale.getDefault());
    }

    public static String comments(int count, @NonNull Locale locale) {
        if (isBangla(locale)) {
            return count == 1 ? num(count, locale) + "টি মন্তব্য দেখুন" : "সব " + num(count, locale) + "টি মন্তব্য দেখুন";
        }
        return count == 1 ? "View 1 comment" : "View all " + count + " comments";
    }

    /* ================================================================== */
    /* 4. Followers ("1 follower" / "%d followers")                       */
    /* ================================================================== */

    public static String followers(@Nullable Context context, int count) {
        if (context != null) {
            try {
                return context.getResources().getQuantityString(R.plurals.followers, count, count);
            } catch (Exception ignored) {}
        }
        return followers(count, Locale.getDefault());
    }

    public static String followers(int count, @NonNull Locale locale) {
        if (isBangla(locale)) {
            return num(count, locale) + " জন অনুসরণকারী";
        }
        return count == 1 ? "1 follower" : count + " followers";
    }

    /* ================================================================== */
    /* 5. Posts ("1 post" / "%d posts")                                   */
    /* ================================================================== */

    public static String posts(@Nullable Context context, int count) {
        if (context != null) {
            try {
                return context.getResources().getQuantityString(R.plurals.posts, count, count);
            } catch (Exception ignored) {}
        }
        return posts(count, Locale.getDefault());
    }

    public static String posts(int count, @NonNull Locale locale) {
        if (isBangla(locale)) {
            return num(count, locale) + "টি পোস্ট";
        }
        return count == 1 ? "1 post" : count + " posts";
    }

    /* ================================================================== */
    /* 6. Answers ("1 answer" / "%d answers")                             */
    /* ================================================================== */

    public static String answers(@Nullable Context context, int count) {
        if (context != null) {
            try {
                return context.getResources().getQuantityString(R.plurals.answers, count, count);
            } catch (Exception ignored) {}
        }
        return answers(count, Locale.getDefault());
    }

    public static String answers(int count, @NonNull Locale locale) {
        if (isBangla(locale)) {
            return num(count, locale) + "টি উত্তর";
        }
        return count == 1 ? "1 answer" : count + " answers";
    }

    /* ================================================================== */
    /* 7. Requests ("1 request" / "%d requests")                          */
    /* ================================================================== */

    public static String requests(@Nullable Context context, int count) {
        if (context != null) {
            try {
                return context.getResources().getQuantityString(R.plurals.requests, count, count);
            } catch (Exception ignored) {}
        }
        return requests(count, Locale.getDefault());
    }

    public static String requests(int count, @NonNull Locale locale) {
        if (isBangla(locale)) {
            return num(count, locale) + "টি অনুরোধ";
        }
        return count == 1 ? "1 request" : count + " requests";
    }

    /* ================================================================== */
    /* 8. Minutes ("1 minute" / "%d minutes")                             */
    /* ================================================================== */

    public static String minutes(@Nullable Context context, int count) {
        if (context != null) {
            try {
                return context.getResources().getQuantityString(R.plurals.minutes, count, count);
            } catch (Exception ignored) {}
        }
        return minutes(count, Locale.getDefault());
    }

    public static String minutes(int count, @NonNull Locale locale) {
        if (isBangla(locale)) {
            return num(count, locale) + " মিনিট";
        }
        return count == 1 ? "1 minute" : count + " minutes";
    }

    /* ================================================================== */
    /* 9. Hours ("1 hour" / "%d hours")                                   */
    /* ================================================================== */

    public static String hours(@Nullable Context context, int count) {
        if (context != null) {
            try {
                return context.getResources().getQuantityString(R.plurals.hours, count, count);
            } catch (Exception ignored) {}
        }
        return hours(count, Locale.getDefault());
    }

    public static String hours(int count, @NonNull Locale locale) {
        if (isBangla(locale)) {
            return num(count, locale) + " ঘণ্টা";
        }
        return count == 1 ? "1 hour" : count + " hours";
    }

    /* ================================================================== */
    /* 10. Days ("1 day" / "%d days")                                     */
    /* ================================================================== */

    public static String days(@Nullable Context context, int count) {
        if (context != null) {
            try {
                return context.getResources().getQuantityString(R.plurals.days, count, count);
            } catch (Exception ignored) {}
        }
        return days(count, Locale.getDefault());
    }

    public static String days(int count, @NonNull Locale locale) {
        if (isBangla(locale)) {
            return num(count, locale) + " দিন";
        }
        return count == 1 ? "1 day" : count + " days";
    }

    /* ================================================================== */
    /* 11. Members ("1 member" / "%d members")                            */
    /* ================================================================== */

    public static String members(@Nullable Context context, int count) {
        if (context != null) {
            try {
                return context.getResources().getQuantityString(R.plurals.members, count, count);
            } catch (Exception ignored) {}
        }
        return members(count, Locale.getDefault());
    }

    public static String members(int count, @NonNull Locale locale) {
        if (isBangla(locale)) {
            return num(count, locale) + " জন সদস্য";
        }
        return count == 1 ? "1 member" : count + " members";
    }

    /* ================================================================== */
    /* 12. Donations ("1 donation" / "%d donations")                      */
    /* ================================================================== */

    public static String donations(@Nullable Context context, int count) {
        if (context != null) {
            try {
                return context.getResources().getQuantityString(R.plurals.donations, count, count);
            } catch (Exception ignored) {}
        }
        return donations(count, Locale.getDefault());
    }

    public static String donations(int count, @NonNull Locale locale) {
        if (isBangla(locale)) {
            return num(count, locale) + "টি দান";
        }
        return count == 1 ? "1 donation" : count + " donations";
    }

    /* ================================================================== */
    /* Liked by line rules:                                               */
    /* - "Liked by X" (1 like)                                            */
    /* - "Liked by X and Y" (2 likes)                                     */
    /* - "Liked by X, Y and 1 other" (3 likes)                            */
    /* - "Liked by X, Y and N others" (>3 likes)                          */
    /* - "Liked by X and 1 other" (2 likes, 1 user known)                 */
    /* - "Liked by X and N others" (>2 likes, 1 user known)               */
    /* ================================================================== */

    public static String formatLikedByLine(@Nullable String user1, @Nullable String user2, int totalLikes, @NonNull Locale locale) {
        if (totalLikes <= 0) return "";
        boolean bn = isBangla(locale);
        String prefix = bn ? "পছন্দ করেছেন " : "Liked by ";

        if (totalLikes == 1) {
            return prefix + (user1 != null ? user1 : "");
        }

        if (user2 != null && !user2.isEmpty()) {
            if (totalLikes == 2) {
                return bn
                        ? prefix + user1 + " এবং " + user2
                        : prefix + user1 + " and " + user2;
            }
            int others = totalLikes - 2;
            String tail = likedByTail(others, locale);
            return prefix + user1 + ", " + user2 + " " + tail;
        }

        int others = totalLikes - 1;
        String tail = likedByTail(others, locale);
        return prefix + (user1 != null ? user1 : "") + " " + tail;
    }
}
