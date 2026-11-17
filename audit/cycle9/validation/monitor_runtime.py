#!/usr/bin/env python3
"""Collect own inference-process observations. Never declares Android/AI qualification PASS."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import time


def status(pid):
    values = {}
    for line in Path(f'/proc/{pid}/status').read_text().splitlines():
        if ':' in line:
            key, value = line.split(':', 1);values[key] = value.strip()
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--service-pid', required=True, type=int)
    parser.add_argument('--binary', required=True, type=Path)
    parser.add_argument('--seconds', type=float, default=90)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if not 0 < args.seconds <= 600:parser.error('duration must be in (0,600]')
    uid = status(os.getpid())['Uid'].split()[0]
    if status(args.service_pid)['Uid'].split()[0] != uid:parser.error('service must belong to this user')
    with args.binary.open('rb') as source:digest = hashlib.file_digest(source, 'sha256').hexdigest()
    report = {'machine':platform.machine(), 'kernel':platform.release(), 'service_pid':args.service_pid,
              'binary_sha256':digest, 'android_device_verified':False, 'automatic_verdict':'NONE', 'frames':[]}
    started = time.monotonic();seen = False
    while time.monotonic()-started < args.seconds:
        frame = {'elapsed_s':round(time.monotonic()-started,3), 'service_alive':False, 'models':[], 'errors':[]}
        try:
            service = status(args.service_pid)
            frame['service_alive'] = True
            frame['service_fds'] = len(list(Path(f'/proc/{args.service_pid}/fd').iterdir()))
            child_file=Path(f'/proc/{args.service_pid}/task/{args.service_pid}/children')
            if child_file.exists():children=child_file.read_text().split()
            else:
                children=[]
                for item in Path('/proc').iterdir():
                    if not item.name.isdigit():continue
                    try:
                        info=status(item.name)
                        if info.get('PPid')==str(args.service_pid) and info.get('Uid','').split()[0]==uid:children.append(item.name)
                    except (OSError,IndexError):continue
            for pid in children:
                try:
                    info = status(pid)
                    if info['Uid'].split()[0] != uid:continue
                    executable = Path(f'/proc/{pid}/exe')
                    if executable.readlink().name != args.binary.name:continue
                    with executable.open('rb') as source:actual = hashlib.file_digest(source,'sha256').hexdigest()
                    if actual != digest:
                        frame['errors'].append('child binary fingerprint mismatch');continue
                    frame['models'].append({'pid':int(pid),'rss':info.get('VmRSS','unknown'),'threads':info.get('Threads','unknown')})
                    seen = True
                except FileNotFoundError:pass  # Child exited during the snapshot.
                except (PermissionError,OSError,KeyError) as error:frame['errors'].append(type(error).__name__)
        except (OSError,KeyError) as error:frame['errors'].append(type(error).__name__)
        report['frames'].append(frame)
        time.sleep(.2)
    report['observed_model_process'] = seen
    report['inconclusive_if_no_model_observed'] = not seen
    # Exclusive creation: never replace previous evidence, keys or user files.
    with args.output.open('x') as output:json.dump(report,output,indent=2)
    print(json.dumps({'output':str(args.output),'observed_model_process':seen,'automatic_verdict':'NONE'}))


if __name__ == '__main__':main()
