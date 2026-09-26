# Google Play readiness

CountAway is **not currently published or configured for Google Play**. This document records the minimum compatibility contract needed to make a future Play launch straightforward without changing CountAway's existing GitHub/Obtainium or F-Droid distribution model.

## Current readiness baseline

- applicationId: `com.santiagorodriguez.countaway`
- current release line: 1.2.0 / versionCode 9
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

This artifact proves that the exact reviewed source can be packaged as an Android App Bundle. It is not attached to the public GitHub Release.

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
- countdowns/settings remain local;
- reminders/widgets are local;
- backup export/import is explicitly user-directed through Android's document interface;
- countdown sharing is explicitly user-directed through Android's share sheet.

This is a readiness assessment, **not a permanently valid Play Console declaration**. Immediately before filling or updating Play Console Data Safety, audit the exact release candidate and current Google definitions again. Any new SDK, permission, network feature, account feature, cloud storage, telemetry, crash reporting, or external service can change the answer.

## Versioning

Keep one global version sequence across every store.

- `versionCode` must never go backwards.
- `versionName` continues to describe the public semantic release.
- Do not create a separate Play-only versionCode line unless a concrete future migration requires it and cross-store update behavior is explicitly reviewed.

## Store assets and console setup — deferred

The following are intentionally **not** part of CountAway 1.2.0 readiness work:

- Play-specific screenshots;
- Play-specific store icon or listing artwork changes;
- Play Console application creation/configuration;
- Play App Signing enrollment;
- Play upload-key generation;
- Data Safety submission;
- content rating / target-audience declarations;
- testing tracks or production rollout;
- any Google Play badge or Play-specific user-facing copy.

Before an actual Play submission, prepare compliant store assets separately and verify current Play Console requirements.

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
