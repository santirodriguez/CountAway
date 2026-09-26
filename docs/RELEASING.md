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
- `prepare-draft-release` is allowed from `main` or the exact version-derived `release/<version>` branch, and only when the selected remote branch still resolves to the supplied exact SHA.
- Draft preparation creates or updates a draft GitHub Release configured with tag name `v<version>` and the exact validated target commit. GitHub does not create the actual `refs/tags/v<version>` Git ref until that draft is published.
- A missing `v<version>` Git ref while the matching GitHub Release is still a draft is expected. Do not create a duplicate tag manually or treat the missing ref as a preparation failure.
- The release version must exactly match `versionName` in the release source.
- A prepared release must have a matching `CHANGELOG.md` section, `docs/releases/<version>.md`, all three Fastlane changelogs named after the exact `versionCode` for `en-US`, `es`, and `ca`, and the expected screenshot set.
- The release APK must be signed by certificate SHA-256 `dfbf9e4ba5b71bc4f7e70ee58f514410f90fb1aee9e9ebe522af68ad93cad42a`.
- The release APK must preserve applicationId `com.santiagorodriguez.countaway`, minSdk 26, target/compile SDK 36, the expected permission surface, no app-declared runtime libraries, the expected AGP/Kotlin runtime baseline (`kotlin-stdlib:2.2.10` plus `org.jetbrains:annotations:13.0`), no native code, R8 mapping, and resource shrinking.
- App data must remain excluded from Android cloud backup and device-to-device migration by `allowBackup=false`, legacy `fullBackupContent` exclusions for Android 11 and lower, and `dataExtractionRules` exclusions for Android 12+; user-controlled JSON export/import remains the intentional portability path.
- Stable public release assets must be immutable. Once a release is public, rerunning release preparation must not replace an existing APK or checksum with different bytes.
- GitHub Actions dependencies are pinned to immutable commit SHAs.
- Hosted API 37 emulator execution is a non-gating diagnostic while the current Android 17 system images can abort inside SurfaceFlinger/mapper.ranchu before CountAway starts.
- A successful physical Android 17/API 37 acceptance run is mandatory before release readiness; hosted-emulator diagnostics cannot replace it.

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
- current APK-size delta against the previous public 1.1.8 APK (402,801 bytes);
- Play-readiness AAB SHA-256/byte size and basic bundle structure, retained only as internal Actions evidence;
- compiled instrumentation-test APK and ordinary test/lint reports;
- screenshot SHA-256s during stable preparation;
- F-Droid Gate A evidence when preparing a stable draft.

Normal CI only compiles instrumentation tests. A `release-candidate` run uses API 26/33/36 emulators as blocking automated acceptance: it smoke-launches the exact signed candidate and executes the instrumentation suite, while API 33 also verifies that the immutable public 1.1.8 APK can be upgraded in place to the signed candidate. Emulator provisioning/boot is delegated to `ReactiveCircus/android-emulator-runner` v2.38.0 pinned to immutable commit `a421e43855164a8197daf9d8d40fe71c6996bb0d`; this action has explicit Ubuntu-24.04 AVD handling, configurable boot timeouts, non-integer system-image API support, and `google_apis_ps16k` support.

API 37 remains an acceptance target, but its hosted-emulator job is explicitly diagnostic and non-gating. Current Android 17 `google_apis_ps16k` images can abort in `SurfaceFlinger`/`mapper.ranchu` on the host-advertised `ReadColorBufferDMA` path, tearing down framework services before CountAway starts; this reproduced with both canary/default-graphics and stable/`swangle_indirect` configurations. The diagnostic retains the best-known stable configuration—platform/system image 37.0, emulator 37.1.11 build 15917651, `swangle_indirect`, 4 GB RAM, and a 420-second boot timeout—and records its outcome without treating it as product acceptance. Reinstate it as a blocking automated gate only after an updated system image/emulator no longer exhibits the framework abort. Until then, a successful physical Android 17/API 37 run is mandatory before release readiness.

API 26/33/36 retain the stable channel, channel-default emulator, `swiftshader_indirect` graphics, and 300-second boot timeouts. The CountAway-specific signed APK, upgrade, instrumentation, alarm/logcat, and artifact checks remain repository-owned scripts around that emulator lifecycle. The package-level upgrade smoke does not create user data inside 1.1.8, so data-preservation upgrade acceptance, physical-device, launcher, Doze, TalkBack, and other human checks remain separate release gates.

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
10. for `release-candidate`, uses API 26/33/36 emulators as blocking signed-launch and instrumentation acceptance, performs the signed package-level 1.1.8 -> candidate upgrade smoke on API 33 using the immutable public 1.1.8 APK digest, and separately attempts the same API 37 path as a non-gating hosted-emulator diagnostic;
11. uploads the release candidate, validation evidence, reproducibility evidence, per-API acceptance evidence, and R8 mapping as workflow artifacts.

Public release files use this naming convention:

```text
CountAway-v<version>.apk
CountAway-v<version>.apk.sha256
```

The signing, verification, test/lint, dependency, size, reproducibility, and R8 artifacts are verification/debug evidence and do not need to be attached to the public release.

