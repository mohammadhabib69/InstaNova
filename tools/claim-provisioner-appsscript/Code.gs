/**
 * InstaNova claim provisioner (Google Apps Script web app).
 *
 * Grants `role: "authenticated"` to the Firebase user that presents its own ID token.
 * Supabase reads that claim to choose a Postgres role; Firebase does not mint it, so
 * without this every new account runs as `anon` and the signup profile insert is
 * rejected by RLS.
 *
 * Security model
 * --------------
 * - No credential is stored anywhere. The script runs inside Google as its owner and
 *   uses ScriptApp.getOAuthToken(), so no key, secret or refresh token ever leaves
 *   Google or is embedded in the app.
 * - The uid is taken from Identity Toolkit's answer about the presented token, never
 *   from the request body, so a caller can only modify its own account.
 * - The web app is public, but the only thing an unauthenticated caller can do is
 *   receive "invalid token".
 *
 * Deploy: see tools/claim-provisioner-appsscript/README.md
 */

var PROJECT_ID = 'instanova-62ff0';
var WEB_API_KEY = 'AIzaSyASqXVe3-N1_ThlWJfQAi3Pxt6VlA90a6E';
var IDENTITY = 'https://identitytoolkit.googleapis.com/v1';

function doGet() {
  return json({ status: 'ok', project: PROJECT_ID });
}

function doPost(e) {
  var body;
  try {
    body = JSON.parse(e.postData.contents);
  } catch (err) {
    return json({ ok: false, error: 'invalid json body' });
  }

  var idToken = body && body.idToken;
  if (!idToken) return json({ ok: false, error: 'missing idToken' });

  var caller = lookup(idToken);
  if (!caller) return json({ ok: false, error: 'invalid or expired Firebase ID token' });

  var claims = {};
  if (caller.customAttributes) {
    try {
      claims = JSON.parse(caller.customAttributes);
    } catch (err) {
      claims = {};
    }
  }
  if (claims.role === 'authenticated') {
    return json({ ok: true, uid: caller.localId, changed: false });
  }

  // Merge, never replace: unrelated custom claims must survive.
  claims.role = 'authenticated';
  var res = UrlFetchApp.fetch(IDENTITY + '/projects/' + PROJECT_ID + '/accounts:update', {
    method: 'post',
    contentType: 'application/json',
    headers: { Authorization: 'Bearer ' + ScriptApp.getOAuthToken() },
    payload: JSON.stringify({
      localId: caller.localId,
      customAttributes: JSON.stringify(claims)
    }),
    muteHttpExceptions: true
  });

  if (res.getResponseCode() !== 200) {
    return json({ ok: false, error: 'identity toolkit rejected the update', detail: res.getContentText() });
  }
  return json({ ok: true, uid: caller.localId, changed: true });
}

/** Returns {localId, customAttributes} for a valid ID token, or null. */
function lookup(idToken) {
  var res = UrlFetchApp.fetch(IDENTITY + '/accounts:lookup?key=' + encodeURIComponent(WEB_API_KEY), {
    method: 'post',
    contentType: 'application/json',
    payload: JSON.stringify({ idToken: idToken }),
    muteHttpExceptions: true
  });
  if (res.getResponseCode() !== 200) return null;
  var users = JSON.parse(res.getContentText()).users;
  if (!users || !users.length) return null;
  return users[0];
}

function json(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
