'use strict';

/**
 * Notification creation (tasks P7-06, P7-07).
 *
 * Clients cannot create notifications — `firestore.rules` denies it, and that is deliberate:
 * a client that can write its own notifications can also write someone else's. So the
 * document has to be created here, by a trusted writer, in response to an event that
 * already passed the rules.
 *
 * Two properties this module is responsible for:
 *
 *  * **Idempotency (P7-07).** The document id is a claim key derived from the event
 *    (`like_<likeId>`), and the write uses `create()`. A retried trigger, a duplicate
 *    event delivery, or two parallel invocations all collide on the same id and the second
 *    one fails with ALREADY_EXISTS instead of producing a second notification.
 *  * **Silence where the user expects it.** Self-actions never notify. Blocks are checked
 *    in both directions. A disabled preference means no document at all — not a document
 *    that the client then hides, which would leave a badge count that never clears.
 */

const prefs = require('./prefs');

/** Deterministic id so the same event can only ever produce one notification. */
function claimId(type, eventId) {
  return `${type}_${eventId}`;
}

async function getDoc(db, path) {
  const snap = await db.doc(path).get();
  return snap.exists ? snap.data() : null;
}

/** Blocks are stored as `blocks/{blockerUid}_{blockedUid}`. */
async function isBlockedEitherWay(db, a, b) {
  const [ab, ba] = await Promise.all([
    getDoc(db, `blocks/${a}_${b}`),
    getDoc(db, `blocks/${b}_${a}`),
  ]);
  return Boolean(ab || ba);
}

async function isMuted(db, muterUid, mutedUid) {
  return Boolean(await getDoc(db, `mutes/${muterUid}_${mutedUid}`));
}

/**
 * Decide whether this event should produce a notification, and if so write it.
 *
 * Returns { created: boolean, reason?: string } — the reason matters for tests and for the
 * emulator logs; "no notification" with no explanation is untraceable in production.
 */
async function notify(db, { type, eventId, recipientUid, actorUid, targetId, text }) {
  if (!recipientUid || !actorUid) return { created: false, reason: 'missing-uid' };
  if (recipientUid === actorUid) return { created: false, reason: 'self' };

  const recipient = await getDoc(db, `users/${recipientUid}`);
  if (!recipient) return { created: false, reason: 'no-recipient-document' };

  if (!prefs.allows(recipient.settings || {}, prefs.prefKeyFor(type))) {
    return { created: false, reason: 'preference-off' };
  }
  if (await isBlockedEitherWay(db, recipientUid, actorUid)) {
    return { created: false, reason: 'blocked' };
  }
  if (await isMuted(db, recipientUid, actorUid)) {
    return { created: false, reason: 'muted' };
  }

  const id = claimId(type, eventId);
  const doc = {
    notificationId: id,
    recipientUid,
    type,
    actorUid,
    targetId: targetId || '',
    text: text || '',
    read: false,
    createdAt: new Date(),
  };

  try {
    await db.collection('notifications').doc(id).create(doc);
    return { created: true, id };
  } catch (e) {
    if (e && (e.code === 6 || /ALREADY_EXISTS/.test(String(e.message)))) {
      return { created: false, reason: 'duplicate' };
    }
    throw e;
  }
}

/** Like -> notify the post author. */
async function onLike(db, likeId, like) {
  if (!like || !like.postId || !like.uid) return { created: false, reason: 'malformed' };
  const post = await getDoc(db, `posts/${like.postId}`);
  if (!post || !post.authorUid) return { created: false, reason: 'no-post' };
  return notify(db, {
    type: 'like',
    eventId: likeId,
    recipientUid: post.authorUid,
    actorUid: like.uid,
    targetId: like.postId,
    text: 'liked your post',
  });
}

/** Comment -> notify the post author (and the parent comment author, if it is a reply). */
async function onComment(db, commentId, comment) {
  if (!comment || !comment.postId || !comment.authorUid) return { created: false, reason: 'malformed' };
  const post = await getDoc(db, `posts/${comment.postId}`);
  if (!post || !post.authorUid) return { created: false, reason: 'no-post' };
  const result = await notify(db, {
    type: 'comment',
    eventId: commentId,
    recipientUid: post.authorUid,
    actorUid: comment.authorUid,
    targetId: comment.postId,
    text: 'commented on your post',
  });
  // Mentions ride along with the comment that contains them. The author is skipped: they
  // already got the comment notification, and two notifications for one comment is noise.
  await notifyMentions(db, {
    text: comment.text,
    actorUid: comment.authorUid,
    postId: comment.postId,
    eventId: commentId,
    skipUids: [post.authorUid],
  });
  return result;
}

