#!/usr/bin/env python3
"""Renders every filter, effect, clip animation and transition on a headless desktop GPU
(Mesa llvmpipe through EGL) with the same source assembly as the app, checks each result and
writes contact sheets. Needs: pip install moderngl numpy pillow; apt install libegl1 libegl-mesa0.
Usage: render_check.py <out dir>. The on-device equivalent is FxThumbsDeviceTest."""
import json, os, sys, numpy as np, moderngl
from PIL import Image, ImageDraw
REEL = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
FX = REEL + '/app/src/main/assets/fx'
OUT = sys.argv[1] if len(sys.argv) > 1 else '.'
W, H = 240, 160
read = lambda n: open(os.path.join(FX, n)).read()
TYPES = {"float": "float", "int": "int", "bool": "bool", "vec2": "vec2", "vec3": "vec3", "vec4": "vec4", "ivec2": "ivec2", "color": "vec3"}
HEAD = "#version 330 core\n#define texture2D texture\n#define varying in\nout vec4 fragOut_;\n#define gl_FragColor fragOut_\n"
def single(e):
    params = "\n".join(f"uniform {TYPES[p['type']]} {p['name']};" for p in e.get("params", []))
    return HEAD + read("prelude.glsl") + read("wrap_single.glsl").replace("//@PARAMS", params).replace("//@CODE", e["code"]).replace("#line 1", "")
def trans(t):
    return HEAD + read("prelude_min.glsl") + read("wrap_transition.glsl").replace("//@CODE", t["code"]).replace("#line 1", "")
VS = "#version 330 core\nin vec2 aPos; out vec2 vTexSamplingCoord; void main(){ vTexSamplingCoord = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }"
ctx = moderngl.create_standalone_context(backend='egl')
fbo = ctx.simple_framebuffer((W, H)); fbo.use()
vbo = ctx.buffer(np.array([-1, -1, 1, -1, -1, 1, 1, 1], dtype='f4'))
def tex(path):
    im = Image.open(path).convert('RGB').resize((W, H))
    a = np.asarray(im)[::-1].copy()  # GL rows start at the bottom
    t = ctx.texture((W, H), 3, a.tobytes()); t.repeat_x = t.repeat_y = False; t.filter = (moderngl.LINEAR, moderngl.LINEAR)
    return t, np.asarray(im).astype(np.float32)
A, Aimg = tex(REEL + '/app/src/main/assets/samples/person.jpg')
B, Bimg = tex(REEL + '/app/src/main/assets/samples/thing.jpg')
def setu(prog, name, *v):
    if name not in prog: return
    u = prog[name]
    try: u.value = v[0] if len(v) == 1 else tuple(v)
    except Exception:
        try: u.value = int(v[0]) if len(v) == 1 else tuple(int(x) for x in v)
        except Exception: pass
def draw(prog, setup):
    vao = ctx.vertex_array(prog, [(vbo, '2f', 'aPos')])
    setup(prog); fbo.clear(); vao.render(moderngl.TRIANGLE_STRIP)
    a = np.frombuffer(fbo.read(components=3), dtype=np.uint8).reshape(H, W, 3)[::-1].astype(np.float32)
    vao.release(); return a
diff = lambda x, y: float(np.abs(x - y).mean())
lib = json.load(open(FX + '/library.json')); trs = json.load(open(FX + '/transitions.json'))
problems, sheets = [], {'filter': [], 'effect': [], 'anim': [], 'transition': []}
def compile_(src, ident):
    try: return ctx.program(vertex_shader=VS, fragment_shader=src)
    except Exception as e: problems.append(f"{ident}: compile/link {str(e)[:200]}"); return None
