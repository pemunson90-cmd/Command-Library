"""Regression checks for corrupt handoff pointers and architecture dependency cycles."""
import json, shutil, tempfile, unittest
from pathlib import Path
from check_continuity import validate
ROOT = Path(__file__).resolve().parents[1]
class ContinuityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        for name in ('README.md','AGENTS.md','CLAUDE.md'):
            shutil.copyfile(ROOT/name, self.root/name)
        for name in ('coordination','docs/architecture','docs/gauntlet'):
            shutil.copytree(ROOT/name,self.root/name)
    def tearDown(self): self.tmp.cleanup()
    def change(self,path,fn):
        p=self.root/path; v=json.loads(p.read_text()); fn(v); p.write_text(json.dumps(v))
    def edit(self,path,fn):
        p=self.root/path; p.write_text(fn(p.read_text()))
    def test_valid_snapshot(self): self.assertEqual(validate(self.root),[])
    def test_checkpoint_mismatch(self):
        self.change('coordination/state.json',lambda v:v.update(checkpoint_id='wrong'))
        self.assertTrue(any('CURRENT checkpoint differs' in e for e in validate(self.root)))
    def test_stale_queue_checkpoint(self):
        self.edit('coordination/QUEUE.md',lambda s:s.replace('Checkpoint-ID: cp-','Checkpoint-ID: stale-',1))
        self.assertTrue(any('QUEUE checkpoint differs' in e for e in validate(self.root)))
    def test_stale_human_checkpoint(self):
        self.edit('docs/gauntlet/STATE.md',lambda s:s.replace('Checkpoint: cp-','Checkpoint: stale-',1))
        self.assertTrue(any('human state checkpoint differs' in e for e in validate(self.root)))
    def test_duplicate_queue_task_ids(self):
        def duplicate(s):
            row=next(line for line in s.splitlines() if line.startswith('| orch-kotlin-toolchain-01 |'))
            return s.replace(row+'\n',row+'\n'+row+'\n',1)
        self.edit('coordination/QUEUE.md',duplicate)
        self.assertTrue(any('QUEUE has duplicate task IDs' in e for e in validate(self.root)))
    def test_malformed_utc_timestamp(self):
        self.change('coordination/state.json',lambda v:v.update(updated_utc='tomorrow'))
        self.assertTrue(any('invalid state updated_utc' in e for e in validate(self.root)))
    def test_malformed_lease_timestamp(self):
        self.change('coordination/ORCHESTRATOR.json',lambda v:v.update(heartbeat_utc='2099-99-99T99:99:99Z'))
        self.assertTrue(any('invalid lease heartbeat_utc' in e for e in validate(self.root)))
    def test_malformed_released_lease_expiry(self):
        def corrupt(v):
            v.update(status='released',expires_utc='tomorrow')
        self.change('coordination/ORCHESTRATOR.json',corrupt)
        self.assertTrue(any('invalid lease expires_utc' in e for e in validate(self.root)))
    def test_wrong_active_lease_task(self):
        self.change('coordination/ORCHESTRATOR.json',lambda v:v.update(task_id='another-task'))
        self.assertTrue(any('lease task differs' in e for e in validate(self.root)))
    def test_missing_next_task(self):
        self.change('coordination/state.json',lambda v:v.update(next_task='missing-card'))
        self.assertTrue(any('next task card missing' in e for e in validate(self.root)))
    def test_dependency_cycle(self):
        def cycle(v):
            core=next(c for c in v['components'] if c['id']=='core');core['dependencies']=['catalog']
        self.change('docs/architecture/registry.json',cycle)
        self.assertTrue(any('dependency cycle' in e for e in validate(self.root)))
    def test_missing_coverage(self):
        self.change('docs/architecture/coverage.json',lambda v:v['requirements'].pop())
        self.assertTrue(any('requirement joins differ' in e for e in validate(self.root)))
if __name__=='__main__':unittest.main()
