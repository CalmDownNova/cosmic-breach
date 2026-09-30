"""Resolve a conflicted JSON object file (sounds.json, lang, manifests) during a git merge by taking
the union of both branches' keys: ours first, then theirs' new keys. Fails if a key differs.

Usage (during a conflicted merge): python scripts/merge-json.py <file> [<file> ...]
"""
import collections
import json
import subprocess
import sys


def staged(stage, path):
    return subprocess.run(["git", "show", f":{stage}:{path}"], capture_output=True, text=True,
                          encoding="utf-8", check=True).stdout


for path in sys.argv[1:]:
    raw_ours = staged(2, path)
    ours = json.loads(raw_ours, object_pairs_hook=collections.OrderedDict)
    theirs = json.loads(staged(3, path), object_pairs_hook=collections.OrderedDict)
    merged = collections.OrderedDict(ours)
    for key, value in theirs.items():
        if key in merged and merged[key] != value:
            sys.exit(f"{path}: key {key!r} differs between branches; resolve by hand")
        merged.setdefault(key, value)
    newline = "\r\n" if "\r\n" in raw_ours else "\n"
    text = json.dumps(merged, indent=2, ensure_ascii=False)
    if newline == "\r\n":
        text = text.replace("\n", "\r\n")
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text + newline)
    print(f"{path}: {len(ours)} + {len(theirs)} -> {len(merged)} keys")
