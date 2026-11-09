#!/usr/bin/env python3
"""Pure-Python tests for schema-8 signed-upgrade evidence comparison."""
from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("upgrade-v124-fixture.py")
SPEC = importlib.util.spec_from_file_location("upgrade_v124_fixture", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Missing fixture validator")
fixture = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(fixture)


class UpgradeV124FixtureTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.before = self.root / "before.json"
        self.after = self.root / "after.json"
        self.original = fixture.expected_payload()
        self.write_pair(self.original, self.original)

    def write_pair(self, before, after):
        self.before.write_text(json.dumps(before))
        self.after.write_text(json.dumps(after))

    def test_four_realistic_schema8_event_kinds(self):
        events = self.original["events"]
        self.assertEqual(4, len(events))
        self.assertEqual(4, len({event["id"] for event in events}))
        self.assertNotIn("eventTime", events[0])
        self.assertNotIn("eventZone", events[0])
        self.assertEqual(("23:05", "America/Argentina/Buenos_Aires"),
                         (events[1]["eventTime"], events[1]["eventZone"]))
        self.assertEqual(("weekly", "00:05", "Europe/Madrid"),
                         (events[2]["repeatRule"], events[2]["eventTime"], events[2]["eventZone"]))
        self.assertEqual(("count_up", "none"),
                         (events[3]["countMode"], events[3]["repeatRule"]))

    def test_same_stored_events_in_different_order_pass(self):
        swapped = copy.deepcopy(self.original)
        swapped["events"].reverse()
        self.write_pair(self.original, swapped)
        fixture.verify(self.before, self.after)

    def test_generator_and_cli_verifier(self):
        generated = self.root / "generated.json"
        run = subprocess.run([sys.executable, str(SCRIPT), "generate", str(generated)],
                             capture_output=True, text=True)
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertEqual(self.original, json.loads(generated.read_text()))
        run = subprocess.run([sys.executable, str(SCRIPT), "verify",
                              str(self.before), str(generated)], capture_output=True, text=True)
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertIn("ids_and_values=preserved", run.stdout)
        self.assertIn("predecessor=1.2.4", run.stdout)
        current = subprocess.run([sys.executable, str(SCRIPT), "verify",
                                  str(self.before), str(generated), "--predecessor", "1.2.5"],
                                 capture_output=True, text=True)
        self.assertEqual(0, current.returncode, current.stderr)
        self.assertIn("predecessor=1.2.5", current.stdout)

    def test_identity_changes_fail_closed(self):
        for damage in ("missing", "different", "duplicated"):
            with self.subTest(damage=damage):
                modified = copy.deepcopy(self.original)
                if damage == "missing":
                    modified["events"].pop()
                elif damage == "different":
                    modified["events"][0]["id"] = "different-uuid"
                else:
                    modified["events"][0]["id"] = modified["events"][1]["id"]
                self.write_pair(self.original, modified)
                with self.assertRaisesRegex(ValueError, "events|IDs"):
                    fixture.verify(self.before, self.after)

    def test_schema_mismatch_fails_closed(self):
        corrupted = copy.deepcopy(self.original)
        corrupted["schemaVersion"] = 7
        self.write_pair(self.original, corrupted)
        with self.assertRaisesRegex(ValueError, "schema"):
            fixture.verify(self.before, self.after)

    def test_any_preserved_field_change_fails(self):
        for index, field, value in (
            (0, "date", "2027-06-18"),
            (1, "eventTime", "23:06"),
            (1, "eventZone", "Europe/Madrid"),
            (2, "repeatRule", "none"),
            (3, "countMode", "count_down"),
            (0, "title", "Wrong title"),
        ):
            with self.subTest(field=field, index=index):
                altered = copy.deepcopy(self.original)
                altered["events"][index][field] = value
                self.write_pair(self.original, altered)
                with self.assertRaisesRegex(ValueError, "altered stored values"):
                    fixture.verify(self.before, self.after)

    def test_corrupt_or_missing_evidence_is_rejected(self):
        self.after.write_text("{invalid", encoding="utf-8")
        with self.assertRaises(json.JSONDecodeError):
            fixture.verify(self.before, self.after)
        self.after.unlink()
        with self.assertRaises(OSError):
            fixture.verify(self.before, self.after)

    def test_false_success_from_a_title_only_probe_is_impossible(self):
        incomplete = {"schemaVersion": 8, "events": [
            {"title": event["title"]} for event in fixture.EVENTS]}
        self.write_pair(self.original, incomplete)
        with self.assertRaisesRegex(ValueError, "identity"):
            fixture.verify(self.before, self.after)


if __name__ == "__main__":
    unittest.main()
