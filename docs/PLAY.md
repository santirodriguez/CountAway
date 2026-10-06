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

Official references:
- Android App Bundles: https://developer.android.com/guide/app-bundle
- Play App Signing: https://support.google.com/googleplay/android-developer/answer/9842756
- Google Play user-data/privacy policy: https://support.google.com/googleplay/android-developer/answer/10144311
- target API requirements: https://developer.android.com/google/play/requirements/target-sdk

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

Until a dedicated Play upload key is configured, do not describe this readiness artifact as the final upload-signed Play bundle.

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

Any future Play signing setup must verify the resulting Play app-signing certificate against the historical CountAway identity before production distribution.

## Privacy policy

Canonical repository policy:

```text
PRIVACY.md
```

In-app policy URL:

```text
https://github.com/santirodriguez/CountAway/blob/main/PRIVACY.md
```

The policy is store-neutral and covers CountAway / Ya Estamos / Ja Queda Poc.

CountAway itself has no Internet permission. The About/Help link delegates the URL to an external browser through Android.

Every actual release-preparation path verifies that the public main-branch policy URL resolves and that the raw main-branch policy has the expected CountAway policy title. This includes the release-branch recovery path: recovery must not prepare a releasable artifact while the app's user-facing privacy URL would still be broken. Release-candidate mode does not require the public URL because it does not prepare or publish a release.

If the policy is later moved to a dedicated HTTPS page on `santiagorodriguez.com`, first inspect the actual hosting architecture, then update the in-app URL, this document, and the release gate atomically. Do not invent an undocumented Hostinger path.

## Data Safety expectation

Based on the current runtime, CountAway is designed so the developer does not collect or share user data:

- no account system;
- no analytics or advertising;
- no Internet permission;
- count-down/count-up events and settings remain local;
- reminders/widgets are local;
- backup export/import is explicitly user-directed through Android's document interface;
- event sharing is explicitly user-directed through Android's share sheet.

This is a readiness assessment, **not a permanently valid Play Console declaration**. Immediately before filling or updating Play Console Data Safety, audit the exact release candidate and current Google definitions again. Any new SDK, permission, network feature, account feature, cloud storage, telemetry, crash reporting, or external service can change the answer.

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

Repository-side PNG exports can be checked with `python3 scripts/verify-play-assets.py <icon|feature|screenshot> <file>...`. JPEG exports remain valid Play inputs and should be inspected with an image tool before submission.

Console application creation/configuration, Play App Signing enrollment, upload-key generation, declarations, tracks, and rollout remain manual consequential gates.

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
4. Sign the exact reviewed AAB with JAR-compatible AAB signing tooling. Do not use APK-only `apksigner` as the AAB signing procedure.
5. Recompute the signed AAB SHA-256; inspect its manifest/package/version with bundletool; verify the signing certificate corresponds to the intended upload key; retain only non-secret fingerprints/checksums as evidence.
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
- Health declaration: review the app's presets/content rather than blindly selecting "no health features".
- Donation link: confirm it is a voluntary tip with no digital reward or entitlement before billing-policy sign-off.
- Support contact: use a monitored maintainer-provided address; do not invent one in repository metadata.
- Countries, category/tags, identity/trader/account verification, and any personal-account testing requirement are Console/account decisions.

Repository state should describe Play as **prepared**, **submitted**, **in testing**, or **production** accurately. Do not add a Google Play badge before a public listing exists.
