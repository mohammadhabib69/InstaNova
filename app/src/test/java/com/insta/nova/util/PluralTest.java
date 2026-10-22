package com.insta.nova.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

public class PluralTest {

    private static final Locale EN = Locale.US;
    private static final Locale BN = new Locale("bn");

    private static final int[] TEST_CASES = {
            0, 1, 2, 11, 999, 1000, 9999, 10000, 12500, 1200000
    };

    @Test
    public void testLikesEnglish() {
        assertEquals("0 likes", Plural.likes(0, EN));
        assertEquals("1 like", Plural.likes(1, EN));
        assertEquals("2 likes", Plural.likes(2, EN));
        assertEquals("11 likes", Plural.likes(11, EN));
        assertEquals("999 likes", Plural.likes(999, EN));
        assertEquals("1000 likes", Plural.likes(1000, EN));
        assertEquals("9999 likes", Plural.likes(9999, EN));
        assertEquals("10000 likes", Plural.likes(10000, EN));
        assertEquals("12500 likes", Plural.likes(12500, EN));
        assertEquals("1200000 likes", Plural.likes(1200000, EN));
    }

    @Test
    public void testLikesBangla() {
        assertEquals("০টি লাইক", Plural.likes(0, BN));
        assertEquals("১টি লাইক", Plural.likes(1, BN));
        assertEquals("২টি লাইক", Plural.likes(2, BN));
        assertEquals("১১টি লাইক", Plural.likes(11, BN));
        assertEquals("৯৯৯টি লাইক", Plural.likes(999, BN));
        assertEquals("১০০০টি লাইক", Plural.likes(1000, BN));
        assertEquals("৯৯৯৯টি লাইক", Plural.likes(9999, BN));
        assertEquals("১০০০০টি লাইক", Plural.likes(10000, BN));
        assertEquals("১২৫০০টি লাইক", Plural.likes(12500, BN));
        assertEquals("১২০০০০০টি লাইক", Plural.likes(1200000, BN));
    }

    @Test
    public void testLikedByTailEnglish() {
        // Specifically testing the bug fix: "Liked by qaverify and 1 other" vs "and N others"
        assertEquals("and 0 others", Plural.likedByTail(0, EN));
        assertEquals("and 1 other", Plural.likedByTail(1, EN));
        assertEquals("and 2 others", Plural.likedByTail(2, EN));
        assertEquals("and 11 others", Plural.likedByTail(11, EN));
        assertEquals("and 999 others", Plural.likedByTail(999, EN));
        assertEquals("and 1000 others", Plural.likedByTail(1000, EN));
        assertEquals("and 9999 others", Plural.likedByTail(9999, EN));
        assertEquals("and 10000 others", Plural.likedByTail(10000, EN));
        assertEquals("and 12500 others", Plural.likedByTail(12500, EN));
        assertEquals("and 1200000 others", Plural.likedByTail(1200000, EN));
    }

    @Test
    public void testLikedByTailBangla() {
        assertEquals("এবং অন্য ০ জন", Plural.likedByTail(0, BN));
        assertEquals("এবং অন্য ১ জন", Plural.likedByTail(1, BN));
        assertEquals("এবং অন্য ২ জন", Plural.likedByTail(2, BN));
        assertEquals("এবং অন্য ১১ জন", Plural.likedByTail(11, BN));
        assertEquals("এবং অন্য ৯৯৯ জন", Plural.likedByTail(999, BN));
        assertEquals("এবং অন্য ১০০০ জন", Plural.likedByTail(1000, BN));
        assertEquals("এবং অন্য ৯৯৯৯ জন", Plural.likedByTail(9999, BN));
        assertEquals("এবং অন্য ১০০০০ জন", Plural.likedByTail(10000, BN));
        assertEquals("এবং অন্য ১২৫০০ জন", Plural.likedByTail(12500, BN));
        assertEquals("এবং অন্য ১২০০০০০ জন", Plural.likedByTail(1200000, BN));
    }

    @Test
    public void testCommentsViewEnglish() {
        // Specifically testing the bug fix: "View 1 comment" vs "View all %d comments"
        assertEquals("View all 0 comments", Plural.comments(0, EN));
        assertEquals("View 1 comment", Plural.comments(1, EN));
        assertEquals("View all 2 comments", Plural.comments(2, EN));
        assertEquals("View all 11 comments", Plural.comments(11, EN));
        assertEquals("View all 999 comments", Plural.comments(999, EN));
        assertEquals("View all 1000 comments", Plural.comments(1000, EN));
        assertEquals("View all 9999 comments", Plural.comments(9999, EN));
        assertEquals("View all 10000 comments", Plural.comments(10000, EN));
        assertEquals("View all 12500 comments", Plural.comments(12500, EN));
        assertEquals("View all 1200000 comments", Plural.comments(1200000, EN));
    }

