import pathlib
import tempfile
import unittest
import struct
import zlib
from unittest.mock import patch
from collect_visual_captures import EXPECTED, collect, safe_device_path, valid_png

def chunk(kind, payload):
    return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload))

PNG = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 6, 0, 0, 0))
       + chunk(b'IDAT', zlib.compress(b'\0' * 5)) + chunk(b'IEND', b''))


class CaptureSafetyTest(unittest.TestCase):
    def test_device_path_rejects_other_data_and_traversal(self):
        base = '/sdcard/Android/data/com.architectpsg.commandlibrary.feature.shell.test/files/visual-acceptance/'
        self.assertTrue(safe_device_path(base + 'synthetic-life-360x800-dark-2.0.png'))
        for path in (base + '../secret', base + 'a;cat secret', '/sdcard/Android/data/other/files/test.png'):
            self.assertFalse(safe_device_path(path))

    def test_rejects_empty_or_zero_dimension_png(self):
        self.assertTrue(valid_png(PNG))
        self.assertFalse(valid_png(b''))
        self.assertFalse(valid_png(PNG[:33]))
        self.assertFalse(valid_png(PNG[:16] + b'\0'*8 + PNG[24:]))

    def test_passing_suite_missing_captures_fails_without_broad_device_read(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            report = root / 'feature/shell/build/outputs/androidTest-results/connected/device/TEST-visual.xml'
            report.parent.mkdir(parents=True)
            report.write_text('<testsuite>' + ''.join(f'<testcase classname="VisualSystemAcceptanceTest" name="case[{i}]"/>' for i in range(36)) + '</testsuite>')
            with patch('collect_visual_captures.subprocess.run') as run:
                with self.assertRaises(SystemExit):
                    collect(root, root/'captures', 'adb')
                run.assert_not_called()

    def test_skipped_matrix_cannot_pass_evidence_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            report = root / 'feature/shell/build/outputs/androidTest-results/connected/device/TEST-visual.xml'
            report.parent.mkdir(parents=True)
            report.write_text('<testsuite>' + ''.join(
                f'<testcase classname="VisualSystemAcceptanceTest" name="case[{i}]"><skipped/></testcase>'
                for i in range(36)) + '</testsuite>')
            with self.assertRaises(SystemExit):
                collect(root, root/'captures', 'adb')

    def test_complete_host_matrix_needs_no_device_reads(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            build = root / 'feature/shell/build/outputs'
            report = build / 'androidTest-results/connected/device/TEST-visual.xml'
            report.parent.mkdir(parents=True)
            report.write_text('<testsuite>' + ''.join(
                f'<testcase classname="VisualSystemAcceptanceTest" name="case[{i}]"/>'
                for i in range(36)) + '</testsuite>')
            images = build / 'connected_android_test_additional_output/device'
            images.mkdir(parents=True)
            for name in EXPECTED:
                (images / name).write_bytes(PNG)
            with patch('collect_visual_captures.subprocess.run') as run:
                collect(root, root/'captures', 'adb')
                run.assert_not_called()
            self.assertEqual(36, len(list((root/'captures').glob('*.png'))))

    def test_api23_logcat_marker_recovers_capture_missing_from_xml(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            results = root / 'feature/shell/build/outputs/androidTest-results/connected/debug'
            report = results / 'TEST-k2.xml'
            report.parent.mkdir(parents=True)
            report.write_text('<testsuite>' + ''.join(
                f'<testcase classname="VisualSystemAcceptanceTest" name="case[{i}]"/>'
                for i in range(36)) + '</testsuite>')
            missing = sorted(EXPECTED)[0]
            captures = root / 'captures'
            captures.mkdir()
            for name in EXPECTED - {missing}:
                (captures / name).write_bytes(PNG)
            logcat = results / 'k2(AVD) - 6.0/logcat-com.example.VisualSystemAcceptanceTest-case.txt'
            logcat.parent.mkdir()
            device_path = ('/storage/emulated/0/Android/data/'
                           'com.architectpsg.commandlibrary.feature.shell.test/'
                           'files/visual-acceptance/' + missing)
            logcat.write_text(
                '10-13 17:18:43.286 I System.out: VISUAL_CAPTURE_PATH=' + device_path + '\n'
                '10-13 17:18:43.287 I System.out: VISUAL_CAPTURE_PATH=/sdcard/other/secret.png\n')
            with patch('collect_visual_captures.subprocess.run') as run:
                run.return_value.returncode = 0
                run.return_value.stdout = PNG
                collect(root, captures, 'adb')
                run.assert_called_once_with(['adb', 'exec-out', 'cat', device_path],
                                            capture_output=True, timeout=30)
            self.assertEqual(36, len(list(captures.glob('*.png'))))

if __name__ == '__main__':
    unittest.main()
