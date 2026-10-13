"""Retain only the synthetic visual acceptance matrix, never app data."""
import argparse
import pathlib
import re
import shutil
import struct
import subprocess
import xml.etree.ElementTree as ET

EXPECTED = {
    f"synthetic-{view}-{size}-{theme}-{scale}.png"
    for view in ('search', 'detail', 'life')
    for size in ('360x800', '900x800', '360x360')
    for theme in ('light', 'dark')
    for scale in ('1.0', '2.0')
}


def valid_png(data):
    return (len(data) >= 33 and data[:8] == b'\x89PNG\r\n\x1a\n'
            and data[12:16] == b'IHDR'
            and all(struct.unpack('>II', data[16:24])))


def safe_device_path(path):
    return bool(re.fullmatch(
        r'/(?:sdcard|storage/emulated/0)/Android/(?:data|media)/'
        r'com\.architectpsg\.commandlibrary\.feature\.shell(?:\.test)?/'
        r'[A-Za-z0-9_./-]+', path)) and '..' not in pathlib.PurePosixPath(path).parts


def collect(root, output, adb):
    output.mkdir(parents=True, exist_ok=True)
    for path in root.glob('**/build/outputs/connected_android_test_additional_output/**/*.png'):
        if path.name in EXPECTED and valid_png(path.read_bytes()):
            shutil.copyfile(path, output / path.name)
    visual_cases = []
    device_paths = set()
    for report in root.glob('**/build/outputs/androidTest-results/connected/**/TEST-*.xml'):
        tree = ET.parse(report).getroot()
        visual_cases.extend(case for case in tree.iter('testcase')
                            if 'VisualSystemAcceptanceTest' in case.get('classname', ''))
        for line in ''.join(tree.itertext()).splitlines():
            marker = 'VISUAL_CAPTURE_PATH='
            if marker in line:
                path = line.split(marker, 1)[1].strip()
                if safe_device_path(path) and pathlib.PurePosixPath(path).name in EXPECTED:
                    device_paths.add(path)
    for path in sorted(device_paths):
        destination = output / pathlib.PurePosixPath(path).name
        if destination.exists():
            continue
        result = subprocess.run([adb, 'exec-out', 'cat', path], capture_output=True, timeout=30)
        if result.returncode == 0 and valid_png(result.stdout):
            destination.write_bytes(result.stdout)
    missing = EXPECTED - {path.name for path in output.glob('*.png') if valid_png(path.read_bytes())}
    print(f'Visual captures retained: {len(EXPECTED) - len(missing)}/{len(EXPECTED)}')
    if missing:
        print('Missing synthetic captures:', ', '.join(sorted(missing)))
    # Failed suites retain partial evidence; passing suites must retain the complete matrix.
    passed = len(visual_cases) == 3 and all(
        not any(case.find(kind) is not None for kind in ('failure', 'error', 'skipped'))
        for case in visual_cases)
    if passed and missing:
        raise SystemExit('Passing visual suite has incomplete screenshot evidence')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=pathlib.Path, default=pathlib.Path('android-app'))
    parser.add_argument('--output', type=pathlib.Path, default=pathlib.Path('visual-captures'))
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args()
    collect(args.root, args.output, args.adb)
