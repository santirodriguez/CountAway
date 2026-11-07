#!/usr/bin/env python3
"""Deterministic schema-8 upgrade evidence for the public v1.2.4 -> candidate path.

The signed release-candidate workflow imports this fixture through CountAway's
public backup UI, then exports pre/post-upgrade backups. This tool compares
event identity AND all stored values; it does not simulate an APK upgrade.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

PREVIOUS_PUBLIC_VERSION = "1.2.4"
SCHEMA_VERSION = 8

EVENTS = (
    {
        "id": "a5167e10-0000-4000-8000-000000000001",
        "title": "UpgradeDate124",
        "date": "2027-06-17",
        "type": "event",
        "iconKey": "calendar",
        "reminderKey": "off",
        "repeatRule": "none",
        "countMode": "count_down",
        "createdAt": "2026-10-30T12:00:00Z",
    },
    {
        "id": "a5167e10-0000-4000-8000-000000000002",
        "title": "UpgradeTimed124",
        "date": "2027-05-15",
        "type": "event",
        "iconKey": "calendar",
        "reminderKey": "off",
        "repeatRule": "none",
        "countMode": "count_down",
        "createdAt": "2026-10-30T12:00:00Z",
        "eventTime": "23:05",
        "eventZone": "America/Argentina/Buenos_Aires",
    },
    {
        "id": "a5167e10-0000-4000-8000-000000000003",
        "title": "UpgradeWeekly124",
        "date": "2026-10-18",
        "type": "event",
        "iconKey": "calendar",
        "reminderKey": "off",
        "repeatRule": "weekly",
        "countMode": "count_down",
        "createdAt": "2026-10-30T12:00:00Z",
        "eventTime": "00:05",
        "eventZone": "Europe/Madrid",
    },
    {
        "id": "a5167e10-0000-4000-8000-000000000004",
        "title": "UpgradeUp124",
        "date": "2025-08-14",
        "type": "event",
        "iconKey": "calendar",
        "reminderKey": "off",
        "repeatRule": "none",
        "countMode": "count_up",
        "createdAt": "2026-10-30T12:00:00Z",
        "eventTime": "12:00",
        "eventZone": "America/New_York",
    },
)


def expected_payload() -> dict:
    return {"schemaVersion": SCHEMA_VERSION, "events": [dict(e) for e in EVENTS]}


def read_json(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("Backup root must be an object")
    return data


def assert_exact_events(payload: dict, label: str) -> dict[str, dict]:
    if type(payload.get("schemaVersion")) is not int or payload["schemaVersion"] != SCHEMA_VERSION:
        raise ValueError(f"{label}: incorrect schema version")
    records = payload.get("events")
    if not isinstance(records, list) or len(records) != len(EVENTS):
        raise ValueError(f"{label}: missing or extra events")
    if any(not isinstance(event, dict) or not isinstance(event.get("id"), str)
           for event in records):
        raise ValueError(f"{label}: malformed event identity")
    by_id = {event["id"]: event for event in records}
    expected = {event["id"]: event for event in EVENTS}
    if len(by_id) != len(EVENTS) or set(by_id) != set(expected):
        raise ValueError(f"{label}: event IDs were changed, duplicated or lost")
    for event_id, event in expected.items():
        if by_id[event_id] != event:
            diffs = sorted(set(by_id[event_id]) | set(event))
            differing = [key for key in diffs if by_id[event_id].get(key) != event.get(key)]
            raise ValueError(f"{label}: altered stored values for {event['title']}: {differing}")
    return by_id


def verify(before: Path, after: Path) -> None:
    previous = assert_exact_events(read_json(before), "before")
    updated = assert_exact_events(read_json(after), "after")
    if previous != updated:
        raise ValueError("Pre/post-upgrade event identities or values differ")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    generate = sub.add_parser("generate")
    generate.add_argument("output", type=Path)
    check = sub.add_parser("verify")
    check.add_argument("before", type=Path)
    check.add_argument("after", type=Path)
    args = parser.parse_args()
    try:
        if args.command == "generate":
            with args.output.open("x", encoding="utf-8") as stream:
                json.dump(expected_payload(), stream, ensure_ascii=False, separators=(",", ":"))
                stream.write("\n")
            print(f"upgrade_fixture=generated schema={SCHEMA_VERSION} events={len(EVENTS)}")
        else:
            verify(args.before, args.after)
            print(f"upgrade_fixture=PASS predecessor={PREVIOUS_PUBLIC_VERSION} "
                  f"schema={SCHEMA_VERSION} events={len(EVENTS)} ids_and_values=preserved")
    except (ValueError, OSError, json.JSONDecodeError) as error:
        parser.exit(1, f"upgrade_fixture=BLOCKED: {error}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
