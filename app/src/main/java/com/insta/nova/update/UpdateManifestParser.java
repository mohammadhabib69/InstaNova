package com.insta.nova.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses the {@code latest.json} manifest published with a GitHub Release.
 *
 * <p>Deliberately strict about the things that make an install safe — the download URL
 * must be HTTPS and the declared checksum must be a real SHA-256 — and tolerant about
 * naming, so a hand-written manifest with {@code notes} instead of {@code releaseNotes}
 * still works. Anything malformed raises {@link UpdateParseException}; the caller then
 * treats the check as "no update" instead of showing a broken dialog.
 */
public final class UpdateManifestParser {

    /** Guards against a hostile or accidental multi-megabyte manifest. */
    public static final int MAX_MANIFEST_CHARS = 256 * 1024;

    private static final int MAX_NOTES = 25;
    private static final int MAX_NOTE_CHARS = 500;

    private UpdateManifestParser() {
    }

    public static UpdateInfo parse(String json) throws UpdateParseException {
        return parse(json, false);
    }

    /**
     * @param allowInsecureApkUrl debug only — lets a build point at a local HTTP test
     *                            server. Release builds always require HTTPS.
     */
    public static UpdateInfo parse(String json, boolean allowInsecureApkUrl)
            throws UpdateParseException {
        if (json == null || json.trim().isEmpty()) {
            throw new UpdateParseException("empty manifest");
        }
        if (json.length() > MAX_MANIFEST_CHARS) {
            throw new UpdateParseException("manifest too large");
        }

        JsonElement rootElement;
        try {
            rootElement = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new UpdateParseException("malformed JSON");
        }
        if (rootElement == null || !rootElement.isJsonObject()) {
            throw new UpdateParseException("manifest is not a JSON object");
        }
        JsonObject root = rootElement.getAsJsonObject();

        String versionName = firstNonEmpty(root, "versionName", "version_name", "version", "tag");
        if (versionName != null) versionName = stripTagPrefix(versionName);

        int versionCode = intValue(root, "versionCode", "version_code", "build");
        if (versionCode <= 0) {
            throw new UpdateParseException("versionCode missing");
        }
        if (isBlank(versionName)) {
            versionName = String.valueOf(versionCode);
        }

        String apkUrl = firstNonEmpty(root, "apkUrl", "apk_url", "apk", "downloadUrl", "download_url");
        if (isBlank(apkUrl)) {
            apkUrl = githubAssetUrl(root);
        }
        apkUrl = apkUrl == null ? null : apkUrl.trim();
        if (isBlank(apkUrl)) {
            throw new UpdateParseException("apkUrl missing");
        }
        String scheme = apkUrl.toLowerCase(Locale.US);
        if (!scheme.startsWith("https://")
                && !(allowInsecureApkUrl && scheme.startsWith("http://"))) {
            throw new UpdateParseException("apkUrl must be HTTPS");
        }

        String sha256 = normalizeSha256(firstNonEmpty(root, "sha256", "checksum", "sha"));
        long sizeBytes = longValue(root, "sizeBytes", "size_bytes", "size", "bytes");
        String publishedAt = firstNonEmpty(root, "publishedAt", "published_at", "date", "released");

        return new UpdateInfo(
                versionCode,
                versionName.trim(),
                apkUrl,
                sha256,
                Math.max(0L, sizeBytes),
                publishedAt == null ? "" : publishedAt.trim(),
                readNotes(root));
    }

