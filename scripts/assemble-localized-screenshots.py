#!/usr/bin/env python3
"""Package original locale-specific Android CI screenshots for maintainer review.

Never synthesizes, crops, translates, or re-encodes captured pixels. The output
ZIP contains only the canonical Fastlane PNG paths for manual branch upload.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import tarfile
import zipfile

LOCALES = ("en-US", "es", "ca")
ROLES = (
    "1-home-dark.png",
    "2-editor-timed.png",
    "3-classic-widget-style.png",
    "4-about.png",
    "5-home-light.png",
    "6-home-widgets.png",
)
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def load_regular(tar: tarfile.TarFile, name: str) -> bytes:
    matches = [member for member in tar.getmembers() if member.name == name]
    if len(matches) != 1 or not matches[0].isfile():
        raise ValueError(f"Required original artifact missing or duplicated: {name}")
    stream = tar.extractfile(matches[0])
    if stream is None:
        raise ValueError(f"Cannot open original artifact: {name}")
    with stream:
        return stream.read()


def verify_png(data: bytes, origin: str) -> None:
    if len(data) < 45 or not data.startswith(PNG_SIGNATURE):
        raise ValueError(f"{origin}: invalid PNG signature")
    if data[12:16] != b"IHDR":
        raise ValueError(f"{origin}: missing IHDR")
    width, height, bit_depth, color_type, compression, filtering, interlace = (
        struct.unpack(">IIBBBBB", data[16:29])
    )
    if (width, height, bit_depth, color_type, compression, filtering, interlace) != (
        1080, 1920, 8, 2, 0, 0, 0,
    ):
        raise ValueError(f"{origin}: expected genuine 1080x1920 RGB 8-bit PNG")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--renders", required=True, type=Path)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_sha):
        parser.error("--source-sha must be an exact Git commit SHA")
    if not re.fullmatch(r"\d+\.\d+\.\d+", args.version):
        parser.error("--version must be a semantic release version")
    target = args.output_dir
    target.mkdir(parents=True, exist_ok=True)
    prefix = f"CountAway-v{args.version}-screenshots-en-es-ca-{args.source_sha[:12]}"
    archive = target / f"{prefix}.zip"
    manifest_path = target / f"{prefix}.json"
    sha_path = target / f"{prefix}.sha256"
    entries = []
    with tarfile.open(args.renders, "r:") as tar:
        for locale in LOCALES:
            source_dir = f"layout-evidence/release-review/{locale}"
            receipt = load_regular(tar, f"{source_dir}/REVIEW_ONLY.txt").decode("utf-8")
            if f"versionName={args.version}" not in receipt:
                raise ValueError(f"{locale}: screenshot version differs from requested release")
            launcher = load_regular(tar, f"{source_dir}/LAUNCHER_CAPTURE.txt").decode("utf-8")
            if (
                "native_configuration_verified=true" not in launcher
                or "status=CAPTURED_REVIEW_ONLY; native_pin_and_widget_config=true" not in launcher
                or f"locale={locale}" not in launcher
            ):
                raise ValueError(f"{locale}: real launcher widget not fully configured")
            for index, original_name in enumerate(ROLES, start=1):
                origin = f"{source_dir}/{original_name}"
                content = load_regular(tar, origin)
                verify_png(content, origin)
                canonical = (
                    f"fastlane/metadata/android/{locale}/images/"
                    f"phoneScreenshots/{index}.png"
                )
                entries.append({
                    "locale": locale, "role": original_name, "canonical_path": canonical,
                    "sha256": hashlib.sha256(content).hexdigest(), "bytes": len(content),
                    "content": content,
                })
    if len(entries) != 18 or len({e["canonical_path"] for e in entries}) != 18:
        raise ValueError("Expected 18 unique locale/role screenshots")

    tmp = archive.with_suffix(".tmp")
    try:
        with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_STORED) as output:
            for entry in entries:
                output.writestr(entry["canonical_path"], entry["content"])
        with zipfile.ZipFile(tmp, "r") as result:
            if result.namelist() != [entry["canonical_path"] for entry in entries]:
                raise ValueError("Screenshot package inventory changed")
            for entry in entries:
                if hashlib.sha256(result.read(entry["canonical_path"])).hexdigest() != entry["sha256"]:
                    raise ValueError(f"Screenshot ZIP bytes changed: {entry['canonical_path']}")
        tmp.replace(archive)
    finally:
        if tmp.exists():
            tmp.unlink()

    manifest = {
        "source_sha": args.source_sha, "version": args.version,
        "locales": list(LOCALES), "human_visual_approval_required": True,
        "source": "Original Android emulator full-screen screenshots; no pixel edits",
        "files": [{key: value for key, value in e.items() if key != "content"} for e in entries],
    }
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    sha_path.write_text(
        "\n".join(e["sha256"] + "  " + e["canonical_path"] for e in entries) + "\n",
        encoding="utf-8",
    )
    print(f"screenshot_bundle=verified version={args.version} source={args.source_sha} count=18")
    print(f"bundle={archive} manifest={manifest_path} sha256={sha_path}")


if __name__ == "__main__":
    main()
