#!/usr/bin/env python3
"""Prepare a lossless resume-state proposal outside the source checkout.

Only the orchestrator may integrate the proposed state and archive at a checkpoint.
This tool never edits source state, leases, locks, mirrors or checkpoint history.
"""
import argparse
import hashlib
import json
from pathlib import Path


def prepare(root, output, keep):
    root, output = root.resolve(), output.resolve()
    if output == root or root in output.parents:
        raise ValueError('Output must be outside the source repository')
    original = (root / 'coordination/state.json').read_bytes()
    state = json.loads(original)
    verification = state['verification']
    if not isinstance(verification, dict):
        raise ValueError('Expected verification object')
    missing = set(keep) - verification.keys()
    if missing:
        raise ValueError('Unknown verification keys: ' + ', '.join(sorted(missing)))
    if not keep:
        raise ValueError('Keep at least one explicitly reviewed current verification key')
    digest = hashlib.sha256(original).hexdigest()
    archive = f'coordination/history/resume-state-{digest}.json'
    state['verification'] = {key: value for key, value in verification.items() if key in keep}
    # Any previous archive pointer remains inside this exact historical snapshot.
    state['verification_archive'] = {'path': archive, 'sha256': digest,
                                     'checkpoint_id': state['checkpoint_id'],
                                     'verification_entries': len(verification)}
    candidate = (json.dumps(state, indent=2) + '\n').encode()
    output.mkdir(parents=True, exist_ok=False)
    archive_path = output / archive
    archive_path.parent.mkdir(parents=True)
    with archive_path.open('xb') as file:
        file.write(original)
    with (output / 'coordination/state.json').open('xb') as file:
        file.write(candidate)
    return len(original), len(candidate)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output', type=Path, required=True,
                        help='New directory outside source checkout; never overwritten')
    parser.add_argument('--keep', action='append', required=True,
                        help='Explicit current verification key; repeat as needed')
    args = parser.parse_args()
    try:
        before, after = prepare(args.root, args.output, args.keep)
    except (ValueError, OSError, KeyError) as exc:
        parser.exit(1, str(exc) + '\n')
    print(f'Prepared proposal only: {before} -> {after} bytes. Live state unchanged.')


if __name__ == '__main__':
    main()
