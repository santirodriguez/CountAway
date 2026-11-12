#!/usr/bin/env python3
"""Regression checks for staging authentic-looking synthetic screenshot fixtures."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("prepare-play-assets.py")
SPEC = importlib.util.spec_from_file_location("prepare_play_assets", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load Play asset preparation module")
assets = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(assets)


class PlayScreenshotStagingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "fastlane/metadata/android/en-US/images/phoneScreenshots"
        self.source.mkdir(parents=True)
        self.output = self.root / "play-readiness/store-assets"
        self.cwd = Path.cwd()
        import os
        os.chdir(self.root)
        self.addCleanup(os.chdir, self.cwd)

    def write_test_image(self, name, width=1080, height=1920, color_type=2, locale="en-US"):
        channels = 3 if color_type == 2 else 4
        row = bytes(width * channels)
        location = self.root / "fastlane/metadata/android" / locale / "images/phoneScreenshots" / name
        assets.write_png(location, width, height, color_type, [row] * height)

    def populate(self):
        for number in range(1, 7):
            self.write_test_image(f"{number}.png")

    def test_stages_identical_files_with_manifest(self):
        self.populate()
        assets.stage_phone_screenshots(self.output)
        manifest = (self.output / "phoneScreenshots.sha256").read_text(encoding="utf-8")
        for number in range(1, 7):
            name = f"{number}.png"
            original = self.source / name
            staged = self.output / "phoneScreenshots" / name
            self.assertEqual(assets.sha256(original), assets.sha256(staged))
            self.assertIn(f"  phoneScreenshots/{name}\n", manifest)

    def test_rejects_missing_screenshot_before_staging(self):
        for number in range(1, 6):
            self.write_test_image(f"{number}.png")
        with self.assertRaises(SystemExit):
            assets.stage_phone_screenshots(self.output)
        self.assertFalse((self.output / "phoneScreenshots").exists())

    def test_rejects_incompatible_dimensions_before_staging(self):
        self.populate()
        self.write_test_image("4.png", 540, 1200)
        with self.assertRaises(SystemExit):
            assets.stage_phone_screenshots(self.output)
        self.assertFalse((self.output / "phoneScreenshots").exists())

    def test_rejects_alpha_before_staging(self):
        self.populate()
        self.write_test_image("6.png", color_type=6)
        with self.assertRaises(SystemExit):
            assets.stage_phone_screenshots(self.output)
        self.assertFalse((self.output / "phoneScreenshots").exists())

    def test_stages_all_three_locales_without_reencoding(self):
        self.populate()
        for locale in ("es", "ca"):
            for number in range(1, 7):
                self.write_test_image(f"{number}.png", locale=locale)
        assets.stage_phone_screenshots(self.output)
        manifest = (self.output / "phoneScreenshots.sha256").read_text(encoding="utf-8")
        self.assertEqual(18, len(manifest.splitlines()))
        for locale in ("es", "ca"):
            for number in range(1, 7):
                name = f"{number}.png"
                original = self.root / "fastlane/metadata/android" / locale / "images/phoneScreenshots" / name
                staged = self.output / "phoneScreenshots" / locale / name
                self.assertEqual(original.read_bytes(), staged.read_bytes())
                self.assertIn(f"  phoneScreenshots/{locale}/{name}", manifest)

    def test_rejects_one_localized_directory_without_the_other(self):
        self.populate()
        self.write_test_image("1.png", locale="es")
        with self.assertRaises(SystemExit):
            assets.stage_phone_screenshots(self.output)
        self.assertFalse((self.output / "phoneScreenshots").exists())

    def test_rejects_malformed_catalan_image_before_staging(self):
        self.populate()
        for locale in ("es", "ca"):
            for number in range(1, 7):
                self.write_test_image(f"{number}.png", locale=locale)
        self.write_test_image("4.png", width=540, locale="ca")
        with self.assertRaises(SystemExit):
            assets.stage_phone_screenshots(self.output)
        self.assertFalse((self.output / "phoneScreenshots").exists())


if __name__ == "__main__":
    unittest.main()
