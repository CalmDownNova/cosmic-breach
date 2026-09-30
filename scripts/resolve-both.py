"""Resolve git conflict hunks by keeping both sides (ours first). For additive registration lines only.

Usage: python scripts/resolve-both.py <file> [<file> ...]
"""
import re
import sys

PATTERN = re.compile(r'<<<<<<< [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n', re.S)

for path in sys.argv[1:]:
    with open(path, encoding='utf-8', newline='') as f:
        text = f.read()
    resolved, count = PATTERN.subn(lambda m: m.group(1) + m.group(2), text)
    with open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(resolved)
    print(f"{path}: kept both sides of {count} hunk(s)")
