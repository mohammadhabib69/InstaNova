'use strict';

/**
 * Account deletion cascade (task P10-10).
 *
 * Deleting the Auth user alone would leave every post, comment, like and chat message
 * behind under a uid that no longer resolves to a person — data that still renders, still
 * counts in feeds, and can never be attributed or cleaned up afterwards. So the order is:
 * remove the data, then remove the identity, then record what happened.
 *
 * Two things this deliberately does NOT hide:
 *
 *  * **Supabase Storage objects are not deleted.** Media lives in Supabase, not Firebase
 *    Storage, and removing it needs the Supabase service key — which this project does not
 *    hold. Those objects keep working URLs until a separate sweep runs. Recorded as a
 *    limitation rather than silently ignored.
 *  * **Confessions cannot be attributed.** The anonymity rule strips every author field, so
 *    there is no way to find a user's confessions. That is the cost of anonymity, decided
 *    in Phase 9; it is not a bug in this cascade.
 *
 * Counters are decremented rather than recomputed: deleting a like must leave the post's
 * `likesCount` at the truth, or every remaining post that user touched keeps an inflated
 * number forever. Aggregating from scratch would need a full scan of that post's likes.
 */

const BATCH_LIMIT = 400;

/** Collections keyed by a single `uid`-ish field. */
const OWNED = [
  { collection: 'posts', fields: ['authorUid'] },
  { collection: 'likes', fields: ['uid'] },
  { collection: 'comments', fields: ['authorUid'] },
  { collection: 'savedPosts', fields: ['uid'] },
  { collection: 'devices', fields: ['uid'] },
  { collection: 'stories', fields: ['authorUid'] },
  { collection: 'storyViews', fields: ['viewerUid'] },
  { collection: 'notifications', fields: ['recipientUid'] },
  { collection: 'follows', fields: ['followerUid', 'followingUid'] },
  { collection: 'followRequests', fields: ['requesterUid', 'targetUid'] },
  { collection: 'blocks', fields: ['blockerUid', 'blockedUid'] },
  { collection: 'mutes', fields: ['muterUid', 'mutedUid'] },
];

/** Fields that count as "this user authored it" — used for the second removal pass. */
const AUTHORED = ['authorUid', 'senderUid'];

async function collect(db, collection, field, uid) {
  const snap = await db.collection(collection).where(field, '==', uid).get();
  return snap.docs;
}

/** Accepts either query snapshots (which carry `.ref`) or bare DocumentReferences. */
async function deleteDocs(db, docs) {
  const refs = (docs || []).map((d) => (typeof d.ref !== 'undefined' ? d.ref : d));
  let deleted = 0;
  for (let i = 0; i < refs.length; i += BATCH_LIMIT) {
    const chunk = refs.slice(i, i + BATCH_LIMIT);
    if (!chunk.length) continue;
    const batch = db.batch();
    chunk.forEach((ref) => batch.delete(ref));
    // eslint-disable-next-line no-await-in-loop
    await batch.commit();
    deleted += chunk.length;
  }
  return deleted;
}

/**
 * @param {{db: FirebaseFirestore.Firestore, auth?: object, rtdb?: object}} deps
 * @param {string} uid
 * @returns {Promise<object>} counts, for the audit record and the caller's response
 */
