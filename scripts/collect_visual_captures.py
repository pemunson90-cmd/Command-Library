"""Retain only the synthetic visual acceptance matrix, never app data."""
import argparse
import pathlib
import re
import shutil
import struct
import subprocess
import xml.etree.ElementTree as ET
import zlib

EXPECTED = {
    f"synthetic-{view}-{size}-{theme}-{scale}.png"
    for view in ('search', 'detail', 'life')
    for size in ('360x800', '900x800', '360x360')
    for theme in ('light', 'dark')
    for scale in ('1.0', '2.0')
}


def valid_png(data):
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        return False
    offset, header, compressed, ended = 8, None, bytearray(), False
    while offset + 12 <= len(data):
        length = struct.unpack('>I', data[offset:offset + 4])[0]
        end = offset + 12 + length
        if end > len(data):
            return False
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:end - 4]
        if zlib.crc32(kind + payload) != struct.unpack('>I', data[end - 4:end])[0]:
            return False
        if kind == b'IHDR':
            if offset != 8 or length != 13:
                return False
            header = struct.unpack('>IIBBBBB', payload)
        elif kind == b'IDAT':
            compressed.extend(payload)
        elif kind == b'IEND':
            ended = length == 0 and end == len(data)
            break
        offset = end
    if not header or not ended or not compressed:
        return False
    width, height, depth, color, compression, filtering, interlace = header
    if not (0 < width <= 16000 and 0 < height <= 16000 and width * height <= 16000000):
        return False
    if depth != 8 or color not in (2, 6) or (compression, filtering, interlace) != (0, 0, 0):
        return False
    expected = height * (1 + width * (3 if color == 2 else 4))
    try:
        decoder = zlib.decompressobj()
        pixels = decoder.decompress(compressed, expected + 1)
        return decoder.eof and not decoder.unused_data and len(pixels) == expected
    except zlib.error:
        return False


def safe_device_path(path):
    return bool(re.fullmatch(
        r'/(?:sdcard|storage/emulated/0)/Android/(?:data|media)/'
        r'com\.architectpsg\.commandlibrary\.feature\.shell(?:\.test)?/'
        r'(?:files/visual-acceptance|(?:files/)?additional_test_output)/'
        r'synthetic-[A-Za-z0-9_.-]+\.png', path)) and '..' not in pathlib.PurePosixPath(path).parts


def collect(root, output, adb):
    output.mkdir(parents=True, exist_ok=True)
    for path in root.glob('**/build/outputs/connected_android_test_additional_output/**/*.png'):
        if path.name in EXPECTED and valid_png(path.read_bytes()):
            shutil.copyfile(path, output / path.name)
    visual_cases = []
    device_paths = set()
    def add_capture_paths(lines):
        for line in lines:
            marker = 'VISUAL_CAPTURE_PATH='
            if marker in line:
                path = line.split(marker, 1)[1].strip()
                if safe_device_path(path) and pathlib.PurePosixPath(path).name in EXPECTED:
                    device_paths.add(path)

    for report in root.glob('**/build/outputs/androidTest-results/connected/**/TEST-*.xml'):
        tree = ET.parse(report).getroot()
        visual_cases.extend(case for case in tree.iter('testcase')
                            if 'VisualSystemAcceptanceTest' in case.get('classname', ''))
        add_capture_paths(''.join(tree.itertext()).splitlines())
    # API23's connected runner retains System.out in per-test logcat files, not XML.
    for logcat in root.glob('**/build/outputs/androidTest-results/connected/**/logcat-*VisualSystemAcceptanceTest*.txt'):
        add_capture_paths(logcat.read_text(errors='replace').splitlines())
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
    passed = bool(visual_cases) and all(
        not any(case.find(kind) is not None for kind in ('failure', 'error'))
        for case in visual_cases)
    expected_suite = any(root.glob('**/src/androidTest/**/VisualSystemAcceptanceTest.kt'))
    if expected_suite and not visual_cases:
        raise SystemExit('Visual acceptance source has no executed cases')
    unique_cases = {(case.get('classname'), case.get('name')) for case in visual_cases
                    if case.find('skipped') is None}
    if passed and (missing or len(unique_cases) < 36):
        raise SystemExit('Passing visual suite has incomplete matrix evidence')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=pathlib.Path, default=pathlib.Path('android-app'))
    parser.add_argument('--output', type=pathlib.Path, default=pathlib.Path('visual-captures'))
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args()
    collect(args.root, args.output, args.adb)
