package com.insta.nova.calling;

import org.junit.Test;
import static org.junit.Assert.*;

public class CallHistoryStoreTest {

    @Test
    public void testCallRecordMapping() {
        CallSession session = new CallSession("call-audio-1", "conv-1", "caller-a", "recipient-b", CallSession.TYPE_AUDIO, true);
        session.durationSeconds = 125;
        session.status = CallSession.STATUS_ACCEPTED;
        session.recipientUsername = "test_friend";

        assertEquals(125, session.durationSeconds);
        assertEquals(CallSession.STATUS_ACCEPTED, session.status);
        assertFalse(session.isVideo());
        assertEquals("test_friend", session.recipientUsername);
    }

    @Test
    public void testVideoCallRecordProperties() {
        CallSession session = new CallSession("call-video-1", "conv-2", "caller-a", "recipient-b", CallSession.TYPE_VIDEO, false);
        session.status = CallSession.STATUS_MISSED;
        session.callerUsername = "calling_user";

        assertTrue(session.isVideo());
        assertFalse(session.isOutgoing);
        assertEquals(CallSession.STATUS_MISSED, session.status);
        assertEquals("calling_user", session.callerUsername);
    }
}
