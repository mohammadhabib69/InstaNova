// Notification creation tests (P7-06, P7-07) — run against the Firestore emulator.
//
//   firebase emulators:exec --only firestore "npm test"     (from functions/)
//
// These call the same modules the triggers call. The trigger wrappers in index.js are three
// lines each precisely so that this covers the behaviour and the wiring stays trivial.

import { createRequire } from 'node:module';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const require = createRequire(import.meta.url);
const notify = require('../lib/notify.js');
const prefs = require('../lib/prefs.js');

const projectId = process.env.GCLOUD_PROJECT || 'instanova-functions-test';
initializeApp({ projectId });
const db = getFirestore();

const results = [];
function check(name, condition, detail) {
  results.push({ name, ok: Boolean(condition), detail });
}

const ALICE = 'alice_uid';
const BOB = 'bob_uid';
const CAROL = 'carol_uid';

async function seed() {
  await db.doc(`users/${ALICE}`).set({ uid: ALICE, username: 'alice', settings: {} });
  await db.doc(`users/${BOB}`).set({ uid: BOB, username: 'bob', settings: {} });
  await db.doc(`users/${CAROL}`).set({ uid: CAROL, username: 'carol', settings: {} });
  await db.doc('posts/p1').set({ postId: 'p1', authorUid: ALICE, caption: 'first' });
}

async function reset() {
  for (const path of ['notifications', 'likes', 'comments', 'follows', 'blocks', 'mutes']) {
    const snap = await db.collection(path).get();
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
  }
}

const countNotifications = async (id) => {
  if (id) {
    const snap = await db.doc(`notifications/${id}`).get();
    return snap.exists ? 1 : 0;
  }
  const all = await db.collection('notifications').get();
  return all.size;
};

await reset();
await seed();

// ---- happy path ---------------------------------------------------------------
let r = await notify.onLike(db, 'like1', { postId: 'p1', uid: BOB });
check('like by another user creates a notification', r.created && r.id === 'like_like1', JSON.stringify(r));
const created = (await db.doc('notifications/like_like1').get()).data() || {};
check('recipient is the post author', created.recipientUid === ALICE, created.recipientUid);
check('type is the canonical lowercase form the client routes on', created.type === 'like', created.type);
check('actor is the liker', created.actorUid === BOB, created.actorUid);
check('starts unread', created.read === false, String(created.read));

// ---- self action --------------------------------------------------------------
r = await notify.onLike(db, 'like2', { postId: 'p1', uid: ALICE });
check('liking your own post is silent', !r.created && r.reason === 'self', JSON.stringify(r));
check('and writes nothing', (await countNotifications('like_like2')) === 0);

// ---- idempotency (P7-07) ------------------------------------------------------
r = await notify.onLike(db, 'like1', { postId: 'p1', uid: BOB });
check('replaying the same event is a duplicate', !r.created && r.reason === 'duplicate', JSON.stringify(r));
check('duplicate does not create a second document', (await countNotifications('like_like1')) === 1);
check(
  'total notifications is still 1 after replay',
  (await countNotifications()) === 1,
  String(await countNotifications())
);

// ---- preference off -----------------------------------------------------------
await db.doc(`users/${ALICE}`).set({ settings: { notifyLikes: false } }, { merge: true });
r = await notify.onLike(db, 'like3', { postId: 'p1', uid: BOB });
check('disabled preference suppresses the document entirely', !r.created && r.reason === 'preference-off', JSON.stringify(r));
await db.doc(`users/${ALICE}`).set({ settings: { notifyLikes: true } }, { merge: true });

// ---- blocks, both directions --------------------------------------------------
await db.doc(`blocks/${ALICE}_${BOB}`).set({ blockerUid: ALICE, blockedUid: BOB });
r = await notify.onLike(db, 'like4', { postId: 'p1', uid: BOB });
check('blocked actor cannot notify', !r.created && r.reason === 'blocked', JSON.stringify(r));
await db.doc(`blocks/${ALICE}_${BOB}`).delete();

await db.doc(`blocks/${CAROL}_${ALICE}`).set({ blockerUid: CAROL, blockedUid: ALICE });
r = await notify.onLike(db, 'like5', { postId: 'p1', uid: CAROL });
check(
  'recipient blocking the actor also suppresses it',
  !r.created && r.reason === 'blocked',
  JSON.stringify(r)
);
await db.doc(`blocks/${CAROL}_${ALICE}`).delete();

// ---- mutes --------------------------------------------------------------------
await db.doc(`mutes/${ALICE}_${BOB}`).set({ muterUid: ALICE, mutedUid: BOB });
r = await notify.onLike(db, 'like6', { postId: 'p1', uid: BOB });
check('muted actor is silent', !r.created && r.reason === 'muted', JSON.stringify(r));
await db.doc(`mutes/${ALICE}_${BOB}`).delete();

// ---- comments -----------------------------------------------------------------
r = await notify.onComment(db, 'c1', { postId: 'p1', authorUid: BOB, text: 'nice' });
check('comment notifies the post author', r.created && r.id === 'comment_c1', JSON.stringify(r));

// ---- follows ------------------------------------------------------------------
r = await notify.onFollow(db, `${CAROL}_${ALICE}`, { followerUid: CAROL, followingUid: ALICE });
check('follow notifies the followed account', r.created && r.id === `follow_${CAROL}_${ALICE}`, JSON.stringify(r));

