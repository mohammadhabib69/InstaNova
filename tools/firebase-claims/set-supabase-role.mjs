#!/usr/bin/env node
/**
 * One-off: give existing Firebase users the Supabase `role: "authenticated"` claim.
 *
 * WHY THIS EXISTS
 * ---------------
 * Supabase Third-Party Auth maps a Firebase ID token onto a Postgres role by reading
 * the token's `role` claim. Firebase never emits that claim, so every request runs as
 * `anon` and all `to authenticated` RLS policies are skipped. Setting a Firebase custom
 * claim fixes the token itself; nothing in Supabase needs to change and RLS stays on.
 *
 * WHAT IT DOES
 * ------------
 * Walks every user page by page, merges { role: "authenticated" } into the user's
 * EXISTING custom claims (it never replaces them), and reports a summary.
 *
 * WHAT IT DOES NOT DO
 * -------------------
 * - It does not create, delete or disable users.
 * - It does not touch passwords, email addresses or providers.
 * - It does not cover users who sign up AFTER this runs. Re-run it for new accounts.
 * - It never touches Supabase (no SQL, no RLS, no Storage).
 *
 * AUTHENTICATION MODES (exactly one is required)
 * ----------------------------------------------
 *   --adc            Application Default Credentials (gcloud auth application-default login)
 *   --key <path>     service-account JSON, read at runtime and never embedded
 *
 * DRY RUN IS THE DEFAULT
 * ----------------------
 * Running without --apply only lists what would change. Nothing is written.
 *
 * USAGE
 * -----
 *   cd tools/firebase-claims
 *   npm install
 *
 *   # ADC, dry run (default) — lists affected UIDs, modifies nothing
 *   node set-supabase-role.mjs --adc
 *
 *   # ADC, apply — writes the claim (asks you to type "apply")
 *   node set-supabase-role.mjs --adc --apply
 *
 *   # service-account key, same two phases
 *   node set-supabase-role.mjs --key ~/secrets/instanova-sa.json
 *   node set-supabase-role.mjs --key ~/secrets/instanova-sa.json --apply
 *
 * Options:
 *   --adc | --key <path>  exactly one authentication mode
 *   --apply               actually write claims (default is dry run)
 *   --dry-run             force dry run even if --apply is also present
 *   --project <id>        must equal instanova-62ff0; any other value is refused
 *   --uid <uid>           limit to a single user
 *   --page-size <n>       users per page (default 1000, max 1000)
 *   --yes                 skip the interactive confirmation (for automation)
 *
 * The --key mode refuses a key file stored inside this repository or one that is
 * group/world readable. ADC needs no key file at all.
 *
 * IAM: the caller (ADC user or service account) needs permission to read and update
 * Firebase users, e.g. roles/firebase.admin (or the Firebase Admin SDK Administrator
 * Service Agent role on a service account).
 */

import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline/promises';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const EXPECTED_PROJECT = 'instanova-62ff0';
const CLAIM_ROLE = 'authenticated';
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

function parseArgs(argv) {
  const args = { apply: false, dryRunFlag: false, yes: false, pageSize: 1000 };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--apply') args.apply = true;
    else if (a === '--dry-run') args.dryRunFlag = true;
    else if (a === '--adc') args.adc = true;
    else if (a === '--yes') args.yes = true;
    else if (a === '--key') args.key = argv[++i];
    else if (a === '--project') args.project = argv[++i];
    else if (a === '--uid') args.uid = argv[++i];
    else if (a === '--page-size') args.pageSize = Math.min(Number(argv[++i]) || 1000, 1000);
    else if (a === '--help' || a === '-h') args.help = true;
    else throw new Error(`Unknown argument: ${a}`);
  }
  return args;
}

