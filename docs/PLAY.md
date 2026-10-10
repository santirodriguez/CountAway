# Google Play preparation and submission

CountAway is preparing its first Google Play closed-test submission while preserving the existing GitHub/Obtainium and F-Droid distribution model. Repository preparation is distinct from Play Console submission: upload-key reset, track uploads, and publication remain separate maintainer-controlled operations.

## Current readiness baseline

- applicationId: `com.santiagorodriguez.countaway`
- target release: 1.2.3 / versionCode 12 (from `app/build.gradle.kts`)
- minSdk: 26
- targetSdk / compileSdk: 36
- no Internet permission
- no app-declared runtime SDK dependencies
- no native libraries
- existing GitHub/F-Droid signing certificate SHA-256:
  `dfbf9e4ba5b71bc4f7e70ee58f514410f90fb1aee9e9ebe522af68ad93cad42a`

These are repository invariants unless a future release explicitly reviews and approves a change.

As of 2026-10-06, Google Play requires new phone/tablet apps and app updates submitted after August 31, 2026 to target Android 16 / API 36 or later. CountAway's current `targetSdk = 36` satisfies that submission requirement.

Policy review date: **2026-10-06**. Recheck these sources immediately before any actual Console submission because Play requirements can change.

Official references:
- Android App Bundles: https://developer.android.com/guide/app-bundle
- target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Play App Signing: https://support.google.com/googleplay/android-developer/answer/9842756
- preview/store assets: https://support.google.com/googleplay/android-developer/answer/9866151
- Data Safety: https://support.google.com/googleplay/android-developer/answer/10787469
- privacy policy requirements: https://support.google.com/googleplay/android-developer/answer/18258653
- Health apps declaration: https://support.google.com/googleplay/android-developer/answer/14738291
- personal-account testing requirements: https://support.google.com/googleplay/android-developer/answer/14151465
- payments/tips policy: https://support.google.com/googleplay/android-developer/answer/10281818

## Distribution architecture

Keep one Android application identity and one source tree:

```text
same source / same applicationId / global versionCode sequence
├── GitHub / Obtainium -> signed APK
├── F-Droid           -> existing reproducible-build flow
└── Google Play       -> future signed AAB upload
```

Do not add a Play-only product flavor, Google SDK, Play Services dependency, analytics SDK, billing SDK, advertising SDK, or new runtime permission merely to make the project "Play-ready". Add any future store-specific runtime dependency only when an actual product requirement justifies it and F-Droid compatibility has been separately reviewed.

## AAB readiness gate

Normal CI and the CountAway Release workflow run `bundleRelease` in addition to the existing APK build.

The release workflow retains an internal Actions artifact named like:

```text
CountAway-v<version>-play-readiness-<source-sha>
```

It contains:
- `CountAway-v<version>-play-readiness.aab`
- its SHA-256 checksum;
- source/version/package/size evidence.

This artifact proves that the exact reviewed source can be packaged as an Android App Bundle. CI validates the bundle with Google's official bundletool, pinned by version and SHA-256, and compares the package, versionName, and versionCode read structurally from the packaged base manifest against the expected release identity. It is not attached to the public GitHub Release.

A readiness artifact is not upload-ready until the exact reviewed AAB is signed with the dedicated Play upload key and the signed bundle passes the checks below.

Public GitHub releases remain restricted to:

```text
CountAway-v<version>.apk
CountAway-v<version>.apk.sha256
```

F-Droid continues to use the existing source/reproducibility/signing contract. The AAB gate must not alter F-Droid metadata, introduce proprietary runtime dependencies, or replace the upstream APK used by GitHub/Obtainium.

## Signing identities — cross-store contract

CountAway is enrolled in **Play App Signing** with the same long-lived **app-signing** identity used by its existing GitHub/F-Droid distribution. Its app-signing certificate SHA-256 must remain:

```text
dfbf9e4ba5b71bc4f7e70ee58f514410f90fb1aee9e9ebe522af68ad93cad42a
```

Keep these separate:

