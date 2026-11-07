#!/usr/bin/env python3
"""Check the text served by CountAway's public release websites.

The workflow downloads the HTML with curl --fail. This validator ignores HTML
formatting and character-entity differences without weakening required content.
JavaScript-only data and script/style blocks do not count as published text.
"""

from __future__ import annotations

import argparse
from html.parser import HTMLParser
from pathlib import Path
import re
import sys
import unicodedata


PAGE_MARKERS = {
    "project": (
        ("project headline", "Count the days that matter."),
    ),
    "privacy": (
        ("developer identity", "CountAway is an app developed by Santiago Rodriguez"),
        ("English policy heading", "What CountAway does not collect"),
        ("Spanish policy heading", "Qué datos no recopila CountAway"),
        ("Catalan policy heading", "Quines dades no recopila CountAway"),
        ("privacy contact", "support@cajapersonal.org"),
        ("English language label", "English"),
        ("Spanish language label", "Español"),
        ("Catalan language label", "Català"),
    ),
}


class PageTextParser(HTMLParser):
    """Extract document text, excluding scripts and other non-content nodes."""

    SKIPPED = frozenset({"script", "style", "template", "noscript", "svg"})
    BLOCKS = frozenset({
        "br", "hr", "p", "div", "section", "article", "main", "header",
        "footer", "aside", "li", "ul", "ol", "h1", "h2", "h3", "h4",
        "h5", "h6", "td", "th", "tr", "table", "blockquote", "pre",
    })

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.fragments: list[str] = []
        self.skip_depth = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in self.SKIPPED:
            self.skip_depth += 1
        elif not self.skip_depth and tag in {"br", "hr"}:
            self.fragments.append(" ")

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if not self.skip_depth and tag in self.BLOCKS:
            self.fragments.append(" ")

    def handle_endtag(self, tag: str) -> None:
        if self.skip_depth:
            if tag in self.SKIPPED:
                self.skip_depth -= 1
        elif tag in self.BLOCKS:
            self.fragments.append(" ")

    def handle_data(self, data: str) -> None:
        if not self.skip_depth:
            self.fragments.append(data)


def normalize(text: str) -> str:
    """Normalize whitespace, HTML nonbreaking spaces and Unicode variants."""
    return re.sub(r"\s+", " ", unicodedata.normalize("NFKC", text)).strip()


def document_text(markup: str) -> str:
    parser = PageTextParser()
    parser.feed(markup)
    parser.close()
    return normalize("".join(parser.fragments))


def missing_markers(markup: str, page: str) -> list[str]:
    if page not in PAGE_MARKERS:
        raise ValueError(f"Unknown page type: {page}")
    text = document_text(markup)
    return [
        f"{page}: missing {label} ({phrase!r})"
        for label, phrase in PAGE_MARKERS[page]
        if normalize(phrase) not in text
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-html", type=Path, required=True)
    parser.add_argument("--privacy-html", type=Path, required=True)
    args = parser.parse_args()

    problems: list[str] = []
    for page, path in (("project", args.project_html), ("privacy", args.privacy_html)):
        try:
            markup = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as error:
            problems.append(f"{page}: cannot read downloaded HTML: {error}")
            continue
        if not markup.strip():
            problems.append(f"{page}: downloaded HTML is empty")
            continue
        missing = missing_markers(markup, page)
        problems.extend(missing)
        if not missing:
            print(f"public_site=PASS page={page} checks={len(PAGE_MARKERS[page])}")

    if problems:
        for problem in problems:
            print(f"public_site=BLOCKED {problem}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
