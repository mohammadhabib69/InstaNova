package com.insta.nova.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.insta.nova.data.model.StoryGroup;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StoryTrayOrderingTest {

    private static final String OWNER = "owner-uuid";

    @Test
    public void ownerWithNoActiveStoryIsStillFirst() {
        StoryGroup other = group("other-uuid", "other", true);

        List<StoryGroup> result = StoryTrayOrdering.ownerFirst(
                new ArrayList<>(Arrays.asList(other)), OWNER, "qaverify", "avatar.jpg");

        assertEquals(2, result.size());
        StoryGroup owner = result.get(0);
        assertEquals(OWNER, owner.getUserId());
        assertEquals("qaverify", owner.getUsername());
        assertEquals("avatar.jpg", owner.getAvatarUrl());
        assertTrue(owner.isOwner());
        assertFalse(owner.hasActiveStory());
        assertEquals("other-uuid", result.get(1).getUserId());
    }

    @Test
    public void ownerWithActiveStoryKeepsActiveStateAndMovesFirst() {
        StoryGroup other = group("other-uuid", "other", true);
        StoryGroup owner = group(OWNER, "qaverify", true);
        owner.setAvatarUrl("real-owner.jpg");
        owner.setHasUnseen(true);

        List<StoryGroup> result = StoryTrayOrdering.ownerFirst(
                new ArrayList<>(Arrays.asList(other, owner)), OWNER, "cached", "cached.jpg");

        assertEquals(2, result.size());
        StoryGroup first = result.get(0);
        assertEquals(OWNER, first.getUserId());
        assertTrue(first.hasActiveStory());
        assertTrue(first.hasUnseen());
        assertEquals("real-owner.jpg", first.getAvatarUrl());
    }

    @Test
    public void noActiveStoriesStillProducesOwnerRow() {
        List<StoryGroup> result = StoryTrayOrdering.ownerFirst(
                new ArrayList<>(), OWNER, "qaverify", "");

        assertEquals(1, result.size());
        StoryGroup owner = result.get(0);
        assertTrue(owner.isOwner());
        assertFalse(owner.hasActiveStory());
        assertFalse(owner.hasUnseen());
    }

    @Test
    public void duplicateOwnerAndDuplicateOthersAreCollapsed() {
        StoryGroup owner1 = group(OWNER, "qaverify", true);
        StoryGroup owner2 = group(OWNER, "qaverify", false);
        StoryGroup other1 = group("other-uuid", "other", true);
        StoryGroup other2 = group("other-uuid", "other", false);

        List<StoryGroup> result = StoryTrayOrdering.ownerFirst(
                new ArrayList<>(Arrays.asList(owner1, other1, owner2, other2)),
                OWNER, "qaverify", "");

        assertEquals(2, result.size());
        assertEquals(OWNER, result.get(0).getUserId());
        assertEquals("other-uuid", result.get(1).getUserId());
    }

    @Test
    public void missingOwnerIdentityDoesNotInventARow() {
        List<StoryGroup> result = StoryTrayOrdering.ownerFirst(
                new ArrayList<>(), "", "qaverify", "avatar.jpg");
        assertTrue(result.isEmpty());
    }

    @Test
    public void findOwnerUsesExactIdOnly() {
        StoryGroup other = group("other-uuid", "other", true);
        StoryGroup owner = group(OWNER, "qaverify", true);
        List<StoryGroup> groups = Arrays.asList(other, owner);

        assertNotNull(StoryTrayOrdering.findOwner(groups, OWNER));
        assertEquals(owner, StoryTrayOrdering.findOwner(groups, OWNER));
        assertEquals(null, StoryTrayOrdering.findOwner(groups, "owner"));
    }

    private static StoryGroup group(String id, String username, boolean active) {
        StoryGroup group = new StoryGroup(id, username, id + ".jpg", true, false,
                new ArrayList<>());
        group.setHasActiveStory(active);
        return group;
    }
}
