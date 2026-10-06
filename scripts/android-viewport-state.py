#!/usr/bin/env python3
"""Validate effective WMS size/density: an override takes precedence over physical."""
import json
import pathlib
import re
import sys


def effective(path, metric):
    values = {}
    for line in pathlib.Path(path).read_text().splitlines():
        match = re.fullmatch(rf'(Physical|Override) {metric}: (\d+(?:x\d+)?)', line.strip())
        if not match and line.strip().startswith((f'Physical {metric}:', f'Override {metric}:')):
            raise ValueError(f'Malformed {metric}: {line}')
        if match:
            kind, value = match.groups()
            if kind in values:
                raise ValueError(f'Ambiguous {metric}: duplicate {kind}')
            values[kind] = value
    if 'Physical' not in values:
        raise ValueError(f'Missing physical {metric}')
    return values.get('Override', values['Physical'])


if __name__ == '__main__':
    try:
        size_path, density_path, requested_size, requested_density = sys.argv[1:]
        size = effective(size_path, 'size')
        density = effective(density_path, 'density')
        if (size, density) != (requested_size, requested_density):
            raise ValueError(f'Viewport mismatch: observed {size}/{density}, requested {requested_size}/{requested_density}')
        print(json.dumps({'effective_size': size, 'effective_density': int(density), 'result': 'PASS'}))
    except (ValueError, OSError) as error:
        raise SystemExit(str(error))
