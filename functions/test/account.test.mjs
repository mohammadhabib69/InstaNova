// Account deletion cascade tests (P10-10).
//
//   firebase emulators:exec --only firestore,database,auth "npm test"   (from functions/)
//
// The interesting assertions are not "the user's rows are gone" — that is the easy half.
// They are: the *other* user's data survives, and the counters that pointed at the deleted
// rows come back down. An account deletion that leaves every post it ever liked with an
// inflated count is a bug nobody notices for months.

import { createRequire } from 'node:module';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { getDatabase } from 'firebase-admin/database';

const require = createRequire(import.meta.url);
const { deleteAccount } = require('../lib/account.js');

const projectId = process.env.GCLOUD_PROJECT || 'instanova-functions-test';
const databaseURL = process.env.FIREBASE_DATABASE_EMULATOR_HOST
  ? `http://${process.env.FIREBASE_DATABASE_EMULATOR_HOST}?ns=${projectId}`
  : undefined;

initializeApp({ projectId, databaseURL });
const db = getFirestore();
const auth = getAuth();
const rtdb = getDatabase();

const ALICE = 'alice_uid';
const BOB = 'bob_uid';

const results = [];
const check = (name, ok, detail) => results.push({ name, ok: Boolean(ok), detail });

async function wipeFirestore() {
  for (const c of ['users', 'publicProfiles', 'usernames', 'posts', 'likes', 'comments',
    'follows', 'followRequests', 'blocks', 'mutes', 'savedPosts', 'devices', 'stories',
    'storyViews', 'notifications', 'deletions']) {
    const snap = await db.collection(c).get();
    if (snap.empty) continue;
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
  }
}

async function seed() {
  await db.doc(`users/${ALICE}`).set({ uid: ALICE, username: 'alice', settings: {} });
  await db.doc(`users/${BOB}`).set({ uid: BOB, username: 'bob', settings: {} });
  await db.doc(`publicProfiles/${ALICE}`).set({ uid: ALICE, username: 'alice', usernameLower: 'alice', followersCount: 1, followingCount: 1 });
  await db.doc(`publicProfiles/${BOB}`).set({ uid: BOB, username: 'bob', usernameLower: 'bob', followersCount: 1, followingCount: 1 });
  await db.doc('usernames/alice').set({ username: 'alice', uid: ALICE });

  // Alice's own post, with engagement from other people.
  await db.doc('posts/p_alice').set({ postId: 'p_alice', authorUid: ALICE, caption: 'mine', likesCount: 2, commentsCount: 1 });
  await db.doc('likes/l_bob_alice').set({ likeId: 'l_bob_alice', postId: 'p_alice', uid: BOB });
  await db.doc('likes/l_carol_alice').set({ likeId: 'l_carol_alice', postId: 'p_alice', uid: 'carol_uid' });
  await db.doc('comments/c_bob_alice').set({ commentId: 'c_bob_alice', postId: 'p_alice', authorUid: BOB, text: 'nice' });

  // Bob's post, which Alice liked and commented on. Must survive, with corrected counters.
  await db.doc('posts/p_bob').set({ postId: 'p_bob', authorUid: BOB, caption: 'bob', likesCount: 1, commentsCount: 1 });
  await db.doc('likes/l_alice_bob').set({ likeId: 'l_alice_bob', postId: 'p_bob', uid: ALICE });
  await db.doc('comments/c_alice_bob').set({ commentId: 'c_alice_bob', postId: 'p_bob', authorUid: ALICE, text: 'hey' });

  // Social graph both ways.
  await db.doc(`follows/${ALICE}_${BOB}`).set({ edgeId: `${ALICE}_${BOB}`, followerUid: ALICE, followingUid: BOB });
  await db.doc(`follows/${BOB}_${ALICE}`).set({ edgeId: `${BOB}_${ALICE}`, followerUid: BOB, followingUid: ALICE });

  await db.doc(`devices/${ALICE}_dev`).set({ uid: ALICE, deviceId: 'dev', token: 'tok' });
  await db.doc(`savedPosts/${ALICE}_p_bob`).set({ uid: ALICE, postId: 'p_bob' });
  await db.doc(`blocks/${ALICE}_zayn`).set({ blockerUid: ALICE, blockedUid: 'zayn' });
  await db.doc(`mutes/${ALICE}_zayn`).set({ muterUid: ALICE, mutedUid: 'zayn' });
  await db.doc(`followRequests/${ALICE}_dave`).set({ requesterUid: ALICE, targetUid: 'dave', status: 'pending' });

  await db.doc('notifications/like_x').set({ recipientUid: ALICE, type: 'like', actorUid: BOB, read: false });
  await db.doc('notifications/actor_x').set({ recipientUid: BOB, type: 'like', actorUid: ALICE, read: false });

  await db.doc('stories/s_alice').set({ storyId: 's_alice', authorUid: ALICE, audience: 'public', expiresAt: Date.now() + 3600_000 });
  await db.doc('storyViews/v_alice').set({ viewId: 'v_alice', storyId: 's_alice', viewerUid: BOB });

  // RTDB: one conversation, one message from each side.
  await rtdb.ref('chats/c1/members').set({ [ALICE]: true, [BOB]: true });
  await rtdb.ref('chats/c1/reads').set({ [ALICE]: Date.now() });
  await rtdb.ref('chats/c1/messages/m1').set({ senderUid: ALICE, text: 'from alice', createdAt: Date.now() });
  await rtdb.ref('chats/c1/messages/m2').set({ senderUid: BOB, text: 'from bob', createdAt: Date.now() });

  await auth.createUser({ uid: ALICE, email: `alice_${Date.now()}@example.test` });
}

