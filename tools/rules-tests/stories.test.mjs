// InstaNova — stories, story views, highlights (Phase 5).
//
// The two things that must hold: an expired story cannot be created, and a non-public
// story is unreadable by anyone but its author until Functions can check the follow edge.

import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection, doc, getDoc, getDocs, limit, orderBy, query, setDoc, where, deleteDoc, updateDoc,
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
  projectId: 'instanova-stories-test',
  firestore: { rules: readFileSync('../../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
});

const HOUR = 3600_000;
const now = Date.now();

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'publicProfiles/alice'), { uid: 'alice', username: 'alice', usernameLower: 'alice' });
  await setDoc(doc(db, 'publicProfiles/bob'), { uid: 'bob', username: 'bob', usernameLower: 'bob' });
  await setDoc(doc(db, 'stories/s_public'), { storyId: 's_public', authorUid: 'alice', audience: 'public', createdAt: now, expiresAt: now + 20 * HOUR });
  await setDoc(doc(db, 'stories/s_friends'), { storyId: 's_friends', authorUid: 'alice', audience: 'followers', createdAt: now, expiresAt: now + 20 * HOUR });
  await setDoc(doc(db, 'stories/s_old'), { storyId: 's_old', authorUid: 'alice', audience: 'public', createdAt: now - 30 * HOUR, expiresAt: now - 6 * HOUR });
  await setDoc(doc(db, 'storyViews/s_public_bob'), { viewId: 's_public_bob', storyId: 's_public', viewerUid: 'bob' });
});

const alice = env.authenticatedContext('alice').firestore();
const bob = env.authenticatedContext('bob').firestore();
const zayn = env.authenticatedContext('zayn').firestore();

const story = (over = {}) => ({
  storyId: 's9', authorUid: 'alice', audience: 'public',
  createdAt: now, expiresAt: now + 24 * HOUR, ...over,
});

await check('author creates a story expiring in the future', 'allow', () =>
  setDoc(doc(alice, 'stories/s9'), story()));

await check('create a story with an expiry in the past', 'deny', () =>
  setDoc(doc(alice, 'stories/s10'), story({ storyId: 's10', expiresAt: now - 1000 })));

await check('create a story in another user\'s name', 'deny', () =>
  setDoc(doc(zayn, 'stories/s11'), story({ storyId: 's11' })));

await check('unknown audience rejected', 'deny', () =>
  setDoc(doc(alice, 'stories/s12'), story({ storyId: 's12', audience: 'everyone' })));

await check('stranger reads a public story', 'allow', () => getDoc(doc(zayn, 'stories/s_public')));

await check('stranger reads a followers-only story', 'deny', () =>
  getDoc(doc(zayn, 'stories/s_friends')));

await check('author reads own followers-only story', 'allow', () =>
  getDoc(doc(alice, 'stories/s_friends')));

await check('active-story feed excludes expired documents', 'allow', async () => {
  const snap = await getDocs(query(
    collection(zayn, 'stories'),
    where('audience', '==', 'public'),
    where('expiresAt', '>', Date.now()),
    orderBy('expiresAt'), limit(50)));
  const ids = snap.docs.map((d) => d.id);
  if (ids.includes('s_old')) throw new Error(`expired story leaked: ${ids}`);
});

await check('viewer records their own view', 'allow', () =>
  setDoc(doc(bob, 'storyViews/s9_bob'), { viewId: 's9_bob', storyId: 's9', viewerUid: 'bob' }));

await check('record a view in another viewer\'s name', 'deny', () =>
  setDoc(doc(zayn, 'storyViews/s9_bob'), { viewId: 's9_bob', storyId: 's9', viewerUid: 'bob' }));

await check('author reads the viewer list', 'allow', () =>
  getDocs(query(collection(alice, 'storyViews'), where('storyId', '==', 's_public'), limit(10))));

await check('stranger reads someone else\'s viewer list', 'deny', () =>
  getDocs(query(collection(zayn, 'storyViews'), where('storyId', '==', 's_public'), limit(10))));

await check('author deletes own story', 'allow', () => deleteDoc(doc(alice, 'stories/s9')));

await check('stranger cannot delete a story', 'deny', () => deleteDoc(doc(zayn, 'stories/s_public')));

// P5-04 replacement: the scheduled Cloud Function is Blaze-blocked, so expired stories are
// swept by any signed-in client. The second branch only ever matches stories whose
// expiresAt is already in the past, i.e. content no reader can see.
await check('any signed-in user may sweep an EXPIRED story', 'allow', () =>
  deleteDoc(doc(zayn, 'stories/s_old')));

await check('owner creates a highlight', 'allow', () =>
  setDoc(doc(alice, 'highlights/h1'), { highlightId: 'h1', ownerUid: 'alice', title: 'Trip', audience: 'public', items: [] }));

await check('highlight with more than 50 items', 'deny', () =>
  setDoc(doc(alice, 'highlights/h2'), {
    highlightId: 'h2', ownerUid: 'alice', title: 'Big', audience: 'public',
    items: Array.from({ length: 51 }, (_, i) => ({ mediaId: `m${i}`, bucket: 'stories', path: `alice/${i}.jpg` })),
  }));

await check('someone else edits my highlight', 'deny', () =>
  updateDoc(doc(zayn, 'highlights/h1'), { title: 'pwned' }));

await env.cleanup();

console.log(results.join('\n'));
console.log(`\n${results.length - failed.length}/${results.length} story/highlight tests passed`);
process.exit(failed.length === 0 ? 0 : 1);
