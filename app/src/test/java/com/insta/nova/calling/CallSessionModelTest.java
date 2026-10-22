package com.insta.nova.calling;

import org.junit.Test;
import org.webrtc.SessionDescription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class CallSessionModelTest {

    @Test
    public void testCallSessionInitialization() {
        CallSession session = new CallSession("call-123", "conv-456", "caller-1", "recipient-2", CallSession.TYPE_AUDIO, true);
        assertEquals("call-123", session.callId);
        assertEquals("conv-456", session.conversationId);
        assertEquals("caller-1", session.callerId);
        assertEquals("recipient-2", session.recipientId);
        assertEquals(CallSession.TYPE_AUDIO, session.callType);
        assertTrue(session.isOutgoing);
        assertEquals(CallSession.STATUS_RINGING, session.status);
        assertFalse(session.isVideo());
    }

    @Test
    public void testVideoCallDetection() {
        CallSession session = new CallSession("call-v1", "conv-456", "caller-1", "recipient-2", CallSession.TYPE_VIDEO, true);
        assertTrue(session.isVideo());
        assertEquals(CallSession.TYPE_VIDEO, session.callType);
    }

    @Test
    public void testLocalSignalingBusRegistrationAndDispatch() {
        LocalSignalingBus bus = LocalSignalingBus.getInstance();
        String testCallId = "bus-test-" + System.currentTimeMillis();

        CallSession session = new CallSession(testCallId, "conv-1", "c1", "r1", CallSession.TYPE_AUDIO, true);
        bus.saveSession(session);

        CallSession retrieved = bus.getSession(testCallId);
        assertNotNull(retrieved);
        assertEquals(testCallId, retrieved.callId);

        AtomicReference<String> endReason = new AtomicReference<>(null);
        LocalSignalingBus.SignalingEventListener listener = new LocalSignalingBus.SignalingEventListener() {
            @Override public void onCallSessionUpdate(CallSession s) {}
            @Override public void onRemoteOfferReceived(String id, SessionDescription sdp) {}
            @Override public void onRemoteAnswerReceived(String id, SessionDescription sdp) {}
            @Override public void onRemoteIceCandidateReceived(String id, org.webrtc.IceCandidate candidate) {}
            @Override public void onCallEnded(String id, String reason) {
                endReason.set(reason);
            }
        };

        bus.registerListener(testCallId, listener);
        bus.postCallEnded(testCallId, "cancelled");

        bus.unregisterListener(testCallId, listener);
        bus.removeSession(testCallId);
        assertNull(bus.getSession(testCallId));
    }

    @Test
    public void testUuidValidationAndSanitization() {
        String validUuid = java.util.UUID.randomUUID().toString();
        assertTrue(CallManager.isValidUuid(validUuid));
        assertFalse(CallManager.isValidUuid("call-" + validUuid));
        assertFalse(CallManager.isValidUuid("conv-123"));
        assertFalse(CallManager.isValidUuid(null));
        assertFalse(CallManager.isValidUuid(""));
        assertFalse(CallManager.isValidUuid("not-a-uuid"));

        // Test cleanUuid
        assertEquals(validUuid, CallSignalingClient.cleanUuid("call-" + validUuid));
        assertEquals(validUuid, CallSignalingClient.cleanUuid(validUuid));
        assertNull(CallSignalingClient.cleanUuid(null));
        assertEquals("random-str", CallSignalingClient.cleanUuid("random-str"));
    }

    @Test
    public void testCallSessionDtoDeserializationWithProfiles() {
        String json = "{\n" +
                "  \"id\": \"e9231f4a-810a-4e2b-bb47-3cfcf0ec7f12\",\n" +
                "  \"conversation_id\": \"b32870e2-63e8-4660-84cf-eb95627e7eb1\",\n" +
                "  \"caller_id\": \"40ffc0b4-f194-46de-965f-529cfce0cfd4\",\n" +
                "  \"recipient_id\": \"3ca21443-ceac-4151-b812-952c35f02a10\",\n" +
                "  \"call_type\": \"video\",\n" +
                "  \"status\": \"ringing\",\n" +
                "  \"duration_seconds\": 45,\n" +
                "  \"caller\": {\n" +
                "    \"id\": \"40ffc0b4-f194-46de-965f-529cfce0cfd4\",\n" +
                "    \"username\": \"alice\",\n" +
                "    \"avatar_url\": \"https://example.com/alice.jpg\"\n" +
                "  },\n" +
                "  \"recipient\": {\n" +
                "    \"id\": \"3ca21443-ceac-4151-b812-952c35f02a10\",\n" +
                "    \"username\": \"bob\",\n" +
                "    \"avatar_url\": \"https://example.com/bob.jpg\"\n" +
                "  }\n" +
                "}";

        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.insta.nova.data.remote.dto.CallSessionDto dto = gson.fromJson(json, com.insta.nova.data.remote.dto.CallSessionDto.class);

        assertNotNull(dto);
        assertEquals("e9231f4a-810a-4e2b-bb47-3cfcf0ec7f12", dto.id);
        assertEquals("b32870e2-63e8-4660-84cf-eb95627e7eb1", dto.conversationId);
        assertEquals("video", dto.callType);
        assertEquals("ringing", dto.status);
        assertEquals(45, dto.durationSeconds);

        assertNotNull(dto.caller);
        assertEquals("alice", dto.caller.getUsername());
        assertEquals("https://example.com/alice.jpg", dto.caller.getAvatarUrl());

        assertNotNull(dto.recipient);
        assertEquals("bob", dto.recipient.getUsername());
        assertEquals("https://example.com/bob.jpg", dto.recipient.getAvatarUrl());
    }
}
