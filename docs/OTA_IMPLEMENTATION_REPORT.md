# Native OTA Update System — Implementation & Verification Report

Date: 2026-11-09
App: InstaNova (`com.insta.nova`), versionCode 2 / versionName 1.1.0
Distribution: GitHub Releases (no Play Store dependency)

## 1. Scope delivered

| Requirement | Status | Where |
| --- | --- | --- |
| GitHub Releases hosting + `latest.json` manifest | Done | `UpdateChecker`, `UpdateManifestParser`, `tools/ota/release.sh` |
| Check on every app launch | Done | `UpdateLaunchWatcher` registered in `InstaApp` |
| Compare installed vs latest version | Done | `UpdateInfo.isNewerThan` (versionCode authoritative) |
| No notification when up to date | Done | `UpdateManager` returns to `IDLE`, no UI |
| Offline must not disrupt the app | Done | Checker failures are swallowed; state returns to idle |
| Native popup: title, version, What's new, Update Now, Later | Done | `UpdateDialog` + `dialog_update_available.xml` |
| Update is optional (Later) | Done | Dialog dismisses, app unchanged |
| APK download from GitHub Releases | Done | `UpdateDownloadService` (OkHttp, HTTPS-only in release) |
| Download progress notification | Done | `UpdateNotifications` channel `instanova_app_updates`, id 9101 |
| APK integrity + package verification | Done | `PackageVerifier` (SHA-256, package, versionCode, signing certificate) |
| Native package installer | Done | `UpdateInstaller` (explicit PackageInstaller intent) |
| "Install unknown apps" permission handling | Done | `UpdateInstaller.canInstall` + settings round-trip in `UpdateManager` |
| App usable on the new version after install | Done | Verified on device; `PackageReplacedReceiver` cleans up |
| versionCode increments per release | Enforced | `app/build.gradle` + `tools/ota/release.sh` warning |
| versionName + release notes from JSON | Done | Manifest is the single source of truth |
| Same applicationId + signing key | Preserved | `com.insta.nova`; signature mismatch blocks install by design |

## 2. Constraints respected

- No existing InstaNova screen, layout, colour, style or flow was changed. The only
  presentation additions are the new dialog layout, its card drawable, its icon and a new
  dialog theme.
- Firebase Authentication, Realtime Database, Firestore, Storage and FCM code untouched.
- Supabase client, database, storage and RLS untouched — no migration, no policy change.
- Login, registration, posts, reels, messages, notifications and the Nova modules untouched.
- No new third-party dependency: the updater uses OkHttp, Gson, AndroidX and the platform
  `DownloadManager`-free HTTP stack already in the project.
- No hardcoded update information: every field in the dialog comes from `latest.json`.

## 3. Files

### Added

```
app/src/main/java/com/insta/nova/update/
  UpdateInfo.java                release model
  UpdateManifestParser.java      strict latest.json parsing
  VersionName.java               dotted-version fallback comparison
  UpdateChecker.java             HTTPS fetch, background, silent failures
  UpdateManager.java             state machine + download/install orchestration
  UpdateDialog.java              native update popup
  UpdateDownloadService.java     foreground (dataSync) download + progress
  PackageVerifier.java           SHA-256 / package / versionCode / certificate
  UpdateInstaller.java           permission gate + installer intent
  UpdateNotifications.java       channel + progress / ready / failure cards
  UpdateStore.java               pending APK persistence
  UpdateInstallActivity.java     invisible notification trampoline
  PackageReplacedReceiver.java   post-update cleanup
  UpdateLaunchWatcher.java       first-screen-resume trigger
app/src/main/res/layout/dialog_update_available.xml
app/src/main/res/drawable/bg_update_dialog.xml
app/src/main/res/drawable/ic_update_download.xml
app/src/test/java/com/insta/nova/update/UpdateManifestParserTest.java
app/src/test/java/com/insta/nova/update/VersionNameTest.java
docs/OTA_UPDATES.md
docs/OTA_IMPLEMENTATION_REPORT.md   (this file)
tools/ota/release.sh
```

### Modified (8 files, additive: 200 insertions, 2 deletions)

| File | Change |
| --- | --- |
| `app/build.gradle` | versionCode 2 / versionName 1.1.0, `UPDATE_MANIFEST_URL`, optional `RELEASE_*` signing, `-PappVersionCode/-PappVersionName/-PupdateManifestUrl` overrides, `printOtaVersion` task |
| `app/src/main/AndroidManifest.xml` | `REQUEST_INSTALL_PACKAGES`, `FOREGROUND_SERVICE_DATA_SYNC`, service, trampoline activity, `MY_PACKAGE_REPLACED` receiver |
| `app/src/main/java/com/insta/nova/InstaApp.java` | registers `UpdateLaunchWatcher` (last statement in `onCreate`) |
| `app/src/main/res/values/strings.xml` | 20 update strings |
| `app/src/main/res/values-bn/strings.xml` | Bangla translations |
| `app/src/main/res/values/themes.xml` | `Theme.InstaClone.UpdateDialog` (new style only) |
| `app/src/main/res/xml/file_paths.xml` | `files-path name="updates" path="updates/"` |
| `local.properties.example` | OTA manifest URL + release signing keys |