await wipeFirestore();
await rtdb.ref('chats').remove();
try {
  await auth.deleteUser(ALICE);
} catch (e) {
  // not there — fine
}
await seed();

const report = await deleteAccount({ db, auth, rtdb }, ALICE);

// ---- the user's own footprint --------------------------------------------------
check('own post is gone', !(await db.doc('posts/p_alice').get()).exists);
check('likes by others on that post are gone', !(await db.doc('likes/l_bob_alice').get()).exists);
check('comments by others on that post are gone', !(await db.doc('comments/c_bob_alice').get()).exists);
check('the user document is gone', !(await db.doc(`users/${ALICE}`).get()).exists);
check('the public projection is gone', !(await db.doc(`publicProfiles/${ALICE}`).get()).exists);
check('the username reservation is released', !(await db.doc('usernames/alice').get()).exists);
check('devices are gone', !(await db.doc(`devices/${ALICE}_dev`).get()).exists);
check('saved posts are gone', !(await db.doc(`savedPosts/${ALICE}_p_bob`).get()).exists);
check('blocks are gone', !(await db.doc(`blocks/${ALICE}_zayn`).get()).exists);
check('mutes are gone', !(await db.doc(`mutes/${ALICE}_zayn`).get()).exists);
check('follow requests are gone', !(await db.doc(`followRequests/${ALICE}_dave`).get()).exists);
check('stories are gone', !(await db.doc('stories/s_alice').get()).exists);
check('views of those stories are gone', !(await db.doc('storyViews/v_alice').get()).exists);
check('notifications addressed to the user are gone', !(await db.doc('notifications/like_x').get()).exists);
check('notifications caused by the user are gone', !(await db.doc('notifications/actor_x').get()).exists);

// ---- the other user is untouched, except for counters --------------------------
const bobPost = (await db.doc('posts/p_bob').get()).data() || {};
check('the other user\'s post survives', bobPost.caption === 'bob', JSON.stringify(bobPost.caption));
check('its like count is corrected', bobPost.likesCount === 0, String(bobPost.likesCount));
check('its comment count is corrected', bobPost.commentsCount === 0, String(bobPost.commentsCount));
check('the other user document survives', (await db.doc(`users/${BOB}`).get()).exists);

const bobProfile = (await db.doc(`publicProfiles/${BOB}`).get()).data() || {};
check('its follower count is corrected', bobProfile.followersCount === 0, String(bobProfile.followersCount));
check('its following count is corrected', bobProfile.followingCount === 0, String(bobProfile.followingCount));

// ---- realtime database ---------------------------------------------------------
check('the conversation membership is removed', !(await rtdb.ref(`chats/c1/members/${ALICE}`).get()).exists());
check('the read marker is removed', !(await rtdb.ref(`chats/c1/reads/${ALICE}`).get()).exists());
check('their messages are removed', !(await rtdb.ref('chats/c1/messages/m1').get()).exists());
check('the other user\'s messages survive', (await rtdb.ref('chats/c1/messages/m2').get()).exists());

// ---- identity and audit --------------------------------------------------------
let authGone = false;
try {
  await auth.getUser(ALICE);
} catch (e) {
  authGone = true;
}
check('the Auth user is deleted', authGone);

const audit = (await db.doc(`deletions/${ALICE}`).get()).data() || {};
check('an audit record is written', Boolean(audit.deletedAt), JSON.stringify(Object.keys(audit)));
check('the audit record names the remaining gaps', audit.storageObjectsRemaining === true && audit.confessionsUnaffected === true);
check(
  'the report counts what it deleted',
  report.countsDeleted > 0 && report.countersAdjusted > 0,
  JSON.stringify({ deleted: report.countsDeleted, counters: report.countersAdjusted })
);

let failed = 0;
for (const r of results) {
  if (!r.ok) failed += 1;
  console.log(`${r.ok ? 'PASS' : 'FAIL'}  ${r.name}${r.ok ? '' : `  -> ${r.detail}`}`);
}
console.log(`\n${results.length - failed}/${results.length} account deletion tests passed`);
process.exit(failed === 0 ? 0 : 1);
