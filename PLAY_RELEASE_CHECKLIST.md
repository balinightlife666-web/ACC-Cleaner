# ACC Cleaner — Google Play Release Checklist

Target baseline: v1.1.0 / versionCode 7 / package `com.acc.cleaner`

## 1. Code and Android baseline

- [x] targetSdk 36 / compileSdk 36
- [x] Review-first deletion flow
- [x] No auto-select in general cleaner
- [x] No auto-delete
- [x] Two-step permanent-delete confirmation
- [x] Folder Scan fallback through Storage Access Framework
- [x] WhatsApp Business cleaner separated from general scan
- [x] In-app privacy/access explanation
- [x] Backup disabled
- [x] No INTERNET permission in pre-monetization baseline
- [ ] CI: lintRelease PASS
- [ ] CI: assembleRelease PASS
- [ ] CI: bundleRelease PASS
- [ ] Physical-device QA PASS

## 2. MANAGE_EXTERNAL_STORAGE declaration

Google Play declaration should describe the core use as **file management / file maintenance**.

Suggested factual description:

> ACC Cleaner is a file-maintenance utility. Its Scan Perangkat feature locates files and folders across shared storage so the user can review metadata, preview supported files, select files, and delete only files the user explicitly chooses. Without broad access, automatic device-wide file maintenance cannot operate across multiple shared-storage directories. The app also provides Folder Scan through Android's Storage Access Framework as a narrower alternative. ACC Cleaner does not upload files and does not auto-delete.

Before submission:

- [ ] Confirm store listing prominently describes device-wide file maintenance as a core feature
- [ ] Complete the All files access / Permissions Declaration form in Play Console
- [ ] Ensure reviewer can reach the permission flow without login or hidden steps
- [ ] Keep Folder Scan available as the privacy-friendly limited-scope alternative

## 3. Privacy and Data safety

- [x] Repository privacy policy drafted: `PRIVACY_POLICY.md`
- [x] Privacy/access text available inside the app
- [ ] Add real developer contact to privacy policy
- [ ] Publish privacy policy on an active HTTPS URL
- [ ] Add that URL in Play Console
- [ ] Complete Data safety form based on the exact production build
- [ ] If AdMob/analytics is added later, update privacy policy and Data safety before release

## 4. Signing and artifacts

- [ ] Create permanent upload keystore securely
- [ ] Enable Google Play App Signing
- [ ] Store upload-key material outside the repository
- [ ] Configure CI signing secrets only after the upload key is fixed
- [ ] Produce signed AAB
- [ ] Preserve signing/upload key and recovery information securely

Do not use the stable debug keystore for Google Play production.

## 5. Play Console app content

- [ ] App name, short description and full description
- [ ] App icon and feature graphic
- [ ] Phone screenshots
- [ ] Privacy policy URL
- [ ] Ads declaration (No for pre-monetization baseline; update when AdMob is added)
- [ ] Target audience
- [ ] Content rating questionnaire
- [ ] All files access declaration
- [ ] Data safety form
- [ ] App access instructions if Play review needs special steps

## 6. Testing and rollout

- [ ] Internal test install from Play-generated artifact
- [ ] Closed testing requirements completed if the developer account is subject to them
- [ ] Test Android 11, 13/14 and 16 behavior where available
- [ ] Verify denied-permission path remains usable through Folder Scan
- [ ] Verify WA Business folder-not-found state
- [ ] Verify 1,000+ and 10,000+ candidate handling does not freeze or auto-delete
- [ ] Verify permanent deletion warnings on real files
- [ ] Production rollout only after signed baseline and policy declarations are complete

## 7. Monetization — after baseline

AdMob is deliberately excluded from v1.1.0 pre-release hardening. After the signed baseline is stable:

- Add Google Mobile Ads SDK
- Add consent handling where required
- Update privacy policy
- Update Data safety
- Change Play Console Ads declaration to Yes
- Re-run release QA before publishing the monetized build
