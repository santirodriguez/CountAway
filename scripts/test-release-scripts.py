#!/usr/bin/env python3
"""Regression tests for release-script safety and Android formatter contracts.

The signing fixture uses mocked tools and no real keystores. The existing
Android CI signing self-test supplies the separate real JDK/JAR coverage.
"""
from pathlib import Path
import hashlib
import importlib.util
import os
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
SIGNING = ROOT / "scripts/sign-play-bundle.sh"
SPEC = importlib.util.spec_from_file_location("localizations", ROOT / "scripts/verify-localizations.py")
assert SPEC and SPEC.loader
localizations = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(localizations)

MOCK_TOOLS = r'''
keytool() {
  while (( $# )); do
    if [[ "$1" == -file ]]; then printf 'test certificate\n' > "$2"; return 0; fi
    shift
  done
  return 1
}
openssl() { printf 'sha256 Fingerprint=AA:BB:CC\n'; }
jarsigner() {
  if [[ "$1" == -verify ]]; then printf 'jar verified.\n'; return 0; fi
  while (( $# )); do
    if [[ "$1" == -signedjar ]]; then
      cp -- "$3" "$2"
      printf '\nsigned-fixture\n' >> "$2"
      if [[ -n "${TEST_RACE_RECEIPT:-}" ]]; then
        printf 'racing unrelated receipt\n' > "$TEST_RACE_RECEIPT"
      fi
      return 0
    fi
    shift
  done
  return 1
}
java() {
  if [[ "${TEST_FAIL_VALIDATE:-}" == yes ]]; then return 1; fi
  if [[ " $* " == *' validate '* ]]; then return 0; fi
  case "$*" in
    *'/manifest/@package'*) printf 'com.example.fixture\n' ;;
    *'/manifest/@android:versionName'*) printf '1.2.4\n' ;;
    *'/manifest/@android:versionCode'*) printf '13\n' ;;
    *) return 1 ;;
  esac
}
'''


class SigningPathTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.inputs = {}
        for name in ("input.aab", "upload.jks", "upload.pem", "bundletool.jar"):
            path = self.root / name
            path.write_bytes(("sentinel:" + name).encode())
            self.inputs[name] = path
        self.output = self.root / "signed.aab"
        self.receipt = self.root / "signed.receipt.txt"
        self.mock = self.root / "tools.sh"
        self.mock.write_text(MOCK_TOOLS)
        (self.root / "subdir").mkdir()
        self.original = {name: path.read_bytes() for name, path in self.inputs.items()}

    def run_sign(self, output=None, receipt=None, *, full=True, extra_env=None):
        args = ["bash", str(SIGNING), str(self.inputs["input.aab"]),
                str(output if output is not None else self.output),
                str(self.inputs["upload.jks"]), "upload-test", str(self.inputs["upload.pem"])]
        if full:
            args += [str(self.inputs["bundletool.jar"]), "com.example.fixture", "1.2.4", "13",
                     str(receipt if receipt is not None else self.receipt)]
        env = os.environ.copy()
        env.update({"BASH_ENV": str(self.mock), "PLAY_UPLOAD_STORE_PASSWORD": "fixture",
                    "PLAY_UPLOAD_KEY_PASSWORD": "fixture", "TMPDIR": str(self.root)})
        if extra_env:
            env.update(extra_env)
        result = subprocess.run(args, cwd=self.root, env=env, capture_output=True, text=True)
        for name, path in self.inputs.items():
            self.assertEqual(path.read_bytes(), self.original[name], (name, result.stderr))
        return result

    def assert_no_stage(self):
        self.assertEqual(list(self.root.glob(".countaway-sign.*")), [])
        self.assertEqual(list(self.root.glob(".countaway-receipt.*")), [])

    def test_destination_aliases_and_existing_files_are_rejected(self):
        variants = [*self.inputs.values(), self.root / "subdir" / ".." / "input.aab"]
        alias = self.root / "alias-to-input"
        alias.symlink_to(self.inputs["input.aab"])
        variants.append(alias)
        hardlink = self.root / "hardlink-to-input"
        os.link(self.inputs["input.aab"], hardlink)
        variants.append(hardlink)
        existing = self.root / "existing-output"
        existing.write_text("keep me")
        variants.append(existing)
        for candidate in variants:
            with self.subTest(destination=candidate):
                before = candidate.read_bytes()
                result = self.run_sign(output=candidate)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(candidate.read_bytes(), before)
                self.assertFalse(self.receipt.exists())
                self.assert_no_stage()

    def test_receipt_overwrite_and_destination_overlap_rejected(self):
        self.receipt.write_text("previous receipt")
        result = self.run_sign()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.receipt.read_text(), "previous receipt")
        self.assertFalse(self.output.exists())
        self.assert_no_stage()
        self.receipt.unlink()
        result = self.run_sign(output=self.output, receipt=self.output)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.output.exists())
        self.assert_no_stage()

    def test_default_receipt_is_checked_before_signing(self):
        default = self.root / "signed.aab.receipt.txt"
        default.write_text("previous default")
        result = self.run_sign(full=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(default.read_text(), "previous default")
        self.assertFalse(self.output.exists())
        self.assert_no_stage()

    def test_safe_staging_and_final_receipt_hash(self):
        result = self.run_sign()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.inputs["input.aab"].read_bytes(), self.original["input.aab"])
        self.assertTrue(self.output.is_file())
        expected = hashlib.sha256(self.output.read_bytes()).hexdigest()
        self.assertIn("signed_sha256=" + expected, self.receipt.read_text())
        self.assertIn("input_sha256=" + hashlib.sha256(self.original["input.aab"]).hexdigest(), self.receipt.read_text())
        self.assertIn("upload_certificate_sha256=aabbcc", self.receipt.read_text())
        self.assertIn("bundletool_validation=true", self.receipt.read_text())
        self.assert_no_stage()

    def test_failed_validation_does_not_publish_partial_files(self):
        result = self.run_sign(extra_env={"TEST_FAIL_VALIDATE": "yes"})
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.output.exists())
        self.assertFalse(self.receipt.exists())
        self.assert_no_stage()

    def test_raced_receipt_is_preserved_and_output_rolled_back(self):
        result = self.run_sign(extra_env={"TEST_RACE_RECEIPT": str(self.receipt)})
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.receipt.read_text(), "racing unrelated receipt\n")
        self.assertFalse(self.output.exists())
        self.assert_no_stage()


