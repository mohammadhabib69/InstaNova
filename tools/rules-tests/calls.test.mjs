// InstaNova — call signalling rules (Phase 8).
//
// What must hold: only the two participants can see a call, participants cannot be
// reassigned mid-ring, state values are constrained, and a WebRTC signal can only be
// written by its own side.

import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { ref, set, get, update } from 'firebase/database';

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
  projectId: 'instanova-calls-test',
  database: { rules: readFileSync('../../database.rules.json', 'utf8'), host: '127.0.0.1', port: 9000 },
});

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.database();
  await set(ref(db, 'calls/c1'), { state: 'ringing', callerUid: 'alice', calleeUid: 'bob', createdAt: 1000, type: 'audio' });
});

const alice = env.authenticatedContext('alice').database();
const bob = env.authenticatedContext('bob').database();
const zayn = env.authenticatedContext('zayn').database();

const call = (over = {}) => ({ state: 'ringing', callerUid: 'alice', calleeUid: 'bob', createdAt: 2000, type: 'audio', ...over });

await check('caller creates an outgoing call', 'allow', () =>
  set(ref(alice, 'calls/c2'), call()));

await check('create a call claiming someone else as caller', 'deny', () =>
  set(ref(zayn, 'calls/c3'), call({ callerUid: 'alice', calleeUid: 'bob' })));

await check('call without created timestamp', 'deny', () =>
  set(ref(alice, 'calls/c4'), { state: 'ringing', callerUid: 'alice', calleeUid: 'bob' }));

await check('call in an unknown state', 'deny', () =>
  set(ref(alice, 'calls/c5'), call({ state: 'maybe' })));

await check('callee reads the incoming call', 'allow', () => get(ref(bob, 'calls/c1')));

await check('caller reads their own call', 'allow', () => get(ref(alice, 'calls/c1')));

await check('stranger reads a call', 'deny', () => get(ref(zayn, 'calls/c1')));

await check('callee accepts the call', 'allow', () => update(ref(bob, 'calls/c1'), { state: 'accepted' }));

await check('stranger changes call state', 'deny', () => update(ref(zayn, 'calls/c1'), { state: 'ended' }));

await check('callee cannot hand the call to a third party', 'deny', () =>
  update(ref(bob, 'calls/c1'), { calleeUid: 'zayn' }));

await check('caller writes their own WebRTC signal', 'allow', () =>
  set(ref(alice, 'callSignals/c1/alice/offer'), { type: 'offer', payload: '{"sdp":"v=0"}', createdAt: 1200 }));

await check('caller writes the callee\'s signal slot', 'deny', () =>
  set(ref(alice, 'callSignals/c1/bob/answer'), { type: 'answer', payload: '{}', createdAt: 1200 }));

await check('signal without payload', 'deny', () =>
  set(ref(bob, 'callSignals/c1/bob/answer'), { type: 'answer', createdAt: 1200 }));

await check('caller ends the call', 'allow', () => update(ref(alice, 'calls/c1'), { state: 'ended', endedAt: 1400 }));

await env.cleanup();

console.log(results.join('\n'));
console.log(`\n${results.length - failed.length}/${results.length} call signalling tests passed`);
process.exit(failed.length === 0 ? 0 : 1);