Play-readiness validation also builds `bundleRelease` and retains an Android App Bundle plus checksum/evidence as a GitHub Actions artifact. This AAB is **not** a public GitHub release asset and does not replace the signed APK used by GitHub/Obtainium or the existing F-Droid flow. Until a future Play upload key is deliberately configured, treat it as packaging/readiness evidence rather than an upload-ready Play artifact. The AAB gate uses Google's official bundletool, pinned by version and SHA-256, to validate the bundle and compare the package, versionName, and versionCode read structurally from the packaged base manifest against the expected CountAway release identity.

## F-Droid Gate A

CountAway metadata has been accepted into the official `fdroid/fdroiddata` repository. Acceptance of metadata or a green inclusion pipeline is not sufficient to release a new CountAway version.

Before `prepare-draft-release`, Gate A requires the existing CountAway package to be operational in the official public F-Droid repository:

1. the package must be returned by F-Droid's public package API/index;
2. at least one published CountAway APK must be downloadable from the official F-Droid repository;
3. that downloaded APK must verify with CountAway's historical signing certificate above;
4. a manual installation/smoke check of the published F-Droid APK must already have been completed by the maintainer.

The workflow independently verifies items 1–3 during draft preparation. A 404, API error, missing APK, empty package record, or signing mismatch fails closed. The workflow cannot prove the human installation step; record that check before starting draft preparation.

For CountAway 1.1.8, Gate A was completed on September 22, 2026. Keep these checks enabled during draft preparation as a regression guard.

This gate applies even if a release is prepared manually outside the normal checklist. The app itself performs no network check and receives no Internet permission.

Before any actual draft/tag release preparation, the public `main` privacy-policy URL must resolve and the raw policy must match the repository's CountAway policy title. This gate also applies to the documented release-branch recovery path; recovery is not allowed to ship a build with a broken in-app privacy URL.

## Prepare a draft release

After the exact release candidate is accepted, Gate A is satisfied, and the release branch is frozen:

1. confirm `versionName` and `versionCode` are final;
2. confirm `CHANGELOG.md`, `docs/releases/<version>.md`, all three Fastlane changelogs, screenshots, and README screenshot references are present and coherent;
3. confirm the accepted release candidate was built from the exact intended source SHA and review its retained validation report, checksum, APK size/delta, signing identity, permissions, runtime-dependency report, R8 mapping, and independent rebuild result;
4. confirm blocking emulator acceptance on API 26/33/36, review the API 37 emulator diagnostic, record successful physical Android 17/API 37 acceptance, and verify real upgrade preservation from the previous public release;
5. confirm Gate A, including the maintainer's F-Droid APK installation check;
6. merge the reviewed `release/<version>` pull request into `main` with a normal merge commit unless a different merge strategy has been explicitly reviewed and approved;
7. verify the merged `main` tree exactly matches the reviewed release-branch tree and wait for Android CI to pass on that exact `main` head;
8. keep the `release/<version>` branch present through draft/tag/publication verification;
9. run **CountAway Release** manually from the exact current `main` head, choose `prepare-draft-release`, and enter that exact `main` SHA.

The normal release path is therefore:

```text
accepted release candidate
-> reviewed release branch
-> merge to main
-> verify identical tree + green main CI
-> prepare draft from exact main head
-> review draft
-> publish
```

The workflow still supports the exact version-derived `release/<version>` branch as a recovery/exception path, but routine releases should prepare the draft from the reviewed merged `main` head. The workflow re-fetches the selected remote branch, rejects stale or mismatched SHAs, validates release metadata/screenshots and F-Droid Gate A, and creates or updates a draft GitHub Release with tag name `v<version>` and `target_commitish` set to that exact commit.

At this stage the tag name is reserved by the draft release, but the Git ref does not yet exist. GitHub can expose the draft through an `untagged-...` URL, and resolving `v<version>` as a repository ref can return not found. Both are expected until publication.

A direct push of an existing valid `v<version>` tag remains supported. Its commit must either be in current `main` history or exactly match the current version-derived release-branch head. Do not delete the release branch before tag/publication verification.

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
- install and smoke-test the signed APK on a physical Android 17/API 37 device;
- verify an upgrade from the previous public CountAway release preserves countdowns and existing widgets;
- verify the SHA-256 checksum;
- verify the signing certificate SHA-256 matches the expected fingerprint above;
- review APK size against the previous public 1.1.8 release and explain material growth;
- confirm final release notes, Fastlane metadata, screenshots, and public assets;
- confirm Gate A remains satisfied;
- confirm the release is still a draft and targets the intended commit.

Only then publish the prepared GitHub Release. In the normal web path, publication creates the stable `v<version>` Git ref on the draft's configured target commit. Immediately after publication, verify that the tag resolves to that exact commit, that the tagged commit is in `main` history, and that the public APK and checksum URLs resolve before continuing to F-Droid.

Never move an existing stable tag after publication. Keep the release branch until release/tag/distribution verification is complete.

For post-publication F-Droid verification, continue with [`FDROID.md`](FDROID.md).
