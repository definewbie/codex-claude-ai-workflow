import os,sys,unittest,shutil
from unittest.mock import patch
import test_continuation as original
import continuation as c
import gate_support as g
class RequiredBehavior(original.AssemblyTest):
 def test_changed_parent_path_with_same_selected_environment(self):
  selection=g.read(self.out/'local-launch-selection.json')
  selection['explicit_child_environment']['PATH']=os.environ['PATH']
  self.write(self.out/'local-launch-selection.json',selection)
  with patch.dict(os.environ,{'PATH':'/irrelevant-parent-only'}): c.inspect()
 def test_runner_uses_selected_absolute_programs(self):
  self.allow();selection=g.read(self.out/'local-launch-selection.json')
  selection['executables']={n:shutil.which(n) for n in ['java','rtk']}
  self.write(self.out/'local-launch-selection.json',selection)
  with patch('subprocess.Popen',self.process_double()):self.run_entry('run-one-step.py',['first','plan.decision'])
  self.assertEqual(selection['executables']['rtk'],self.argv[0])
  self.assertEqual(selection['executables']['java'],self.argv[2])
suite=unittest.TestSuite(RequiredBehavior(name) for name in ['test_changed_parent_path_with_same_selected_environment','test_runner_uses_selected_absolute_programs'])
r=unittest.TextTestRunner(verbosity=2).run(suite)
sys.exit(not r.wasSuccessful())
