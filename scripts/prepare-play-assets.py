#!/usr/bin/env python3
"""Create Google Play-compatible static PNG derivatives without changing visible pixels."""
from __future__ import annotations

import argparse
import binascii
import hashlib
from pathlib import Path
import shutil
import struct
import zlib

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def fail(message: str) -> None:
    raise SystemExit(message)


def read_chunks(path: Path):
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        fail(f"{path}: not a PNG")
    pos = len(PNG_SIGNATURE)
    chunks = []
    while pos + 12 <= len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        crc = struct.unpack(">I", data[pos + 8 + length:pos + 12 + length])[0]
        expected = binascii.crc32(kind + payload) & 0xFFFFFFFF
        if crc != expected:
            fail(f"{path}: bad CRC in {kind.decode('ascii', errors='replace')}")
        chunks.append((kind, payload))
        pos += 12 + length
        if kind == b"IEND":
            break
    if not chunks or chunks[-1][0] != b"IEND":
        fail(f"{path}: missing IEND")
    return chunks


def paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa = abs(p - a)
    pb = abs(p - b)
    pc = abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def decode_png(path: Path):
    chunks = read_chunks(path)
    ihdr = next((payload for kind, payload in chunks if kind == b"IHDR"), None)
    if ihdr is None or len(ihdr) != 13:
        fail(f"{path}: invalid IHDR")
    width, height, bit_depth, color_type, compression, filter_method, interlace = struct.unpack(
        ">IIBBBBB", ihdr
    )
    if bit_depth != 8:
        fail(f"{path}: only 8-bit PNG input is supported")
    if compression != 0 or filter_method != 0 or interlace != 0:
        fail(f"{path}: unsupported PNG compression/filter/interlace mode")
    if color_type not in (2, 3, 6):
        fail(f"{path}: unsupported PNG color type {color_type}")

    palette = next((payload for kind, payload in chunks if kind == b"PLTE"), None)
    transparency = next((payload for kind, payload in chunks if kind == b"tRNS"), None)
    if color_type == 3:
        if palette is None or len(palette) % 3:
            fail(f"{path}: invalid palette")
        if transparency is not None:
            fail(f"{path}: paletted source with transparency requires an explicit visual decision")

    idat = b"".join(payload for kind, payload in chunks if kind == b"IDAT")
    raw = zlib.decompress(idat)
    source_bpp = {2: 3, 3: 1, 6: 4}[color_type]
    stride = width * source_bpp
    expected = height * (stride + 1)
    if len(raw) != expected:
        fail(f"{path}: decompressed byte count {len(raw)} != expected {expected}")

    rows = []
    offset = 0
    previous = bytearray(stride)
    for _ in range(height):
        filter_type = raw[offset]
        offset += 1
        encoded = raw[offset:offset + stride]
        offset += stride
        row = bytearray(stride)
        for index, value in enumerate(encoded):
            left = row[index - source_bpp] if index >= source_bpp else 0
            up = previous[index]
            upper_left = previous[index - source_bpp] if index >= source_bpp else 0
            if filter_type == 0:
                decoded = value
            elif filter_type == 1:
                decoded = (value + left) & 0xFF
            elif filter_type == 2:
                decoded = (value + up) & 0xFF
            elif filter_type == 3:
                decoded = (value + ((left + up) // 2)) & 0xFF
            elif filter_type == 4:
                decoded = (value + paeth(left, up, upper_left)) & 0xFF
            else:
                fail(f"{path}: unknown PNG filter {filter_type}")
            row[index] = decoded
        rows.append(bytes(row))
        previous = row

    return {
        "width": width,
        "height": height,
        "color_type": color_type,
        "palette": palette,
        "rows": rows,
    }


def rgb_rows(image):
    color_type = image["color_type"]
    palette = image["palette"]
    converted = []
    if color_type == 2:
        return list(image["rows"])
    if color_type == 6:
        for row in image["rows"]:
            out = bytearray()
            for index in range(0, len(row), 4):
                out.extend(row[index:index + 3])
            converted.append(bytes(out))
        return converted
    for row in image["rows"]:
        out = bytearray()
        for index in row:
            base = index * 3
            if palette is None or base + 3 > len(palette):
                fail("palette index outside PLTE")
            out.extend(palette[base:base + 3])
        converted.append(bytes(out))
    return converted


def opaque_rgba_rows(image):
    if image["color_type"] == 6:
        rows = list(image["rows"])
        if any(pixel != 255 for row in rows for pixel in row[3::4]):
            fail("icon input contains non-opaque alpha; refusing to change visible compositing")
        return rows
    rows = []
    for rgb in rgb_rows(image):
        out = bytearray()
        for index in range(0, len(rgb), 3):
            out.extend(rgb[index:index + 3])
            out.append(255)
        rows.append(bytes(out))
    return rows


def chunk(kind: bytes, payload: bytes) -> bytes:
    return (
        struct.pack(">I", len(payload))
        + kind
        + payload
        + struct.pack(">I", binascii.crc32(kind + payload) & 0xFFFFFFFF)
    )


def write_png(path: Path, width: int, height: int, color_type: int, rows) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = b"".join(b"\x00" + row for row in rows)
    ihdr = struct.pack(">IIBBBBB", width, height, 8, color_type, 0, 0, 0)
    encoded = (
        PNG_SIGNATURE
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw, level=9))
        + chunk(b"IEND", b"")
    )
    path.write_bytes(encoded)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare_icon(source: Path, destination: Path) -> None:
    image = decode_png(source)
    if (image["width"], image["height"]) != (512, 512):
        fail(f"{source}: icon source must already be 512x512")
    source_rgb = rgb_rows(image)
    rows = opaque_rgba_rows(image)
    write_png(destination, 512, 512, 6, rows)
    output = decode_png(destination)
    if output["color_type"] != 6 or rgb_rows(output) != source_rgb:
        fail(f"{destination}: generated icon changed visible pixels")
    print(
        f"play_icon=prepared source={source} output={destination} "
        f"sha256={sha256(destination)} visible_pixels=identical alpha=opaque"
    )


