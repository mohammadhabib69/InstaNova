// InstaNova — social graph rules (tasks P3-06, P3-07, P3-08, P3-09).
//
// Proves the parts that are easy to get wrong: private accounts must go through a
// request instead of an edge, only the counterparty may touch its own counter, and a
// block can only be created/removed by the blocker.

import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  doc, getDoc, setDoc, deleteDoc, updateDoc, increment,
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
    else { results.push(`FAIL  allow  ${name}  -> ${e.message.split('\n')[0]}`); failed.push(name); }
  }
}

const env = await initializeTestEnvironment({
  projectId: 'instanova-social-test',
  firestore: { rules: readFileSync('../../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
});

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'publicProfiles/alice'), { uid: 'alice', username: 'alice', usernameLower: 'alice', isPrivate: false, followersCount: 0, followingCount: 0 });
  await setDoc(doc(db, 'publicProfiles/bob'), { uid: 'bob', username: 'bob', usernameLower: 'bob', isPrivate: true, followersCount: 0, followingCount: 0 });
  await setDoc(doc(db, 'publicProfiles/carol'), { uid: 'carol', username: 'carol', usernameLower: 'carol', isPrivate: false, followersCount: 3, followingCount: 0, bio: 'orig' });
  await setDoc(doc(db, 'publicProfiles/zayn'), { uid: 'zayn', username: 'zayn', usernameLower: 'zayn', isPrivate: false, followersCount: 0, followingCount: 0 });
  await setDoc(doc(db, 'publicProfiles/dave'), { uid: 'dave', username: 'dave', usernameLower: 'dave', isPrivate: true, followersCount: 0, followingCount: 0 });
  await setDoc(doc(db, 'followRequests/alice_bob'), { requesterUid: 'alice', targetUid: 'bob', status: 'pending' });
  await setDoc(doc(db, 'blocks/alice_zayn'), { blockerUid: 'alice', blockedUid: 'zayn' });
  await setDoc(doc(db, 'blocks/dave_zayn'), { blockerUid: 'dave', blockedUid: 'zayn' });
  await setDoc(doc(db, 'follows/alice_bob'), { edgeId: 'alice_bob', followerUid: 'alice', followingUid: 'bob' });
  await setDoc(doc(db, 'mutes/alice_zayn'), { muterUid: 'alice', mutedUid: 'zayn', kind: 'mute' });
});

const alice = env.authenticatedContext('alice').firestore();
const bob = env.authenticatedContext('bob').firestore();
const zayn = env.authenticatedContext('zayn').firestore();

const edge = (a, b) => doc(alice, `follows/${a}_${b}`);
const edgePayload = (a, b) => ({ edgeId: `${a}_${b}`, followerUid: a, followingUid: b });

await check('follow a public account', 'allow', () =>
  setDoc(edge('alice', 'carol'), edgePayload('alice', 'carol')));

await check('create an edge under someone else\'s uid', 'deny', () =>
  setDoc(edge('carol', 'alice'), edgePayload('carol', 'alice')));

await check('edge id must match the document id', 'deny', () =>
  setDoc(doc(alice, 'follows/random'), { edgeId: 'alice_carol', followerUid: 'alice', followingUid: 'carol' }));

await check('follow a PRIVATE account directly (must request instead)', 'deny', () =>
  setDoc(edge('alice', 'bob'), edgePayload('alice', 'bob')));

await check('participant reads own edge', 'allow', () => getDoc(edge('alice', 'carol')));

await check('stranger reads an edge to a public account', 'allow', () => getDoc(edge('alice', 'carol')));

await check('stranger reads an edge to a private account', 'deny', () =>
  setDoc(edge('zayn', 'bob'), edgePayload('zayn', 'bob')).then(() => getDoc(edge('zayn', 'bob'))));

await check('other user bumps my followersCount by 1 (follow)', 'allow', () =>
  updateDoc(doc(alice, 'publicProfiles/carol'), { followersCount: increment(1) }));

await check('other user bumps followersCount by 5', 'deny', () =>
  updateDoc(doc(alice, 'publicProfiles/carol'), { followersCount: increment(5) }));

await check('other user edits someone else\'s bio', 'deny', () =>
  updateDoc(doc(alice, 'publicProfiles/carol'), { bio: 'pwned' }));

await check('target accepts a follow request', 'allow', () =>
  updateDoc(doc(bob, 'followRequests/alice_bob'), { status: 'accepted' }));

await check('requester accepts their own request', 'deny', () =>
  updateDoc(doc(alice, 'followRequests/alice_bob'), { status: 'accepted' }));

await check('blocker creates a block', 'allow', () =>
  setDoc(doc(alice, 'blocks/alice_carol'), { blockerUid: 'alice', blockedUid: 'carol' }));

await check('non-blocker creates a block in my name', 'deny', () =>
  setDoc(doc(zayn, 'blocks/alice_bob'), { blockerUid: 'alice', blockedUid: 'bob' }));

await check('blocked user removes the block', 'deny', () => deleteDoc(doc(zayn, 'blocks/alice_zayn')));

await check('blocker removes the block', 'allow', () => deleteDoc(doc(alice, 'blocks/alice_zayn')));

await check('third party reads my mute document', 'deny', () => getDoc(doc(zayn, 'mutes/alice_zayn')));

await check('muter reads own mute document', 'allow', () => getDoc(doc(alice, 'mutes/alice_zayn')));

// "Remove follower" is the TARGET deleting the edge — not any bystander.
await check('third party deletes an unrelated follow edge', 'deny', () =>
  deleteDoc(doc(zayn, 'follows/alice_carol')));

await check('target removes a follower (own edge deletion)', 'allow', () =>
  deleteDoc(doc(env.authenticatedContext('carol').firestore(), 'follows/alice_carol')));

// ---- P3-13: private-account matrix (edge pre-seeded by an accepted request) ----
await check('follower reads an edge into a private account', 'allow', () =>
  getDoc(doc(alice, 'follows/alice_bob')));

await check('private target reads the same edge', 'allow', () =>
  getDoc(doc(bob, 'follows/alice_bob')));

await check('stranger reads an edge into a private account', 'deny', () =>
  getDoc(doc(zayn, 'follows/alice_bob')));

// A request is a push notification in waiting, so the rules validate the target itself.
await check('request to a PRIVATE account is allowed', 'allow', () =>
  setDoc(doc(alice, 'followRequests/alice_bob_req'), { requesterUid: 'alice', targetUid: 'bob', status: 'pending' }));

await check('request to a PUBLIC account is rejected', 'deny', () =>
  setDoc(doc(alice, 'followRequests/alice_carol'), { requesterUid: 'alice', targetUid: 'carol', status: 'pending' }));

await check('request to a uid that does not exist is rejected', 'deny', () =>
  setDoc(doc(alice, 'followRequests/alice_ghost'), { requesterUid: 'alice', targetUid: 'ghost', status: 'pending' }));

await check('request to an account that blocked you is rejected', 'deny', () =>
  setDoc(doc(zayn, 'followRequests/zayn_dave'), { requesterUid: 'zayn', targetUid: 'dave', status: 'pending' }));

await check('requester cannot submit a request in another user\'s name', 'deny', () =>
  setDoc(doc(env.authenticatedContext('zayn').firestore(), 'followRequests/carol_bob'),
    { requesterUid: 'carol', targetUid: 'bob', status: 'pending' }));

await env.cleanup();

console.log(results.join('\n'));
console.log(`\n${results.length - failed.length}/${results.length} social graph tests passed`);
process.exit(failed.length === 0 ? 0 : 1);
