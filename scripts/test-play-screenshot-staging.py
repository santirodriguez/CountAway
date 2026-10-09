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

    def write_test_image(self, name, width=1080, height=1920, color_type=2):
        channels = 3 if color_type == 2 else 4
        row = bytes(width * channels)
        assets.write_png(
            self.source / name,
            width, height, color_type,
            [row] * height,
        )

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


if __name__ == "__main__":
    unittest.main()