class FormatterContractTests(unittest.TestCase):
    def test_explicit_indexes_can_be_reordered(self):
        self.assertEqual(localizations.signature("%1$s: %2$d"),
                         localizations.signature("%2$04d - %1$s"))

    def test_width_precision_literals_and_previous_argument(self):
        self.assertEqual(localizations.signature("100%% complete%n: %1$.2f / %<g"),
                         frozenset({(1, "decimal")}))
        self.assertEqual(localizations.signature("%1$tY-%1$tm"),
                         frozenset({(1, "datetime")}))
        self.assertEqual(localizations.signature("%04d %s"),
                         frozenset({(1, "integer"), (2, "string")}))

    def test_unsupported_or_dangling_tokens_are_rejected(self):
        for bad in ("%q", "%1$", "%1$04Q", "%<s", "%1$t", "%n %1$q"):
            with self.subTest(bad=bad), self.assertRaises(SystemExit):
                localizations.signature(bad)

    def test_plural_categories_may_differ_with_explicit_exception(self):
        baseline = {"other": frozenset({(1, "integer")})}
        translated = {"one": frozenset(), "many": baseline["other"],
                      "other": baseline["other"]}
        localizations.validate_plural_contract("es", ("plurals", "share_days_left"), baseline, translated)
        translated["one"] = frozenset({(1, "string")})
        with self.assertRaises(SystemExit):
            localizations.validate_plural_contract("es", ("plurals", "share_days_left"), baseline, translated)

    def test_each_plural_branch_is_validated_not_just_union(self):
        baseline = {"one": frozenset({(2, "string")}),
                    "other": frozenset({(1, "integer"), (2, "string")})}
        valid = {"one": baseline["one"], "other": baseline["other"]}
        localizations.validate_plural_contract("ca", ("plurals", "notification_upcoming_body"), baseline, valid)
        for changed in (dict(valid, one=frozenset({(2, "integer")})),
                        dict(valid, other=frozenset({(2, "string")})),
                        dict(valid, many=frozenset({(1, "string"), (2, "string")}))):
            with self.assertRaises(SystemExit):
                localizations.validate_plural_contract("ca", ("plurals", "notification_upcoming_body"), baseline, changed)

    def test_duplicate_plural_categories_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "strings.xml"
            path.write_text(textwrap.dedent("""\
                <resources><plurals name="example">
                <item quantity="other">%1$d</item>
                <item quantity="other">%1$s</item>
                </plurals></resources>
            """))
            with self.assertRaises(SystemExit):
                localizations.resources(Path(tmp))


if __name__ == "__main__":
    unittest.main()
