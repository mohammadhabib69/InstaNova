// InstaNova — NOVA blood / SOS / community rules (Phase 9).
//
// These documents hold phone numbers and live locations. The tests assert the negative
// cases first, because a leak here is not a cosmetic bug.

import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, increment } from 'firebase/firestore';

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
  projectId: 'instanova-nova-safety-test',
  firestore: { rules: readFileSync('../../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
});

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'novaDonors/alice'), { uid: 'alice', bloodGroup: 'B+', phone: '+8801700000000' });
  await setDoc(doc(db, 'novaDonorPublic/alice'), { uid: 'alice', bloodGroup: 'B+', district: 'dhaka', available: true });
  await setDoc(doc(db, 'novaSosAlerts/a1'), {
    alertId: 'a1', authorUid: 'alice', audience: 'campus', lat: 23.8, lng: 90.4, contactPhone: '+8801700000000', createdAt: 1000,
  });
  await setDoc(doc(db, 'novaEvents/e1'), { eventId: 'e1', title: 'Hackathon', startsAt: 2000 });
});

const alice = env.authenticatedContext('alice').firestore();
const bob = env.authenticatedContext('bob').firestore();
const zayn = env.authenticatedContext('zayn').firestore();

// ---- donor contact data ---------------------------------------------------
await check('donor reads own contact document', 'allow', () => getDoc(doc(alice, 'novaDonors/alice')));

await check('stranger reads a donor\'s phone number', 'deny', () => getDoc(doc(zayn, 'novaDonors/alice')));

await check('stranger reads the public donor projection', 'allow', () => getDoc(doc(zayn, 'novaDonorPublic/alice')));

await check('publish a donor projection carrying a phone number', 'deny', () =>
  setDoc(doc(bob, 'novaDonorPublic/bob'), { uid: 'bob', bloodGroup: 'O+', district: 'dhaka', phone: '+8801711111111' }));

await check('publicate a donor projection without contact data', 'allow', () =>
  setDoc(doc(bob, 'novaDonorPublic/bob'), { uid: 'bob', bloodGroup: 'O+', district: 'dhaka', available: true }));

// ---- blood requests -------------------------------------------------------
await check('post a blood request', 'allow', () =>
  setDoc(doc(alice, 'novaBloodRequests/r1'), {
    requestId: 'r1', requesterUid: 'alice', bloodGroup: 'B+', district: 'dhaka', contactPhone: '+8801700000000', pledgesCount: 0,
  }));

await check('post a blood request in another user\'s name', 'deny', () =>
  setDoc(doc(zayn, 'novaBloodRequests/r2'), { requestId: 'r2', requesterUid: 'alice', bloodGroup: 'B+', pledgesCount: 0 }));

await check('any signed-in user can read a blood request (that is the point)', 'allow', () =>
  getDoc(doc(zayn, 'novaBloodRequests/r1')));

await check('stranger bumps a request\'s pledge count', 'allow', () =>
  updateDoc(doc(bob, 'novaBloodRequests/r1'), { pledgesCount: increment(1) }));

// ---- SOS alerts -----------------------------------------------------------
await check('author reads their own SOS alert', 'allow', () => getDoc(doc(alice, 'novaSosAlerts/a1')));

await check('stranger reads an SOS alert', 'deny', () => getDoc(doc(zayn, 'novaSosAlerts/a1')));

await check('a responder registers on the alert', 'allow', () =>
  setDoc(doc(bob, 'novaAlertResponses/a1_bob'), { responseId: 'a1_bob', alertId: 'a1', responderUid: 'bob' }));

await check('responder can then read the alert they are helping with', 'allow', () => getDoc(doc(bob, 'novaSosAlerts/a1')));

await check('respond on behalf of another user', 'deny', () =>
  setDoc(doc(zayn, 'novaAlertResponses/a1_bob'), { responseId: 'a1_bob', alertId: 'a1', responderUid: 'bob' }));

await check('stranger still cannot read the alert', 'deny', () => getDoc(doc(zayn, 'novaSosAlerts/a1')));

// ---- events / rsvp / study ------------------------------------------------
await check('anyone signed in reads an event', 'allow', () => getDoc(doc(zayn, 'novaEvents/e1')));

await check('client creates an event', 'deny', () =>
  setDoc(doc(alice, 'novaEvents/e2'), { eventId: 'e2', title: 'Fake' }));

await check('rsvp to an event', 'allow', () =>
  setDoc(doc(bob, 'novaEventRsvps/e1_bob'), { rsvpId: 'e1_bob', eventId: 'e1', uid: 'bob' }));

await check('read someone else\'s rsvp', 'deny', () => getDoc(doc(zayn, 'novaEventRsvps/e1_bob')));

await check('stranger creates a study session in my name', 'deny', () =>
  setDoc(doc(zayn, 'novaStudySessions/s1'), { sessionId: 's1', uid: 'alice', minutes: 30 }));

await check('own study session', 'allow', () =>
  setDoc(doc(alice, 'novaStudySessions/s2'), { sessionId: 's2', uid: 'alice', minutes: 45 }));

await check('delete a lost&found item that is not mine', 'deny', () =>
  setDoc(doc(alice, 'novaLostFound/l1'), { itemId: 'l1', authorUid: 'alice', title: 'wallet' }).then(() =>
    deleteDoc(doc(zayn, 'novaLostFound/l1'))));

// ---- campus membership / announcements / insights --------------------
await check('read the campus member directory', 'allow', () =>
  env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('novaCampusMembers/alice').set({ uid: 'alice', universityId: 'du', verified: true });
  }).then(() => getDoc(doc(zayn, 'novaCampusMembers/alice'))));

await check('self-declare as a verified campus member', 'deny', () =>
  setDoc(doc(bob, 'novaCampusMembers/bob'), { uid: 'bob', universityId: 'du', verified: true }));

await check('client publishes a campus announcement', 'deny', () =>
  setDoc(doc(alice, 'novaAlerts/x1'), { alertId: 'x1', title: 'fake alert' }));

await check('read a campus announcement', 'allow', () =>
  env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('novaAlerts/x2').set({ alertId: 'x2', title: 'real alert' });
  }).then(() => getDoc(doc(zayn, 'novaAlerts/x2'))));

await check('client writes an insight aggregate', 'deny', () =>
  setDoc(doc(alice, 'novaInsights/i1'), { insightId: 'i1', total: 999 }));

await env.cleanup();

console.log(results.join('\n'));
console.log(`\n${results.length - failed.length}/${results.length} NOVA safety/community tests passed`);
process.exit(failed.length === 0 ? 0 : 1);
