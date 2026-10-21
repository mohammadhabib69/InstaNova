/**
 * provision-role — grants `role: "authenticated"` to the calling Firebase user.
 *
 * WHY
 * ---
 * Supabase picks the Postgres role from the `role` claim of the JWT it receives.
 * Firebase never mints that claim, so a brand-new account is treated as `anon` and
 * every `to authenticated` policy (including the signup profile insert) rejects it.
 * Only a trusted server can add the claim; the APK must never hold admin credentials.
 *
 * HOW
 * ---
 * 1. The caller sends its own Firebase ID token as `Authorization: Bearer <token>`.
 * 2. This function asks Google who that token belongs to (Identity Toolkit lookup).
 *    The uid comes from Google, never from the request body, so a caller can only
 *    ever modify its own account.
 * 3. It mints a short-lived OAuth2 access token from the service-account key held in
 *    Supabase secrets, then merges `{ role: "authenticated" }` into that user's
 *    existing custom claims.
 *
 * The function is idempotent and does nothing if the claim is already present.
 *
 * DEPLOY (free plan; no billing account needed)
 * ---------------------------------------------
 *   npx supabase login
 *   npx supabase link --project-ref poqdemxiuoocbzkwbyhy
 *   npx supabase secrets set \
 *       FIREBASE_PROJECT_ID=instanova-62ff0 \
 *       FIREBASE_CLIENT_EMAIL=<service-account client_email> \
 *       FIREBASE_PRIVATE_KEY="<service-account private_key>" \
 *       FIREBASE_WEB_API_KEY=<the same web API key the app uses>
 *   npx supabase functions deploy provision-role --no-verify-jwt
 *
 * `--no-verify-jwt` is required because the gateway would otherwise demand a Supabase
 * JWT; the caller presents a Firebase token and this function verifies it itself.
 */

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const IDENTITY_BASE = "https://identitytoolkit.googleapis.com/v1";
const SCOPE = "https://www.googleapis.com/auth/identitytoolkit";

type ServiceAccount = {
  projectId: string;
  clientEmail: string;
  privateKey: string;
  webApiKey: string;
};

function readServiceAccount(): ServiceAccount | { error: string } {
  const projectId = Deno.env.get("FIREBASE_PROJECT_ID") ?? "";
  const clientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL") ?? "";
  // Secrets arrive with literal "\n" sequences; PEM needs real newlines.
  const privateKey = (Deno.env.get("FIREBASE_PRIVATE_KEY") ?? "").replace(/\\n/g, "\n");
  const webApiKey = Deno.env.get("FIREBASE_WEB_API_KEY") ?? "";

  const missing = [
    ["FIREBASE_PROJECT_ID", projectId],
    ["FIREBASE_CLIENT_EMAIL", clientEmail],
    ["FIREBASE_PRIVATE_KEY", privateKey],
    ["FIREBASE_WEB_API_KEY", webApiKey],
  ].filter(([, v]) => !v).map(([k]) => k);

  if (missing.length) return { error: `missing secrets: ${missing.join(", ")}` };
  return { projectId, clientEmail, privateKey, webApiKey };
}

function base64url(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Signs the OAuth2 JWT assertion for the service account (RS256). */
async function signAssertion(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const payload = {
    iss: sa.clientEmail,
    scope: SCOPE,
    aud: TOKEN_URL,
    iat: now,
    exp: now + 3600,
  };
  const encoder = new TextEncoder();
  const unsigned = `${base64url(encoder.encode(JSON.stringify(header)))}.` +
    `${base64url(encoder.encode(JSON.stringify(payload)))}`;

  const pem = sa.privateKey
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const raw = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));

  const key = await crypto.subtle.importKey(
    "pkcs8",
    raw,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    encoder.encode(unsigned),
  );
  return `${unsigned}.${base64url(new Uint8Array(signature))}`;
}

async function accessToken(sa: ServiceAccount): Promise<string> {
  const assertion = await signAssertion(sa);
  const res = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const body = await res.json();
  if (!res.ok || !body.access_token) {
    throw new Error(`token exchange failed: ${res.status} ${JSON.stringify(body)}`);
  }
  return body.access_token as string;
}

type LookupUser = {
  localId: string;
  customAttributes?: string;
};

async function lookupCaller(sa: ServiceAccount, idToken: string): Promise<LookupUser | null> {
  const res = await fetch(
    `${IDENTITY_BASE}/accounts:lookup?key=${encodeURIComponent(sa.webApiKey)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ idToken }),
    },
  );
  const body = await res.json();
  if (!res.ok) return null;
  const user = body.users?.[0];
  return user ? { localId: user.localId, customAttributes: user.customAttributes } : null;
}

function json(status: number, payload: unknown): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json(405, { error: "method not allowed" });

  const sa = readServiceAccount();
  if ("error" in sa) return json(500, { error: sa.error });

  const auth = req.headers.get("Authorization") ?? "";
  const idToken = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  if (!idToken) return json(401, { error: "missing Firebase ID token" });

  let caller: LookupUser | null;
  try {
    caller = await lookupCaller(sa, idToken);
  } catch {
    return json(401, { error: "could not verify token" });
  }
  if (!caller) return json(401, { error: "invalid or expired Firebase ID token" });

  // Merge, never replace: an account may already carry unrelated custom claims.
  let claims: Record<string, unknown> = {};
  if (caller.customAttributes) {
    try {
      claims = JSON.parse(caller.customAttributes);
    } catch {
      claims = {};
    }
  }
  if (claims.role === "authenticated") {
    return json(200, { uid: caller.localId, changed: false });
  }
  claims.role = "authenticated";

  try {
    const token = await accessToken(sa);
    const res = await fetch(
      `${IDENTITY_BASE}/projects/${sa.projectId}/accounts:update`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          localId: caller.localId,
          customAttributes: JSON.stringify(claims),
        }),
      },
    );
    if (!res.ok) {
      const text = await res.text();
      return json(502, { error: `identity toolkit rejected the update: ${res.status}`, detail: text });
    }
  } catch (e) {
    return json(500, { error: e instanceof Error ? e.message : "provisioning failed" });
  }

  return json(200, { uid: caller.localId, changed: true });
});
