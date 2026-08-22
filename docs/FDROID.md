# F-Droid inclusion

This document tracks CountAway's upstream requirements for the official F-Droid repository.

F-Droid documentation:

- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Build_Metadata_Reference/
- https://f-droid.org/docs/Reproducible_Builds/
- https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/

## Upstream readiness

CountAway is intentionally straightforward for F-Droid:

- source is public;
- license is Apache-2.0;
- the Android project uses the Gradle wrapper and standard Maven repositories;
- runtime code has no proprietary SDK dependency;
- the manifest has no Internet permission;
- Fastlane metadata lives under `fastlane/metadata/android/`;
- English metadata uses F-Droid's `en-US` fallback locale, alongside `es` and `ca`;
- phone screenshots are stored as PNG files under the `en-US` Fastlane metadata;
- official releases use semantic `v<version>` tags;
- release APKs are signed with the same long-lived upstream key.

The F-Droid category is `Timer`.

Do not add an F-Droid badge to the README until CountAway is actually published in the official repository.

## Current submission

The official inclusion merge request is `fdroid/fdroiddata!46416`.

The first reproducible build remains CountAway 1.1.0 (`versionCode 2`) and must stay pinned to its original release source commit:

```text
d729772bef136372e43e8b1ac7824987784093f7
```

F-Droid CI has successfully rebuilt that source and verified it against the upstream signed 1.1.0 APK.

Do not move the 1.1.0 build block to a later source commit. Android Gradle Plugin release output includes source revision information, so changing the source commit causes the rebuilt APK to differ from the already-published 1.1.0 binary even when the code change is metadata-only.

## CountAway 1.1.1

Version 1.1.1 (`versionCode 3`) is a maintenance release for the corrected F-Droid metadata:

- English Fastlane metadata uses `en-US` instead of `en`;
- localized Fastlane titles are present for English, Spanish, and Catalan;
- phone screenshots use PNG files supported by F-Droid;
- no functional app behavior changes are included.

After the 1.1.1 release is published, add a separate 1.1.1 build block to the fdroiddata merge request using the exact final release commit. Keep the existing 1.1.0 build block unchanged.

## Reproducible upstream APKs

The fdroiddata metadata uses the upstream GitHub release binary and the CountAway signing certificate so F-Droid can verify reproducible builds:

```yaml
Binaries: 
  https://github.com/santirodriguez/CountAway/releases/download/v%v/CountAway-v%v.apk
AllowedAPKSigningKeys: dfbf9e4ba5b71bc4f7e70ee58f514410f90fb1aee9e9ebe522af68ad93cad42a
```

This lets users move between compatible upstream and F-Droid builds without changing Android signing identity.

The release workflow pins `apksigner` to Android Build Tools 34.0.0. Do not weaken verification or add scanner exceptions merely to make a build pass; investigate any reproducibility failure at its source.

## Release checklist

For 1.1.1 and later releases:

1. Keep `versionName`, `versionCode`, Fastlane changelog files, and `docs/releases/<version>.md` synchronized.
2. Get Android CI green on the exact proposed release commit.
3. Merge the release PR only after review and explicit approval.
4. Prepare the release from the exact final `main` commit and keep the semantic tag on that commit.
5. Preserve signing-key continuity and verify the release workflow output before publication.
6. Update fdroiddata with a new build block that points to the exact release commit; never rewrite an older reproducible build to a newer commit.
7. Keep automatic update detection restricted to stable semantic version tags.
8. Wait for fdroiddata CI and maintainer review before considering the F-Droid update complete.

Once the fdroiddata merge request is accepted and CountAway is actually published, the README can be updated separately with the official F-Droid badge/link.