/** Follow edge -> notify the followed account. */
async function onFollow(db, edgeId, edge) {
  if (!edge || !edge.followerUid || !edge.followingUid) return { created: false, reason: 'malformed' };
  return notify(db, {
    type: 'follow',
    eventId: edgeId,
    recipientUid: edge.followingUid,
    actorUid: edge.followerUid,
    targetId: edge.followerUid,
    text: 'started following you',
  });
}


/**
 * Extract @handles from free text. Lowercased and de-duplicated, capped at 10 — a comment
 * with more mentions than that is spam, and resolving each one costs a query.
 */
function parseMentions(text) {
  if (!text) return [];
  const found = new Set();
  const re = /@([a-z0-9._]{3,30})/gi;
  let m;
  while ((m = re.exec(String(text))) !== null) {
    found.add(m[1].toLowerCase());
    if (found.size >= 10) break;
  }
  return [...found];
}

/**
 * Mention -> notify each resolved user. Handles that match nobody are dropped silently:
 * people mistype @names constantly and there is nothing useful to do about it.
 *
 * `skipUids` exists so a comment does not notify the post author twice — once for the
 * comment, once for the mention inside it.
 */
async function notifyMentions(db, { text, actorUid, postId, eventId, skipUids = [] }) {
  const handles = parseMentions(text);
  if (!handles.length) return { created: 0, handles: [] };
  const skip = new Set([actorUid, ...skipUids]);
  let created = 0;
  const resolved = [];
  for (const handle of handles) {
    // eslint-disable-next-line no-await-in-loop
    const snap = await db.collection('publicProfiles').where('usernameLower', '==', handle).limit(1).get();
    if (snap.empty) continue;
    const targetUid = snap.docs[0].id;
    resolved.push(handle);
    if (skip.has(targetUid)) continue;
    // eslint-disable-next-line no-await-in-loop
    const result = await notify(db, {
      type: 'mention',
      // Keyed by the event that carried the mention (the comment), not by the post: being
      // mentioned in two different comments is two events and should notify twice.
      eventId: `${eventId || postId}_${handle}`,
      recipientUid: targetUid,
      actorUid,
      targetId: postId,
      text: 'mentioned you',
    });
    if (result.created) created += 1;
  }
  return { created, handles: resolved };
}

/** Follow request -> notify the account being asked (private accounts only). */
async function onFollowRequest(db, requestId, request) {
  if (!request || !request.requesterUid || !request.targetUid) {
    return { created: false, reason: 'malformed' };
  }
  return notify(db, {
    type: 'follow_request',
    eventId: requestId,
    recipientUid: request.targetUid,
    actorUid: request.requesterUid,
    targetId: request.requesterUid,
    text: 'requested to follow you',
  });
}

/**
 * Chat message -> notify every other member of the conversation.
 *
 * The members map is passed in rather than read here so this can be tested without the
 * Realtime Database emulator: the caller (an RTDB trigger) owns the snapshot read, and this
 * owns the decision. One notification per recipient, keyed `${cid}_${mid}_${uid}` so a
 * retried delivery for one member cannot duplicate another's.
 */
async function onMessage(db, { conversationId, messageId, message, memberUids }) {
  if (!message || !message.senderUid) return { created: 0, reason: 'malformed' };
  const sender = message.senderUid;
  let created = 0;
  for (const uid of memberUids || []) {
    if (!uid || uid === sender) continue;
    // eslint-disable-next-line no-await-in-loop
    const result = await notify(db, {
      type: 'message',
      eventId: `${conversationId}_${messageId}_${uid}`,
      recipientUid: uid,
      actorUid: sender,
      targetId: conversationId,
      text: message.text ? String(message.text).slice(0, 120) : 'sent you a message',
    });
    if (result.created) created += 1;
  }
  return { created };
}

module.exports = {
  claimId, notify, onLike, onComment, onFollow, onFollowRequest, onMessage,
  parseMentions, notifyMentions, isBlockedEitherWay, isMuted,
};
