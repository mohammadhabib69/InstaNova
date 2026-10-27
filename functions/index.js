'use strict';

/**
 * InstaNova Cloud Functions — trigger wiring only.
 *
 * Every trigger here is three lines that delegate to a tested module. That is deliberate:
 * the trigger glue is the part that cannot be unit-tested (it needs the emulator to fire),
 * so the logic lives somewhere it can be, and this file stays small enough to read.
 *
 * Deploying needs Blaze. Running them does not:
 *   firebase emulators:exec --only firestore,functions "npm test"
 *
 * What is NOT here, and why:
 *  * Counters (P2-04) — the client already maintains them ±1 under a field allow-list.
 *    Making them authoritative here would mean two writers and a race, for no gain while
 *    the numbers are verified correct (P3-12: 8/8).
 *  * Moderation (P9-05) — needs a classifier that does not exist yet; the rules already
 *    deny client-side publishing of the fields it would write.
 *  * Incoming calls — the caller's client already raises the call notification through FCM;
 *    a trigger here would double-notify.
 */

const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const { onDocumentCreated } = require('firebase-functions/v2/firestore');
const { onValueCreated } = require('firebase-functions/v2/database');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { setGlobalOptions } = require('firebase-functions/v2');

const notify = require('./lib/notify');
const push = require('./lib/push');
const { cleanupExpiredStories } = require('./lib/cleanup');
const { deleteAccount } = require('./lib/account');

initializeApp();
setGlobalOptions({ region: 'asia-southeast1', maxInstances: 10 });

const REGION = 'asia-southeast1';

exports.onLikeCreated = onDocumentCreated(
  { document: 'likes/{likeId}', region: REGION },
  async (event) => {
    const db = getFirestore();
    const result = await notify.onLike(db, event.params.likeId, event.data && event.data.data());
    if (!result.created) {
      console.log(`like ${event.params.likeId}: no notification (${result.reason})`);
    }
  }
);

exports.onCommentCreated = onDocumentCreated(
  { document: 'comments/{commentId}', region: REGION },
  async (event) => {
    const db = getFirestore();
    const result = await notify.onComment(db, event.params.commentId, event.data && event.data.data());
    if (!result.created) {
      console.log(`comment ${event.params.commentId}: no notification (${result.reason})`);
    }
  }
);

exports.onFollowCreated = onDocumentCreated(
  { document: 'follows/{edgeId}', region: REGION },
  async (event) => {
    const db = getFirestore();
    const result = await notify.onFollow(db, event.params.edgeId, event.data && event.data.data());
    if (!result.created) {
      console.log(`follow ${event.params.edgeId}: no notification (${result.reason})`);
    }
  }
);

/** Private accounts collect requests instead of edges, so this is its own trigger. */
exports.onFollowRequestCreated = onDocumentCreated(
  { document: 'followRequests/{requestId}', region: REGION },
  async (event) => {
    const db = getFirestore();
    const result = await notify.onFollowRequest(
      db,
      event.params.requestId,
      event.data && event.data.data()
    );
    if (!result.created) {
      console.log(`followRequest ${event.params.requestId}: no notification (${result.reason})`);
    }
  }
);

/**
 * Chat lives in the Realtime Database, so this is an RTDB trigger, not a Firestore one.
 * Members are read once per message: a fan-out loop that re-read the member map per
 * recipient would multiply the cost by the group size.
 */
exports.onChatMessageCreated = onValueCreated(
  { ref: '/chats/{conversationId}/messages/{messageId}', region: REGION },
  async (event) => {
    const db = getFirestore();
    const snapshot = event.data;
    if (!snapshot || !snapshot.exists()) return;
    const conversationId = event.params.conversationId;
    const membersSnap = await snapshot.ref.root.child(`chats/${conversationId}/members`).get();
    const memberUids = membersSnap.exists() ? Object.keys(membersSnap.val()) : [];
    const result = await notify.onMessage(db, {
      conversationId,
      messageId: event.params.messageId,
      message: snapshot.val(),
      memberUids,
    });
    console.log(`message ${event.params.messageId}: notifications=${result.created}`);
  }
);

/** Delivery is separate from creation so a failed push cannot lose the record. */
exports.onNotificationCreated = onDocumentCreated(
  { document: 'notifications/{notificationId}', region: REGION },
  async (event) => {
    const db = getFirestore();
    const data = event.data && event.data.data();
    if (!data) return;
    const result = await push.sendPush(db, data, getMessaging());
    if (result.skipped) {
      console.log(`notification ${event.params.notificationId}: push skipped (${result.skipped})`);
    } else {
      console.log(`notification ${event.params.notificationId}: pushed=${result.sent} pruned=${result.pruned}`);
    }
  }
);

/** P5-04. Hourly is frequent enough for 24h stories and cheap at this volume. */
exports.cleanupExpiredStories = onSchedule(
  { schedule: 'every 60 minutes', region: REGION, timeZone: 'Asia/Dhaka' },
  async () => {
    const db = getFirestore();
    let total = { deletedStories: 0, deletedViews: 0 };
    // Drain the backlog within one invocation, bounded by a hard iteration cap so a table
    // full of expired rows cannot run the function past its timeout.
    for (let i = 0; i < 10; i += 1) {
      const result = await cleanupExpiredStories(db);
      total.deletedStories += result.deletedStories;
      total.deletedViews += result.deletedViews;
      if (!result.remaining) break;
    }
    console.log(`story cleanup: stories=${total.deletedStories} views=${total.deletedViews}`);
  }
);

/**
 * Account deletion (P10-10). Callable, so the caller is authenticated by the framework and
 * the uid cannot be spoofed — a client-supplied uid would let anyone delete anyone.
 */
exports.deleteAccount = onCall({ region: REGION }, async (request) => {
  const uid = request.auth && request.auth.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Sign in required.');

  const { getAuth } = require('firebase-admin/auth');
  const { getDatabase } = require('firebase-admin/database');

  const report = await deleteAccount(
    { db: getFirestore(), auth: getAuth(), rtdb: getDatabase() },
    uid
  );
  // Only a count leaves the server; the caller does not need the breakdown and should not
  // learn anything about rows it never owned.
  return { ok: true, deleted: report.countsDeleted };
});
