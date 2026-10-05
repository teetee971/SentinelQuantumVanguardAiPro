"""Read user-0 role holders from known dumpsys formats; unreadable is never absent."""

import re
import sys


def holders_from_dump(role, text):
    text = text.replace("\r", "")
    users = set(re.findall(r"(?:user_id|mUserId|userId)\s*[=:]\s*(\d+)", text))
    if users != {"0"} or text.count("{") != text.count("}"):
        raise ValueError("Unknown or ambiguous role dump scope")

    def packages(value):
        value = value.strip()
        if value.startswith("[") and value.endswith("]"):
            value = value[1:-1]
        values = [part for part in re.split(r"[,\s]+", value) if part]
        if any(not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+", item) for item in values):
            raise ValueError("Unreadable holders")
        return values

    compact = re.findall(r"(?<![A-Za-z0-9_.])" + re.escape(role) + r"\s*[=:]\s*\[([^\[\]]*)\]", text)
    if compact:
        if len(compact) != 1:
            raise ValueError("Ambiguous compact role")
        return packages(compact[0])

    # Android 10 emits complete leaf blocks with only name=ROLE when no holder exists.
    # A missing holders field is meaningful only inside a complete, named role block.
    blocks = re.findall(r"\{([^{}]*)\}", text)
    matching = [block for block in blocks if re.search(
        r"^\s*name\s*[=:]\s*" + re.escape(role) + r"\s*$", block, re.M
    )]
    if len(matching) != 1:
        raise ValueError("Role absent from dump or ambiguous")
    fields = re.findall(r"^\s*holders\s*[=:]\s*([^\n]*)$", matching[0], re.M)
    if len(fields) > 1:
        raise ValueError("Ambiguous holders")
    return packages(fields[0]) if fields else []


if __name__ == "__main__":
    try:
        role, path = sys.argv[1:]
        with open(path, encoding="utf-8", errors="strict") as source:
            holders = holders_from_dump(role, source.read())
        for holder in holders:
            print(holder)
    except (ValueError, OSError, UnicodeError):
        print("Role oracle unreadable", file=sys.stderr)
        sys.exit(1)
