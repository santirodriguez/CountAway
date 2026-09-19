# Releasing CountAway

CountAway releases are built from the repository and signed with a long-lived Android signing key that must never be committed.

## One-time signing key setup

Generate the release key on a trusted local machine and keep an offline backup:

```bash
keytool -genkeypair -v \
  -keystore countaway-release.jks \
  -alias countaway \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Do not commit, email, or otherwise publish the keystore or its passwords. The repository already ignores `*.jks` and `*.keystore` files.

Store the following repository secrets in GitHub Actions:

- `COUNTAWAY_RELEASE_KEYSTORE_B64`: base64-encoded contents of the keystore
- `COUNTAWAY_RELEASE_STORE_PASSWORD`: keystore password
- `COUNTAWAY_RELEASE_KEY_ALIAS`: key alias, normally `countaway`
- `COUNTAWAY_RELEASE_KEY_PASSWORD`: key password

On GNU/Linux, the keystore value can be prepared with:

```bash
base64 -w 0 countaway-release.jks
```

On macOS:

```bash
base64 < countaway-release.jks | tr -d '\n'
```

## Release invariants

The release process is intentionally strict:

- `versionName` and `versionCode` come from `app/build.gradle.kts`; the workflow does not maintain a second version value.
- A manual workflow run explicitly selects either `release-candidate` or `prepare-draft-release`.
- Every manual release run must also provide the exact 40-character source commit SHA expected for that run. The workflow rejects a selected branch/ref that resolves to a different SHA.
- `prepare-draft-release` is allowed only from `main` and only for the current `main` head.
- Draft preparation creates or updates a draft GitHub Release configured with tag name `v<version>` and the exact validated target commit. GitHub does not create the actual `refs/tags/v<version>` Git ref until that draft is published.
- A missing `v<version>` Git ref while the matching GitHub Release is still a draft is expected. Do not create a duplicate tag manually or treat the missing ref as a preparation failure.
- The release version must exactly match `versionName` in the release source.
- A prepared release must have a matching `CHANGELOG.md` section, `docs/releases/<version>.md`, all three Fastlane changelogs named after the exact `versionCode` for `en-US`, `es`, and `ca`, and the expected screenshot set.
- The release APK must be signed by certificate SHA-256 `dfbf9e4ba5b71bc4f7e70ee58f514410f90fb1aee9e9ebe522af68ad93cad42a`.
- The release APK must preserve applicationId `com.santiagorodriguez.countaway`, minSdk 26, target/compile SDK 36, the expected permission surface, no app-declared runtime libraries, the expected AGP/Kotlin runtime baseline (`kotlin-stdlib:2.2.10` plus `org.jetbrains:annotations:13.0`), no native code, R8 mapping, and resource shrinking.
- App data must remain excluded from Android cloud backup and device-to-device migration by `allowBackup=false`, legacy `fullBackupContent` exclusions for Android 11 and lower, and `dataExtractionRules` exclusions for Android 12+; user-controlled JSON export/import remains the intentional portability path.
- Stable public release assets must be immutable. Once a release is public, rerunning release preparation must not replace an existing APK or checksum with different bytes.
- GitHub Actions dependencies are pinned to immutable commit SHAs.

Normal pull-request CI is useful but not equivalent to candidate validation: GitHub may test a PR merge ref. The release-candidate workflow binds its output to the explicitly supplied source SHA and records that identity in retained validation evidence.

### Stable public branding asset

`docs/assets/branding/countaway.webp` is a stable public compatibility path used by external catalogs such as Obtainium. Its visual contents may be refreshed, but the path, filename, and `.webp` extension must not change. It is intentionally allowed to duplicate the F-Droid or README icon when those consumers require different paths or formats.

## Release toolchain

The release workflow uses JDK 17 and explicitly installs Android Build Tools 34.0.0 for `zipalign`, `apksigner`, and `aapt`.

The Gradle/AGP build already produces aligned APK output. The workflow verifies that alignment instead of rewriting the APK, then signs the exact Gradle output with the pinned `apksigner`. This is intentional: F-Droid documents compatibility constraints around newer `apksigner` output and `apksigcopier` used for reproducible builds.

Do not replace the pinned signing toolchain with “latest” without re-validating the F-Droid reproducible-build path.

## Validation evidence

Android CI retains source identity, test results, lint/build reports, and the compiled instrumentation-test APK through the repository's GitHub Actions artifact-retention policy.

Release-candidate and draft-preparation runs retain their validation/reproducibility artifacts through that same repository policy. The currently observed repository policy expires Actions artifacts after 7 days, so review or download release evidence inside that window unless the repository setting is explicitly changed. These artifacts record, as applicable:

- exact source SHA and version;
- signed APK SHA-256 and byte size;
- unsigned APK SHA-256 for independent rebuild comparison;
- signing certificate and pinned `apksigner` version;
- package, min/target/compile SDKs, and exact manifest permission surface;
- release runtime dependency report, no app-declared runtime libraries, the expected Kotlin/annotations baseline, and absence of native libraries;
- presence of R8 mapping and resource shrinking;
- current APK-size deltas against the previous public 1.1.7 APK (377,945 bytes) and the first 1.1.8 RC (382,041 bytes);
- compiled instrumentation-test APK and ordinary test/lint reports;
- screenshot SHA-256s during stable preparation;
- F-Droid Gate A evidence when preparing a stable draft.

The first 1.1.8 RC was produced by Actions run 35129580248 and had APK SHA-256 `847368f26019971a04d62abc282bdb47c9f8408b120f2ced026d568f296c3625`.

Normal CI only compiles instrumentation tests. A `release-candidate` run executes them on API 26/33/36/37 emulators after smoke-launching the exact signed candidate on each API, and API 33 also verifies that the immutable public 1.1.7 APK can be upgraded in place to the signed candidate. Emulator jobs use an isolated current Android command-line-tools installation under the runner temporary directory and explicit AVD paths; API 37 uses the published `system-images;android-37.0;google_apis_ps16k;x86_64` image with 4 GB RAM. That package-level smoke does not create user data inside 1.1.7, so data-preservation upgrade acceptance, physical-device, launcher, Doze, TalkBack, and other human checks remain separate release gates.

## Independent rebuild comparison

Every release workflow run performs a second `assembleRelease` in a separate GitHub-hosted runner using the same exact source SHA, JDK, Gradle setup, and repository configuration. The SHA-256 of the independently rebuilt unsigned APK must match the primary job's unsigned APK SHA-256.

This is a repository-side reproducibility check, not a substitute for F-Droid's own reproducible-build verification. A mismatch blocks readiness and must be investigated. Do not change the signing certificate, add scanner/reproducibility exceptions, or publish a tag merely to force a result.

## Build a release candidate

Run the **CountAway Release** workflow manually from the branch and commit that should be tested, choose `release-candidate`, and enter the exact full commit SHA shown for that source.

There is no version input. The workflow derives `versionName` and `versionCode` directly from `app/build.gradle.kts` and rejects ambiguous or invalid values.

A release-candidate run:

1. rejects a source ref that does not resolve to the supplied exact SHA;
2. resolves the application version from Gradle;
3. validates package/SDK/build invariants, no app-declared runtime libraries, and the expected Kotlin/annotations runtime baseline;
4. runs tests and lint and compiles the instrumentation-test APK;
5. builds the R8/resource-shrunk release APK;
6. verifies APK alignment and signs with pinned Android Build Tools 34.0.0;
7. verifies signing certificate, package/version/SDK information, exact permissions, absence of native code, R8 mapping, and resource shrinking;
8. records APK checksum/size, size deltas, and validation reports;
9. independently rebuilds the same unsigned APK on another runner and compares SHA-256;
10. for `release-candidate`, boots API 26/33/36/37 emulators, smoke-launches the exact signed APK on each, performs a signed package-level 1.1.7 -> candidate upgrade smoke on API 33 using the immutable public 1.1.7 APK digest, then runs the compiled Android instrumentation suite from the same exact source SHA;
11. uploads the release candidate, validation evidence, reproducibility evidence, per-API acceptance evidence, and R8 mapping as workflow artifacts.

Public release files use this naming convention:

```text
CountAway-v<version>.apk
CountAway-v<version>.apk.sha256
```

The signing, verification, test/lint, dependency, size, reproducibility, and R8 artifacts are verification/debug evidence and do not need to be attached to the public release.

## F-Droid Gate A

CountAway metadata has been accepted into the official `fdroid/fdroiddata` repository. Acceptance of metadata or a green inclusion pipeline is not sufficient to release a new CountAway version.

Before `prepare-draft-release`, Gate A requires the existing CountAway package to be operational in the official public F-Droid repository:

1. the package must be returned by F-Droid's public package API/index;
2. at least one published CountAway APK must be downloadable from the official F-Droid repository;
3. that downloaded APK must verify with CountAway's historical signing certificate above;
4. a manual installation/smoke check of the published F-Droid APK must already have been completed by the maintainer.

The workflow independently verifies items 1–3 during draft preparation. A 404, API error, missing APK, empty package record, or signing mismatch fails closed. The workflow cannot prove the human installation step; record that check before starting draft preparation.

This gate applies even if a release is prepared manually outside the normal checklist. The app itself performs no network check and receives no Internet permission.

## Prepare a draft release

After the release candidate is approved, Gate A is satisfied, and the final release commit is on `main`:

1. confirm `versionName` and `versionCode` are final;
2. confirm `CHANGELOG.md`, `docs/releases/<version>.md`, all three Fastlane changelogs, screenshots, and README screenshot references are present and coherent;
3. confirm the approved release candidate was built from the exact intended source SHA and review its retained validation report, checksum, APK size/deltas, signing identity, permissions, runtime-dependency report, R8 mapping, and independent rebuild result;
4. confirm device/emulator acceptance, including upgrade preservation from the previous public release;
5. confirm Gate A, including the maintainer's F-Droid APK installation check;
6. merge release changes only after explicit approval;
7. run **CountAway Release** manually from the current `main` head, choose `prepare-draft-release`, and enter that exact `main` SHA.

The workflow revalidates the selected SHA, rebuilds the exact source, validates release metadata and screenshots, repeats the release-contract checks, verifies F-Droid Gate A from public evidence, and creates or updates a draft GitHub Release with tag name `v<version>` and `target_commitish` set to that exact commit.

At this stage the tag name is reserved by the draft release, but the Git ref does not yet exist. GitHub can expose the draft through an `untagged-...` URL, and resolving `v<version>` as a repository ref can return not found. Both are expected until publication.

A direct push of an existing valid `v<version>` tag remains supported, but the normal web release path is `prepare-draft-release` from `main` followed by explicit draft publication.

The draft release receives only:

```text
CountAway-v<version>.apk
CountAway-v<version>.apk.sha256
```

If a public release with the same stable tag already exists, the workflow refuses different asset bytes and leaves an identical public release untouched.

The draft must remain unpublished until its configured target commit, release notes, APK, checksum, signing identity, installation behavior, and retained validation evidence have been reviewed.

## Publish

Publishing is intentionally separate from preparation. Before publishing the GitHub Release:

- verify the approved candidate and prepared draft source identity are the intended exact commit;
- install and smoke-test the signed APK on a real Android device or emulator;
- verify an upgrade from the previous public CountAway release preserves countdowns and existing widgets;
- verify the SHA-256 checksum;
- verify the signing certificate SHA-256 matches the expected fingerprint above;
- review APK size against the previous public release and first 1.1.8 RC, and explain material growth;
- confirm final release notes, Fastlane metadata, screenshots, and public assets;
- confirm Gate A remains satisfied;
- confirm the release is still a draft and targets the intended commit.

Only then publish the prepared GitHub Release. In the normal web path, publication creates the stable `v<version>` Git ref on the draft's configured target commit. Immediately after publication, verify that the tag resolves to that exact commit and that the public APK and checksum URLs resolve before continuing to F-Droid.

Never move an existing stable tag after publication.

For post-publication F-Droid verification, continue with [`FDROID.md`](FDROID.md).
