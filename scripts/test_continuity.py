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
    def test_valid_snapshot(self): self.assertEqual(validate(self.root),[])
    def test_checkpoint_mismatch(self):
        self.change('coordination/state.json',lambda v:v.update(checkpoint_id='wrong'))
        self.assertTrue(any('CURRENT checkpoint differs' in e for e in validate(self.root)))
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
