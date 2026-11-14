#!/usr/bin/env python3
"""Converts the gl-transitions npm package into the app's transition catalog.

Usage: import_gl_transitions.py <path to gl-transitions.json> <out assets/fx dir>
Keeps transitions that need no extra textures. Every kept transition is later
compiled by validate_shaders.py with the same wrapper the app uses at runtime.
"""
import json
import re
import sys

src, out_dir = sys.argv[1], sys.argv[2]
data = json.load(open(src))

NICE = {
    "fade": "Dissolve", "fadecolor": "Dip to color", "fadegrayscale": "Grey fade",
    "crosswarp": "Cross warp", "crosszoom": "Cross zoom", "directionalwarp": "Warp",
}

def nice_name(n):
    if n.lower() in NICE:
        return NICE[n.lower()]
    n = n.replace("_", " ")
    n = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", n)
    n = re.sub(r"(?<=[A-Za-z])(?=[0-9])", " ", n)
    return n[:1].upper() + n[1:]

def category(t):
    tags = " ".join(t.get("tags") or []).lower() + " " + t["name"].lower()
    for cat, words in [
        ("Glitch", ["glitch", "digital", "pixel", "noise", "static"]),
        ("Zoom", ["zoom", "scale", "spin", "rotate", "swirl", "vortex"]),
        ("Slide", ["slide", "swipe", "push", "wipe", "direction", "cube", "door", "book", "page", "fold", "flip", "angular"]),
        ("Shape", ["circle", "heart", "star", "polka", "hexagon", "square", "grid", "mosaic", "radial", "window", "bow", "diamond"]),
        ("Light", ["burn", "luma", "flash", "light", "fire", "bright", "color"]),
        ("Warp", ["warp", "ripple", "wave", "morph", "water", "displace", "dreamy", "kaleido", "wind"]),
    ]:
        if any(w in tags for w in words):
            return cat
    return "Basic"

out = []
skipped = []
for t in sorted(data, key=lambda t: t["name"].lower()):
    if t.get("textures") or any(v == "sampler2D" for v in (t.get("paramsTypes") or {}).values()):
        skipped.append(t["name"])
        continue
    params = []
    for k, ty in (t.get("paramsTypes") or {}).items():
        d = t["defaultParams"].get(k)
        if isinstance(d, bool):
            d = 1 if d else 0
        params.append({"name": k, "type": ty, "default": d if isinstance(d, list) else [d]})
    out.append({
        "id": "glt_" + re.sub(r"[^a-z0-9]+", "_", t["name"].lower()).strip("_"),
        "name": nice_name(t["name"]),
        "category": category(t),
        "author": t.get("author", ""),
        "license": t.get("license", "MIT"),
        "params": params,
        "code": t["glsl"],
    })

json.dump(out, open(f"{out_dir}/transitions.json", "w"), indent=0)
print(f"kept {len(out)}, skipped {len(skipped)} needing textures: {', '.join(skipped)}")
