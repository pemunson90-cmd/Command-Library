import pathlib
import tempfile
import unittest
from unittest.mock import patch
from collect_visual_captures import collect, safe_device_path, valid_png

PNG = b'\x89PNG\r\n\x1a\n' + b'\0\0\0\rIHDR' + b'\0\0\0\1' * 2 + b'\x08\x06\0\0\0' + b'\0'*4

class CaptureSafetyTest(unittest.TestCase):
    def test_device_path_rejects_other_data_and_traversal(self):
        base = '/sdcard/Android/data/com.architectpsg.commandlibrary.feature.shell.test/files/visual-acceptance/'
        self.assertTrue(safe_device_path(base + 'synthetic-life-360x800-dark-2.0.png'))
        for path in (base + '../secret', base + 'a;cat secret', '/sdcard/Android/data/other/files/test.png'):
            self.assertFalse(safe_device_path(path))

    def test_rejects_empty_or_zero_dimension_png(self):
        self.assertTrue(valid_png(PNG))
        self.assertFalse(valid_png(b''))
        self.assertFalse(valid_png(PNG[:16] + b'\0'*8 + PNG[24:]))

    def test_passing_suite_missing_captures_fails_without_broad_device_read(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            report = root / 'feature/shell/build/outputs/androidTest-results/connected/device/TEST-visual.xml'
            report.parent.mkdir(parents=True)
            report.write_text('<testsuite>' + '<testcase classname="VisualSystemAcceptanceTest"/>'*3 + '</testsuite>')
            with patch('collect_visual_captures.subprocess.run') as run:
                with self.assertRaises(SystemExit):
                    collect(root, root/'captures', 'adb')
                run.assert_not_called()

if __name__ == '__main__':
    unittest.main()