    /**
     * Lower-cases and validates a checksum. A value that is present but not a SHA-256 is
     * rejected: silently dropping it would turn integrity verification off without telling
     * anybody, which is worse than refusing the manifest.
     */
    static String normalizeSha256(String raw) throws UpdateParseException {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.US);
        if (value.startsWith("sha256:")) {
            value = value.substring("sha256:".length()).trim();
        }
        value = value.replace(" ", "").replace("\\", "");
        if (value.isEmpty()) return "";
        if (value.length() != 64) {
            throw new UpdateParseException("sha256 must be 64 hex characters");
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean hex = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f');
            if (!hex) {
                throw new UpdateParseException("sha256 must be hexadecimal");
            }
        }
        return value;
    }

    /** Builds a GitHub asset URL from {@code repo}/{@code tag}/{@code assetName} when present. */
    private static String githubAssetUrl(JsonObject root) {
        String repo = firstNonEmpty(root, "repo", "repository");
        String tag = firstNonEmpty(root, "tag", "releaseTag", "release_tag");
        String asset = firstNonEmpty(root, "assetName", "asset_name", "asset", "apkName", "apk_name");
        if (isBlank(repo) || isBlank(tag) || isBlank(asset)) {
            return null;
        }
        String cleanRepo = repo.trim().replaceAll("^https?://github\\.com/", "").replaceAll("/+$", "");
        if (!cleanRepo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) return null;
        String cleanAsset = asset.trim().replace(" ", "%20");
        return "https://github.com/" + cleanRepo + "/releases/download/"
                + tag.trim() + "/" + cleanAsset;
    }

    private static List<String> readNotes(JsonObject root) {
        List<String> notes = new ArrayList<>();
        JsonElement element = firstElement(root, "releaseNotes", "release_notes", "notes",
                "whatsNew", "whats_new", "changelog", "changes");
        if (element == null || element.isJsonNull()) return notes;

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                if (item == null || item.isJsonNull()) continue;
                addNote(notes, item.isJsonPrimitive() ? item.getAsString() : item.toString());
                if (notes.size() >= MAX_NOTES) break;
            }
            return notes;
        }
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            if (raw == null) return notes;
            String[] lines = raw.split("\\r?\\n");
            for (String line : lines) {
                addNote(notes, line);
                if (notes.size() >= MAX_NOTES) break;
            }
        }
        return notes;
    }

    private static void addNote(List<String> notes, String raw) {
        if (raw == null) return;
        String value = raw.trim();
        // Release notes are commonly written as "- item" or "* item".
        while (value.startsWith("-") || value.startsWith("*") || value.startsWith("•")) {
            value = value.substring(1).trim();
        }
        if (value.isEmpty()) return;
        if (value.length() > MAX_NOTE_CHARS) {
            value = value.substring(0, MAX_NOTE_CHARS) + "…";
        }
        notes.add(value);
    }

    private static JsonElement firstElement(JsonObject root, String... names) {
        for (String name : names) {
            if (root.has(name) && !root.get(name).isJsonNull()) {
                return root.get(name);
            }
        }
        return null;
    }

    private static String firstNonEmpty(JsonObject root, String... names) {
        for (String name : names) {
            if (!root.has(name)) continue;
            JsonElement element = root.get(name);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) continue;
            String value = element.getAsString();
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return null;
    }

    private static int intValue(JsonObject root, String... names) {
        for (String name : names) {
            if (!root.has(name)) continue;
            JsonElement element = root.get(name);
            if (element == null || element.isJsonNull()) continue;
            try {
                if (element.isJsonPrimitive()) {
                    String raw = element.getAsString().trim();
                    return (int) Long.parseLong(raw);
                }
            } catch (RuntimeException ignored) {
                // Try the next alias.
            }
        }
        return 0;
    }

    private static long longValue(JsonObject root, String... names) {
        for (String name : names) {
            if (!root.has(name)) continue;
            JsonElement element = root.get(name);
            if (element == null || element.isJsonNull()) continue;
            try {
                if (element.isJsonPrimitive()) {
                    return Long.parseLong(element.getAsString().trim());
                }
            } catch (RuntimeException ignored) {
                // Try the next alias.
            }
        }
        return 0L;
    }

    private static String stripTagPrefix(String value) {
        String trimmed = value.trim();
        if (trimmed.length() > 1
                && (trimmed.charAt(0) == 'v' || trimmed.charAt(0) == 'V')
                && Character.isDigit(trimmed.charAt(1))) {
            return trimmed.substring(1);
        }
        return trimmed;
    }

    /** Manifest problem. The message is for logs, never shown to the user verbatim. */
    public static final class UpdateParseException extends Exception {
        public UpdateParseException(String message) {
            super(message);
        }
    }

    /** Local null/empty check: this class runs in plain JVM unit tests too. */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}