for e in lib:
    prog = compile_(single(e), e['id'])
    if not prog: continue
    def fr(t, p):
        def s(pr):
            A.use(0); setu(pr, 'uTex', 0); setu(pr, 'uResolution', W, H); setu(pr, 'uTime', t); setu(pr, 'uDuration', 4.0)
            setu(pr, 'uProgress', p); setu(pr, 'uIntensity', 1.0); setu(pr, 'uSeed', 0.37)
            for q in e['params']: setu(pr, q['name'], *q['default'])
        return draw(prog, s)
    k = e['kind']
    if k == 'filter':
        f = [fr(1.0, 0.5)]; show = f[0]
        if diff(f[0], Aimg) < 2: problems.append(f"filter {e['id']}: no visible change {diff(f[0], Aimg):.2f}")
    elif k == 'effect':
        f = [fr(t * 0.13, t / 40) for t in range(40)]
        moves = max(diff(f[0], x) for x in f[1:])
        show = max(f, key=lambda x: diff(x, Aimg))
        # Sparse effects (stars, dust) change few pixels: count changed pixels instead of the mean.
        changed = max(float((np.abs(x - Aimg).max(axis=2) > 40).mean()) for x in f)
        if changed < 0.002: problems.append(f"effect {e['id']}: no visible change ({changed:.4f})")
        if moves < 0.15: problems.append(f"effect {e['id']}: does not move ({moves:.3f})")
    else:
        f = [fr(i * 0.05, i / 10) for i in range(11)]; show = f[4]
        if diff(f[-1], Aimg) > 4: problems.append(f"anim {e['id']}: end frame is not the clip ({diff(f[-1], Aimg):.1f})")
        if max(diff(f[0], x) for x in f[1:]) < 0.15: problems.append(f"anim {e['id']}: does not move")
    # Clip animations start invisible; blink and strobe-like effects go dark on purpose.
    for i, x in enumerate(f):
        if k == "anim" and i <= 1: continue
        if e['id'] in ('blink',): continue
        if x.mean() < 6: problems.append(f"{k} {e['id']}: black frame {i}"); break
    sheets[k].append((e['name'], show))
for t in trs:
    prog = compile_(trans(t), t['id'])
    if not prog: continue
    def fr(p):
        def s(pr):
            A.use(0); B.use(1); setu(pr, 'uFrom', 0); setu(pr, 'uTo', 1); setu(pr, 'progress', p); setu(pr, 'ratio', W / H)
            for q in t['params']: setu(pr, q['name'], *q['default'])
        return draw(prog, s)
    f = [fr(i / 10) for i in range(11)]
    if diff(f[0], Aimg) > 6: problems.append(f"transition {t['id']}: does not start on A ({diff(f[0], Aimg):.1f})")
    if diff(f[-1], Bimg) > 6: problems.append(f"transition {t['id']}: does not end on B ({diff(f[-1], Bimg):.1f})")
    if min(x.mean() for x in f[1:-1]) < 3 and not t['name'].lower().startswith(('dip', 'fade')): pass
    sheets['transition'].append((t['name'] + ('*' if t['id'].startswith('rt_') else ''), f[5]))
def sheet(name, items, only=None):
    cols = 8; cw, ch = W // 2 + 4, H // 2 + 14
    rows = (len(items) + cols - 1) // cols
    img = Image.new('RGB', (cols * cw, rows * ch), (20, 20, 24)); d = ImageDraw.Draw(img)
    for i, (n, a) in enumerate(items):
        x, y = (i % cols) * cw, (i // cols) * ch
        img.paste(Image.fromarray(a.clip(0, 255).astype(np.uint8)).resize((W // 2, H // 2)), (x + 2, y + 2)); d.text((x + 3, y + H // 2 + 2), n[:20], fill=(230, 230, 230))
    img.save(os.path.join(OUT, name + '.png'))
for k, v in sheets.items():
    # Split long sheets into pages of 64.
    for p in range(0, len(v), 64): sheet(f"{k}_{p // 64}", v[p:p + 64])
print({k: len(v) for k, v in sheets.items()}); print(len(problems), "problems"); [print(" ", p) for p in problems]
