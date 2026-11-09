# Native OTA updates (GitHub Releases)

InstaNova ships updates outside the Play Store: the app checks a static `latest.json`
published as a GitHub Release asset, downloads the APK from the same release, verifies it,
and hands it to Android's own package installer. Nothing in the data layer changes — the
updater never touches Firebase Authentication, the Realtime Database, Firestore, Supabase,
storage or RLS.

## Flow

```
App launch
  └─ InstaApp registers UpdateLaunchWatcher
       └─ first non-splash Activity resumes
            ├─ GET https://github.com/<owner>/<repo>/releases/latest/download/latest.json
            ├─ versionCode(manifest) > versionCode(installed)?
            │     ├─ no  → stay silent (also when offline / 404 / bad JSON)
            │     └─ yes → UpdateDialog: "New Update Available", version, What's new,
            │              [Update Now] [Later]
            ├─ Update Now → UpdateDownloadService (foreground, dataSync)
            │     ├─ progress notification + in-dialog progress
            │     ├─ SHA-256, package name, versionCode, signing certificate checks
            │     └─ UpdateInstaller → system package installer
            │           ├─ no "install unknown apps" grant → system settings, then resume
            │           └─ installed → MY_PACKAGE_REPLACED clears the leftover APK
            └─ Later → dialog closes, app continues unchanged; prompt returns next launch
```

## Components

| File | Responsibility |
| --- | --- |
| `update/UpdateInfo.java` | One release (version code/name, APK URL, checksum, notes) |
| `update/UpdateManifestParser.java` | Strict `latest.json` parsing: HTTPS-only APK URL, validated SHA-256, aliases |
| `update/VersionName.java` | Dotted-version comparison fallback when a manifest omits `versionCode` |
| `update/UpdateChecker.java` | OkHttp fetch off the main thread; any failure = "no update" |
| `update/UpdateManager.java` | State machine: check → available → download → verify → install |
| `update/UpdateDialog.java` | Native popup built from the app's own theme tokens |
| `update/UpdateDownloadService.java` | Foreground download with progress notification |
| `update/PackageVerifier.java` | SHA-256 + package identity + signing certificate |
| `update/UpdateInstaller.java` | "Install unknown apps" gate + `ACTION_VIEW` installer intent |
| `update/UpdateNotifications.java` | `instanova_app_updates` channel, progress/ready/failure |
| `update/UpdateInstallActivity.java` | Invisible trampoline for the notification tap |
| `update/PackageReplacedReceiver.java` | Post-update cleanup |

Integration points (the only edits to existing files):

- `InstaApp.onCreate` registers `UpdateLaunchWatcher`.
- `AndroidManifest.xml` declares `REQUEST_INSTALL_PACKAGES`,
  `FOREGROUND_SERVICE_DATA_SYNC`, the service, the trampoline activity and the receiver.
- `res/xml/file_paths.xml` exposes `files/updates/` to the installer through the existing
  FileProvider (camera paths untouched).
- `res/values/themes.xml` adds `Theme.InstaClone.UpdateDialog`; no existing style changed.
- `app/build.gradle` bumps `versionCode`/`versionName` and adds `UPDATE_MANIFEST_URL`.
- `values/strings.xml` and `values-bn/strings.xml` add the new copy.

## latest.json

```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "apkUrl": "https://github.com/mohammadhabib69/InstaNova/releases/download/v1.1.0/InstaNova-1.1.0.apk",
  "sha256": "3f5c…64 hex chars…",
  "sizeBytes": 48210304,
  "publishedAt": "2026-11-09T10:00:00Z",
  "releaseNotes": [
    "In-app update notification",
    "Faster feed loading"
  ]
}
```

Rules enforced by the parser:

- `versionCode` is required and is the comparison key.
- `apkUrl` must be HTTPS (HTTP allowed only in debug builds, for the local test harness).
- `sha256` is optional but, when present, must be 64 hex characters; a malformed value
  rejects the whole manifest instead of silently skipping the integrity check.
- `releaseNotes` accepts an array, a newline-separated string, or `- bullet` lines.

`apkUrl` may be omitted if `repo`, `tag` and `assetName` are provided; the parser then
builds `https://github.com/<repo>/releases/download/<tag>/<assetName>`.

## Release workflow

1. Raise `versionCode` (and `versionName`) in `app/build.gradle` — Android refuses to
   install an APK whose version code is not higher.
2. Make sure `local.properties` holds the release signing values
   (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
   `RELEASE_KEY_PASSWORD`). Every release must use the same key, otherwise the signature
   check fails on purpose.
3. Run `tools/ota/release.sh` (add `--notes-file notes.txt`, repeatable `--notes "…"`, or
   `--publish` to create the GitHub release with `gh`).
4. Upload **both** `InstaNova-<version>.apk` and `latest.json` to the release. The release
   must not be a draft or a pre-release: `releases/latest/download/latest.json` only
   resolves to the latest full release.
5. The app needs no change for the next version — it always reads that same URL.

## Verification

- `./gradlew :app:testDebugUnitTest --tests "com.insta.nova.update.*"` — manifest parsing,
  version comparison, checksum rules (16 tests).
- Local end-to-end (no GitHub needed): build an old and a new APK with
  `-PappVersionCode=1 -PappVersionName=1.0.0 -PupdateManifestUrl=http://127.0.0.1:8099/latest.json`,
  serve `latest.json` + the new APK with `python3 -m http.server 8099`,
  `adb reverse tcp:8099 tcp:8099`, install the old APK, launch, and walk the dialog.
  Debug builds accept the HTTP test URL; release builds never do.