    @Test
    public void testCommentsViewBangla() {
        assertEquals("সব ০টি মন্তব্য দেখুন", Plural.comments(0, BN));
        assertEquals("১টি মন্তব্য দেখুন", Plural.comments(1, BN));
        assertEquals("সব ২টি মন্তব্য দেখুন", Plural.comments(2, BN));
        assertEquals("সব ১১টি মন্তব্য দেখুন", Plural.comments(11, BN));
        assertEquals("সব ৯৯৯টি মন্তব্য দেখুন", Plural.comments(999, BN));
        assertEquals("সব ১০০০টি মন্তব্য দেখুন", Plural.comments(1000, BN));
        assertEquals("সব ৯৯৯৯টি মন্তব্য দেখুন", Plural.comments(9999, BN));
        assertEquals("সব ১০০০০টি মন্তব্য দেখুন", Plural.comments(10000, BN));
        assertEquals("সব ১২৫০০টি মন্তব্য দেখুন", Plural.comments(12500, BN));
        assertEquals("সব ১২০০০০০টি মন্তব্য দেখুন", Plural.comments(1200000, BN));
    }

    @Test
    public void testFollowersAndPostsEnglish() {
        assertEquals("1 follower", Plural.followers(1, EN));
        assertEquals("2 followers", Plural.followers(2, EN));
        assertEquals("11 followers", Plural.followers(11, EN));
        assertEquals("1 post", Plural.posts(1, EN));
        assertEquals("2 posts", Plural.posts(2, EN));
        assertEquals("11 posts", Plural.posts(11, EN));
    }

    @Test
    public void testFollowersAndPostsBangla() {
        assertEquals("১ জন অনুসরণকারী", Plural.followers(1, BN));
        assertEquals("২ জন অনুসরণকারী", Plural.followers(2, BN));
        assertEquals("১১ জন অনুসরণকারী", Plural.followers(11, BN));
        assertEquals("১টি পোস্ট", Plural.posts(1, BN));
        assertEquals("২টি পোস্ট", Plural.posts(2, BN));
        assertEquals("১১টি পোস্ট", Plural.posts(11, BN));
    }

    @Test
    public void testFullLikedByLineScenarios() {
        // "Liked by X"
        assertEquals("Liked by qaverify", Plural.formatLikedByLine("qaverify", null, 1, EN));
        assertEquals("পছন্দ করেছেন qaverify", Plural.formatLikedByLine("qaverify", null, 1, BN));

        // "Liked by X and Y"
        assertEquals("Liked by userA and userB", Plural.formatLikedByLine("userA", "userB", 2, EN));
        assertEquals("পছন্দ করেছেন userA এবং userB", Plural.formatLikedByLine("userA", "userB", 2, BN));

        // "Liked by X and 1 other" (total 2 likes, 1 user known)
        assertEquals("Liked by qaverify and 1 other", Plural.formatLikedByLine("qaverify", null, 2, EN));
        assertEquals("পছন্দ করেছেন qaverify এবং অন্য ১ জন", Plural.formatLikedByLine("qaverify", null, 2, BN));

        // "Liked by X and 11 others" (total 12 likes, 1 user known)
        assertEquals("Liked by qaverify and 11 others", Plural.formatLikedByLine("qaverify", null, 12, EN));
        assertEquals("পছন্দ করেছেন qaverify এবং অন্য ১১ জন", Plural.formatLikedByLine("qaverify", null, 12, BN));

        // "Liked by X, Y and 1 other" (total 3 likes, 2 users known)
        assertEquals("Liked by userA, userB and 1 other", Plural.formatLikedByLine("userA", "userB", 3, EN));
        assertEquals("পছন্দ করেছেন userA, userB এবং অন্য ১ জন", Plural.formatLikedByLine("userA", "userB", 3, BN));

        // "Liked by X, Y and 10 others" (total 12 likes, 2 users known)
        assertEquals("Liked by userA, userB and 10 others", Plural.formatLikedByLine("userA", "userB", 12, EN));
        assertEquals("পছন্দ করেছেন userA, userB এবং অন্য ১০ জন", Plural.formatLikedByLine("userA", "userB", 12, BN));
    }
}
