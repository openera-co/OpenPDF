#!/usr/bin/env python3
"""Builds assets/fx/library.json from tools/fx/library.glsl (filters, effects, clip animations)."""
import json
import os
import re

HERE = os.path.dirname(__file__)
src = open(os.path.join(HERE, "library.glsl")).read()
out = []
blocks = re.split(r"(?m)^//== id: ", src)[1:]
for b in blocks:
    lines = b.splitlines()
    head = [x.strip() for x in lines[0].split("|")]
    ident, name, kind, cat = head
    params, code = [], []
    for ln in lines[1:]:
        if ln.startswith("//== param:"):
            parts = ln[len("//== param:"):].split()
            pname, ptype, pdef = parts[0], parts[1], parts[2]
            pmin, pmax = float(parts[3]), float(parts[4])
            params.append({"name": pname, "type": ptype, "default": [float(v) for v in pdef.split(",")], "min": pmin, "max": pmax})
        elif ln.startswith("// ----"):
            continue
        else:
            code.append(ln)
    body = "\n".join(code).strip()
    # Trailing comments belong to the next section, not this shader.
    body = re.sub(r"(\n//[^\n]*)+$", "", body)
    out.append({"id": ident, "name": name, "kind": kind, "category": cat, "params": params, "code": body})

ids = [e["id"] for e in out]
assert len(ids) == len(set(ids)), "duplicate ids"
json.dump(out, open(os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "fx", "library.json"), "w"), indent=0)
kinds = {}
for e in out:
    kinds[e["kind"]] = kinds.get(e["kind"], 0) + 1
print(len(out), kinds)
