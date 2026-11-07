#!/usr/bin/env python3
"""Verify CountAway's live policy pages and canonical privacy contact.

GitHub Actions fetches both live pages with curl --fail. Sites can encode or
obfuscate email addresses in the returned HTML even when the rendered policy
shows them, so the contact is checked against the published policy source.
"""

from __future__ import annotations

import argparse
from html.parser import HTMLParser
from pathlib import Path
import re
import sys
import unicodedata


SITE_MARKERS = {
    "project": (
        ("project headline", "Count the days that matter."),
    ),
    "privacy": (
        ("app identity", "CountAway"),
        ("English policy heading", "What CountAway does not collect"),
        ("Spanish policy heading", "Qué datos no recopila CountAway"),
        ("Catalan policy heading", "Quines dades no recopila CountAway"),
        ("English language label", "English"),
        ("Spanish language label", "Español"),
        ("Catalan language label", "Català"),
    ),
}
CONTACT_EMAIL = "support@cajapersonal.org"
POLICY_HEADINGS = ("## English", "## Español", "## Català")


class VisibleText(HTMLParser):
    """Extract human-readable text, not scripts, attributes, or CSS."""

    SKIPPED = frozenset({"script", "style", "template", "noscript", "svg"})

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []
        self.skipped: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in self.SKIPPED or self.skipped:
            self.skipped.append(tag)

    def handle_endtag(self, tag: str) -> None:
        if self.skipped:
            if tag == self.skipped[-1]:
                self.skipped.pop()
        elif tag in {"p", "div", "section", "h1", "h2", "h3", "li", "br"}:
            self.parts.append(" ")

    def handle_data(self, data: str) -> None:
        if not self.skipped:
            self.parts.append(data)


def normalize(value: str) -> str:
    return re.sub(r"\s+", " ", unicodedata.normalize("NFKC", value)).strip()


def missing_site_markers(markup: str, page: str) -> list[str]:
    if page not in SITE_MARKERS:
        raise ValueError(f"Unknown page type: {page}")
    parser = VisibleText()
    parser.feed(markup)
    parser.close()
    text = normalize("".join(parser.parts))
    return [
        f"{page}: missing {label} ({phrase!r})"
        for label, phrase in SITE_MARKERS[page]
        if normalize(phrase) not in text
    ]


def policy_contact_problems(markdown: str) -> list[str]:
    """Require the support address in every canonical policy language."""
    errors = []
    if not markdown.startswith("# CountAway Privacy Policy\n"):
        errors.append("privacy source: missing CountAway policy title")
    for index, heading in enumerate(POLICY_HEADINGS):
        start = markdown.find("\n" + heading + "\n")
        if start < 0:
            errors.append(f"privacy source: missing {heading}")
            continue
        start += len(heading) + 2
        end = markdown.find("\n" + POLICY_HEADINGS[index + 1] + "\n", start) if index + 1 < len(POLICY_HEADINGS) else -1
        section = markdown[start:end if end >= 0 else None]
        if CONTACT_EMAIL not in section:
            errors.append(f"privacy source: missing support contact in {heading}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-html", type=Path, required=True)
    parser.add_argument("--privacy-html", type=Path, required=True)
    parser.add_argument("--policy-source", type=Path, default=Path(__file__).resolve().parents[1] / "PRIVACY.md")
    args = parser.parse_args()
    errors: list[str] = []
    for page, path in (("project", args.project_html), ("privacy", args.privacy_html)):
        try:
            markup = path.read_text(encoding="utf-8")
            if not markup.strip():
                raise ValueError("downloaded HTML is empty")
            missing = missing_site_markers(markup, page)
            errors.extend(missing)
            if not missing:
                print(f"public_site=PASS page={page} checks={len(SITE_MARKERS[page])}")
        except (OSError, ValueError, UnicodeError) as error:
            errors.append(f"{page}: unable to validate downloaded HTML ({error})")
    try:
        missing = policy_contact_problems(args.policy_source.read_text(encoding="utf-8"))
        errors.extend(missing)
        if not missing:
            print("public_site=PASS policy_source=trilingual_contact")
    except (OSError, UnicodeError) as error:
        errors.append(f"privacy source: unable to read canonical policy ({error})")
    for error in errors:
        print(f"public_site=BLOCKED {error}", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
