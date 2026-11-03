package com.insta.nova.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** Task P7-10 — the tap-to-destination contract. */
public class NotificationRouteTest {

    private static Map<String, String> payload(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void emptyPayloadGoesNowhere() {
        assertEquals(NotificationRoute.Kind.NONE, NotificationRoute.forPayload(null).kind);
        assertEquals(NotificationRoute.Kind.NONE, NotificationRoute.forPayload(payload()).kind);
    }

    @Test
    public void unknownTypeFallsBackToHub() {
        NotificationRoute r = NotificationRoute.forPayload(payload("type", "something_new"));
        assertEquals(NotificationRoute.Kind.NOTIFICATIONS, r.kind);
        assertNull(r.id);
    }

    @Test
    public void missingTypeFallsBackToHub() {
        assertEquals(NotificationRoute.Kind.NOTIFICATIONS,
                NotificationRoute.forPayload(payload("post_id", "p1")).kind);
    }

    @Test
    public void postTypesOpenThePost() {
        for (String type : new String[]{"like", "comment", "mention", "comment_like"}) {
            NotificationRoute r = NotificationRoute.forPayload(
                    payload("type", type, "post_id", "p1"));
            assertEquals("type=" + type, NotificationRoute.Kind.POST, r.kind);
            assertEquals("p1", r.id);
        }
    }

    @Test
    public void followTypesOpenTheActorProfile() {
        for (String type : new String[]{"follow", "follow_request", "follow_accepted"}) {
            NotificationRoute r = NotificationRoute.forPayload(
                    payload("type", type, "actor_uid", "u9"));
            assertEquals("type=" + type, NotificationRoute.Kind.PROFILE, r.kind);
            assertEquals("u9", r.id);
        }
    }

    @Test
    public void messageOpensTheConversation() {
        NotificationRoute r = NotificationRoute.forPayload(
                payload("type", "message", "conversation_id", "c7"));
        assertEquals(NotificationRoute.Kind.CHAT, r.kind);
        assertEquals("c7", r.id);
    }

    @Test
    public void callOpensTheCallScreen() {
        NotificationRoute r = NotificationRoute.forPayload(
                payload("type", "incoming_call", "call_id", "call3"));
        assertEquals(NotificationRoute.Kind.CALL, r.kind);
        assertEquals("call3", r.id);
    }

    @Test
    public void terminalCallEventsDismissRatherThanNavigate() {
        assertEquals(NotificationRoute.Kind.NONE,
                NotificationRoute.forPayload(payload("type", "call_ended", "call_id", "call3")).kind);
        assertEquals(NotificationRoute.Kind.NONE,
                NotificationRoute.forPayload(payload("type", "call_cancelled", "call_id", "call3")).kind);
    }

    @Test
    public void missingIdDegradesToHubNotToAWrongScreen() {
        assertEquals(NotificationRoute.Kind.NOTIFICATIONS,
                NotificationRoute.forPayload(payload("type", "like")).kind);
        assertEquals(NotificationRoute.Kind.NOTIFICATIONS,
                NotificationRoute.forPayload(payload("type", "message", "conversation_id", "  ")).kind);
        assertEquals(NotificationRoute.Kind.NOTIFICATIONS,
                NotificationRoute.forPayload(payload("type", "follow", "actor_uid", "")).kind);
    }

    @Test
    public void idsAreTrimmed() {
        NotificationRoute r = NotificationRoute.forPayload(
                payload("type", "like", "post_id", "  p1  "));
        assertEquals("p1", r.id);
    }

    @Test
    public void deepLinksStartANewTaskButTheHubDoesNot() {
        assertTrue(NotificationRoute.forPayload(payload("type", "like", "post_id", "p1")).newTask);
        assertTrue(NotificationRoute.forPayload(payload("type", "message", "conversation_id", "c1")).newTask);
        assertFalse(NotificationRoute.forPayload(payload("type", "nope")).newTask);
        assertFalse(NotificationRoute.forPayload(null).newTask);
    }
}