## 4. Flow implemented

```
launch → first non-splash Activity resumed
  → GET .../releases/latest/download/latest.json
  → newer?  no → silent
            yes → dialog (Later | Update Now)
  → Update Now → foreground download + progress notification
  → SHA-256 + package + versionCode + certificate checks
  → "install unknown apps" gate if needed
  → system package installer
  → MY_PACKAGE_REPLACED clears the staged APK and pending state
```

## 5. Verification

### Builds

| Command | Result |
| --- | --- |
| `./gradlew :app:compileDebugJavaWithJavac` | BUILD SUCCESSFUL (baseline before changes also passed) |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew :app:assembleRelease` | BUILD SUCCESSFUL, `lintVitalRelease` passed (unsigned until `RELEASE_*` is configured) |

Release APK manifest verified with `aapt2 dump`:
`REQUEST_INSTALL_PACKAGES`, `FOREGROUND_SERVICE_DATA_SYNC` present;
`UpdateDownloadService` with `foregroundServiceType=dataSync`; trampoline activity and
receiver registered; `BuildConfig.UPDATE_MANIFEST_URL` =
`https://github.com/mohammadhabib69/InstaNova/releases/latest/download/latest.json`.

### Unit tests

| Suite | Result |
| --- | --- |
| `com.insta.nova.update.*` | 16 tests, 0 failures |
| Full `testDebugUnitTest` | **368 tests, 0 failures** |

OTA tests cover: canonical manifest, snake_case aliases, tag prefix, GitHub asset URL
synthesis, missing versionCode, missing apkUrl, insecure URL rejection (and the debug-only
allowance), malformed checksum, note aliasing/capping, version comparison and the
versionCode-over-name precedence.

### On-device end-to-end (vivo I2302, Android 14)

Old build 1.0.0 (versionCode 1) installed, new APK served locally:

1. Launch → "New Update Available / Version 1.1.0 is ready to install / What's new / Update Now / Later".
2. `Later` → dialog dismissed, feed fully usable.
3. Relaunch → dialog shown again (per requirement).
4. `Update Now` → download to `files/updates/InstaNova-2.apk` (61,987,649 bytes) → verification passed.
5. Install-unknown-apps screen opened automatically; after granting and returning, the
   native installer opened with "Do you want to update this app?".
6. After `Update` → installed build reported `versionCode=2`, `versionName=1.1.0`.
7. Relaunch on the new version → no dialog (up-to-date path).
8. Second cycle (v2 → v3 test build): progress notification observed as
   `Downloading update / InstaNova 1.1.1 / progress 59%`, `category=progress`,
   flags `FOREGROUND_SERVICE|ONGOING_EVENT|ONLY_ALERT_ONCE|SILENT`.
9. Cancelling the installer and relaunching produced the ready state
   ("Download complete. Finish the installation in the next screen."), i.e. the verified
   APK is reused instead of being downloaded again.
10. Ready notification verified as `Update ready to install — Tap to install InstaNova 1.1.1`,
    `category=recommendation`, surviving the foreground-service teardown.

### Regression sweep on the updated build

Home feed, Profile (mrtest001, 32 posts), Reels destination, Create sheet,
Direct-message inbox, Activity/notifications screen and Settings → About (shows
"Version 1.1.0") all open normally. Crash buffer contained no `com.insta.nova` entry.

### Defects found during testing and fixed

1. `Later` inherited MaterialButton's blue `backgroundTint` → both buttons now use
   `AppCompatButton` with the app's own drawables.
2. Install intent was implicit → OEMs with several APK handlers showed an "Open with"
   chooser; the intent now targets `com.google.android.packageinstaller` /
   `com.android.packageinstaller` explicitly, falling back to implicit only if neither exists.
3. The foreground service rejected a debug HTTP test URL → allowed only when
   `BuildConfig.DEBUG`; release builds remain HTTPS-only.
4. `stopForeground(STOP_FOREGROUND_REMOVE)` deleted the freshly posted "ready to install"
   card (same notification id) → foreground is now stopped **before** the final card is posted.

## 6. Out of scope / requires the owner

- **Release signing.** No keystore is configured in this workspace, so
  `assembleRelease` produces `app-release-unsigned.apk`. Because `PackageVerifier`
  compares certificates, every release must be signed with the same key as the installed
  app. The currently distributed build is debug-signed, so either keep signing with that
  key or ship one manual APK to move users to a new key.
- **Publishing the GitHub release.** Creating the release (and uploading
  `InstaNova-<version>.apk` + `latest.json`) was not performed; it is an external action on
  the owner's repository. `tools/ota/release.sh --publish` does it when run by the owner.
- Release notes are supplied by the operator (`--notes`, `--notes-file`, or the git log
  since the previous `ota-v*` tag); the app never invents text.

## 7. Operator checklist for each release

1. Bump `versionCode` (and `versionName`) in `app/build.gradle`.
2. Confirm `RELEASE_*` values in `local.properties`.
3. `tools/ota/release.sh --notes-file notes.txt` → `build/ota/InstaNova-<version>.apk` + `build/ota/latest.json`.
4. Create a **full** GitHub release (not draft, not pre-release) with both files.
5. Existing installs see the popup on their next launch; no client change is needed for
   future versions.
