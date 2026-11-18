#!/usr/bin/env python3
"""Runs every filter and effect over real footage (make_test_clip.py) on a headless GPU, with the
app's own shader assembly, and reports what still pictures cannot show:

  flicker  output changes much more from frame to frame than the footage does (filters only:
           a filter is a look, it must not shimmer; effects are reported for review)
  dead     runs of near-black or near-white frames
  clip     shadows or highlights crushed far beyond the footage's own
  scale    the look at 1080p export differs from the 720p preview, with uResolution as the pass
           size (legacy) and as the 720p-normalised size ClipRenderer now uses

Writes report.txt, strips of the flagged ones, and montage.mp4 (two seconds of each id given
with --montage). Usage: fx_video_check.py <clip.npy> <out dir> [--ids a,b] [--montage a,b]
"""
import json, os, sys, subprocess
import numpy as np
import moderngl
from PIL import Image

REEL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
FX = REEL + "/app/src/main/assets/fx"
read = lambda n: open(os.path.join(FX, n)).read()
TYPES = {"float": "float", "int": "int", "bool": "bool", "vec2": "vec2", "vec3": "vec3", "vec4": "vec4", "ivec2": "ivec2", "color": "vec3"}
HEAD = "#version 330 core\n#define texture2D texture\n#define varying in\nout vec4 fragOut_;\n#define gl_FragColor fragOut_\n"
VS = "#version 330 core\nin vec2 aPos; out vec2 vTexSamplingCoord; void main(){ vTexSamplingCoord = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }"
# Effects that flash, strobe or blink on purpose: flicker is their job.
FLASHY = {"strobe", "blink", "colorstrobe", "beatflash", "frameflash", "invertflash", "neonflicker", "lightning",
          "thunder", "tvstatic", "digitalnoise", "signalloss", "corrupt", "bytecrush", "rgbjitter", "scantear",
          "linejitter", "glitch", "blockglitch", "datamosh", "glitchpulse", "framedrop", "disco", "partylights",
          "club", "discofloor", "rave", "posterpulse", "glitchcolor", "teletext", "camcorder", "vhsrewind"}


def single(e):
    params = "\n".join(f"uniform {TYPES[p['type']]} {p['name']};" for p in e.get("params", []))
    return HEAD + read("prelude.glsl") + read("wrap_single.glsl").replace("//@PARAMS", params).replace("//@CODE", e["code"]).replace("#line 1", "")


