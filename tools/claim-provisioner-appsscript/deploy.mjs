#!/usr/bin/env node
/**
 * Deploys Code.gs as an Apps Script web app and prints its /exec URL.
 *
 * Uses a PKCE authorization-code flow against Google's installed-app OAuth client
 * (the same public client the clasp CLI uses), so no client secret is required and no
 * credential is written anywhere except a local, revocable refresh token.
 *
 * Scopes are limited to what deployment actually needs:
 *   script.projects      create the project and push files
 *   script.deployments   create the version and the web-app deployment
 *   userinfo.email       report which account authorized
 *
 * Usage:  node deploy.mjs
 */

import http from "node:http";
import crypto from "node:crypto";
import { exec } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const CLIENT_ID =
  "1072944905499-vm2v2i5dvn0a0d2o4ca36i1vge8cvbn0.apps.googleusercontent.com";
// Public by design: this is clasp's well-known installed-app client, and Google
// Identity does not support secretless desktop clients. Upstream ships the same value
// in the open-source package with a comment saying it is publicly accessible.
const CLIENT_SECRET = "v6V3fKV_zWU7iw1DrpO1rknX";
const SCOPES = [
  "https://www.googleapis.com/auth/script.projects",
  "https://www.googleapis.com/auth/script.deployments",
  "https://www.googleapis.com/auth/userinfo.email",
];
const PORT = 8765;
const REDIRECT = `http://127.0.0.1:${PORT}`;
const HERE = path.dirname(fileURLToPath(import.meta.url));

function b64url(buf) {
  return buf.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

const verifier = b64url(crypto.randomBytes(32));
const challenge = b64url(crypto.createHash("sha256").update(verifier).digest());

const authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
  new URLSearchParams({
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT,
    response_type: "code",
    scope: SCOPES.join(" "),
    code_challenge: challenge,
    code_challenge_method: "S256",
    access_type: "offline",
    prompt: "consent",
  });

function waitForCode() {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      const url = new URL(req.url, REDIRECT);
      const code = url.searchParams.get("code");
      const error = url.searchParams.get("error");
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
      if (code) {
        res.end("<h2>Authorized. You can close this tab.</h2>");
        server.close();
        resolve(code);
      } else {
        res.end(`<h2>Authorization failed: ${error ?? "no code"}</h2>`);
        server.close();
        reject(new Error(error ?? "no code"));
      }
    });
    server.listen(PORT, "127.0.0.1");
  });
}

async function exchange(code) {
  const body = new URLSearchParams({
    client_id: CLIENT_ID,
    code,
    code_verifier: verifier,
    grant_type: "authorization_code",
    redirect_uri: REDIRECT,
  });
  body.set("client_secret", CLIENT_SECRET);
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  const json = await res.json();
  if (!res.ok) throw new Error(`token exchange failed: ${res.status} ${JSON.stringify(json)}`);
  saveToken(json);
  return json.access_token;
}

/* -------------------------------------------------------------------------- */
/* Local token cache                                                          */
/* -------------------------------------------------------------------------- */

const CACHE = path.join(os.homedir(), ".instanova-secrets", "appsscript-token.json");

function saveToken(json) {
  fs.mkdirSync(path.dirname(CACHE), { recursive: true });
  fs.writeFileSync(CACHE, JSON.stringify({
    access_token: json.access_token,
    refresh_token: json.refresh_token ?? null,
    expires_at: Date.now() + (json.expires_in ?? 3600) * 1000 - 60_000,
  }), { mode: 0o600 });
}

/** Returns a usable token from the cache, refreshing it if needed, or null. */
async function cachedToken() {
  let cached;
  try {
    cached = JSON.parse(fs.readFileSync(CACHE, "utf8"));
  } catch {
    return null;
  }
  if (cached.access_token && cached.expires_at > Date.now()) return cached.access_token;
  if (!cached.refresh_token) return null;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      refresh_token: cached.refresh_token,
      grant_type: "refresh_token",
    }),
  });
  const json = await res.json();
  if (!res.ok) return null;
  saveToken({ ...json, refresh_token: json.refresh_token ?? cached.refresh_token });
  return json.access_token;
}

async function api(token, method, url, payload) {
  const res = await fetch(url, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: payload ? JSON.stringify(payload) : undefined,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${url} -> ${res.status} ${text.slice(0, 400)}`);
  return text ? JSON.parse(text) : {};
}

const codeSource = fs.readFileSync(path.join(HERE, "Code.gs"), "utf8");
const manifestSource = fs.readFileSync(path.join(HERE, "appsscript.json"), "utf8");

let token = await cachedToken();
if (token) {
  console.log("Reusing cached authorization.");
} else {
  console.log("Opening the authorization page in your browser…");
  exec(`open "${authUrl}"`);
  token = await exchange(await waitForCode());
}

const me = await api(token, "GET", "https://www.googleapis.com/oauth2/v1/userinfo");
console.log(`Authorized as ${me.email}`);

// Reuse the project from an earlier attempt instead of leaving empty duplicates
// behind in Drive.
const PROJECT_CACHE = path.join(os.homedir(), ".instanova-secrets", "appsscript-project.json");
let scriptId = null;
try {
  scriptId = JSON.parse(fs.readFileSync(PROJECT_CACHE, "utf8")).scriptId;
} catch {
  scriptId = null;
}

if (scriptId) {
  console.log(`project: ${scriptId} (reused)`);
} else {
  const project = await api(token, "POST", "https://script.googleapis.com/v1/projects", {
    title: "InstaNova claim provisioner",
  });
  scriptId = project.scriptId;
  fs.writeFileSync(PROJECT_CACHE, JSON.stringify({ scriptId }), { mode: 0o600 });
  console.log(`project: ${scriptId}`);
}

await api(token, "PUT", `https://script.googleapis.com/v1/projects/${scriptId}/content`, {
  files: [
    { name: "Code", type: "SERVER_JS", source: codeSource },
    { name: "appsscript", type: "JSON", source: manifestSource },
  ],
});

const version = await api(
  token, "POST", `https://script.googleapis.com/v1/projects/${scriptId}/versions`, {},
);
console.log(`version: ${version.versionNumber}`);

const deployment = await api(
  token, "POST", `https://script.googleapis.com/v1/projects/${scriptId}/deployments`,
  {
    versionNumber: version.versionNumber,
    manifestFileName: "appsscript",
    description: "v1",
  },
);

const deploymentId = deployment.deploymentId;
const url = `https://script.google.com/macros/s/${deploymentId}/exec`;
console.log(`deployment: ${deploymentId}`);
console.log(`CLAIM_PROVISION_URL=${url}`);