| Role | Typical local file | Internal alias | Used for |
| --- | --- | --- | --- |
| Original app-signing key | `countaway-release.jks` | `countaway` | Direct GitHub APK signing and the historical identity Google uses to sign Play-delivered APKs. |
| Play **upload** key | `countaway-play-upload.jks` | `countaway-play-upload` | Signing AAB uploads for Google to authenticate. Not the certificate used to sign installed Play APKs. |
| Public Play upload certificate | `countaway-play-upload-certificate.pem` | N/A | Registering/verifying the upload key with Play. Does not contain private signing material. |
| Historical PEPK transfer | `encryptedPrivateKey` plus `certificate.pem` | N/A | One-time transfer of the *original app-signing* identity to Play, not the private upload key or a new upload credential. |

Play Console displays the currently accepted **Upload key certificate** separately from **App signing key**. Compare the active upload-certificate fingerprint to the certificate exported from the selected local upload JKS **before every Play signing operation**. A pending upload-key reset is not equivalent to activation: wait for Google's specified effective time and verify the new public certificate in Console before uploading. A lost upload JKS requires a replacement upload key, independently verified backup and a **Request upload key reset**; never use **Change app signing key** for this problem.

**Do not regenerate the original app-signing keystore, replace its Java alias or alter the four historical `COUNTAWAY_RELEASE_*` GitHub Actions secrets to solve an upload-key issue.** Those secrets sign the GitHub APK. Never commit or publish private JKS files, PEPK material or passwords. Preserve independent, restorable backups of both JKS identities and keep the required passwords separately protected.

The same applicationId, original app-signing certificate and an increasing global versionCode preserve cross-store update compatibility; they do **not** establish source-code provenance. Play-distributed APKs are generated from submitted bundles and need not be byte-identical to the GitHub APK. For each authorized upload retain the source commit, unsigned AAB hash, signed AAB hash, active upload-certificate fingerprint, package/version receipt and Play track decision. Play does not require a corresponding GitHub release.

## Privacy policy

Canonical repository policy:

```text
PRIVACY.md
```

Current in-app and Play privacy-policy URL:

```text
https://countaway.cajapersonal.org/privacy/
```

CountAway project site:

```text
https://countaway.cajapersonal.org/
```

The hosted policy is static, public HTML and is store-neutral across CountAway / Ya Estamos / Ja Queda Poc. It was deployed and manually accepted on 2026-10-06 on the CountAway Hostinger project surface. The canonical policy source text remains `PRIVACY.md` in this repository.

Google Play requires the submitted privacy-policy URL to be active, publicly accessible, non-geofenced, non-PDF, and non-editable. Treat the hosted URL as a release gate: if the hosting surface moves or its accessibility changes, update the hosted page, in-app resource, contract tests, release workflow, and this document coherently before release preparation.

CountAway itself has no Internet permission. The About/Help links delegate the project/privacy URLs to an external browser through Android.

Every actual release-preparation path verifies that the hosted project and privacy URLs resolve, that the hosted privacy page exposes the CountAway policy title and all three policy languages, and that the repository policy still contains the canonical multilingual headings. The release-branch recovery path is held to the same gate. Release-candidate mode does not require the public URLs because it does not prepare or publish a release.

## Data Safety expectation

Based on the current runtime, CountAway is designed so the developer does not collect or share user data:

- no account system;
- no analytics or advertising;
- no Internet permission;
- count-down/count-up events and settings remain local;
- reminders/widgets are local;
- backup export/import is explicitly user-directed through Android's document interface;
- event sharing is explicitly user-directed through Android's share sheet.

This is a readiness assessment, **not a permanently valid Play Console declaration**. Google defines collection as transmitting user data off the device; local-only processing is outside that collection definition. Even when an app collects or shares no user data, a Data Safety form and privacy-policy link are still required once the app is active on closed/open/production tracks; an app exclusively on internal testing is currently exempt from the Data Safety form.

Immediately before filling or updating Play Console Data Safety, audit the exact release candidate and current Google definitions again. Any new SDK, permission, network feature, account feature, cloud storage, telemetry, crash reporting, or external service can change the answer.

## Versioning

Keep one global version sequence across every store.

- `versionCode` must never go backwards.
- `versionName` continues to describe the public semantic release.
- Do not create a separate Play-only versionCode line unless a concrete future migration requires it and cross-store update behavior is explicitly reviewed.

## Store assets and console setup

