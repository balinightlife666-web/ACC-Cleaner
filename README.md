# ACC Cleaner

Native Android storage-cleaning utility focused on safe, user-controlled cleanup.

## v1.0.3 review-first scope

- Storage usage summary
- Deep Scan (optional Android “All files access” permission)
- Folder Scan fallback using Android Storage Access Framework
- Large file detection (>=100 MB)
- Old Download detection (>=30 days)
- Screenshot detection
- APK installer detection
- Temporary/log file detection
- Visible file review before delete
- File name, type, size, source, path, modified time and risk explanation
- Open/preview action where Android grants a readable URI
- Multi-select cleanup
- Double confirmation before permanent deletion
- No automatic selection
- No automatic deletion

## Build

The repository includes GitHub Actions workflows. Push to `main` or run **Android Build** manually. The downloadable artifact is `ACC-Cleaner-debug`.

Local build requirements:

- JDK 17
- Android SDK 36
- Android Build Tools 36.0.0
- Gradle 9.5.0

Run:

```bash
gradle :app:assembleDebug
```

## Storage policy

Android 11+ restricts broad filesystem access. ACC Cleaner supports two modes:

1. **Deep Scan** — requires the user to explicitly grant “All files access”.
2. **Folder Scan** — uses Android's system folder picker and works without broad storage access.

ACC Cleaner is review-first: scan results must be shown to the user before deletion controls are enabled. The app never auto-selects or auto-deletes files.
