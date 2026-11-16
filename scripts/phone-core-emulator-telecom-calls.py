"""Qualify teardown from Telecom's live mCalls section, never historical events.

A console response containing only OK is not an absence oracle. Unknown/truncated
Telecom formats fail closed. Used only by the developer emulator harness.
"""
import re
import sys


def no_live_calls(text):
    lines = text.replace("\r", "").splitlines()
    roots = [i for i, line in enumerate(lines) if line.strip() == "CallsManager:"]
    if len(roots) != 1:
        raise ValueError("Missing or ambiguous CallsManager")
    root = roots[0]
    sections = [i for i in range(root + 1, len(lines)) if lines[i].strip() == "mCalls:"]
    if len(sections) != 1:
        raise ValueError("Missing or ambiguous live call section")
    start = sections[0]
    indent = len(lines[start]) - len(lines[start].lstrip())
    if indent <= len(lines[root]) - len(lines[root].lstrip()):
        raise ValueError("Invalid live call section scope")
    entries = []
    for line in lines[start + 1:]:
        if not line.strip():
            continue
        current_indent = len(line) - len(line.lstrip())
        if current_indent <= indent:
            if current_indent != indent or not re.fullmatch(r"m[A-Za-z0-9]+:", line.strip()):
                raise ValueError("Unknown live call section boundary")
            # Any entry still owned by CallsManager prevents teardown qualification,
            # including DISCONNECTED entries not yet removed by Telecom.
            return not entries
        if not re.match(r"\[Call id=[^,]+, state=[A-Z_]+,", line.strip()):
            raise ValueError("Unreadable live call entry")
        entries.append(line)
    raise ValueError("Truncated live call section")


if __name__ == "__main__":
    try:
        with open(sys.argv[1], encoding="utf-8", errors="strict") as source:
            absent = no_live_calls(source.read())
        sys.exit(0 if absent else 1)
    except (ValueError, OSError, UnicodeError, IndexError) as error:
        print("Telecom teardown oracle UNKNOWN: " + str(error), file=sys.stderr)
        sys.exit(2)