function usage() {
  const header = fs.readFileSync(fileURLToPath(import.meta.url), 'utf8').split('*/')[0];
  console.log(header.replace(/^#!.*\n/, '').replace(/^\/\*\*?/, '').replace(/^ \* ?/gm, ''));
}

/** --key must never point into the repo, and must not be readable by others. */
function assertKeyIsSafe(keyPath) {
  const abs = path.resolve(keyPath.replace(/^~(?=$|\/)/, process.env.HOME || ''));
  if (!fs.existsSync(abs)) throw new Error(`Key file not found: ${abs}`);

  const real = fs.realpathSync(abs);
  if (real.startsWith(REPO_ROOT + path.sep)) {
    throw new Error('Refusing to use a service-account key stored inside the project. '
      + 'Move it outside the repository (e.g. ~/secrets/) and try again.');
  }
  const mode = fs.statSync(real).mode & 0o777;
  if (mode & 0o077) {
    throw new Error(`Key file permissions are too open (0${mode.toString(8)}). `
      + `Run: chmod 600 "${real}"`);
  }
  return real;
}

function parseServiceAccount(realPath) {
  let json;
  try {
    json = JSON.parse(fs.readFileSync(realPath, 'utf8'));
  } catch {
    throw new Error('Key file is not valid JSON.');
  }
  if (!json.project_id || !json.client_email || !json.private_key) {
    throw new Error('Key file is not a Firebase service-account JSON (missing project_id/client_email/private_key).');
  }
  return json;
}

/** Builds the Admin SDK app for the selected credential mode. */
async function initAdmin(admin, args) {
  const options = {
    credential: args.adc
      ? admin.credential.applicationDefault()
      : admin.credential.cert(args.serviceAccount),
    projectId: EXPECTED_PROJECT,
  };
  const app = admin.initializeApp(options, `claims-${Date.now()}`);

  // Never trust an implicit project: check what the SDK will actually talk to.
  const resolved = app.options.projectId;
  if (resolved !== EXPECTED_PROJECT) {
    await app.delete().catch(() => {});
    throw new Error(`Admin SDK resolved project "${resolved}", expected "${EXPECTED_PROJECT}". Refusing to continue.`);
  }
  return { app, auth: admin.auth(app) };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) return usage();

  // ---- exactly one credential mode ----
  const modes = [Boolean(args.adc), Boolean(args.key)];
  if (modes.filter(Boolean).length !== 1) {
    usage();
    throw new Error('Choose exactly one authentication mode: --adc or --key <path>.');
  }

  // ---- project validation ----
  const projectId = args.project || EXPECTED_PROJECT;
  if (projectId !== EXPECTED_PROJECT) {
    throw new Error(`Refusing to operate on project "${projectId}". This tool only targets "${EXPECTED_PROJECT}".`);
  }

  // Dry run is the default; --dry-run always wins over --apply.
  const dryRun = !args.apply || args.dryRunFlag;

  if (args.key) {
    const realKeyPath = assertKeyIsSafe(args.key);
    args.serviceAccount = parseServiceAccount(realKeyPath);
    if (args.serviceAccount.project_id !== EXPECTED_PROJECT) {
      throw new Error(`Service account belongs to project "${args.serviceAccount.project_id}", expected "${EXPECTED_PROJECT}".`);
    }
  }

  // Imported lazily so --help works before `npm install`.
  const admin = (await import('firebase-admin')).default;
  const { app, auth } = await initAdmin(admin, args);

  console.log(`project        : ${projectId}`);
  console.log(`credential     : ${args.adc
    ? 'Application Default Credentials (no key file)'
    : `service account ${args.serviceAccount.client_email.split('@')[0]}@… (redacted)`}`);
  console.log(`mode           : ${dryRun ? 'DRY RUN — no accounts will be modified' : 'APPLY'}`);
  console.log(`claim          : {"role": "${CLAIM_ROLE}"} (merged into existing claims)\n`);

  if (!dryRun && !args.yes) {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    const answer = await rl.question('This writes custom claims to Firebase users. Type "apply" to continue: ');
    rl.close();
    if (answer.trim().toLowerCase() !== 'apply') {
      console.log('Aborted. Nothing was changed.');
      await app.delete().catch(() => {});
      return;
    }
  }

  let pageToken;
  let scanned = 0;
  let updated = 0;
  let alreadyOk = 0;
  const failures = [];

  try {
    do {
      const page = await auth.listUsers(args.pageSize, pageToken);
      const users = args.uid ? page.users.filter((u) => u.uid === args.uid) : page.users;

      for (const user of users) {
        scanned++;
        const existing = user.customClaims || {};
        if (existing.role === CLAIM_ROLE) {
          alreadyOk++;
          continue;
        }
        const merged = { ...existing, role: CLAIM_ROLE };

        if (dryRun) {
          console.log(`would update  ${user.uid}  (existing claims: ${Object.keys(existing).join(', ') || 'none'})`);
          updated++;
          continue;
        }

        try {
          await auth.setCustomUserClaims(user.uid, merged);
          console.log(`updated       ${user.uid}`);
          updated++;
        } catch (err) {
          // Never echo credentials; only the message and the uid.
          const message = err && err.message ? err.message : 'unknown error';
          console.error(`FAILED        ${user.uid}  ${message}`);
          failures.push({ uid: user.uid, message });
        }
      }
      pageToken = page.pageToken;
    } while (pageToken);
  } finally {
    await app.delete().catch(() => {});
  }

  console.log('\n--- summary ---');
  console.log(`scanned       : ${scanned}`);
  console.log(dryRun ? `would update  : ${updated}` : `updated       : ${updated}`);
  console.log(`already had it: ${alreadyOk}`);
  console.log(`failed        : ${failures.length}`);
  if (failures.length) {
    console.log('\nRe-run for the failed UIDs (the script is idempotent):');
    for (const f of failures) console.log(`  --uid ${f.uid}`);
    process.exitCode = 1;
  }
  if (dryRun) {
    console.log('\nDry run only. Run the same command with --apply when the list looks right.');
  } else if (updated > 0) {
    console.log('\nUsers must obtain a fresh ID token before Supabase sees the claim:');
    console.log('  - sign out and back in, or');
    console.log('  - the app calls getIdToken(true) on the next login (already implemented),');
    console.log('  - otherwise the old token stays valid until it expires (up to 60 minutes).');
  }
}

main().catch((err) => {
  console.error(`\nERROR: ${err.message}`);
  process.exit(1);
});
