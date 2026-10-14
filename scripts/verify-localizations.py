#!/usr/bin/env python3
"""Validate CountAway's enabled and candidate Android language resources."""
from pathlib import Path
import argparse
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
CATALOG = RES / "xml/supported_languages.xml"
FORMAT = re.compile(
    r"%(?:(?P<index>[1-9][0-9]*)\$)?(?P<flags>[-#+ 0,(<]*)"
    r"(?P<width>[0-9]+)?(?:\.(?P<precision>[0-9]+))?"
    r"(?P<conversion>[bBhHsScCdoxXeEfgGaAtT%n])"
)
FORMAT_TYPES = {
    "b": "boolean", "h": "hash", "s": "string", "c": "character",
    "d": "integer", "o": "integer", "x": "integer",
    "e": "decimal", "f": "decimal", "g": "decimal", "a": "decimal",
    "t": "datetime",
}
DATE_TIME_SUFFIXES = set("HIklMSLNpzZsQBbhAaCYyjmdeRTrDFc")
PLURAL_QUANTITIES = {"zero", "one", "two", "few", "many", "other"}
# Android may request a singular without supplying the numeric field in the
# localized copy. This known exception is limited to one resource/quantity.
PLURAL_OPTIONAL_ARGUMENTS = {("plurals", "share_days_left", "one")}
LANGUAGE_VALUES_DIR = re.compile(r"^values-(?:b\+[A-Za-z0-9+]+|[a-z]{2,3}(?:-r[A-Z]{2})?)$")


def die(message):
    raise SystemExit(message)


def load_catalog():
    root = ET.parse(CATALOG).getroot()
    default = root.attrib.get("defaultTag", "").strip()
    languages = []
    for node in root.findall("language"):
        item = {key: node.attrib.get(key, "").strip() for key in (
            "tag", "qualifier", "endonym", "fdroidLocale", "playLocale", "icon"
        )}
        missing = [key for key, value in item.items() if not value]
        if missing:
            die(f"{CATALOG}: missing {', '.join(missing)}")
        languages.append(item)
    if not languages:
        die(f"{CATALOG}: no enabled languages")
    for key in ("tag", "qualifier", "fdroidLocale", "playLocale"):
        values = [item[key] for item in languages]
        if len(values) != len(set(values)):
            die(f"{CATALOG}: duplicate {key}")
    if default not in [item["tag"] for item in languages]:
        die(f"{CATALOG}: defaultTag is not enabled")
    return default, languages


def signature(text):
    """Return argument index and compatible Java Formatter conversion families.

    Explicit argument indexes may be reordered; ordinary implicit indexes
    advance independently of explicit indexes. Escaped percent and %n do not
    consume arguments. Unsupported or malformed tokens fail instead of being
    silently dropped from the format contract.
    """
    text = text or ""
    requirements = set()
    implicit = 0
    previous = None
    offset = 0
    while True:
        start = text.find("%", offset)
        if start < 0:
            break
        token = FORMAT.match(text, start)
        if token is None:
            die(f"unsupported format token near {text[start:start + 12]!r} in {text!r}")
        index = token.group("index")
        flags = token.group("flags")
        width = token.group("width")
        precision = token.group("precision")
        kind = token.group("conversion")
        offset = token.end()

        if kind in {"%", "n"}:
            if index or "<" in flags or (kind == "n" and (flags or width or precision)) or precision:
                die(f"invalid literal format token {token.group()!r} in {text!r}")
            continue
        if flags.count("<") > 1 or ("<" in flags and index):
            die(f"invalid previous-argument flag in {text!r}")
        if "<" in flags:
            if previous is None:
                die(f"previous-argument flag has no argument in {text!r}")
            argument = previous
        elif index:
            argument = int(index)
        else:
            implicit += 1
            argument = implicit
        previous = argument

        family = FORMAT_TYPES[kind.lower()]
        if family == "datetime":
            if offset >= len(text) or text[offset] not in DATE_TIME_SUFFIXES:
                die(f"unsupported date/time format near {text[start:start + 12]!r}")
            offset += 1
        requirements.add((argument, family))
    return frozenset(requirements)


