// InstaNova — notifications and device registry (Phase 7).
//
// The critical property: a client can never CREATE a notification. If it could, anyone
// could deliver "you have a new follower" to a stranger, and the feed would be a spam channel.

import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection, doc, getDoc, getDocs, limit, orderBy, query, setDoc, updateDoc, deleteDoc, where,
  serverTimestamp,
} from 'firebase/firestore';

const results = [];
const failed = [];
async function check(name, expected, fn) {
  try {
    await fn();
    if (expected === 'allow') results.push(`PASS  allow  ${name}`);
    else { results.push(`FAIL  deny   ${name} (was allowed)`); failed.push(name); }
  } catch (e) {
    if (expected === 'deny') results.push(`PASS  deny   ${name}`);
    else { results.push(`FAIL  allow  ${name}  -> [${e.code || '?'}] ${e.message.split('\n')[0]}`); failed.push(name); }
  }
}

const env = await initializeTestEnvironment({
  projectId: 'instanova-notifications-test',
  firestore: { rules: readFileSync('../../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
});

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'notifications/n1'), {
    recipientUid: 'alice', type: 'like', actorUid: 'bob', targetId: 'p1', text: 'bob liked your post', read: false, createdAt: 1000,
  });
  await setDoc(doc(db, 'notifications/n2'), {
    recipientUid: 'bob', type: 'follow', actorUid: 'alice', targetId: 'alice', text: 'alice followed you', read: false, createdAt: 1100,
  });
  await setDoc(doc(db, 'devices/alice_dev1'), { uid: 'alice', deviceId: 'dev1', token: 'tok-1', lastSeenAt: 1000 });
  // Source-of-truth documents the notification rule re-reads to verify an event is real.
  await setDoc(doc(db, 'posts/p1'), { postId: 'p1', authorUid: 'bob', audience: 'public', caption: 'x', likesCount: 1, commentsCount: 0, createdAt: 1000 });
  await setDoc(doc(db, 'likes/like1'), { likeId: 'like1', postId: 'p1', uid: 'alice', createdAt: 1000 });
  await setDoc(doc(db, 'follows/alice_bob'), { followerUid: 'alice', followingUid: 'bob', createdAt: 1000 });
});

const alice = env.authenticatedContext('alice').firestore();
const zayn = env.authenticatedContext('zayn').firestore();

await check('recipient reads own notification', 'allow', () => getDoc(doc(alice, 'notifications/n1')));

await check('stranger reads someone else\'s notification', 'deny', () =>
  getDoc(doc(zayn, 'notifications/n1')));

// Creation is now client-driven but DERIVED: the rule re-reads the claimed event and
// requires that the caller is its actor and the recipient is the document they touched.
await check('actor reports their own like (derived from the real like doc)', 'allow', () =>
  setDoc(doc(alice, 'notifications/like_like1'), {
    recipientUid: 'bob', type: 'like', actorUid: 'alice', eventId: 'like1', targetId: 'p1',
    text: 'alice liked your post', read: false, createdAt: serverTimestamp(),
  }));

await check('the recipient cannot be redirected to a third party', 'deny', () =>
  setDoc(doc(alice, 'notifications/like_like1b'), {
    recipientUid: 'carol', type: 'like', actorUid: 'alice', eventId: 'like1', targetId: 'p1',
    text: 'x', read: false, createdAt: serverTimestamp(),
  }));

await check('the actor cannot be forged', 'deny', () =>
  setDoc(doc(alice, 'notifications/like_like1c'), {
    recipientUid: 'bob', type: 'like', actorUid: 'bob', eventId: 'like1', targetId: 'p1',
    text: 'x', read: false, createdAt: serverTimestamp(),
  }));

await check('a notification for a non-existent event is denied', 'deny', () =>
  setDoc(doc(alice, 'notifications/like_ghost'), {
    recipientUid: 'bob', type: 'like', actorUid: 'alice', eventId: 'ghost', targetId: 'p1',
    text: 'x', read: false, createdAt: serverTimestamp(),
  }));

await check('someone else\'s follow edge cannot be reported', 'deny', () =>
  setDoc(doc(zayn, 'notifications/follow_alice_bob'), {
    recipientUid: 'bob', type: 'follow', actorUid: 'zayn', eventId: 'alice_bob', targetId: 'alice',
    text: 'x', read: false, createdAt: serverTimestamp(),
  }));

await check('own follow edge reports cleanly', 'allow', () =>
  setDoc(doc(alice, 'notifications/follow_alice_bob'), {
    recipientUid: 'bob', type: 'follow', actorUid: 'alice', eventId: 'alice_bob', targetId: 'alice',
    text: 'alice started following you', read: false, createdAt: serverTimestamp(),
  }));

// Remove what the matrix created so the pagination assertions below still see exactly the
// two seeded notifications. Written with rules disabled: the recipient's own delete rule
// would not let *alice* clean up a notification addressed to bob.
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await deleteDoc(doc(db, 'notifications/like_like1'));
  await deleteDoc(doc(db, 'notifications/follow_alice_bob'));
});

await check('recipient marks their notification read', 'allow', () =>
  updateDoc(doc(alice, 'notifications/n1'), { read: true }));

await check('stranger marks it read', 'deny', () =>
  updateDoc(doc(zayn, 'notifications/n1'), { read: true }));

await check('recipient deletes their notification', 'allow', () =>
  deleteDoc(doc(alice, 'notifications/n1')));

await check('notification page ordered newest first', 'allow', async () => {
  const snap = await getDocs(query(
    collection(env.authenticatedContext('bob').firestore(), 'notifications'),
    where('recipientUid', '==', 'bob'), orderBy('createdAt', 'desc'), limit(20)));
  if (snap.size !== 1) throw new Error(`expected bob's 1 notification, got ${snap.size}`);
});

await check('unread query filters on read == false', 'allow', async () => {
  const snap = await getDocs(query(
    collection(env.authenticatedContext('bob').firestore(), 'notifications'),
    where('recipientUid', '==', 'bob'), where('read', '==', false), limit(100)));
  if (snap.size !== 1) throw new Error(`expected 1 unread, got ${snap.size}`);
});

// ---- device registry -----------------------------------------------------
await check('owner registers a device token', 'allow', () =>
  setDoc(doc(alice, 'devices/alice_dev2'), { uid: 'alice', deviceId: 'dev2', token: 'tok-2' }));

await check('register a device in someone else\'s name', 'deny', () =>
  setDoc(doc(zayn, 'devices/alice_dev3'), { uid: 'alice', deviceId: 'dev3', token: 'tok-3' }));

await check('stranger reads my device tokens', 'deny', () => getDoc(doc(zayn, 'devices/alice_dev1')));

await check('owner refreshes their own token', 'allow', () =>
  updateDoc(doc(alice, 'devices/alice_dev1'), { token: 'tok-1-rotated' }));

await check('stranger deletes my device', 'deny', () => deleteDoc(doc(zayn, 'devices/alice_dev1')));

await check('owner unregisters on logout', 'allow', () => deleteDoc(doc(alice, 'devices/alice_dev2')));

await env.cleanup();

console.log(results.join('\n'));
console.log(`\n${results.length - failed.length}/${results.length} notification/device tests passed`);
process.exit(failed.length === 0 ? 0 : 1);
