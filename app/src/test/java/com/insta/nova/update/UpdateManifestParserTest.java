package com.insta.nova.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Contract of latest.json. These cases mirror what the release script publishes and what
 * the checker must refuse, so a hand-edited manifest cannot silently disable integrity
 * checks or point the app at an insecure URL.
 */
public class UpdateManifestParserTest {

    private static final String CANONICAL = "{"
            + "\"versionCode\": 12,"
            + "\"versionName\": \"1.2.0\","
            + "\"apkUrl\": \"https://github.com/mohammadhabib69/InstaNova/releases/download/v1.2.0/InstaNova-1.2.0.apk\","
            + "\"sha256\": \"ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789\","
            + "\"sizeBytes\": 48210304,"
            + "\"publishedAt\": \"2026-11-09T10:00:00Z\","
            + "\"releaseNotes\": [\"Faster feed\", \"Fixed reels playback\"]"
            + "}";

    @Test
    public void parsesCanonicalManifest() throws Exception {
        UpdateInfo info = UpdateManifestParser.parse(CANONICAL);

        assertEquals(12, info.versionCode);
        assertEquals("1.2.0", info.versionName);
        assertEquals("1.2.0", info.displayVersion());
        assertEquals("1.2.0 (12)", info.displayVersionWithCode());
        assertTrue(info.apkUrl.startsWith("https://github.com/"));
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                info.sha256);
        assertEquals(48210304L, info.sizeBytes);
        assertEquals(2, info.releaseNotes.size());
        assertEquals("Faster feed", info.releaseNotes.get(0));
    }

    @Test
    public void newestReleaseWinsByVersionCode() throws Exception {
        UpdateInfo info = UpdateManifestParser.parse(CANONICAL);
        assertTrue(info.isNewerThan(1, "1.0.0"));
        assertFalse(info.isNewerThan(12, "1.2.0"));
        assertFalse(info.isNewerThan(13, "1.3.0"));
    }

    @Test
    public void acceptsSnakeCaseAliasesAndTagPrefix() throws Exception {
        String json = "{"
                + "\"version_code\": 7,"
                + "\"version_name\": \"v1.1.7\","
                + "\"apk_url\": \"https://example.com/app.apk\","
                + "\"notes\": \"- One\\n- Two\""
                + "}";
        UpdateInfo info = UpdateManifestParser.parse(json);
        assertEquals(7, info.versionCode);
        assertEquals("1.1.7", info.versionName);
        assertEquals(2, info.releaseNotes.size());
        assertEquals("One", info.releaseNotes.get(0));
        assertEquals("Two", info.releaseNotes.get(1));
        assertEquals("", info.sha256);
    }

    @Test
    public void buildsGitHubAssetUrlWhenOnlyRepoTagAssetGiven() throws Exception {
        String json = "{"
                + "\"versionCode\": 4,"
                + "\"tag\": \"v1.0.4\","
                + "\"repo\": \"mohammadhabib69/InstaNova\","
                + "\"assetName\": \"InstaNova-1.0.4.apk\""
                + "}";
        UpdateInfo info = UpdateManifestParser.parse(json);
        assertEquals("https://github.com/mohammadhabib69/InstaNova/releases/download/v1.0.4/InstaNova-1.0.4.apk",
                info.apkUrl);
        assertEquals("1.0.4", info.versionName);
    }

    @Test
    public void rejectsMissingVersionCode() {
        assertRejected("{\"versionName\":\"1.0.1\",\"apkUrl\":\"https://example.com/a.apk\"}");
    }

    @Test
    public void rejectsMissingApkUrl() {
        assertRejected("{\"versionCode\": 3, \"versionName\": \"1.0.3\"}");
    }

    @Test
    public void rejectsInsecureApkUrlUnlessDebugAllowed() throws Exception {
        String json = "{\"versionCode\": 3, \"versionName\": \"1.0.3\","
                + "\"apkUrl\": \"http://127.0.0.1:8099/app.apk\"}";
        assertRejected(json);
        // The debug-only path used by the local update-flow test harness.
        UpdateInfo info = UpdateManifestParser.parse(json, true);
        assertEquals("http://127.0.0.1:8099/app.apk", info.apkUrl);
    }

    @Test
    public void rejectsMalformedChecksum() {
        assertRejected("{\"versionCode\": 3, \"versionName\": \"1.0.3\","
                + "\"apkUrl\": \"https://example.com/a.apk\", \"sha256\": \"deadbeef\"}");
        assertRejected("{\"versionCode\": 3, \"versionName\": \"1.0.3\","
                + "\"apkUrl\": \"https://example.com/a.apk\","
                + "\"sha256\": \"zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz\"}");
    }

    @Test
    public void normalisesPrefixedChecksum() throws Exception {
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                UpdateManifestParser.normalizeSha256(
                        "sha256: ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789"));
    }

    @Test
    public void rejectsGarbage() {
        assertRejected("not json at all");
        assertRejected("[]");
        assertRejected("");
        assertRejected("{\"versionCode\": 0}");
    }

    @Test
    public void capsReleaseNotes() throws Exception {
        StringBuilder notes = new StringBuilder("[");
        for (int i = 0; i < 40; i++) {
            if (i > 0) notes.append(',');
            notes.append("\"change ").append(i).append('"');
        }
        notes.append(']');
        UpdateInfo info = UpdateManifestParser.parse("{\"versionCode\": 9,"
                + "\"versionName\": \"0.9.0\",\"apkUrl\": \"https://example.com/a.apk\","
                + "\"releaseNotes\": " + notes + "}");
        assertEquals(25, info.releaseNotes.size());
    }

    private static void assertRejected(String json) {
        try {
            UpdateManifestParser.parse(json);
            fail("expected rejection: " + json);
        } catch (UpdateManifestParser.UpdateParseException expected) {
            // Expected.
        }
    }
}