Play assets are release inputs, not permission to fabricate app UI. The six accepted v1.2.3 phone screenshots are genuine 1080×1920 RGB PNG captures in Fastlane, replacing earlier 540×1200 sources that did not meet Play's 2:1 long-side ratio limit.

Required Play export checks:

- 512×512 32-bit PNG app icon, at most 1024 KB;
- 1024×500 feature graphic as JPEG or 24-bit PNG without alpha;
- at least two genuine phone screenshots, JPEG or RGB PNG, each side 320–3840 px and longest side no more than twice the shortest side;
- filenames/order/content tied to the exact candidate, including an About/version capture showing v1.2.3.

Do not stretch or repaint an old screenshot to satisfy dimensions. Capture a suitable final-candidate viewport and export it without changing the represented UI.

Store locale mapping comes from `app/src/main/res/xml/supported_languages.xml`. Current mapping is English -> Play `en-US`, Spanish -> Play `es-419`, and Catalan -> Play `ca`. F-Droid keeps its existing `en-US` / `es` / `ca` metadata directories; do not rename those directories for Play.

The existing Fastlane icon and feature graphic remain source/distribution assets and are not rewritten merely for Play. CI and the Release workflow create visually identical Play derivatives with `scripts/prepare-play-assets.py`: the 512×512 icon is encoded as 32-bit RGBA with opaque alpha, and the 1024×500 feature graphic is encoded as 24-bit RGB. The generator decodes both source and output and fails if any visible RGB pixel changes. Generated release derivatives are retained under `play-readiness/store-assets/`.

Phone screenshots have a single canonical location for F-Droid, the README, and Play: `fastlane/metadata/android/en-US/images/phoneScreenshots/1.png` through `6.png`. The six accepted files are genuine, maintainer-approved 1080×1920 (9:16) captures of the reviewed candidate, encoded as 24-bit RGB PNG without alpha. Keep the six filenames and order stable unless the README and release checks are deliberately updated together. Do not distort, pad, or repaint screenshot content.

To stage the accepted screenshots for the Play Console, copy them from the canonical Fastlane files while preserving byte-for-byte identity:

```bash
python3 scripts/prepare-play-assets.py \
  --output-dir play-readiness/store-assets \
  --include-phone-screenshots
python3 scripts/verify-play-assets.py screenshot \
  play-readiness/store-assets/phoneScreenshots/*.png
(cd play-readiness/store-assets && sha256sum -c phoneScreenshots.sha256)
```

Android CI and the release workflow stage all six accepted screenshots, validate their Play-compatible format, and verify that staged file hashes match the canonical sources. The canonical files are never modified by this helper. Google Play Console still requires the maintainer to upload them; it does not automatically read Fastlane screenshots from GitHub.

PNG exports can be checked with `python3 scripts/verify-play-assets.py <icon|feature|screenshot> <file>...`. JPEG screenshot exports remain valid Play inputs and should be inspected with an image tool before submission. Final screenshots are not generated or altered by this pipeline; they must still be genuine exact-candidate captures.

The Play Console app and historical Play App Signing enrollment already exist. Remaining Console declarations, upload-key reset/activation, testing-track submissions and rollout are separate manual consequential gates.

If the developer account is a **personal account created after November 13, 2023**, production access currently requires a closed test with at least 12 testers continuously opted in for at least 14 days, followed by a production-access request in Play Console. Internal testing does not satisfy that production gate. Account type/date must be checked in Play Console; do not assume this requirement applies or does not apply.

## Initial Play closed-test checklist

Before submitting to a permitted Play testing track:

1. re-audit the exact candidate against current Play policy and target API requirements;
2. verify the privacy-policy URL is publicly accessible and accurate;
3. stage and inspect the accepted Play store assets;
4. verify the existing Play Console app has the correct package ID;
5. confirm the already enrolled Play **app-signing certificate** still matches the original CountAway fingerprint;
6. confirm the current Play **upload certificate** is active and matches the locally preserved upload JKS;
7. sign the reviewed AAB using the dedicated upload key, keeping the original unsigned artifact unchanged;
8. complete Data Safety, content rating, audience/app-access declarations, and account-specific testing requirements;
9. submit only to the separately authorized track and test the Play-delivered APK and cross-store updates;
10. do not enter production before both the required testing gate and separate maintainer authorization are satisfied.

