# Auto-Update Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         App Startup                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ MainActivity.onCreate │
                   └──────────────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ checkForAppUpdates() │
                   └──────────────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ UpdateChecker.check  │
                   │   ForUpdates()       │
                   └──────────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────┐
        │ HTTP GET to GitHub API:                  │
        │ /repos/ShlomoV5/EtzHaimBrowser/         │
        │         releases/latest                  │
        └─────────────────────────────────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
           ┌───────┤  Response OK?        ├───────┐
           │       └──────────────────────┘       │
           │ Yes                              No  │
           ▼                                      ▼
┌──────────────────────┐              ┌──────────────────────┐
│ Parse JSON Response  │              │ Log error & show     │
│ - Get version tag    │              │ Toast (update check  │
│ - Get APK URL        │              │ failed)              │
└──────────────────────┘              └──────────────────────┘
           │
           ▼
┌──────────────────────┐
│ Compare Versions     │
│ Latest vs Current    │
└──────────────────────┘
           │
           ▼
   ┌───────────────────┐
   │ Newer Version?    ├───────┐
   └───────────────────┘       │
           │ Yes           No  │
           ▼                   ▼
┌──────────────────────┐    ┌──────────────────┐
│ Show Hebrew Dialog:  │    │ Do Nothing       │
│                      │    │ (already latest) │
│ עדכון זמין          │    └──────────────────┘
│ גרסה חדשה X זמינה   │
│                      │
│ [הורדה]  [אחר כך]   │
└──────────────────────┘
    │           │
    │           └──────────────────────────┐
    │ הורדה                          אחר כך│
    ▼                                      ▼
┌──────────────────────┐           ┌──────────────────┐
│ downloadUpdate()     │           │ Dismiss Dialog   │
│                      │           │ (check again on  │
│ - Create DM Request  │           │  next startup)   │
│ - Register Receiver  │           └──────────────────┘
│ - Enqueue Download   │
└──────────────────────┘
           │
           ▼
┌──────────────────────┐
│ DownloadManager      │
│ Downloads APK to     │
│ Downloads folder     │
└──────────────────────┘
           │
           ▼
┌──────────────────────┐
│ BroadcastReceiver    │
│ onDownloadComplete   │
└──────────────────────┘
           │
           ▼
┌──────────────────────┐
│ installUpdate()      │
│                      │
│ - Get File URI       │
│ - Create Intent      │
│ - Use FileProvider   │
│   for Android N+     │
└──────────────────────┘
           │
           ▼
┌──────────────────────┐
│ Android Package      │
│ Installer Shows UI   │
│ for User to Install  │
└──────────────────────┘
           │
           ▼
┌──────────────────────┐
│ App Updated!         │
│ (User clicks Install)│
└──────────────────────┘
```

## GitHub Release Process

```
┌─────────────────────────────────────────────────────────────────┐
│                    Developer Actions                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ git tag -a v4.1.0    │
                   │ git push origin tag  │
                   └──────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   GitHub Actions Triggered                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ Setup JDK 17         │
                   └──────────────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ ./gradlew            │
                   │   assembleDebug      │
                   └──────────────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ ./gradlew            │
                   │   assembleRelease    │
                   └──────────────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ Create GitHub        │
                   │ Release with tag     │
                   └──────────────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ Attach APK files:    │
                   │ - app-release.apk    │
                   │ - app-debug.apk      │
                   └──────────────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ Generate Release     │
                   │ Notes from commits   │
                   └──────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              Release Published on GitHub                         │
│              Apps can now detect and download it                 │
└─────────────────────────────────────────────────────────────────┘
```

## Version Comparison Logic

```
Current Version: 4.1.0
Latest Version:  4.2.0

Split by '.' → [4, 1, 0] vs [4, 2, 0]

Compare part by part:
  4 == 4  → continue
  1 <  2  → Latest is newer! → Show Update Dialog
```

## Security Considerations

1. **Signed APK**: All APKs (debug and release) are signed
2. **FileProvider**: Secure file sharing for Android N+
3. **Permissions**: REQUEST_INSTALL_PACKAGES required
4. **HTTPS**: All API calls use HTTPS
5. **User Confirmation**: User must approve installation
