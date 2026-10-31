#!/usr/bin/env python3
"""Tests for the README release screenshot reference contract."""

import importlib.util
from pathlib import Path
import unittest

script = Path(__file__).with_name("verify-readme-screenshots.py")
spec = importlib.util.spec_from_file_location("readme_screenshots", script)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


def markup(overrides=None):
    overrides = overrides or {}
    return "\n".join(
        overrides.get(index, f'<img src="{path}" alt="Screenshot {index} updated" />')
        for index, path in enumerate(checker.SCREENSHOT_PATHS, 1)
    )


class ReadmeScreenshotTests(unittest.TestCase):
    def test_all_six_nonempty_alt_texts_pass(self):
        checker.verify_readme_screenshots(markup())

    def test_human_facing_copy_can_change(self):
        checker.verify_readme_screenshots(markup({
            1: '<img width="46%" alt="A completely rewritten description" '
               'src="' + checker.SCREENSHOT_PATHS[0] + '">'
        }))

    def test_missing_screenshot_fails(self):
        with self.assertRaisesRegex(ValueError, r"6\.png"):
            checker.verify_readme_screenshots(markup({6: ""}))

    def test_missing_alt_fails(self):
        with self.assertRaisesRegex(ValueError, r"2\.png"):
            checker.verify_readme_screenshots(markup({
                2: '<img src="' + checker.SCREENSHOT_PATHS[1] + '">'
            }))

    def test_blank_alt_fails(self):
        with self.assertRaisesRegex(ValueError, r"4\.png"):
            checker.verify_readme_screenshots(markup({
                4: '<img alt="   " src="' + checker.SCREENSHOT_PATHS[3] + '">'
            }))

    def test_second_reference_cannot_have_empty_alt(self):
        with self.assertRaisesRegex(ValueError, r"1\.png"):
            checker.verify_readme_screenshots(
                markup() + '\n<img src="' + checker.SCREENSHOT_PATHS[0] + '" alt="">'
            )

    def test_plain_text_and_wrong_path_do_not_count(self):
        with self.assertRaisesRegex(ValueError, r"3\.png"):
            checker.verify_readme_screenshots(markup({
                3: checker.SCREENSHOT_PATHS[2] +
                   '<img src="not-the-canonical-path/3.png" alt="Other">'
            }))


if __name__ == "__main__":
    unittest.main()
