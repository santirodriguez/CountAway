#!/usr/bin/env python3
"""Reject screenshots reused byte-for-byte from CountAway's public 1.2.3 release.

The format and dimensions are checked separately by prepare-play-assets.py.
This check only proves that older files were replaced, not that a capture is
accurate; real-device capture and maintainer visual approval remain mandatory.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

SCREENSHOT_DIR = Path("fastlane/metadata/android/en-US/images/phoneScreenshots")
PUBLISHED_BASELINE_VERSION = "1.2.3"
# Git blob object IDs for the six screenshots at the immutable public v1.2.3 tag.
PUBLISHED_SCREENSHOT_BLOBS = {
    "1.png": "621de701835518c2c7d3804db4dc8dfa2a536bca",
    "2.png": "0a0788b6d58032e5b3709efdc800a22e68b8fb9d",
    "3.png": "aab7edb088dd52ac74eb5965e379a66c58016e90",
    "4.png": "4deacbc50083aafcbd1e311794aa11138bdfb368",
    "5.png": "4f1ffc593a4c61b5219e63b7844b976f40eedc24",
    "6.png": "fefb08da36f90d6330a1c291ccde43657436edea",
}


def git_blob_id(path: Path) -> str:
    """Calculate a Git SHA-1 blob ID without a Git checkout or subprocess."""
    data = path.read_bytes()
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()


def verify_fresh_screenshots(
    version: str,
    directory: Path = SCREENSHOT_DIR,
    baseline: dict[str, str] = PUBLISHED_SCREENSHOT_BLOBS,
) -> None:
    missing = [name for name in baseline if not (directory / name).is_file()]
    if missing:
        raise ValueError("Missing release screenshot(s): " + ", ".join(missing))

    # Historical revalidation of v1.2.3 must not be retroactively blocked.
    if version == PUBLISHED_BASELINE_VERSION:
        return

    stale = [name for name, old_id in baseline.items()
             if git_blob_id(directory / name) == old_id]
    if stale:
        raise ValueError(
            f"CountAway {version} still reuses public {PUBLISHED_BASELINE_VERSION} screenshot(s): "
            + ", ".join(stale)
            + ". Capture all six screens from the current release candidate, "
              "including its real About/version screen, and obtain visual approval."
        )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="Current release versionName")
    parser.add_argument("--directory", type=Path, default=SCREENSHOT_DIR)
    args = parser.parse_args()
    try:
        verify_fresh_screenshots(args.version, args.directory)
    except (ValueError, OSError) as error:
        parser.exit(1, f"release_screenshots=BLOCKED: {error}\n")
    print(f"release_screenshots=not_reused version={args.version} count=6 "
          "note=visual_approval_still_required")


if __name__ == "__main__":
    main()
