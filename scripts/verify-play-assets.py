#!/usr/bin/env python3
"""Validate already-created Google Play PNG exports without modifying source captures."""
from pathlib import Path
import struct
import sys

PNG = b"\x89PNG\r\n\x1a\n"


def fail(path, message):
    raise SystemExit(f"{path}: {message}")


def png_info(path):
    data = path.read_bytes()
    if not data.startswith(PNG) or len(data) < 33:
        fail(path, "not a valid PNG")
    width, height, bit_depth, color_type = struct.unpack(">IIBB", data[16:26])
    return width, height, bit_depth, color_type


def validate(path, kind):
    width, height, bit_depth, color_type = png_info(path)

    if kind == "icon":
        if (width, height) != (512, 512):
            fail(path, f"Play icon must be 512x512, got {width}x{height}")
        if bit_depth != 8 or color_type != 6:
            fail(path, "Play icon must be a 32-bit RGBA PNG (8-bit truecolor with alpha)")
        if path.stat().st_size > 1024 * 1024:
            fail(path, f"Play icon exceeds 1024KB: {path.stat().st_size} bytes")

    elif kind == "feature":
        if (width, height) != (1024, 500):
            fail(path, f"feature graphic must be 1024x500, got {width}x{height}")
        if bit_depth != 8 or color_type != 2:
            fail(path, "PNG feature graphic must be 24-bit RGB with no alpha")

    elif kind == "screenshot":
        if not (320 <= width <= 3840 and 320 <= height <= 3840):
            fail(path, f"screenshot sides must be 320..3840px, got {width}x{height}")
        if max(width, height) > 2 * min(width, height):
            fail(path, f"screenshot longest side may be at most 2x the shortest, got {width}x{height}")
        if bit_depth != 8 or color_type != 2:
            fail(path, "PNG screenshots must be 24-bit RGB with no alpha")


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in {"icon", "feature", "screenshot"}:
        raise SystemExit("usage: verify-play-assets.py <icon|feature|screenshot> <png> [<png> ...]")
    for value in sys.argv[2:]:
        path = Path(value)
        validate(path, sys.argv[1])
        print(f"{sys.argv[1]}=ok path={path}")


if __name__ == "__main__":
    main()
