'use strict';

/**
 * Push delivery for a created notification (task P7-06).
 *
 * Split from notification creation on purpose. The document is the durable record — the
 * bell icon and badge count read it — while the push is best-effort. A push that fails must
 * not roll back the notification, and a quiet-hours window must suppress the buzz without
 * suppressing the record. Fusing the two would force a choice between losing data and
 * waking someone at 03:00.
 *
 * The data payload keys must match `NotificationRoute.KEY_*` on the client, or the tap opens
 * the notifications tab instead of the post. That is the one wire contract in this file.
 */

const prefs = require('./prefs');

/** Max devices per user in one send; a user with more is almost certainly a bug, not a fleet. */
const MAX_DEVICES = 20;

function quietHourNow(now = new Date()) {
  return now.getHours();
}

/**
 * @param {FirebaseFirestore.Firestore} db
 * @param {object} notification the notification document data
 * @param {object} messaging firebase-admin messaging
 * @returns {Promise<{sent:number, skipped:string, pruned:number}>}
 */
async function sendPush(db, notification, messaging, now = new Date()) {
  const recipientUid = notification.recipientUid;
  if (!recipientUid) return { sent: 0, skipped: 'no-recipient', pruned: 0 };

  const recipient = await db.doc(`users/${recipientUid}`).get();
  const settings = (recipient.exists && recipient.data().settings) || {};

  // Quiet hours suppress the buzz, never the record. Calls are exempt inside prefs.
  if (!prefs.deliverNow(settings, prefs.prefKeyFor(notification.type), quietHourNow(now))) {
    return { sent: 0, skipped: 'quiet-hours-or-pref-off', pruned: 0 };
  }

  const devices = await db
    .collection('devices')
    .where('uid', '==', recipientUid)
    .limit(MAX_DEVICES)
    .get();
  if (devices.empty) return { sent: 0, skipped: 'no-devices', pruned: 0 };

  const tokens = [];
  const refByToken = new Map();
  devices.docs.forEach((d) => {
    const token = d.data().token;
    if (token) {
      tokens.push(token);
      refByToken.set(token, d.ref);
    }
  });
  if (!tokens.length) return { sent: 0, skipped: 'no-tokens', pruned: 0 };

  const data = {
    type: notification.type,
    actor_uid: notification.actorUid || '',
    target_id: notification.targetId || '',
    text: notification.text || '',
  };
  // The client maps target_id to post_id / conversation_id / call_id by type; sending the
  // generic key keeps one payload shape for every notification kind.
  if (notification.type === 'like' || notification.type === 'comment') {
    data.post_id = notification.targetId || '';
  } else if (notification.type === 'follow') {
    data.actor_uid = notification.actorUid || '';
  }

  const response = await messaging.sendEachForMulticast({
    tokens,
    data,
    notification: {
      title: 'InstaNova',
      body: `${notification.text || 'New activity'}`,
    },
    android: { priority: 'high' },
  });

  // A token the service rejects as unregistered will never work again; leaving it behind
  // means every future send pays for the same failure.
  let pruned = 0;
  const batch = db.batch();
  response.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (code === 'messaging/registration-token-not-registered' || code === 'messaging/invalid-argument') {
      const ref = refByToken.get(tokens[i]);
      if (ref) {
        batch.delete(ref);
        pruned += 1;
      }
    }
  });
  if (pruned) await batch.commit();

  return { sent: response.successCount, skipped: '', pruned };
}

module.exports = { sendPush, MAX_DEVICES };
