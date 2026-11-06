"""Lossless, owner-applied resume compaction; never mutate live pointers."""
import copy
import datetime as dt
import hashlib
import shutil
from checkpoint import record
from check_continuity import validate
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts/compact_resume.py'


class CompactResumeTests(unittest.TestCase):
    def run_proposal(self, output, *extra):
        return subprocess.run([sys.executable, str(SCRIPT), '--root', str(ROOT),
                               '--output', str(output), '--keep',
                               'ordinary_artwork_candidate', *extra],
                              capture_output=True, text=True)

    def test_lossless_small_proposal_preserves_all_live_fields(self):
        original_bytes = (ROOT / 'coordination/state.json').read_bytes()
        original = json.loads(original_bytes)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'proposal'
            result = self.run_proposal(output)
            self.assertEqual(result.returncode, 0, result.stderr)
            proposed_bytes = (output / 'coordination/state.json').read_bytes()
            proposed = json.loads(proposed_bytes)
            archive = output / proposed['verification_archive']['path']
            self.assertEqual(archive.read_bytes(), original_bytes)
            self.assertEqual(proposed['verification'], {
                'ordinary_artwork_candidate': original['verification']['ordinary_artwork_candidate']})
            expected = copy.deepcopy(original)
            expected['verification'] = proposed['verification']
            expected['verification_archive'] = proposed['verification_archive']
            self.assertEqual(proposed, expected)
            self.assertLess(len(proposed_bytes), len(original_bytes) // 5)
            self.assertEqual((ROOT / 'coordination/state.json').read_bytes(), original_bytes)

    def test_unknown_key_fails_without_output(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'proposal'
            result = self.run_proposal(output, '--keep', 'missing-verification-key')
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('Unknown verification keys', result.stderr)
            self.assertFalse(output.exists())

    def test_existing_output_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'proposal'
            self.assertEqual(self.run_proposal(output).returncode, 0)
            result = self.run_proposal(output)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('exists', result.stderr)

    def test_refuses_output_inside_source(self):
        result = self.run_proposal(ROOT / 'coordination')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('outside the source repository', result.stderr)

    def test_staged_proposal_survives_continuity_and_checkpoint(self):
        proposal = ROOT / 'docs/verification/resume-compaction/proposal'
        proposed = json.loads((proposal / 'coordination/state.json').read_text())
        evidence = proposed['verification_archive']
        archive = (proposal / evidence['path']).read_bytes()
        self.assertEqual(hashlib.sha256(archive).hexdigest(), evidence['sha256'])
        self.assertEqual(len(json.loads(archive)['verification']), evidence['verification_entries'])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ('README.md', 'AGENTS.md', 'CLAUDE.md'):
                shutil.copyfile(ROOT / name, root / name)
            for name in ('coordination', 'docs/architecture', 'docs/gauntlet'):
                shutil.copytree(ROOT / name, root / name)
            shutil.copytree(proposal / 'coordination', root / 'coordination', dirs_exist_ok=True)
            self.assertEqual(validate(root), [])
            record(root, checkpoint='cp-compaction-test', task=proposed['next_task'],
                   step=proposed['next_step'], action=proposed['first_action'],
                   summary='Test fixture only', status=proposed['status'],
                   lease_status='active', base='a' * 40,
                   now=dt.datetime(2026, 11, 6, tzinfo=dt.timezone.utc))
            self.assertEqual(validate(root), [])
            after = json.loads((root / 'coordination/state.json').read_text())
            self.assertEqual(after['verification_archive'], evidence)
            self.assertEqual(after['verification'], proposed['verification'])
            self.assertEqual((root / evidence['path']).read_bytes(), archive)
