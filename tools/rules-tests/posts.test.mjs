// InstaNova — posts, likes and saves (tasks P4-01..P4-04).
//
// Verifies the two things rules must get right here: private authors stay hidden unless
// the viewer follows them, and counters can only move one step at a time.

import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, increment, query, where, collection } from 'firebase/firestore';

const results = [];
const failed = [];
async function check(name, expected, fn) {
  try {
    await fn();
    if (expected === 'allow') results.push(`PASS  allow  ${name}`);
    else { results.push(`FAIL  deny   ${name} (was allowed)`); failed.push(name); }
  } catch (e) {
    if (expected === 'deny') results.push(`PASS  deny   ${name}`);
    else { results.push(`FAIL  allow  ${name}  -> ${e.message.split('\n')[0]}`); failed.push(name); }
  }
}

const env = await initializeTestEnvironment({
  projectId: 'instanova-posts-test',
  firestore: { rules: readFileSync('../../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
});

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'publicProfiles/alice'), { uid: 'alice', username: 'alice', usernameLower: 'alice', isPrivate: false });
  await setDoc(doc(db, 'publicProfiles/bob'), { uid: 'bob', username: 'bob', usernameLower: 'bob', isPrivate: true });
  await setDoc(doc(db, 'publicProfiles/carol'), { uid: 'carol', username: 'carol', usernameLower: 'carol', isPrivate: false });
  await setDoc(doc(db, 'follows/alice_bob'), { edgeId: 'alice_bob', followerUid: 'alice', followingUid: 'bob' });
  await setDoc(doc(db, 'posts/p1'), { postId: 'p1', authorUid: 'carol', caption: 'hello', audience: 'public', likesCount: 0, commentsCount: 0, createdAt: 1000 });
  await setDoc(doc(db, 'posts/p2'), { postId: 'p2', authorUid: 'bob', caption: 'private', audience: 'followers', likesCount: 0, commentsCount: 0, createdAt: 900 });
});

const alice = env.authenticatedContext('alice').firestore();
const carol = env.authenticatedContext('carol').firestore();
const zayn = env.authenticatedContext('zayn').firestore();

const newPost = (over = {}) => ({
  postId: 'p9', authorUid: 'carol', caption: 'x', audience: 'public',
  likesCount: 0, commentsCount: 0, createdAt: 1100, ...over,
});

await check('author creates own post with zeroed counters', 'allow', () =>
  setDoc(doc(carol, 'posts/p9'), newPost()));

await check('create a post in someone else\'s name', 'deny', () =>
  setDoc(doc(zayn, 'posts/p10'), newPost({ postId: 'p10', authorUid: 'carol' })));

await check('create a post with a pre-seeded like count', 'deny', () =>
  setDoc(doc(carol, 'posts/p11'), newPost({ postId: 'p11', likesCount: 5 })));

await check('create a post whose postId field disagrees with the doc id', 'deny', () =>
  setDoc(doc(carol, 'posts/p12'), newPost({ postId: 'other' })));

await check('signed-in user reads a public post', 'allow', () => getDoc(doc(alice, 'posts/p1')));

await check('stranger reads a private author\'s post', 'deny', () => getDoc(doc(zayn, 'posts/p2')));

// LIMITATION L2: with `audience: 'followers'` and no Cloud Function, only the author can
// read it. Followers get access once dispatch moves server-side.
await check('follower reads a followers-only post (blocked until Functions)', 'deny', () =>
  getDoc(doc(alice, 'posts/p2')));

await check('author reads own followers-only post', 'allow', () =>
  getDoc(doc(env.authenticatedContext('bob').firestore(), 'posts/p2')));

await check('like a post (create like doc + +1 counter)', 'allow', async () => {
  await setDoc(doc(alice, 'likes/p1_alice'), { likeId: 'p1_alice', postId: 'p1', uid: 'alice' });
  await updateDoc(doc(alice, 'posts/p1'), { likesCount: increment(1) });
});

await check('create a like in another user\'s name', 'deny', () =>
  setDoc(doc(zayn, 'likes/p1_alice'), { likeId: 'p1_alice', postId: 'p1', uid: 'alice' }));

await check('bump a counter by two', 'deny', () =>
  updateDoc(doc(alice, 'posts/p1'), { likesCount: increment(2) }));

await check('non-author edits the caption', 'deny', () =>
  updateDoc(doc(alice, 'posts/p1'), { caption: 'pwned' }));

await check('author edits own caption', 'allow', () =>
  updateDoc(doc(carol, 'posts/p1'), { caption: 'edited' }));

await check('author cannot raise their own like count', 'deny', () =>
  updateDoc(doc(carol, 'posts/p1'), { likesCount: increment(9) }));

await check('save a post', 'allow', () =>
  setDoc(doc(alice, 'savedPosts/alice_p1'), { saveId: 'alice_p1', uid: 'alice', postId: 'p1' }));

await check('read someone else\'s saves', 'deny', () => getDoc(doc(zayn, 'savedPosts/alice_p1')));

await check('delete someone else\'s post', 'deny', () => deleteDoc(doc(zayn, 'posts/p1')));

await check('author deletes own post', 'allow', () => deleteDoc(doc(carol, 'posts/p9')));

// ---- P4-10: cascade delete permissions -------------------------------
// NOTE: likes are create-once (update is denied by rule), so each case uses its own
// document and a fresh creator — reusing p1_alice turned setDoc into an update and failed
// for the wrong reason.
await check('post author removes someone else\'s like on their post', 'allow', async () => {
  await setDoc(doc(zayn, 'likes/p1_zayn'), { likeId: 'p1_zayn', postId: 'p1', uid: 'zayn' });
  await deleteDoc(doc(carol, 'likes/p1_zayn'));    // carol owns post p1
});

await check('unrelated user cannot remove that like', 'deny', async () => {
  await setDoc(doc(zayn, 'likes/p1_zayn'), { likeId: 'p1_zayn', postId: 'p1', uid: 'zayn' });
  await deleteDoc(doc(bob, 'likes/p1_zayn'));      // bob is neither the liker nor the author
});

await check('post author reads the likes they must cascade', 'allow', () =>
  getDocs(query(collection(carol, 'likes'), where('postId', '==', 'p1'))));

await env.cleanup();

console.log(results.join('\n'));
console.log(`\n${results.length - failed.length}/${results.length} post/like/save tests passed`);
process.exit(failed.length === 0 ? 0 : 1);