// ---- missing documents --------------------------------------------------------
r = await notify.onLike(db, 'like7', { postId: 'missing', uid: BOB });
check('like on a deleted post writes nothing', !r.created && r.reason === 'no-post', JSON.stringify(r));
r = await notify.notify(db, {
  type: 'follow',
  eventId: 'x_y',
  recipientUid: 'ghost_uid',
  actorUid: BOB,
  targetId: BOB,
  text: 'started following you',
});
check(
  'a recipient with no user document writes nothing',
  !r.created && r.reason === 'no-recipient-document',
  JSON.stringify(r)
);

// ---- mentions ------------------------------------------------------------------
await db.doc(`publicProfiles/${CAROL}`).set({ uid: CAROL, username: 'carol', usernameLower: 'carol' });

r = await notify.onComment(db, 'c2', { postId: 'p1', authorUid: BOB, text: 'hey @carol look at this' });
check(
  'a mention creates its own notification',
  (await countNotifications('mention_c2_carol')) === 1,
  'mention_c2_carol'
);
check(
  'the post author still gets exactly one comment notification',
  (await countNotifications('comment_c2')) === 1
);
check(
  'the author is not double-notified by a mention inside their own comment',
  (await countNotifications('mention_c2_alice')) === 0
);

r = await notify.notifyMentions(db, { text: '@nobody_at_all hi', actorUid: BOB, postId: 'p1', eventId: 'c9' });
check('an unknown handle resolves to nobody and writes nothing', r.created === 0, JSON.stringify(r));

r = await notify.onComment(db, 'c3', { postId: 'p1', authorUid: BOB, text: 'note to self @bob' });
check(
  'mentioning yourself writes no mention notification',
  (await countNotifications('mention_c3_bob')) === 0
);

check('handles are parsed case-insensitively and de-duplicated',
  JSON.stringify(notify.parseMentions('@Bob and @bob and @carol')) === JSON.stringify(['bob', 'carol']),
  JSON.stringify(notify.parseMentions('@Bob and @bob and @carol')));
check('short or empty handles are ignored', notify.parseMentions('@a @ @!!').length === 0,
  JSON.stringify(notify.parseMentions('@a @ @!!')));

// ---- follow requests ------------------------------------------------------------
r = await notify.onFollowRequest(db, 'req1', { requesterUid: BOB, targetUid: ALICE });
check('a follow request notifies the target', r.created && r.id === 'follow_request_req1', JSON.stringify(r));
const reqDoc = (await db.doc('notifications/follow_request_req1').get()).data() || {};
check('follow_request type routes to a profile', reqDoc.type === 'follow_request', reqDoc.type);
check('the actor is the requester', reqDoc.actorUid === BOB, reqDoc.actorUid);

// ---- messages -------------------------------------------------------------------
r = await notify.onMessage(db, {
  conversationId: 'cid1',
  messageId: 'm1',
  message: { senderUid: BOB, text: 'hello there' },
  memberUids: [BOB, ALICE, CAROL],
});
check('a message notifies every member except the sender', r.created === 2, JSON.stringify(r));
check('recipient A is notified', (await countNotifications('message_cid1_m1_' + ALICE)) === 1);
check('recipient B is notified', (await countNotifications('message_cid1_m1_' + CAROL)) === 1);
check('the sender is not notified of their own message',
  (await countNotifications('message_cid1_m1_' + BOB)) === 0);

r = await notify.onMessage(db, {
  conversationId: 'cid1',
  messageId: 'm1',
  message: { senderUid: BOB, text: 'hello there' },
  memberUids: [BOB, ALICE, CAROL],
});
check('replaying a message delivery creates nothing new', r.created === 0, JSON.stringify(r));

// ---- the Java/JS preference port must agree -----------------------------------
check('prefs: missing settings allows', prefs.allows(null, prefs.TYPES.LIKES));
check('prefs: string "false" mutes', !prefs.allows({ notifyLikes: 'false' }, prefs.TYPES.LIKES));
check('prefs: overnight window wraps', prefs.inQuietHours({ quietStartHour: 22, quietEndHour: 7 }, 3));
check('prefs: 07:00 is outside the window', !prefs.inQuietHours({ quietStartHour: 22, quietEndHour: 7 }, 7));
check('prefs: calls bypass quiet hours', prefs.deliverNow({ quietStartHour: 22, quietEndHour: 7 }, prefs.TYPES.CALLS, 3));
check('prefs: a like at 03:00 waits', !prefs.deliverNow({ quietStartHour: 22, quietEndHour: 7 }, prefs.TYPES.LIKES, 3));
check('prefs: junk fails open', prefs.allows({ notifyLikes: 'maybe' }, prefs.TYPES.LIKES));

// ---- report -------------------------------------------------------------------
let failed = 0;
for (const r2 of results) {
  if (!r2.ok) failed += 1;
  console.log(`${r2.ok ? 'PASS' : 'FAIL'}  ${r2.name}${r2.ok ? '' : `  -> ${r2.detail}`}`);
}
console.log(`\n${results.length - failed}/${results.length} notification tests passed`);
process.exit(failed === 0 ? 0 : 1);
