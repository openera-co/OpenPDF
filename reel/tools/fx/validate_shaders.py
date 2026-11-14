#!/usr/bin/env python3
"""Compiles every shader in the catalog exactly as ShaderSource.kt assembles it at runtime.

Exit code 1 if any shader fails. Optional --prune removes failing transitions from the
catalog (they come from a third-party collection; our own shaders must never fail).
"""
import json
import os
import subprocess
import sys
import tempfile

FX = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "fx")
read = lambda n: open(os.path.join(FX, n)).read()
GLSL_TYPES = {"float": "float", "int": "int", "bool": "bool", "vec2": "vec2", "vec3": "vec3", "vec4": "vec4", "ivec2": "ivec2", "color": "vec3"}


def assemble_transition(code):
    return "#version 100\n" + read("prelude_min.glsl") + read("wrap_transition.glsl").replace("//@CODE", code)


def assemble_single(entry):
    params = "\n".join(f"uniform {GLSL_TYPES[p['type']]} {p['name']};" for p in entry.get("params", []))
    body = read("wrap_single.glsl").replace("//@PARAMS", params).replace("//@CODE", entry["code"])
    return "#version 100\n" + read("prelude.glsl") + body


def compile_frag(src):
    with tempfile.NamedTemporaryFile("w", suffix=".frag", delete=False) as f:
        f.write(src)
        path = f.name
    r = subprocess.run(["glslangValidator", path], capture_output=True, text=True)
    os.unlink(path)
    return r.returncode == 0, (r.stdout + r.stderr).strip()


def main():
    prune = "--prune" in sys.argv
    failures = 0
    vr = subprocess.run(["glslangValidator", os.path.join(FX, "vertex.vert")], capture_output=True, text=True)
    if vr.returncode:
        print("vertex shader failed:", vr.stdout)
        failures += 1

    trans = json.load(open(os.path.join(FX, "transitions.json")))
    kept, bad = [], []
    for t in trans:
        ok, log = compile_frag(assemble_transition(t["code"]))
        (kept if ok else bad).append(t)
        if not ok:
            print(f"[transition] {t['id']}: {log.splitlines()[1] if len(log.splitlines()) > 1 else log}")
    if bad and prune:
        json.dump(kept, open(os.path.join(FX, "transitions.json"), "w"), indent=0)
        print(f"pruned {len(bad)} transitions")
    elif bad:
        failures += len(bad)
    print(f"transitions: {len(kept)} compile, {len(bad)} fail")

    lib_path = os.path.join(FX, "library.json")
    if os.path.exists(lib_path):
        lib = json.load(open(lib_path))
        nbad = 0
        for e in lib:
            ok, log = compile_frag(assemble_single(e))
            if not ok:
                nbad += 1
                print(f"[{e['kind']}] {e['id']}:\n{log}")
        failures += nbad
        kinds = {}
        for e in lib:
            kinds[e["kind"]] = kinds.get(e["kind"], 0) + 1
        print(f"library: {len(lib) - nbad} compile, {nbad} fail; {kinds}")
    idir = os.path.join(FX, "internal")
    for name in sorted(os.listdir(idir)):
        ok, log = compile_frag("#version 100\n" + read("prelude.glsl") + "#line 1\n" + open(os.path.join(idir, name)).read())
        print(f"internal {name}: {'ok' if ok else 'FAIL'}")
        if not ok:
            print(log)
            failures += 1
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