async function deleteAccount(deps, uid) {
  const { db, auth, rtdb } = deps;
  if (!uid) throw new Error('uid required');

  const report = { uid_deleted: uid, collections: {}, countersAdjusted: 0, rtdbNodes: 0 };

  // ---- 1. what this user owned, before anything is removed -------------------
  // Post ids must be captured first: once the posts are gone, their likes and comments can
  // no longer be found by postId.
  const ownPosts = await collect(db, 'posts', 'authorUid', uid);
  const ownPostIds = ownPosts.map((d) => d.id);

  // ---- 2. counter corrections ------------------------------------------------
  const adjustments = [];
  const likesByUser = await collect(db, 'likes', 'uid', uid);
  for (const like of likesByUser) {
    const postId = like.data().postId;
    if (postId && !ownPostIds.includes(postId)) {
      adjustments.push({ path: `posts/${postId}`, field: 'likesCount', delta: -1 });
    }
  }
  const commentsByUser = await collect(db, 'comments', 'authorUid', uid);
  for (const comment of commentsByUser) {
    const postId = comment.data().postId;
    if (postId && !ownPostIds.includes(postId)) {
      adjustments.push({ path: `posts/${postId}`, field: 'commentsCount', delta: -1 });
    }
  }
  const edges = new Map();
  for (const field of ['followerUid', 'followingUid']) {
    // eslint-disable-next-line no-await-in-loop
    for (const edge of await collect(db, 'follows', field, uid)) {
      edges.set(edge.id, edge.data());
    }
  }
  for (const edge of edges.values()) {
    if (edge.followerUid === uid && edge.followingUid !== uid) {
      adjustments.push({ path: `publicProfiles/${edge.followingUid}`, field: 'followingCount', delta: -1 });
    }
    if (edge.followingUid === uid && edge.followerUid !== uid) {
      adjustments.push({ path: `publicProfiles/${edge.followerUid}`, field: 'followersCount', delta: -1 });
    }
  }
  if (adjustments.length) {
    const batch = db.batch();
    const { FieldValue } = require('firebase-admin/firestore');
    for (const a of adjustments) {
      batch.set(db.doc(a.path), { [a.field]: FieldValue.increment(a.delta) }, { merge: true });
    }
    await batch.commit();
    report.countersAdjusted = adjustments.length;
  }

  // ---- 3. dependents of this user's posts ------------------------------------
  // Likes and comments on a deleted post are unreachable once the post is gone, so they
  // have to be gathered while the ids are still known.
  let dependents = 0;
  for (const postId of ownPostIds) {
    // eslint-disable-next-line no-await-in-loop
    const likes = await collect(db, 'likes', 'postId', postId);
    // eslint-disable-next-line no-await-in-loop
    const comments = await collect(db, 'comments', 'postId', postId);
    // eslint-disable-next-line no-await-in-loop
    dependents += await deleteDocs(db, likes) + await deleteDocs(db, comments);
  }

  // ---- 4. views on this user's stories --------------------------------------
  const ownStories = await collect(db, 'stories', 'authorUid', uid);
  let storyViews = 0;
  for (const story of ownStories) {
    // eslint-disable-next-line no-await-in-loop
    storyViews += await deleteDocs(db, await collect(db, 'storyViews', 'storyId', story.id));
  }

  // ---- 5. the user's own documents ------------------------------------------
  for (const { collection, fields } of OWNED) {
    const seen = new Set();
    let count = 0;
    for (const field of fields) {
      // eslint-disable-next-line no-await-in-loop
      const docs = await collect(db, collection, field, uid);
      const fresh = docs.filter((d) => !seen.has(d.id));
      fresh.forEach((d) => seen.add(d.id));
      // eslint-disable-next-line no-await-in-loop
      count += await deleteDocs(db, fresh);
    }
    report.collections[collection] = count;
  }

  // Notifications this user *caused* are also theirs to clean up: they reference a uid that
  // will not exist, and the recipient can no longer open the actor's profile from them.
  report.collections.notifications += await deleteDocs(db, await collect(db, 'notifications', 'actorUid', uid));

  // ---- 6. username reservation ---------------------------------------------
  const userSnap = await db.doc(`users/${uid}`).get();
  const username = userSnap.exists ? (userSnap.data().username || '') : '';
  if (username) {
    // eslint-disable-next-line no-await-in-loop
    await deleteDocs(db, await collect(db, 'usernames', 'username', username.toLowerCase()));
  }
  // eslint-disable-next-line no-await-in-loop
  await deleteDocs(db, [db.doc(`users/${uid}`), db.doc(`publicProfiles/${uid}`)]);

  // ---- 7. Realtime Database: chat and presence ------------------------------
  if (rtdb) {
    const nodes = [`presence/${uid}`, `typing_all/${uid}`];
    for (const node of nodes) {
      // eslint-disable-next-line no-await-in-loop
      await rtdb.ref(node).remove().catch(() => {});
      report.rtdbNodes += 1;
    }
    // Remove the user from every conversation they can see, and delete their messages.
    // The member list is the only index from a uid back to its conversations.
    // eslint-disable-next-line no-await-in-loop
    const chats = await rtdb.ref('chats').get();
    const chatsValue = chats.val() || {};
    for (const cid of Object.keys(chatsValue)) {
      const chat = chatsValue[cid] || {};
      const ops = [];
      if (chat.members && chat.members[uid]) ops.push(rtdb.ref(`chats/${cid}/members/${uid}`).remove());
      if (chat.reads && chat.reads[uid]) ops.push(rtdb.ref(`chats/${cid}/reads/${uid}`).remove());
      if (chat.messages) {
        for (const mid of Object.keys(chat.messages)) {
          if (chat.messages[mid] && chat.messages[mid].senderUid === uid) {
            ops.push(rtdb.ref(`chats/${cid}/messages/${mid}`).remove());
          }
        }
      }
      // eslint-disable-next-line no-await-in-loop
      if (ops.length) await Promise.all(ops);
      report.rtdbNodes += ops.length;
    }
  }

  // ---- 8. the identity last -------------------------------------------------
  // Only now: while the Auth user exists its uid still resolves, which is what every
  // deletion above is keyed on.
  if (auth) {
    await auth.revokeRefreshTokens(uid).catch(() => {});
    await auth.deleteUser(uid);
    report.authDeleted = true;
  }

  report.countsDeleted = Object.values(report.collections).reduce((a, b) => a + b, 0) + dependents + storyViews;
  report.dependentLikesAndComments = dependents;
  report.storyViews = storyViews;

  // ---- 9. audit record, no PII ---------------------------------------------
  await db.doc(`deletions/${uid}`).set({
    uidHashHint: uid.slice(0, 6),
    deletedAt: new Date(),
    counts: report.collections,
    countersAdjusted: report.countersAdjusted,
    rtdbNodes: report.rtdbNodes,
    // Recorded so the gap is visible to whoever reads the audit trail, not just to whoever
    // reads this file.
    storageObjectsRemaining: true,
    confessionsUnaffected: true,
  });

  return report;
}

module.exports = { deleteAccount, BATCH_LIMIT, AUTHORED };
