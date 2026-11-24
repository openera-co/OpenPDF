#!/usr/bin/env python3
"""Builds a short "real footage" test clip for fx_video_check.py from natural photos.

Shots: a portrait with handheld shake and a slow push-in, a coffee close-up with a pan, the cat
with a zoom out and a whip, and the apple-over-Earth photo with a slow drift. Natural texture,
skin, motion and hard cuts are what exposes flicker and edge artifacts that still pictures hide.
Needs numpy, pillow, scikit-image. Usage: make_test_clip.py <out.npy> [width height fps seconds]
"""
import sys
import numpy as np
from PIL import Image

REEL = __file__.rsplit("/tools/", 1)[0]
out = sys.argv[1]
W, H, FPS = (int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])) if len(sys.argv) > 4 else (640, 360, 30)
SECS = float(sys.argv[5]) if len(sys.argv) > 5 else 8.0


def photos():
    import skimage.data as d
    person = Image.open(REEL + "/app/src/main/assets/samples/person.jpg").convert("RGB")
    apple = Image.open(REEL + "/app/src/main/assets/samples/thing.jpg").convert("RGB")
    return [person, Image.fromarray(d.coffee()), Image.fromarray(d.chelsea()), apple]


def shot(img, t, kind, rng):
    """Crop-and-scale camera move on one photo; t in 0..1 across the shot."""
    iw, ih = img.size
    aspect = W / H
    # Largest W:H crop that fits, then zoom in by z.
    cw0 = min(iw, ih * aspect)
    ch0 = cw0 / aspect
    if kind == "handheld":
        z = 1.15 + 0.12 * t
        dx = np.sin(t * 23.0) * 0.012 + rng.normal(0, 0.004)
        dy = np.cos(t * 17.0) * 0.010 + rng.normal(0, 0.004)
        cx, cy = 0.5 + dx, 0.45 + dy
    elif kind == "pan":
        z = 1.3
        cx, cy = 0.35 + 0.3 * t, 0.5
    elif kind == "zoomout":
        e = t * t * (3 - 2 * t)
        z = 1.6 - 0.5 * e
        cx, cy = 0.5 + 0.04 * np.sin(t * 9), 0.5
    else:
        z = 1.1 + 0.05 * t
        cx, cy = 0.5 - 0.05 * t, 0.5
    cw, ch = cw0 / z, ch0 / z
    x0 = np.clip(cx * iw - cw / 2, 0, iw - cw)
    y0 = np.clip(cy * ih - ch / 2, 0, ih - ch)
    return img.resize((W, H), Image.BICUBIC, box=(x0, y0, x0 + cw, y0 + ch))


def main():
    rng = np.random.default_rng(7)
    imgs = photos()
    plan = [(imgs[0], "handheld", 0.3), (imgs[1], "pan", 0.25), (imgs[2], "zoomout", 0.2), (imgs[3], "drift", 0.25)]
    n = int(SECS * FPS)
    frames = np.zeros((n, H, W, 3), np.uint8)
    bounds = np.cumsum([0] + [p[2] for p in plan])
    for i in range(n):
        u = i / n
        k = int(np.searchsorted(bounds, u, side="right") - 1)
        img, kind, _ = plan[k]
        t = (u - bounds[k]) / (bounds[k + 1] - bounds[k])
        frames[i] = np.asarray(shot(img, t, kind, rng))
    np.save(out, frames)
    print(out, frames.shape, "cuts at frames", [int(b * n) for b in bounds[1:-1]])


if __name__ == "__main__":
    main()