def main():
    clip = np.load(sys.argv[1])[::2]  # 15 fps is enough to see flicker and saves half the time
    out = sys.argv[2]
    os.makedirs(out, exist_ok=True)
    args = dict(zip(sys.argv[3::2], sys.argv[4::2]))
    only = set(args["--ids"].split(",")) if "--ids" in args else None
    montage = args.get("--montage", "").split(",") if "--montage" in args else []
    n, H, W, _ = clip.shape
    fps = 15.0
    ctx = moderngl.create_standalone_context(backend="egl")
    vbo = ctx.buffer(np.array([-1, -1, 1, -1, -1, 1, 1, 1], dtype="f4"))
    fbos = {}

    def fbo(w, h):
        if (w, h) not in fbos: fbos[(w, h)] = ctx.simple_framebuffer((w, h))
        return fbos[(w, h)]

    def tex(a):
        h, w, _ = a.shape
        t = ctx.texture((w, h), 3, np.ascontiguousarray(a[::-1]).tobytes())
        t.repeat_x = t.repeat_y = False
        t.filter = (moderngl.LINEAR, moderngl.LINEAR)
        return t

    def setu(prog, name, *v):
        if name not in prog: return
        try: prog[name].value = v[0] if len(v) == 1 else tuple(v)
        except Exception:
            try: prog[name].value = int(v[0]) if len(v) == 1 else tuple(int(x) for x in v)
            except Exception: pass

    def render(prog, e, t_in, w, h, t, res):
        f = fbo(w, h); f.use(); t_in.use(0)
        setu(prog, "uTex", 0); setu(prog, "uResolution", *res); setu(prog, "uTime", t); setu(prog, "uDuration", n / fps)
        setu(prog, "uProgress", min(1.0, t / (n / fps))); setu(prog, "uIntensity", 1.0); setu(prog, "uSeed", 0.37)
        for q in e["params"]: setu(prog, q["name"], *q["default"])
        vao = ctx.vertex_array(prog, [(vbo, "2f", "aPos")]); f.clear(); vao.render(moderngl.TRIANGLE_STRIP); vao.release()
        return np.frombuffer(f.read(components=3), np.uint8).reshape(h, w, 3)[::-1]

    lib = [e for e in json.load(open(FX + "/library.json")) if e["kind"] in ("filter", "effect")]
    if only: lib = [e for e in lib if e["id"] in only]
    texs = [tex(f) for f in clip]
    src = clip.astype(np.float32)
    cuts = {i for i in range(1, n) if np.abs(src[i] - src[i - 1]).mean() > 25}
    src_motion = np.mean([np.abs(src[i] - src[i - 1]).mean() for i in range(1, n) if i not in cuts])
    src_clip = float(((clip < 3) | (clip > 252)).mean())
    # Export check frame: the portrait shot at 1080p (bicubic upscale stands in for a 1080p source).
    big_in = np.asarray(Image.fromarray(clip[8]).resize((1920, 1080), Image.BICUBIC))
    big_tex = tex(big_in)
    lines, flagged, mont = [], [], {}
    for k, e in enumerate(lib):
        try: prog = ctx.program(vertex_shader=VS, fragment_shader=single(e))
        except Exception as ex:
            lines.append(f"{e['id']}: COMPILE {str(ex)[:120]}"); continue
        frames = [render(prog, e, texs[i], W, H, i / fps, (W, H)) for i in range(n)]
        o = np.stack(frames).astype(np.float32)
        motion = np.mean([np.abs(o[i] - o[i - 1]).mean() for i in range(1, n) if i not in cuts])
        flick = motion / max(src_motion, 1e-3)
        means = o.reshape(n, -1).mean(1)
        dead = int(max(run for run in _runs((means < 4) | (means > 251))))
        clipf = float(((o < 3) | (o > 252)).mean())
        # Preview (720p canvas) versus export (1080p canvas); the test clip stands in at 640x360.
        prev = frames[8].astype(np.float32)
        legacy = render(prog, e, big_tex, 1920, 1080, 8 / fps, (1920, 1080))
        normed = render(prog, e, big_tex, 1920, 1080, 8 / fps, (W, H))
        down = lambda a: np.asarray(Image.fromarray(a).resize((W, H), Image.BOX)).astype(np.float32)
        s_legacy = float(np.abs(down(legacy) - prev).mean())
        s_norm = float(np.abs(down(normed) - prev).mean())
        prog.release()
        why = []
        if e["kind"] == "filter" and flick > 2.5: why.append(f"flicker x{flick:.1f}")
        if e["kind"] == "effect" and flick > 6 and e["id"] not in FLASHY: why.append(f"flicker x{flick:.1f}")
        if dead >= 3 and e["id"] not in FLASHY: why.append(f"dead run {dead}")
        # Art looks (ink, dots, 1-bit) are black and white on purpose; photographic ones must not crush.
        if clipf - src_clip > 0.35 and e["category"] in ("Color", "Detail", "Lens", "Film", "Cinema", "Mood", "Season", "Portrait", "Food", "Travel"): why.append(f"clipping {clipf:.2f}")
        if s_norm > 6: why.append(f"export differs from preview {s_norm:.1f}")
        lines.append(f"{e['kind']:6} {e['id']:18} flicker x{flick:5.2f} dead {dead:3d} clip {clipf:.3f} scale legacy {s_legacy:5.1f} norm {s_norm:5.1f}" + ("  <-- " + ", ".join(why) if why else ""))
        if why:
            flagged.append(e["id"])
            strip = np.concatenate([frames[i] for i in np.linspace(0, n - 1, 6).astype(int)], 1)
            Image.fromarray(strip).resize((strip.shape[1] // 2, strip.shape[0] // 2)).save(f"{out}/flag_{e['id']}.png")
        if e["id"] in montage: mont[e["id"]] = frames[:30]
        if k % 50 == 0: print(k, "/", len(lib), flush=True)
    open(f"{out}/report.txt", "w").write("\n".join(lines) + "\n")
    if mont:
        p = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", "15", "-i", "-",
                              "-pix_fmt", "yuv420p", "-crf", "22", f"{out}/montage.mp4"], stdin=subprocess.PIPE)
        for i in montage:
            for f in mont.get(i, []): p.stdin.write(np.ascontiguousarray(f).tobytes())
        p.stdin.close(); p.wait()
    leg = [float(l.split("legacy")[1].split()[0]) for l in lines if "legacy" in l]
    nor = [float(l.split("norm")[1].split()[0]) for l in lines if "norm" in l]
    print(f"{len(lib)} checked, {len(flagged)} flagged: {', '.join(flagged)}")
    print(f"preview vs export, mean abs diff: legacy {np.mean(leg):.2f} (>6: {sum(x > 6 for x in leg)}), normalised {np.mean(nor):.2f} (>6: {sum(x > 6 for x in nor)})")


def _runs(mask):
    best = cur = 0
    for m in mask:
        cur = cur + 1 if m else 0
        best = max(best, cur)
    yield best


if __name__ == "__main__":
    main()
