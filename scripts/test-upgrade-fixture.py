#!/usr/bin/env python3
"""Execute the release workflow's UI driver against deterministic ADB fixtures.

These tests exercise command ordering, hint handling and failure boundaries.
They do not replace the emulator's real data-bearing signed upgrade gate.
"""
from pathlib import Path
import json
import os
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / '.github/workflows/release.yml'

FAKE_ADB = r'''
from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET

path = Path('state.json')
s = json.loads(path.read_text())
a = sys.argv[1:]
s.setdefault('commands', []).append(a)
if a[:2] == ['shell', 'uiautomator']:
    if s.get('dump_error'):
        s['dump_error_count'] = s.get('dump_error_count', 0) + 1
        path.write_text(json.dumps(s))
        raise SystemExit(1)
elif a[0] == 'pull':
    root = ET.Element('hierarchy')
    if s.get('saved'):
        ET.SubElement(root, 'node', {'text': s['actual'], 'resource-id': 'app:id/eventTitle'})
    else:
        ET.SubElement(root, 'node', {'text': s.get('actual') or 'Trip',
            'resource-id': 'app:id/titleInput', 'focused': 'true',
            'enabled': 'false' if s.get('disabled') else 'true',
            'bounds': '[10,20][300,80]'})
        ET.SubElement(root, 'node', {'text': '', 'package': 'app', 'scrollable': 'true',
            'bounds': '[0,0][320,600]'})
        if not s.get('hidden_save') or s.get('scrolled'):
            ET.SubElement(root, 'node', {'text': 'Save', 'resource-id': 'app:id/saveButton',
                'enabled': 'true', 'bounds': '[10,500][300,560]'})
    ET.ElementTree(root).write(a[2], encoding='unicode')
elif a[:3] == ['shell', 'input', 'keyevent']:
    for key in a[3:]:
        if key == 'KEYCODE_DEL':
            s['actual'] = s.get('actual', '')[:-1]
        elif key != 'KEYCODE_MOVE_END':
            raise SystemExit('Unexpected key event')
elif a[:3] == ['shell', 'input', 'text']:
    s['typed'] = s.get('typed', 0) + 1
    if s.get('input_error'):
        path.write_text(json.dumps(s))
        raise SystemExit(1)
    prefix = 'Y' if s.get('always_wrong') or (s.get('first_wrong') and s['typed'] == 1) else ''
    s['actual'] = s.get('actual', '') + prefix + a[3]
elif a[:3] == ['shell', 'input', 'swipe']:
    s['scrolled'] = True
elif a[:3] == ['shell', 'input', 'tap']:
    if int(a[4]) > 400:
        s['saved'] = True
        s['save_count'] = s.get('save_count', 0) + 1
else:
    raise SystemExit('Unexpected ADB operation: ' + repr(a))
path.write_text(json.dumps(s))
'''


def driver_parts(source: str) -> tuple[str, str]:
    """Read only the existing helper heredoc/functions, without executing CI."""
    python_part = source.split("<<'PYUI'\n", 1)[1].split('\n          PYUI', 1)[0]
    shell_part = source.split('          dump_ui() {', 1)[1].split('          capture_runtime_state() {', 1)[0]
    return textwrap.dedent(python_part), textwrap.dedent('          dump_ui() {' + shell_part)


class UpgradeFixtureDriverTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = WORKFLOW.read_text()
        cls.parser, cls.driver = driver_parts(cls.source)
        compile(cls.parser, 'workflow-ui-helper', 'exec')

    def run_fixture(self, initial: dict, extra: str = '') -> tuple[subprocess.CompletedProcess, dict, str]:
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            (work / 'state.json').write_text(json.dumps(initial))
            (work / 'fake_adb.py').write_text(FAKE_ADB)
            (work / 'countaway-ui.py').write_text(self.parser)
            (work / 'predecessor-home.xml').write_text(
                '<hierarchy><node resource-id="app:id/aboutButton" enabled="true" '
                'bounds="[901,89][1027,215]" /></hierarchy>')
            (work / 'acceptance-evidence').mkdir()
            script = 'set -euo pipefail\n' + self.driver + r'''
python3() { command python3 -S "$@"; }
adb() { python3 fake_adb.py "$@"; }
timeout() { shift; "$@"; }
sleep() { :; }
replace_fixture_title UpgradeProbe123
tap_fixture_save
wait_for_text UpgradeProbe123 acceptance-evidence/saved.xml
''' + extra
            syntax = subprocess.run(['bash', '-n'], input=script, text=True, capture_output=True)
            self.assertEqual(0, syntax.returncode, syntax.stderr)
            result = subprocess.run(['bash', '-c', script], cwd=work,
                env={**os.environ, 'RUNNER_TEMP': directory, 'PACKAGE_ID': 'app', 'API_LEVEL': '33'},
                text=True, capture_output=True, timeout=15)
            state = json.loads((work / 'state.json').read_text())
            receipt = work / 'acceptance-evidence/upgrade-input-api-33.txt'
            return result, state, receipt.read_text() if receipt.exists() else ''

    def assert_success(self, initial: dict, attempts: int = 1) -> None:
        result, state, receipt = self.run_fixture(initial)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('UpgradeProbe123', state['actual'])
        self.assertEqual(1, state['save_count'])
        self.assertEqual(attempts, state['typed'])
        self.assertIn('exact_input_verified=true', receipt)

    def test_empty_field_exposes_hint_instead_of_empty_xml_text(self):
        self.assert_success({'actual': ''})

    def test_existing_title_is_replaced_not_appended(self):
        self.assert_success({'actual': 'Existing title'})

    def test_hint_and_identically_named_real_title_both_work(self):
        self.assert_success({'actual': 'Trip'})

    def test_malformed_first_entry_is_replaced_before_single_save(self):
        self.assert_success({'actual': '', 'first_wrong': True}, attempts=2)

    def test_persistently_wrong_text_fails_without_saving(self):
        result, state, receipt = self.run_fixture({'actual': '', 'always_wrong': True})
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(3, state['typed'])
        self.assertNotIn('saved', state)
        self.assertEqual('', receipt)

    def test_save_is_found_by_scrolling_once_not_blind_repeated_taps(self):
        self.assert_success({'actual': '', 'hidden_save': True})

    def test_each_ui_query_uses_a_new_device_path(self):
        result, state, _ = self.run_fixture({'actual': 'Previous title'})
        self.assertEqual(0, result.returncode, result.stderr)
        dumps = [a[3] for a in state['commands']
                 if a[:3] == ['shell', 'uiautomator', 'dump']]
        pulls = [a[1] for a in state['commands'] if a[:1] == ['pull']]
        self.assertGreaterEqual(len(dumps), 3)
        self.assertEqual(len(dumps), len(set(dumps)))
        self.assertEqual(dumps, pulls)
        self.assertTrue(all(p.startswith('/sdcard/countaway-ui-33-') for p in dumps))

    def test_dump_failure_stops_after_two_attempts(self):
        result, state, receipt = self.run_fixture({'actual': '', 'dump_error': True})
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(2, state['dump_error_count'])
        self.assertIn('stage_exit=dump:', result.stderr)
        self.assertNotIn('typed', state)
        self.assertEqual('', receipt)

    def test_post_upgrade_about_tap_uses_observed_predecessor_button(self):
        result, state, _ = self.run_fixture({'actual': ''},
            'tap_predecessor_about predecessor-home.xml\n')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(['shell', 'input', 'tap', '964', '152'], state['commands'])

    def test_post_upgrade_about_fails_closed_without_observed_button(self):
        result, state, _ = self.run_fixture({'actual': ''},
            'tap_predecessor_about missing-home.xml\n')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn(['shell', 'input', 'tap', '964', '152'], state['commands'])

    def test_signed_upgrade_still_requires_version_and_exact_data_verification(self):
        after_install = self.source.split('            adb install -r "${CANDIDATE_APK}"', 1)[1]
        self.assertIn('tap_predecessor_about "acceptance-evidence/upgrade-created-api-${API_LEVEL}.xml"', after_install)
        self.assertIn('wait_for_text "v${VERSION}"', after_install)
        self.assertIn('scripts/upgrade-v124-fixture.py verify', after_install)
        self.assertIn('upgrade-after-data-api-${API_LEVEL}.json', after_install)

    def test_disabled_field_never_receives_text_or_saves(self):
        result, state, _ = self.run_fixture({'actual': '', 'disabled': True})
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('typed', state)
        self.assertNotIn('saved', state)

    def test_failed_adb_input_is_not_accepted(self):
        result, state, receipt = self.run_fixture({'actual': '', 'input_error': True})
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('saved', state)
        self.assertEqual('', receipt)


if __name__ == '__main__':
    unittest.main()
