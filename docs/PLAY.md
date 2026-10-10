# Google Play preparation and submission

CountAway is being prepared for its first Google Play submission while preserving the existing GitHub/Obtainium and F-Droid distribution model. Repository preparation is distinct from Play Console submission: Console mutations, real signing-key operations, testing tracks, and publication require separate maintainer authority.

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

## Signing identity — critical cross-store rule

CountAway already has a long-lived Android signing identity. Cross-store update compatibility depends on preserving that identity.

When Play App Signing is eventually configured:

1. do **not** casually accept a newly generated Play app-signing identity;
2. choose the Play App Signing path that lets the existing CountAway app-signing key remain the app identity across stores;
3. transfer only the required copy through Google's supported secure enrollment process;
4. create a separate Play **upload key** for future AAB uploads;
5. keep all signing keys, passwords, export files, and credentials out of this repository and out of TEMP-GPT.

Google documents this as the supported model when an app is distributed in multiple stores and the same signing key is required everywhere.

For a **new** Play app, Google currently defaults to a Google-generated app-signing key. CountAway must not accept that default blindly because it already exists outside Play. Before any Play-delivered build is treated as cross-store compatible, use the Play App Signing path that lets the maintainer provide a copy of CountAway's existing app-signing key, then verify the certificate shown by Play against the historical fingerprint. The separate upload key may and should differ.

Any future Play signing setup must verify the resulting Play app-signing certificate against the historical CountAway identity before production distribution.

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

Play assets are release inputs, not permission to fabricate app UI. Final phone screenshots must be genuine captures of the exact accepted v1.2.3 candidate. The existing 540×1200 source screenshots are retained for repository/F-Droid history but exceed Play's 2:1 long-side ratio gate and must not be uploaded as-is.

Required Play export checks:

- 512×512 32-bit PNG app icon, at most 1024 KB;
- 1024×500 feature graphic as JPEG or 24-bit PNG without alpha;
- at least two genuine phone screenshots, JPEG or RGB PNG, each side 320–3840 px and longest side no more than twice the shortest side;
- filenames/order/content tied to the exact candidate, including an About/version capture that does not show v1.2.2.

Do not stretch or repaint an old screenshot to satisfy dimensions. Capture a suitable final-candidate viewport and export it without changing the represented UI.

Store locale mapping comes from `app/src/main/res/xml/supported_languages.xml`. Current mapping is English -> Play `en-US`, Spanish -> Play `es-419`, and Catalan -> Play `ca`. F-Droid keeps its existing `en-US` / `es` / `ca` metadata directories; do not rename those directories for Play.

The existing Fastlane icon and feature graphic remain source/distribution assets and are not rewritten merely for Play. CI and the Release workflow create visually identical Play derivatives with `scripts/prepare-play-assets.py`: the 512×512 icon is encoded as 32-bit RGBA with opaque alpha, and the 1024×500 feature graphic is encoded as 24-bit RGB. The generator decodes both source and output and fails if any visible RGB pixel changes. Generated release derivatives are retained under `play-readiness/store-assets/`.

Phone screenshots have a single canonical location for F-Droid, the README, and Play: `fastlane/metadata/android/en-US/images/phoneScreenshots/1.png` through `6.png`. Replace the outdated sources with six genuine, maintainer-approved 1080x1920 9:16 captures of the exact accepted candidate, encoded as 24-bit RGB PNG without alpha. Keep the six filenames and order stable unless the README and release checks are deliberately updated together. Do not distort, pad, or repaint outdated screen content.

After those screenshots are accepted, stage them for the Play Console from the canonical Fastlane files, preserving byte-for-byte identity:

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

Console application creation/configuration, Play App Signing enrollment, upload-key generation, declarations, tracks, and rollout remain manual consequential gates.

If the developer account is a **personal account created after November 13, 2023**, production access currently requires a closed test with at least 12 testers continuously opted in for at least 14 days, followed by a production-access request in Play Console. Internal testing does not satisfy that production gate. Account type/date must be checked in Play Console; do not assume this requirement applies or does not apply.

## Future Play onboarding checklist

When the maintainer actually decides to publish:

1. re-audit the exact candidate against current Play policy and target API requirements;
2. verify the privacy-policy URL is globally accessible and accurate;
3. prepare the deferred Play store assets;
4. create/configure the Play Console app using the existing package ID;
5. enroll in Play App Signing while preserving the historical CountAway app-signing identity;
6. create a separate upload key and protect it outside the repository;
7. build/sign the upload AAB using that upload key;
8. verify Play reports the expected app-signing certificate;
9. complete Data Safety, content rating, audience/app-access declarations, and any account-specific testing requirement using current Play Console rules;
10. test Play-delivered APKs and cross-store update behavior before production rollout.

No step above is authorized merely by this document. Play Console mutations, signing-key operations, testing-track publication, and production release remain separate explicit actions.

## Offline language delivery

Language configuration splits are disabled so an EN-only device installation also contains Spanish and Catalan. Density/ABI delivery and generated locale configuration remain unchanged. Existing CI and exact-source RC validation inspect BundleConfig, build an EN-only device-specific APK set with ordinary debug test signing, and verify its packaged language resources. The API 33 RC installs that set and switches to Spanish/Catalan with networking disabled. This test package is not a public release asset or a Play upload artifact.


## Manual Play submission path

Use only the exact AAB produced from the accepted release-candidate SHA.

1. Confirm `app/build.gradle.kts` still contains the intended package, versionName and versionCode, and confirm versionCode 12 is unused in Play Console before the first upload.
2. Record the candidate source SHA and the readiness AAB SHA-256 retained by Actions.
3. Create or select the dedicated Play **upload key** outside this repository only after explicit maintainer authorization. The upload key is not the CountAway app-signing identity.
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
