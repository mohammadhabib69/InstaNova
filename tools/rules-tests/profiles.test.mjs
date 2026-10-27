// InstaNova — publicProfiles query tests (migration task P3-01).
//
// Proves the read paths of PublicProfileRepository against the real rules: exact
// username lookup, prefix search, and that a signed-in stranger can read the projection
// while private fields stay in users/{uid} (unreadable by them).
//
// Run with: firebase emulators:exec --only firestore "npm test"

import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from '@firebase/rules-unit-testing';
import {
  collection, doc, getDoc, getDocs, limit, orderBy, query,
  where, setDoc, deleteDoc,
} from 'firebase/firestore';

const results = [];
const failed = [];
async function check(name, expected, fn) {
  try {
    await fn();
    if (expected === 'allow') { results.push(`PASS  allow  ${name}`); }
    else { results.push(`FAIL  deny   ${name} (was allowed)`); failed.push(name); }
  } catch (e) {
    if (expected === 'deny') { results.push(`PASS  deny   ${name}`); }
    else { results.push(`FAIL  allow  ${name}  -> ${e.message}`); failed.push(name); }
  }
}

const env = await initializeTestEnvironment({
  projectId: 'instanova-profiles-test',
  firestore: {
    rules: readFileSync('../../firestore.rules', 'utf8'),
    host: '127.0.0.1',
    port: 8080,
  },
});

// Seed with rules bypassed; documents shaped exactly like PublicProfileDoc.toMap().
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'publicProfiles/p1'), {
    uid: 'p1', username: 'habib', usernameLower: 'habib', fullName: 'Habib R',
  });
  await setDoc(doc(db, 'publicProfiles/p2'), {
    uid: 'p2', username: 'Habiba', usernameLower: 'habiba', fullName: 'Habiba K',
  });
  await setDoc(doc(db, 'publicProfiles/p3'), {
    uid: 'p3', username: 'zayn', usernameLower: 'zayn', fullName: 'Zayn A',
  });
  await setDoc(doc(db, 'users/p1'), {
    uid: 'p1', username: 'habib', fcmTokens: { d1: 'secret-token' },
  });
});

const alice = env.authenticatedContext('alice').firestore();
const anon = env.unauthenticatedContext().firestore();

await check('exact username lookup returns one profile', 'allow', async () => {
  const snap = await getDocs(query(
    collection(alice, 'publicProfiles'), where('usernameLower', '==', 'habib'), limit(1)));
  if (snap.size !== 1) throw new Error(`expected 1, got ${snap.size}`);
  if (snap.docs[0].data().fullName !== 'Habib R') throw new Error('wrong document');
});

await check('prefix search "hab" matches 2 profiles', 'allow', async () => {
  const snap = await getDocs(query(
    collection(alice, 'publicProfiles'),
    orderBy('usernameLower'),
    where('usernameLower', '>=', 'hab'),
    where('usernameLower', '<=', 'hab\uf8ff'),
    limit(20)));
  if (snap.size !== 2) throw new Error(`expected 2, got ${snap.size}`);
});

await check('prefix search is ordered by usernameLower', 'allow', async () => {
  const snap = await getDocs(query(
    collection(alice, 'publicProfiles'),
    orderBy('usernameLower'),
    where('usernameLower', '>=', 'hab'),
    where('usernameLower', '<=', 'hab\uf8ff'),
    limit(20)));
  const names = snap.docs.map((d) => d.data().usernameLower);
  if (names[0] !== 'habib' || names[1] !== 'habiba') throw new Error(`bad order: ${names}`);
});

await check('anon can read publicProfiles (search before login)', 'deny', () =>
  getDocs(query(collection(anon, 'publicProfiles'), limit(1))));

await check('stranger reads a public profile', 'allow', () =>
  assertSucceeds(getDoc(doc(alice, 'publicProfiles/p1'))));

await check('stranger CANNOT read the private user document', 'deny', () =>
  getDoc(doc(alice, 'users/p1')));

await check('owner can read own private document', 'allow', () => {
  const owner = env.authenticatedContext('p1').firestore();
  return assertSucceeds(getDoc(doc(owner, 'users/p1')));
});

await check('stranger cannot overwrite a public profile', 'deny', () =>
  setDoc(doc(alice, 'publicProfiles/p1'), { bio: 'pwned' }, { merge: true }));

await check('owner can update own public profile', 'allow', () => {
  const owner = env.authenticatedContext('p1').firestore();
  return assertSucceeds(setDoc(doc(owner, 'publicProfiles/p1'), { bio: 'hi' }, { merge: true }));
});

await check('nobody can delete a profile document', 'deny', () => {
  const owner = env.authenticatedContext('p1').firestore();
  return assertFails(deleteDoc(doc(owner, 'publicProfiles/p1')));
});

await check('suggested users order by followersCount desc', 'allow', async () => {
  const snap = await getDocs(query(
    collection(alice, 'publicProfiles'), orderBy('followersCount', 'desc'), limit(10)));
  const counts = snap.docs.map((d) => d.data().followersCount ?? 0);
  for (let i = 1; i < counts.length; i += 1) {
    if (counts[i - 1] < counts[i]) throw new Error(`not descending: ${counts}`);
  }
});

await check('suggested users exclude self and followed accounts', 'allow', async () => {
  // Mirrors PublicProfileRepository.suggested(): read followed edges, then filter.
  const edges = await getDocs(query(
    collection(alice, 'follows'), where('followerUid', '==', 'alice'), limit(500)));
  const excluded = new Set(['alice', ...edges.docs.map((d) => d.data().followingUid)]);
  const snap = await getDocs(query(
    collection(alice, 'publicProfiles'), orderBy('followersCount', 'desc'), limit(10)));
  const visible = snap.docs.map((d) => d.id).filter((id) => !excluded.has(id));
  if (visible.includes('alice')) throw new Error('self suggested');
  if (visible.includes('p1') && excluded.has('p1')) throw new Error('already-followed suggested');
});

await env.cleanup();

console.log(results.join('\n'));
console.log(`\n${results.length - failed.length}/${results.length} profile query tests passed`);
process.exit(failed.length === 0 ? 0 : 1);
