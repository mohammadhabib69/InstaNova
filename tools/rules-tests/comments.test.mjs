// InstaNova — comments + comment likes + feed pagination (tasks P4-05, P4-03).
//
// Comment visibility must follow the POST's visibility, and pagination must not repeat
// documents across pages — both are easy to get subtly wrong.

import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection, doc, getDoc, getDocs, limit, orderBy, query, setDoc, updateDoc, deleteDoc,
  startAfter, increment, where,
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
    else { results.push(`FAIL  allow  ${name}  -> [${e.code || 'no-code'}] ${e.message.split('\n')[0]}`); failed.push(name); }
  }
}

const env = await initializeTestEnvironment({
  projectId: 'instanova-comments-test',
  firestore: { rules: readFileSync('../../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
});

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'publicProfiles/alice'), { uid: 'alice', username: 'alice', usernameLower: 'alice', isPrivate: false });
  await setDoc(doc(db, 'publicProfiles/bob'), { uid: 'bob', username: 'bob', usernameLower: 'bob', isPrivate: true });
  await setDoc(doc(db, 'publicProfiles/carol'), { uid: 'carol', username: 'carol', usernameLower: 'carol', isPrivate: false });
  await setDoc(doc(db, 'posts/p1'), { postId: 'p1', authorUid: 'carol', caption: 'public', audience: 'public', likesCount: 0, commentsCount: 1, createdAt: 1000 });
  await setDoc(doc(db, 'posts/p2'), { postId: 'p2', authorUid: 'bob', caption: 'private', audience: 'followers', likesCount: 0, commentsCount: 0, createdAt: 900 });
  await setDoc(doc(db, 'comments/c1'), { commentId: 'c1', postId: 'p1', authorUid: 'alice', text: 'nice', audience: 'public', likesCount: 0, createdAt: 1100 });
  await setDoc(doc(db, 'comments/c2'), { commentId: 'c2', postId: 'p2', authorUid: 'bob', text: 'private thread', audience: 'followers', likesCount: 0, createdAt: 950 });
  // Four posts for the pagination check.
  for (let i = 0; i < 4; i += 1) {
    await setDoc(doc(db, `posts/page${i}`), { postId: `page${i}`, authorUid: 'carol', caption: `p${i}`, audience: 'public', likesCount: 0, commentsCount: 0, createdAt: 2000 + i });
  }
});

const alice = env.authenticatedContext('alice').firestore();
const carol = env.authenticatedContext('carol').firestore();
const zayn = env.authenticatedContext('zayn').firestore();

const comment = (over = {}) => ({
  commentId: 'c9', postId: 'p1', authorUid: 'alice', text: 'hi', audience: 'public', likesCount: 0, createdAt: 1200, ...over,
});

await check('comment on a visible post', 'allow', () =>
  setDoc(doc(alice, 'comments/c9'), comment()));

await check('comment in another user\'s name', 'deny', () =>
  setDoc(doc(zayn, 'comments/c10'), comment({ commentId: 'c10' })));

await check('comment on a private post as a stranger', 'deny', () =>
  setDoc(doc(zayn, 'comments/c11'), comment({ commentId: 'c11', postId: 'p2' })));

await check('read a comment on a public post', 'allow', () => getDoc(doc(zayn, 'comments/c1')));

await check('read a comment on a private post as a stranger', 'deny', () =>
  getDoc(doc(zayn, 'comments/c2')));

await check('author edits own comment', 'allow', () =>
  updateDoc(doc(alice, 'comments/c1'), { text: 'edited' }));

await check('other user edits my comment', 'deny', () =>
  updateDoc(doc(zayn, 'comments/c1'), { text: 'pwned' }));

await check('post owner deletes a comment on their post (moderation)', 'allow', () =>
  deleteDoc(doc(carol, 'comments/c1')));

await check('like a comment', 'allow', async () => {
  await setDoc(doc(alice, 'commentLikes/c9_alice'), { likeId: 'c9_alice', commentId: 'c9', uid: 'alice' });
  await updateDoc(doc(alice, 'comments/c9'), { likesCount: increment(1) });
});

await check('like a comment in another user\'s name', 'deny', () =>
  setDoc(doc(zayn, 'commentLikes/c9_alice'), { likeId: 'c9_alice', commentId: 'c9', uid: 'alice' }));

await check('comment counter moved by two', 'deny', () =>
  updateDoc(doc(alice, 'comments/c9'), { likesCount: increment(2) }));

// ---- P4-03: pagination must not repeat documents -------------------------
await check('second feed page continues after the first', 'allow', async () => {
  // Must mirror the rule + the repository: constrain `audience` or Firestore denies the
  // whole list (rules are not filters).
  const base = (fb) => query(
    collection(fb, 'posts'), where('audience', '==', 'public'),
    orderBy('createdAt', 'desc'));
  const first = await getDocs(query(base(alice), limit(2)));
  const second = await getDocs(query(base(alice),
    startAfter(first.docs[first.docs.length - 1]), limit(2)));

  const firstIds = first.docs.map((d) => d.id);
  const secondIds = second.docs.map((d) => d.id);
  if (firstIds.length !== 2 || secondIds.length === 0) throw new Error(`paging broken: ${firstIds} / ${secondIds}`);
  const overlap = secondIds.filter((id) => firstIds.includes(id));
  if (overlap.length > 0) throw new Error(`page 2 repeated ${overlap}`);
});

await env.cleanup();

console.log(results.join('\n'));
console.log(`\n${results.length - failed.length}/${results.length} comment/pagination tests passed`);
process.exit(failed.length === 0 ? 0 : 1);