def prepare_feature(source: Path, destination: Path) -> None:
    image = decode_png(source)
    if (image["width"], image["height"]) != (1024, 500):
        fail(f"{source}: feature graphic source must already be 1024x500")
    source_rgb = rgb_rows(image)
    write_png(destination, 1024, 500, 2, source_rgb)
    output = decode_png(destination)
    if output["color_type"] != 2 or rgb_rows(output) != source_rgb:
        fail(f"{destination}: generated feature graphic changed visible pixels")
    print(
        f"play_feature=prepared source={source} output={destination} "
        f"sha256={sha256(destination)} visible_pixels=identical"
    )



def stage_phone_screenshots(output: Path) -> None:
    """Copy six accepted Fastlane screenshots into Play assets without altering bytes."""
    source = Path("fastlane/metadata/android/en-US/images/phoneScreenshots")
    if not source.is_dir():
        fail(f"{source}: missing canonical screenshot directory")

    expected = [f"{i}.png" for i in range(1, 7)]
    actual = sorted(
        path.name
        for path in source.iterdir()
        if path.is_file() and path.suffix.lower() in (".png", ".jpg", ".jpeg")
    )
    if actual != sorted(expected):
        fail(f"{source}: expected exactly {expected}, got {actual}")

    # Validate every source before writing any staged outputs.
    for name in expected:
        path = source / name
        image = decode_png(path)
        if (image["width"], image["height"]) != (1080, 1920):
            fail(
                f"{path}: CountAway phone screenshots must be authentic "
                f"1080x1920 captures, got {image['width']}x{image['height']}"
            )
        if image["color_type"] != 2:
            fail(f"{path}: screenshot must be a 24-bit RGB PNG without alpha")

    dest = output / "phoneScreenshots"
    if dest.exists():
        extras = sorted(path.name for path in dest.iterdir() if path.is_file() and path.name not in expected)
        if extras:
            fail(f"{dest}: unexpected existing staged files {extras}")
    dest.mkdir(parents=True, exist_ok=True)

    manifest = []
    for name in expected:
        original = source / name
        staged = dest / name
        shutil.copyfile(original, staged)
        digest = sha256(original)
        if sha256(staged) != digest:
            fail(f"{staged}: staged screenshot differs from canonical source")
        manifest.append(f"{digest}  phoneScreenshots/{name}")
        print(f"play_screenshot=staged source={original} output={staged} sha256={digest}")

    (output / "phoneScreenshots.sha256").write_text("\n".join(manifest) + "\n", encoding="utf-8")

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default="play-assets")
    parser.add_argument("--include-phone-screenshots", action="store_true")
    args = parser.parse_args()
    output = Path(args.output_dir)
    prepare_icon(
        Path("fastlane/metadata/android/en-US/images/icon.png"),
        output / "icon.png",
    )
    prepare_feature(
        Path("fastlane/metadata/android/en-US/images/featureGraphic.png"),
        output / "featureGraphic.png",
    )
    if args.include_phone_screenshots:
        stage_phone_screenshots(output)


if __name__ == "__main__":
    main()
