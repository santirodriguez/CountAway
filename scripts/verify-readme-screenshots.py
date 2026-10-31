#!/usr/bin/env python3
"""Verify README image references for the six canonical release screenshots."""

from html.parser import HTMLParser
from pathlib import Path
import sys

SCREENSHOT_PATHS = tuple(
    f"fastlane/metadata/android/en-US/images/phoneScreenshots/{index}.png"
    for index in range(1, 7)
)


class ReadmeScreenshotParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.references = {}

    def handle_starttag(self, tag, attrs):
        if tag != "img":
            return
        attributes = dict(attrs)
        source = attributes.get("src")
        if source in SCREENSHOT_PATHS:
            self.references.setdefault(source, []).append(attributes.get("alt"))


def verify_readme_screenshots(readme):
    """Require every canonical image to be used with non-blank alt text."""
    parser = ReadmeScreenshotParser()
    parser.feed(readme)
    parser.close()

    problems = []
    for source in SCREENSHOT_PATHS:
        alternatives = parser.references.get(source)
        if not alternatives:
            problems.append(f"Missing README <img> reference: {source}")
        elif any(alt is None or not alt.strip() for alt in alternatives):
            problems.append(f"Missing or empty README image alt text: {source}")

    if problems:
        raise ValueError("; ".join(problems))


def main():
    try:
        readme = (Path(__file__).resolve().parents[1] / "README.md").read_text(
            encoding="utf-8"
        )
        verify_readme_screenshots(readme)
    except (OSError, ValueError) as error:
        print(f"README screenshot metadata: BLOCKED: {error}", file=sys.stderr)
        return 1

    print("README screenshot metadata: PASS (6 images with non-empty alt text)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
