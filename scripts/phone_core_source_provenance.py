"""Bound the source-commit claim to all Android modules, configuration and qualification tools."""
import subprocess


def dirty_build_inputs(root):
    result = subprocess.check_output(['git', '-C', str(root), 'status', '--porcelain=v1', '-z',
        '--untracked-files=all', '--', 'native-android-app', 'scripts/qualify-phone-core-v1.py',
        'scripts/verify-phone-core-v1-release.py', 'scripts/phone_core_source_provenance.py'], text=True)
    # Only untracked generated Room output is allowed; a changed tracked schema is a source change.
    return [entry for entry in result.split('\0') if entry and
            not entry.startswith('?? native-android-app/app/schemas/')]