def resources(directory):
    values = {}
    nontranslatable = set()
    for path in sorted(directory.glob("*.xml")):
        root = ET.parse(path).getroot()
        if root.tag != "resources":
            continue
        for node in root:
            if node.tag not in {"string", "plurals", "string-array"}:
                continue
            name = node.attrib.get("name")
            if not name:
                die(f"{path}: {node.tag} without name")
            key = (node.tag, name)
            if key in values or key in nontranslatable:
                die(f"{path}: duplicate {node.tag}/{name}")
            if node.attrib.get("translatable") == "false":
                nontranslatable.add(key)
                continue
            if node.tag == "string":
                values[key] = ("string", signature("".join(node.itertext())))
            elif node.tag == "plurals":
                branches = {}
                for item in node.findall("item"):
                    quantity = item.attrib.get("quantity", "")
                    if quantity not in PLURAL_QUANTITIES or quantity in branches:
                        die(f"{path}: invalid or duplicate quantity={quantity!r} in plurals/{name}")
                    branches[quantity] = signature("".join(item.itertext()))
                if "other" not in branches:
                    die(f"{path}: plurals/{name} is missing quantity=other")
                values[key] = ("plurals", branches)
            else:
                values[key] = ("string-array", tuple(
                    signature("".join(item.itertext())) for item in node.findall("item")
                ))
    return values, nontranslatable


def validate_plural_contract(label, key, expected, actual):
    """Validate every translated branch, allowing locale-specific categories."""
    fallback = expected["other"]
    for quantity, actual_signature in actual.items():
        expected_signature = expected.get(quantity, fallback)
        allowed = {expected_signature}
        if (*key, quantity) in PLURAL_OPTIONAL_ARGUMENTS:
            allowed.add(frozenset())
        if actual_signature not in allowed:
            die(
                f"{label}: plural contract mismatch for {key} quantity={quantity}: "
                f"{sorted(actual_signature)} not in {[sorted(x) for x in allowed]}"
            )


def validate_directory(label, directory, baseline, nontranslatable):
    if not directory.is_dir():
        die(f"{label}: missing {directory}")
    current, localized_nontranslatable = resources(directory)
    accidental = set(current) & nontranslatable
    if accidental:
        die(f"{label}: overrides nontranslatable resources: {sorted(accidental)}")
    missing = set(baseline) - set(current)
    extra = set(current) - set(baseline)
    if missing:
        die(f"{label}: missing resources: {sorted(missing)}")
    if extra:
        die(f"{label}: extra resources: {sorted(extra)}")
    for key, expected in baseline.items():
        actual = current[key]
        if expected[0] == actual[0] == "plurals":
            validate_plural_contract(label, key, expected[1], actual[1])
        elif expected != actual:
            die(f"{label}: resource contract mismatch for {key}: {actual} != {expected}")
    unexpected_nontranslatable = localized_nontranslatable - nontranslatable
    if unexpected_nontranslatable:
        die(f"{label}: unexpected translatable=false resources: {sorted(unexpected_nontranslatable)}")


def candidate_directories(enabled_qualifiers):
    return sorted(
        path for path in RES.iterdir()
        if path.is_dir()
        and LANGUAGE_VALUES_DIR.match(path.name)
        and path.name not in enabled_qualifiers
    )


def validate(resource_dir=None):
    default, languages = load_catalog()
    default_entry = next(item for item in languages if item["tag"] == default)
    baseline, nontranslatable = resources(RES / default_entry["qualifier"])

    for language in languages:
        if language["tag"] == default:
            continue
        validate_directory(language["tag"], RES / language["qualifier"], baseline, nontranslatable)

    enabled_qualifiers = {item["qualifier"] for item in languages}
    candidates = candidate_directories(enabled_qualifiers)

    if resource_dir:
        directory = RES / resource_dir
        if not LANGUAGE_VALUES_DIR.match(resource_dir):
            die(f"{resource_dir}: not a supported language values directory")
        validate_directory(resource_dir, directory, baseline, nontranslatable)
        if directory not in candidates and resource_dir not in enabled_qualifiers:
            candidates.append(directory)

    for directory in candidates:
        validate_directory(f"candidate:{directory.name}", directory, baseline, nontranslatable)

    candidate_names = ",".join(path.name for path in candidates) or "none"
    print(
        f"localizations=ok default={default} "
        f"enabled={','.join(x['tag'] for x in languages)} "
        f"candidates={candidate_names} resources={len(baseline)}"
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--print-fdroid-locales", action="store_true")
    parser.add_argument("--print-tags", action="store_true")
    parser.add_argument(
        "--resource-dir",
        help="Validate one language resource directory, e.g. values-b+zh+Hans, without enabling it.",
    )
    args = parser.parse_args()
    _, languages = load_catalog()
    if args.print_fdroid_locales:
        print("\n".join(item["fdroidLocale"] for item in languages))
    elif args.print_tags:
        print("\n".join(item["tag"] for item in languages))
    else:
        validate(args.resource_dir)


if __name__ == "__main__":
    main()
