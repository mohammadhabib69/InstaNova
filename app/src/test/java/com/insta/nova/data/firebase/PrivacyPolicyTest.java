package com.insta.nova.data.firebase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The private-account matrix, pinned (task P3-10/P3-13). */
public class PrivacyPolicyTest {

    @Test
    public void publicAccountIsVisibleToEveryone() {
        assertTrue(PrivacyPolicy.canViewContent(false, false, false));
        assertTrue(PrivacyPolicy.canViewFollowerList(false, false, false));
    }

    @Test
    public void privateAccountContentNeedsAFollowEdge() {
        assertFalse(PrivacyPolicy.canViewContent(false, true, false));
        assertTrue(PrivacyPolicy.canViewContent(false, true, true));
        assertTrue("owner always sees own content", PrivacyPolicy.canViewContent(true, true, false));
    }

    @Test
    public void privateAccountFollowerListsStayClosed() {
        assertFalse(PrivacyPolicy.canViewFollowerList(false, true, false));
        assertTrue(PrivacyPolicy.canViewFollowerList(false, true, true));
    }

    @Test
    public void cannotFollowOrMessageYourself() {
        assertFalse(PrivacyPolicy.canRequestFollow(true));
        assertTrue(PrivacyPolicy.canRequestFollow(false));
        assertFalse(PrivacyPolicy.canStartConversation(true, false, true));
    }

    @Test
    public void privateAccountOnlyAcceptsDmsFromPeopleItFollows() {
        assertFalse(PrivacyPolicy.canStartConversation(false, true, false));
        assertTrue(PrivacyPolicy.canStartConversation(false, true, true));
        assertTrue(PrivacyPolicy.canStartConversation(false, false, false));
    }

    @Test
    public void privateProfilesRemainSearchable() {
        assertTrue(PrivacyPolicy.isDiscoverable(true));
        assertTrue(PrivacyPolicy.showsCounters(true));
    }
}
