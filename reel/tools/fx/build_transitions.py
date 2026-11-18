#!/usr/bin/env python3
"""Merges Reel's own transitions (tools/fx/reel_transitions.glsl) into assets/fx/transitions.json.

Blocks start with "//== id: rt_name | Display name | Category". Earlier rt_ entries are replaced,
imported gl-transitions are left alone. Run validate_shaders.py afterwards.
"""
import json
import os
import re

HERE = os.path.dirname(__file__)
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "fx", "transitions.json")
src = open(os.path.join(HERE, "reel_transitions.glsl")).read()
ours = []
for b in re.split(r"(?m)^//== id: ", src)[1:]:
    lines = b.splitlines()
    ident, name, cat = [x.strip() for x in lines[0].split("|")]
    assert ident.startswith("rt_"), ident
    body = "\n".join(lines[1:]).strip()
    body = re.sub(r"(\n//[^\n]*)+$", "", body)
    ours.append({"id": ident, "name": name, "category": cat, "author": "Reel", "license": "MIT", "params": [], "code": body})
existing = [t for t in json.load(open(OUT)) if not t["id"].startswith("rt_")]
names = {t["name"].lower() for t in existing}
for t in ours:
    assert t["name"].lower() not in names, "name taken: " + t["name"]
ids = [t["id"] for t in existing + ours]
assert len(ids) == len(set(ids)), "duplicate ids"
json.dump(existing + ours, open(OUT, "w"), indent=0)
print(len(existing), "imported +", len(ours), "own =", len(existing) + len(ours))
