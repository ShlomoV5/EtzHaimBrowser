# Implementation Summary

This document describes the changes made to fix APK installation issues, add whitelist file support, and implement auto-update functionality.

## Changes Made

### 1. Fixed APK Installation Issue (Release Build Signing)

**Problem:** Release APK builds were unsigned, causing "החבילה לא תקפה" (invalid package) errors on installation.

**Solution:**
- Modified `app/build.gradle` to sign release builds with the debug signing key
- Added `signingConfig signingConfigs.debug` to the release build type
- This ensures release APKs are properly signed and can be installed

**Files Modified:**
- `app/build.gradle`

### 2. Created Whitelist Source of Truth

**Problem:** Whitelist URLs were hardcoded in the source code, making them difficult to manage.

**Solution:**
- Created `whitelist.txt` file in repository root as the source of truth
- Copied `whitelist.txt` to `app/src/main/assets/` for app access
- Modified `MainActivity.getDefaultUrls()` to read from the asset file
- Kept fallback to hardcoded defaults if file not found

**Files Created:**
- `whitelist.txt` (repository root)
- `app/src/main/assets/whitelist.txt` (copied from root)

**Files Modified:**
- `app/src/main/java/com/shlomov5/ehbrowser/MainActivity.java`

**Whitelist Format:**
```
etzhaim.org.il
www.etzhaim.org.il
wordwall.net
www.wordwall.net
```

### 3. Implemented Auto-Update Feature

**Problem:** App needed auto-update capability for kiosk deployments.

**Solution:**
Implemented a complete auto-update system that:
- Checks for updates on app startup (if online)
- Shows Hebrew dialog with "הורדה" (Download) and "אחר כך" (Later) options
- Downloads APK from GitHub releases
- Triggers installation after download

**Components Created:**

#### UpdateChecker Class
- Checks GitHub API for latest release
- Compares version numbers
- Shows update dialog
- Downloads APK using DownloadManager
- Triggers installation using FileProvider (Android 7.0+ compatible)

**Files Created:**
- `app/src/main/java/com/shlomov5/ehbrowser/UpdateChecker.java`
- `app/src/main/res/xml/file_paths.xml`

**Files Modified:**
- `app/src/main/AndroidManifest.xml` (added REQUEST_INSTALL_PACKAGES permission and FileProvider)
- `app/src/main/res/values/strings.xml` (added Hebrew update strings)
- `app/src/main/java/com/shlomov5/ehbrowser/MainActivity.java` (added update check on startup)

#### GitHub Workflow Update
- Updated `.github/workflows/build-apk.yml` to:
  - Trigger on tag pushes (v*)
  - Create GitHub releases automatically
  - Attach both debug and release APKs to releases
  - Generate release notes

**Files Modified:**
- `.github/workflows/build-apk.yml`

### 4. Version Bump
- Updated version from 4.0.1 (versionCode 9) to 4.1.0 (versionCode 10)

## How It Works

### Auto-Update Flow
1. App starts → `MainActivity.onCreate()` calls `checkForAppUpdates()`
2. `UpdateChecker` queries GitHub API: `https://api.github.com/repos/ShlomoV5/EtzHaimBrowser/releases/latest`
3. Compares current version with latest release version
4. If newer version exists with APK asset:
   - Shows Hebrew dialog: "עדכון זמין - גרסה חדשה X זמינה. האם ברצונך להוריד אותה עכשיו?"
   - User clicks "הורדה" → APK downloads to Downloads folder
   - After download completes → triggers installation intent
   - User clicks "אחר כך" → dialog dismisses, check happens again on next app start

### Release Process
1. Commit changes and push to repository
2. Create and push a version tag:
   ```bash
   git tag -a v4.1.0 -m "Version 4.1.0"
   git push origin v4.1.0
   ```
3. GitHub Actions automatically:
   - Builds debug and release APKs
   - Creates a GitHub release with the tag
   - Attaches APK files to the release
4. UpdateChecker will detect the new release and prompt users

### Whitelist Management
To update the whitelist:
1. Edit `whitelist.txt` in repository root
2. Commit and push changes
3. Build new APK
4. The new whitelist will be included in the app assets

## Testing Checklist

- [ ] Build debug APK successfully
- [ ] Install debug APK on device
- [ ] Build release APK successfully
- [ ] Install release APK on device (should work with debug signing)
- [ ] Verify whitelist loads correctly from assets
- [ ] Test auto-update detection (requires creating a test release)
- [ ] Test update download
- [ ] Test APK installation from update

## Notes

- Release APKs are now signed with debug key to avoid signing configuration complexity
- Auto-update requires internet connectivity
- Update check is non-blocking and happens in background thread
- FileProvider is properly configured for Android 7.0+ APK installation
- All UI strings for updates are in Hebrew as requested
