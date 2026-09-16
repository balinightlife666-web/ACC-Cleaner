# ACC Cleaner

Native Android file-maintenance utility focused on safe, user-controlled cleanup.

## v1.1.0 Play release candidate scope

- Target SDK / compile SDK 36
- File Cleaner with device-wide review mode and Folder Scan fallback
- WhatsApp Business Cleaner separated from general results
- Large file, old Download, screenshot, APK installer and temporary/log detection
- Visible file review before deletion
- File name, type, size, source, path, modified time and risk explanation
- Open/preview action where Android grants a readable URI
- Multi-select cleanup
- Double confirmation before permanent deletion
- No automatic selection
- No automatic deletion
- In-app privacy and storage-access explanation
- No INTERNET permission, ad SDK or analytics SDK in the v1.1.0 baseline

## Storage access model

Android 11+ restricts broad filesystem access. ACC Cleaner supports:

1. **Scan Perangkat** — may request Android `MANAGE_EXTERNAL_STORAGE` because the core file-maintenance feature needs to find and manage files/folders across shared storage.
2. **Folder Scan** — uses Android's Storage Access Framework and works without broad filesystem access.
3. **WhatsApp Business Cleaner** — uses broad access to automatically inspect the supported WhatsApp Business storage location.

The broad permission is never used for uploading files or for a third party. Users still choose files and confirm deletion.

## Build

Local requirements:

- JDK 17
- Android SDK 36
- Android Build Tools 36.0.0
- Gradle 9.5.0

Debug build:

```bash
gradle :app:assembleDebug
```

Play-candidate validation:

```bash
gradle clean lintRelease assembleRelease bundleRelease
```

The Play-candidate AAB produced before signing is **not upload-ready**. A permanent upload key must be configured before Google Play submission.

## Google Play preparation

See `PLAY_RELEASE_CHECKLIST.md` and `PRIVACY_POLICY.md`.
