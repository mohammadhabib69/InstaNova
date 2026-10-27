// InstaNova — Realtime Database rules (Phase 6: chat, presence, typing).
//
// First RTDB coverage in the repo. Proves that membership is required to read a thread,
// that a sender cannot be spoofed, and that presence/typing are self-write only.

import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { ref, set, get, serverTimestamp } from 'firebase/database';

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
  projectId: 'instanova-chat-test',
  database: {
    rules: readFileSync('../../database.rules.json', 'utf8'),
    host: '127.0.0.1',
    port: 9000,
  },
});

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.database();
  await set(ref(db, 'chats/c1/members'), { alice: true, bob: true });
  await set(ref(db, 'chats/c1/meta'), { createdAt: 1000, title: 'dm' });
  await set(ref(db, 'chats/c1/messages/m1'), { senderUid: 'alice', text: 'hi', type: 'text', createdAt: 1100 });
});

const alice = env.authenticatedContext('alice').database();
const bob = env.authenticatedContext('bob').database();
const zayn = env.authenticatedContext('zayn').database();

const msg = (over = {}) => ({ senderUid: 'alice', text: 'hello', type: 'text', createdAt: 1200, ...over });

await check('member reads the thread', 'allow', () => get(ref(alice, 'chats/c1/messages')));
await check('non-member reads the thread', 'deny', () => get(ref(zayn, 'chats/c1/messages')));

await check('member sends a message as themselves', 'allow', () =>
  set(ref(alice, 'chats/c1/messages/m2'), msg()));

await check('member sends a message as someone else', 'deny', () =>
  set(ref(alice, 'chats/c1/messages/m3'), msg({ senderUid: 'bob' })));

await check('message missing required fields', 'deny', () =>
  set(ref(alice, 'chats/c1/messages/m4'), { senderUid: 'alice', text: 'no type', createdAt: 1 }));

await check('message text over 4000 characters', 'deny', () =>
  set(ref(alice, 'chats/c1/messages/m5'), msg({ text: 'x'.repeat(4001) })));

await check('non-member writes into the thread', 'deny', () =>
  set(ref(zayn, 'chats/c1/messages/m6'), { senderUid: 'zayn', text: 'hi', type: 'text', createdAt: 1 }));

await check('user writes their own presence', 'allow', () =>
  set(ref(alice, 'presence/alice'), { state: 'online', lastChanged: 1200 }));

await check('user writes someone else\'s presence', 'deny', () =>
  set(ref(alice, 'presence/bob'), { state: 'online', lastChanged: 1200 }));

await check('presence without required fields', 'deny', () =>
  set(ref(alice, 'presence/alice'), { state: 'online' }));

await check('member sets their own typing flag', 'allow', () =>
  set(ref(alice, 'typing/c1/alice'), true));

await check('member sets another user\'s typing flag', 'deny', () =>
  set(ref(alice, 'typing/c1/bob'), true));

await check('non-member sets a typing flag', 'deny', () =>
  set(ref(zayn, 'typing/c1/zayn'), true));

// ---- P6-05 / P6-07: read markers and reactions ------------------------
await check('member sets their own read marker', 'allow', () =>
  set(ref(alice, 'chats/c1/reads/alice'), { lastReadAt: 1200 }));

await check('member sets another member\'s read marker', 'deny', () =>
  set(ref(alice, 'chats/c1/reads/bob'), { lastReadAt: 1200 }));

await check('read marker without a numeric timestamp', 'deny', () =>
  set(ref(alice, 'chats/c1/reads/alice'), { lastReadAt: 'now' }));

await check('non-member reads read markers', 'deny', () => get(ref(zayn, 'chats/c1/reads')));

await check('member reacts to a message', 'allow', () =>
  set(ref(alice, 'chats/c1/reactions/m1/alice'), '❤️'));

await check('member reacts on behalf of another user', 'deny', () =>
  set(ref(alice, 'chats/c1/reactions/m1/bob'), '🔥'));

await check('non-member reacts', 'deny', () =>
  set(ref(zayn, 'chats/c1/reactions/m1/zayn'), '🔥'));

await check('reaction longer than 8 characters', 'deny', () =>
  set(ref(alice, 'chats/c1/reactions/m1/alice'), 'not-an-emoji'));

await env.cleanup();

console.log(results.join('\n'));
console.log(`\n${results.length - failed.length}/${results.length} chat rules tests passed`);
process.exit(failed.length === 0 ? 0 : 1);
