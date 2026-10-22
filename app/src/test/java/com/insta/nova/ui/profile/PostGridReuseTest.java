package com.insta.nova.ui.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;

import com.insta.nova.data.repository.PostRepository;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * C3 contract: the other-user profile screen renders its grid with the <em>same</em>
 * cell, adapter and gutter decoration as the own-profile tab, so the two screens
 * cannot drift apart visually.
 *
 * <p>That reuse is only possible while those types stay public. Reflection only —
 * no device, no network, no Android runtime needed.
 */
public class PostGridReuseTest {

    @Test
    public void gridAdapterIsReusableOutsideItsOwnPackage() throws Exception {
        Class<?> adapter = Class.forName(
                "com.insta.nova.ui.profile.ProfileFragment$MediaGridAdapter");

        assertTrue("MediaGridAdapter must be public for the other-profile screen",
                Modifier.isPublic(adapter.getModifiers()));
        assertTrue("MediaGridAdapter must stay static so it holds no Fragment state",
                Modifier.isStatic(adapter.getModifiers()));

        Class<?> listener = Class.forName(
                "com.insta.nova.ui.profile.ProfileFragment$MediaGridAdapter$OnPostClickListener");
        Constructor<?> ctor = adapter.getConstructor(List.class, listener);
        assertTrue("The (items, listener) constructor must be public",
                Modifier.isPublic(ctor.getModifiers()));
    }

    @Test
    public void gutterDecorationStaysPublicSoGuttersMatchTheProfileTab() throws Exception {
        Class<?> decoration = Class.forName(
                "com.insta.nova.ui.profile.ProfileFragment$GridGapDecoration");

        assertTrue("GridGapDecoration must be public", Modifier.isPublic(decoration.getModifiers()));
        assertTrue("GridGapDecoration constructor must be public",
                Modifier.isPublic(decoration.getConstructor(int.class).getModifiers()));
    }

    @Test
    public void userPostsQueryKeepsItsSignature() throws Exception {
        Method getUserPosts = PostRepository.class.getMethod("getUserPosts", String.class);
        assertEquals(LiveData.class, getUserPosts.getReturnType());
        assertTrue(Modifier.isPublic(getUserPosts.getModifiers()));
    }
}
