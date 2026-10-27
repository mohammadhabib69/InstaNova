// InstaNova — NOVA Hub rules (Phase 9): Q&A, votes, confessions.
//
// The confession cases are the point of this file: anonymity must be a property the server
// enforces, not a convention the client follows.

import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection, doc, getDoc, getDocs, limit, orderBy, query, setDoc, updateDoc, where, increment,
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
  projectId: 'instanova-nova-test',
  firestore: { rules: readFileSync('../../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
});

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'novaUniversities/du'), { name: 'Dhaka University', shortName: 'DU', emailDomains: ['du.ac.bd'] });
  await setDoc(doc(db, 'novaQuestions/q1'), {
    questionId: 'q1', authorUid: 'alice', universityId: 'du', title: 'CSE 101?', body: 'help',
    audience: 'public', solved: false, votes: 0, answersCount: 0, createdAt: 1000,
  });
  await setDoc(doc(db, 'novaAnswers/a1'), {
    answerId: 'a1', questionId: 'q1', authorUid: 'bob', body: 'chapter 2', audience: 'public',
    accepted: false, votes: 0, createdAt: 1100,
  });
  await setDoc(doc(db, 'novaConfessions/c1'), {
    confessionId: 'c1', universityId: 'du', body: 'I study at 3am', hidden: false, likesCount: 0, createdAt: 1200,
  });
});

const alice = env.authenticatedContext('alice').firestore();
const bob = env.authenticatedContext('bob').firestore();
const zayn = env.authenticatedContext('zayn').firestore();

const question = (over = {}) => ({
  questionId: 'q9', authorUid: 'alice', universityId: 'du', title: 't', body: 'b',
  audience: 'public', solved: false, votes: 0, answersCount: 0, createdAt: 1300, ...over,
});

await check('ask a question', 'allow', () => setDoc(doc(alice, 'novaQuestions/q9'), question()));
await check('ask in another user\'s name', 'deny', () =>
  setDoc(doc(zayn, 'novaQuestions/q10'), question({ questionId: 'q10' })));
await check('ask with pre-seeded votes', 'deny', () =>
  setDoc(doc(alice, 'novaQuestions/q11'), question({ questionId: 'q11', votes: 5 })));
await check('stranger reads a public question', 'allow', () => getDoc(doc(zayn, 'novaQuestions/q1')));

await check('cast a vote', 'allow', async () => {
  await setDoc(doc(bob, 'novaVotes/question_q1_bob'), { voteId: 'question_q1_bob', targetType: 'question', targetId: 'q1', uid: 'bob' });
  await updateDoc(doc(bob, 'novaQuestions/q1'), { votes: increment(1) });
});

await check('cast a vote in another user\'s name', 'deny', () =>
  setDoc(doc(zayn, 'novaVotes/question_q1_bob'), { voteId: 'question_q1_bob', targetType: 'question', targetId: 'q1', uid: 'bob' }));

await check('vote counter moved by two', 'deny', () =>
  updateDoc(doc(bob, 'novaQuestions/q1'), { votes: increment(2) }));

await check('answer the question', 'allow', () =>
  setDoc(doc(bob, 'novaAnswers/a9'), {
    answerId: 'a9', questionId: 'q1', authorUid: 'bob', body: 'x', audience: 'public', accepted: false, votes: 0, createdAt: 1400,
  }));

await check('question author marks an answer accepted', 'allow', () =>
  updateDoc(doc(alice, 'novaAnswers/a1'), { accepted: true }));

await check('answer author marks their own answer accepted', 'deny', () =>
  updateDoc(doc(bob, 'novaAnswers/a9'), { accepted: true }));

// ---- confessions: anonymity is server-enforced ---------------------------
await check('post an anonymous confession', 'allow', () =>
  setDoc(doc(alice, 'novaConfessions/c9'), {
    confessionId: 'c9', universityId: 'du', body: 'anonymous', hidden: false, likesCount: 0, createdAt: 1500,
  }));

await check('post a confession carrying an author uid', 'deny', () =>
  setDoc(doc(alice, 'novaConfessions/c10'), {
    confessionId: 'c10', universityId: 'du', body: 'not anonymous', hidden: false, likesCount: 0, authorUid: 'alice', createdAt: 1500,
  }));

await check('post a confession carrying a senderUid', 'deny', () =>
  setDoc(doc(alice, 'novaConfessions/c11'), {
    confessionId: 'c11', universityId: 'du', body: 'x', hidden: false, likesCount: 0, senderUid: 'alice', createdAt: 1500,
  }));

await check('like a confession', 'allow', async () => {
  await setDoc(doc(bob, 'novaConfessionLikes/c1_bob'), { likeId: 'c1_bob', confessionId: 'c1', uid: 'bob' });
  await updateDoc(doc(bob, 'novaConfessions/c1'), { likesCount: increment(1) });
});

await check('deleting a confession from the client', 'deny', () =>
  import('firebase/firestore').then(({ deleteDoc }) => deleteDoc(doc(alice, 'novaConfessions/c9'))));

await check('hidden confessions disappear from the feed', 'allow', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('novaConfessions/c1').update({ hidden: true });
  });
  const snap = await getDocs(query(
    collection(zayn, 'novaConfessions'), where('universityId', '==', 'du'),
    where('hidden', '==', false), orderBy('createdAt', 'desc'), limit(20)));
  if (snap.docs.some((d) => d.id === 'c1')) throw new Error('hidden confession still in feed');
});

await env.cleanup();

console.log(results.join('\n'));
console.log(`\n${results.length - failed.length}/${results.length} NOVA Hub tests passed`);
process.exit(failed.length === 0 ? 0 : 1);
