# F-Droid inclusion

This document tracks the upstream work required to submit CountAway to the official F-Droid repository.

F-Droid documentation:

- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Build_Metadata_Reference/
- https://f-droid.org/docs/Reproducible_Builds/

## Upstream readiness

CountAway is intentionally straightforward for F-Droid:

- source is public;
- license is Apache-2.0;
- the Android project uses the Gradle wrapper and standard Maven repositories;
- runtime code has no proprietary SDK dependency;
- the manifest has no Internet permission;
- Fastlane-style descriptions and changelogs live under `fastlane/metadata/android/`;
- official releases use semantic `v<version>` tags;
- release APKs are signed with the same long-lived upstream key.

Do not add an F-Droid badge to the README until CountAway is actually published in the official repository.

## Before the first submission

For v1.1.0:

1. Get Android CI green on the exact proposed release commit.
2. Build a signed `1.1.0` release candidate with the **CountAway Release** workflow.
3. Install the candidate on a real device or emulator and smoke-test the app, widgets, reminders, backup/restore, and an upgrade from 1.0.0.
4. Merge the release PR only after review and explicit approval.
5. Build the final draft release from the exact `main` commit that will be tagged.
6. Record the full release commit SHA.
7. Record the lowercase SHA-256 fingerprint of the APK signing certificate from the workflow signing report.
8. Verify the APK checksum and publish the GitHub release/tag only after explicit approval.

The full commit SHA and signing fingerprint must be real values from the final release. Never pre-fill or guess them.

## fdroiddata merge request

The official submission belongs in F-Droid's `fdroiddata` repository, not in this repository. Create:

```text
metadata/com.santiagorodriguez.countaway.yml
```

A minimal starting point for the first build is:

```yaml
Categories:
  - Time
License: Apache-2.0
SourceCode: https://github.com/santirodriguez/CountAway
IssueTracker: https://github.com/santirodriguez/CountAway/issues
RepoType: git
Repo: https://github.com/santirodriguez/CountAway.git

Builds:
  - versionName: 1.1.0
    versionCode: 2
    commit: <FULL_V1.1.0_RELEASE_COMMIT_SHA>
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
CurrentVersion: 1.1.0
CurrentVersionCode: 2
```

Add upstream website/donation metadata only when the submitted URLs are the actual public URLs intended for the F-Droid listing.

The first build block should use the **full commit hash**, not a branch name and not a guessed tag target.

## Reproducible upstream APKs

The preferred result is for F-Droid to reproduce the upstream GitHub APK and publish that developer-signed binary. That lets users move between the GitHub/Obtainium and F-Droid distributions without reinstalling because Android sees the same signing identity.

After the final release exists, extend the fdroiddata metadata with the real upstream binary and certificate fingerprint:

```yaml
Binaries: https://github.com/santirodriguez/CountAway/releases/download/v%v/CountAway-v%v.apk
AllowedAPKSigningKeys: <LOWERCASE_SHA256_SIGNING_CERT_FINGERPRINT>
```

F-Droid will only use the upstream binary when its source rebuild verifies successfully. The release workflow pins `apksigner` to Android Build Tools 34.0.0 because F-Droid currently documents verification problems with `apksigner` 35 and newer.

If the reproducibility check fails, do not weaken verification or add scanner exceptions just to make the submission pass. Compare the F-Droid rebuild and upstream artifact, identify the actual source of nondeterminism, fix the upstream build, and retry.

If reproducible upstream APKs cannot be achieved for the first inclusion, the fallback is to omit `Binaries`/`AllowedAPKSigningKeys` and let F-Droid sign its own build. That can still be accepted, but users switching between the upstream APK and F-Droid APK would need to uninstall/reinstall because the signatures differ.

## Submission checks

Before opening the fdroiddata merge request:

- confirm v1.1.0 is public and the tag points to the exact submitted commit;
- confirm versionName `1.1.0` and versionCode `2` match the APK;
- confirm the Fastlane metadata is present upstream;
- confirm there are no proprietary dependencies or downloaded build-time binaries that violate inclusion rules;
- run or rely on fdroiddata CI for `fdroid lint`, scanner checks, and the source build;
- fix build or metadata issues at the source rather than adding broad exceptions;
- keep automatic update detection restricted to stable semantic version tags.

Once the fdroiddata merge request is accepted and the first app build is actually published, add the official F-Droid badge/link to the README.