This checklist does not itself authorize Console mutation, track publication or production release.

## Offline language delivery

Language configuration splits are disabled so an EN-only device installation also contains Spanish and Catalan. Density/ABI delivery and generated locale configuration remain unchanged. Existing CI and exact-source RC validation inspect BundleConfig, build an EN-only device-specific APK set with ordinary debug test signing, and verify its packaged language resources. The API 33 RC installs that set and switches to Spanish/Catalan with networking disabled. This test package is not a public release asset or a Play upload artifact.


## Manual Play submission path

Use only the exact AAB produced from the accepted release-candidate SHA.

1. Confirm `app/build.gradle.kts` still contains the intended package, versionName and versionCode, and confirm versionCode 12 is unused in Play Console before the first upload.
2. Record the candidate source SHA and the readiness AAB SHA-256 retained by Actions.
3. Select the preserved dedicated Play **upload JKS** and compare its public-certificate SHA-256 to the **active Upload key certificate** in Play Console. The upload key is not CountAway's app-signing identity. If access to the registered upload key was lost, first make and verify an independent backup of a new upload key, request an **upload-key reset** using only its public PEM, then wait for Google's confirmed activation before signing/uploading.
4. Sign the exact reviewed AAB with the repository helper, which uses JAR-compatible signing, verifies that the keystore alias matches the supplied public upload certificate, keeps passwords off the command line, leaves the input AAB unchanged, and can revalidate package/version with bundletool:

   ```bash
   bash scripts/sign-play-bundle.sh \
     CountAway-v<version>-play-readiness.aab \
     CountAway-v<version>-play-upload.aab \
     /secure/path/countaway-play-upload.jks \
     countaway-play-upload \
     /secure/path/countaway-play-upload-certificate.pem \
     /path/to/bundletool.jar \
     com.santiagorodriguez.countaway \
     <version> \
     <versionCode> \
     CountAway-v<version>-play-upload.receipt.txt
   ```

   The helper prompts locally for passwords when the `PLAY_UPLOAD_STORE_PASSWORD` / `PLAY_UPLOAD_KEY_PASSWORD` environment variables are absent. Do not use APK-only `apksigner` for the AAB.
5. Review the helper receipt and retain the signed AAB SHA-256 plus the public upload-certificate fingerprint as non-secret evidence. The input readiness AAB checksum must remain unchanged, and bundletool must still report the expected package, versionName and versionCode.
6. Upload that exact signed AAB to the authorized track. Do not rebuild between tracks; promote the same reviewed artifact.
7. Before production, verify the **Play app-signing certificate** is the historical CountAway identity:
   `dfbf9e4ba5b71bc4f7e70ee58f514410f90fb1aee9e9ebe522af68ad93cad42a`.
   The upload certificate may differ. If Play cannot preserve the app-signing identity required for cross-store updates, stop.
8. Install a Play-delivered build and verify package/version/signing identity plus update compatibility with the existing CountAway installation before production rollout.

Never commit a keystore, key, password, export bundle, Play credential, or secret. A readiness AAB is not upload-ready until the explicit signing procedure above has been completed.

## Play declarations checklist

Revalidate every declaration against the exact candidate and current Play wording immediately before submission.

- Data Safety: current design is no developer collection/sharing, subject to final candidate audit.
- Ads: none.
- App access: no account/login gate.
- Advertising ID and sensitive permissions: inspect the merged manifest rather than assuming.
- Content rating and target audience: complete from actual app content.
- Health declaration: the form is required even for apps with no health features. CountAway has generic presets such as Smoke-free and Training, so review the exact candidate honestly; if they remain simple date/count presets rather than health tracking/advice, the expected declaration is "My app doesn't provide any health features."
- Donation link: current Play guidance treats a direct tip/contribution to the creator as outside mandatory Play Billing only when 100% goes to the creator and the payment grants no digital content, service, badge, feature, entitlement, or other in-app benefit. Verify the live donation flow still meets that condition before submission.
- Support contact: use a monitored maintainer-provided address; do not invent one in repository metadata.
- Countries, category/tags, identity/trader/account verification, and any personal-account testing requirement are Console/account decisions.

Repository state should describe Play as **prepared**, **submitted**, **in testing**, or **production** accurately. Do not add a Google Play badge before a public listing exists.
