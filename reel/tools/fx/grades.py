#!/usr/bin/env python3
"""Writes tools/fx/grades.glsl: colour-grade filters built from one table.

Each grade is a row of grading numbers (exposure, white balance, saturation, contrast, split
toning, black lift, optional mono tint). build_library.py reads grades.glsl after library.glsl.
Edit the table and run: python3 tools/fx/grades.py && python3 tools/fx/build_library.py
"""
import os

HERE = os.path.dirname(__file__)

# id, name, category, exposure, temp(-1 cool..1 warm), tint(-1 green..1 magenta), saturation,
# contrast(-0.5..1), shadows rgb offset, highlights rgb offset, black lift (fade), mono tint or None
G = [
    # Film stocks (generic looks, no brand names)
    ("g_portra", "Portrait 160", "Film", 1.03, 0.25, 0.05, 0.9, 0.25, (0.0, 0.01, 0.02), (0.03, 0.01, -0.02), 0.04, None),
    ("g_ektar", "Vivid 100", "Film", 1.0, 0.15, 0.0, 1.35, 0.5, (0.0, 0.0, 0.03), (0.04, 0.02, -0.03), 0.0, None),
    ("g_gold200", "Gold 200", "Film", 1.04, 0.45, 0.05, 1.1, 0.3, (0.02, 0.0, -0.02), (0.06, 0.03, -0.05), 0.03, None),
    ("g_superia", "Green 400", "Film", 1.0, -0.05, -0.25, 1.05, 0.3, (-0.01, 0.03, 0.02), (0.02, 0.02, -0.02), 0.03, None),
    ("g_velvia", "Velvet 50", "Film", 0.98, 0.05, 0.1, 1.55, 0.6, (0.0, -0.01, 0.04), (0.03, 0.0, -0.02), 0.0, None),
    ("g_provia", "Pro 100", "Film", 1.0, 0.0, 0.0, 1.15, 0.35, (0.0, 0.0, 0.02), (0.01, 0.01, 0.0), 0.0, None),
    ("g_cinestill", "Tungsten 800", "Film", 1.02, -0.45, 0.1, 1.1, 0.35, (0.0, 0.02, 0.06), (0.06, 0.0, -0.03), 0.04, None),
    ("g_expired", "Expired", "Film", 1.05, 0.3, 0.3, 0.75, 0.1, (0.05, 0.0, 0.06), (0.05, 0.04, -0.06), 0.1, None),
    ("g_xpro", "Cross Film", "Film", 1.02, 0.0, -0.2, 1.3, 0.55, (0.0, 0.0, 0.1), (0.08, 0.06, -0.1), 0.02, None),
    ("g_instant", "Instant Print", "Film", 1.06, 0.2, 0.05, 0.8, 0.15, (0.02, 0.03, 0.05), (0.05, 0.03, -0.02), 0.12, None),
    ("g_disposable", "Disposable", "Film", 1.08, 0.3, -0.1, 1.2, 0.4, (0.0, 0.02, 0.0), (0.05, 0.03, -0.04), 0.05, None),
    ("g_slide", "Slide", "Film", 0.96, 0.0, 0.05, 1.3, 0.65, (0.0, 0.0, 0.04), (0.02, 0.01, -0.01), 0.0, None),
    # Cinema grades
    ("g_blockbuster", "Blockbuster", "Cinema", 1.0, 0.0, 0.0, 1.15, 0.5, (-0.04, 0.03, 0.07), (0.07, 0.03, -0.05), 0.0, None),
    ("g_noircolor", "Neo Noir", "Cinema", 0.92, -0.2, 0.1, 0.6, 0.6, (0.0, 0.02, 0.06), (0.05, 0.0, 0.03), 0.0, None),
    ("g_bleachcine", "Silver Retain", "Cinema", 1.0, 0.0, 0.0, 0.45, 0.6, (0.0, 0.0, 0.01), (0.02, 0.02, 0.02), 0.0, None),
    ("g_westworld", "Frontier", "Cinema", 1.03, 0.55, 0.0, 0.85, 0.4, (0.03, 0.01, -0.02), (0.06, 0.03, -0.06), 0.03, None),
    ("g_matrixcine", "Code Green", "Cinema", 0.97, 0.0, -0.55, 0.85, 0.45, (-0.02, 0.04, 0.0), (0.0, 0.05, -0.01), 0.0, None),
    ("g_dune", "Dune", "Cinema", 1.02, 0.6, 0.05, 0.8, 0.45, (0.04, 0.01, -0.03), (0.08, 0.04, -0.07), 0.02, None),
    ("g_arctic", "Arctic", "Cinema", 1.03, -0.6, 0.0, 0.7, 0.3, (-0.02, 0.01, 0.06), (0.0, 0.03, 0.06), 0.03, None),
    ("g_nightdrive", "Night Drive", "Cinema", 0.94, -0.3, 0.3, 1.2, 0.5, (0.02, -0.02, 0.1), (0.08, 0.0, 0.05), 0.0, None),
    ("g_amelie", "Paris Café", "Cinema", 1.02, 0.35, -0.15, 1.25, 0.35, (-0.01, 0.04, 0.0), (0.06, 0.03, -0.04), 0.02, None),
    ("g_spaghetti", "Spaghetti West", "Cinema", 1.0, 0.7, 0.0, 0.7, 0.6, (0.03, 0.0, -0.04), (0.07, 0.04, -0.07), 0.0, None),
    ("g_thriller", "Thriller", "Cinema", 0.93, -0.15, -0.2, 0.7, 0.55, (-0.02, 0.03, 0.04), (0.02, 0.03, 0.0), 0.0, None),
    ("g_romcom", "Romance", "Cinema", 1.06, 0.3, 0.2, 1.05, 0.2, (0.03, 0.0, 0.03), (0.05, 0.02, 0.0), 0.04, None),
    ("g_scifi", "Sci-Fi", "Cinema", 0.98, -0.45, 0.0, 0.95, 0.5, (-0.03, 0.03, 0.08), (0.0, 0.04, 0.06), 0.0, None),
    ("g_documentary", "Documentary", "Cinema", 1.0, 0.05, 0.0, 0.85, 0.25, (0.0, 0.0, 0.01), (0.01, 0.01, 0.0), 0.02, None),
    # Mood
    ("g_dreamy", "Dreamy", "Mood", 1.08, 0.15, 0.2, 0.8, -0.2, (0.04, 0.02, 0.06), (0.05, 0.03, 0.02), 0.1, None),
    ("g_melancholy", "Melancholy", "Mood", 0.95, -0.35, 0.0, 0.55, 0.15, (0.0, 0.02, 0.05), (0.02, 0.02, 0.03), 0.06, None),
    ("g_warmglow", "Warm Glow", "Mood", 1.07, 0.6, 0.1, 1.05, 0.15, (0.04, 0.01, -0.02), (0.08, 0.04, -0.03), 0.03, None),
    ("g_gloomy", "Gloomy", "Mood", 0.88, -0.2, -0.1, 0.6, 0.35, (-0.01, 0.01, 0.03), (0.0, 0.0, 0.02), 0.02, None),
    ("g_cozy", "Cozy", "Mood", 1.03, 0.5, 0.1, 0.9, 0.2, (0.05, 0.02, 0.0), (0.06, 0.03, -0.04), 0.06, None),
    ("g_energetic", "Energetic", "Mood", 1.04, 0.1, 0.0, 1.45, 0.5, (0.0, 0.0, 0.03), (0.04, 0.02, -0.02), 0.0, None),
    ("g_calm", "Calm", "Mood", 1.04, -0.2, 0.0, 0.8, 0.0, (0.0, 0.02, 0.04), (0.02, 0.03, 0.03), 0.07, None),
    ("g_nostalgic", "Nostalgic", "Mood", 1.03, 0.4, 0.1, 0.7, 0.1, (0.05, 0.03, 0.0), (0.05, 0.04, -0.05), 0.12, None),
    ("g_mysterious", "Mysterious", "Mood", 0.9, -0.1, 0.35, 0.8, 0.45, (0.02, -0.01, 0.07), (0.03, 0.0, 0.04), 0.0, None),
    ("g_hopeful", "Hopeful", "Mood", 1.08, 0.25, 0.0, 1.1, 0.1, (0.0, 0.02, 0.03), (0.06, 0.05, 0.0), 0.04, None),
    ("g_lonely", "Lonely", "Mood", 0.97, -0.5, 0.0, 0.45, 0.2, (0.0, 0.01, 0.05), (0.01, 0.02, 0.04), 0.08, None),
    ("g_euphoric", "Euphoric", "Mood", 1.06, 0.0, 0.3, 1.5, 0.3, (0.04, 0.0, 0.08), (0.06, 0.02, 0.02), 0.02, None),
    # Seasons and weather
    ("g_summer", "Summer", "Season", 1.06, 0.4, 0.0, 1.25, 0.3, (0.0, 0.02, 0.03), (0.06, 0.04, -0.03), 0.02, None),
    ("g_winter", "Winter", "Season", 1.05, -0.55, 0.05, 0.7, 0.2, (-0.01, 0.02, 0.06), (0.02, 0.04, 0.06), 0.05, None),
    ("g_fall", "Fall", "Season", 1.0, 0.55, -0.05, 1.15, 0.35, (0.03, 0.01, -0.03), (0.08, 0.03, -0.06), 0.02, None),
    ("g_springbloom", "Bloom", "Season", 1.06, 0.1, 0.15, 1.15, 0.1, (0.02, 0.03, 0.03), (0.04, 0.02, 0.02), 0.05, None),
    ("g_overcast", "Overcast", "Season", 0.98, -0.25, 0.0, 0.75, -0.1, (0.0, 0.01, 0.03), (0.02, 0.02, 0.03), 0.06, None),
    ("g_goldenhr", "Golden Light", "Season", 1.05, 0.75, 0.1, 1.15, 0.3, (0.04, 0.0, -0.04), (0.1, 0.05, -0.06), 0.0, None),
    ("g_bluehour", "Blue Hour", "Season", 0.97, -0.7, 0.15, 1.0, 0.3, (-0.02, 0.0, 0.1), (0.04, 0.02, 0.06), 0.02, None),
    ("g_rainy", "Rainy Day", "Season", 0.95, -0.35, -0.1, 0.65, 0.2, (-0.01, 0.02, 0.05), (0.0, 0.02, 0.03), 0.05, None),
    ("g_tropical", "Tropical", "Season", 1.04, 0.2, -0.15, 1.45, 0.35, (-0.02, 0.04, 0.05), (0.05, 0.04, -0.03), 0.0, None),
    ("g_harvest", "Harvest", "Season", 1.02, 0.5, 0.1, 0.95, 0.3, (0.05, 0.02, -0.02), (0.07, 0.04, -0.06), 0.04, None),
    # Portrait and skin
    ("g_softskin", "Soft Skin", "Portrait", 1.06, 0.2, 0.08, 0.92, -0.1, (0.02, 0.01, 0.02), (0.04, 0.02, 0.0), 0.04, None),
    ("g_glowskin", "Skin Glow", "Portrait", 1.1, 0.25, 0.1, 1.0, 0.0, (0.02, 0.01, 0.01), (0.06, 0.03, 0.0), 0.03, None),
    ("g_bronze", "Bronze", "Portrait", 1.0, 0.55, 0.05, 1.1, 0.4, (0.03, 0.0, -0.03), (0.08, 0.03, -0.05), 0.0, None),
    ("g_porcelain", "Porcelain", "Portrait", 1.08, -0.15, 0.05, 0.75, 0.05, (0.0, 0.01, 0.03), (0.03, 0.03, 0.04), 0.05, None),
    ("g_editorial", "Editorial", "Portrait", 1.0, 0.0, 0.05, 0.8, 0.55, (0.0, 0.0, 0.03), (0.03, 0.02, 0.0), 0.0, None),
    ("g_peach", "Peach", "Portrait", 1.06, 0.35, 0.2, 1.0, 0.15, (0.05, 0.02, 0.02), (0.07, 0.03, 0.0), 0.04, None),
    ("g_rosy", "Rosy", "Portrait", 1.04, 0.1, 0.35, 1.05, 0.15, (0.04, 0.0, 0.03), (0.05, 0.01, 0.02), 0.03, None),
    ("g_honey", "Honey", "Portrait", 1.04, 0.5, 0.0, 1.05, 0.2, (0.04, 0.02, -0.02), (0.07, 0.05, -0.05), 0.03, None),
    # Food and travel
    ("g_food", "Delicious", "Food", 1.05, 0.3, 0.05, 1.35, 0.35, (0.02, 0.01, 0.0), (0.05, 0.03, -0.03), 0.0, None),
    ("g_brunch", "Brunch", "Food", 1.09, 0.2, 0.0, 1.1, 0.1, (0.02, 0.02, 0.02), (0.05, 0.04, 0.0), 0.04, None),
    ("g_coffee", "Coffee", "Food", 0.98, 0.45, 0.05, 0.85, 0.45, (0.04, 0.02, 0.0), (0.06, 0.03, -0.04), 0.02, None),
    ("g_fresh", "Fresh", "Food", 1.06, -0.05, -0.15, 1.3, 0.25, (0.0, 0.03, 0.02), (0.02, 0.04, 0.0), 0.0, None),
    ("g_city", "City", "Travel", 1.0, -0.2, 0.0, 0.9, 0.5, (-0.01, 0.02, 0.05), (0.04, 0.02, 0.0), 0.0, None),
    ("g_beach", "Beach", "Travel", 1.07, 0.15, -0.05, 1.25, 0.2, (-0.02, 0.04, 0.06), (0.05, 0.04, -0.02), 0.03, None),
    ("g_mountain", "Mountain", "Travel", 1.02, -0.25, -0.05, 1.1, 0.45, (-0.01, 0.02, 0.05), (0.02, 0.03, 0.02), 0.0, None),
    ("g_desertroad", "Road Trip", "Travel", 1.03, 0.5, 0.0, 1.05, 0.4, (0.03, 0.02, -0.02), (0.07, 0.04, -0.05), 0.02, None),
    ("g_jungle", "Jungle", "Travel", 0.98, 0.0, -0.35, 1.2, 0.4, (-0.02, 0.04, 0.01), (0.03, 0.05, -0.02), 0.0, None),
    ("g_tokyo", "Neon City", "Travel", 0.96, -0.3, 0.4, 1.3, 0.45, (0.02, -0.01, 0.1), (0.07, 0.0, 0.06), 0.0, None),
    ("g_mediterranean", "Seaside", "Travel", 1.06, 0.15, 0.0, 1.2, 0.25, (-0.02, 0.03, 0.07), (0.05, 0.04, 0.0), 0.02, None),
    ("g_safari", "Safari", "Travel", 1.02, 0.55, -0.1, 1.0, 0.45, (0.03, 0.02, -0.03), (0.06, 0.05, -0.06), 0.0, None),
    # Monochrome
    ("g_mono_soft", "Soft Mono", "Mono", 1.04, 0.0, 0.0, 0.0, 0.0, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.08, (1.0, 1.0, 1.0)),
    ("g_mono_hard", "Hard Mono", "Mono", 1.0, 0.0, 0.0, 0.0, 0.85, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.0, (1.0, 1.0, 1.0)),
    ("g_mono_warm", "Warm Mono", "Mono", 1.02, 0.0, 0.0, 0.0, 0.3, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.04, (1.06, 1.0, 0.86)),
    ("g_mono_cool", "Cool Mono", "Mono", 1.02, 0.0, 0.0, 0.0, 0.3, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.04, (0.88, 0.97, 1.08)),
    ("g_mono_green", "Green Mono", "Mono", 1.0, 0.0, 0.0, 0.0, 0.3, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.03, (0.85, 1.05, 0.9)),
    ("g_mono_faded", "Faded Mono", "Mono", 1.05, 0.0, 0.0, 0.0, -0.2, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.18, (1.0, 0.99, 0.97)),
    ("g_mono_film", "Film Mono", "Mono", 1.0, 0.0, 0.0, 0.0, 0.5, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.03, (1.02, 1.0, 0.96)),
    ("g_mono_purple", "Violet Mono", "Mono", 1.0, 0.0, 0.0, 0.0, 0.3, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.03, (1.0, 0.86, 1.1)),
    ("g_mono_red", "Red Mono", "Mono", 1.0, 0.0, 0.0, 0.0, 0.35, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.02, (1.12, 0.86, 0.84)),
    ("g_mono_blue", "Blue Mono", "Mono", 1.0, 0.0, 0.0, 0.0, 0.35, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.02, (0.8, 0.92, 1.15)),
]


def f(x):
    return f"{float(x):.4f}"


def v3(t):
    return f"vec3({f(t[0])}, {f(t[1])}, {f(t[2])})"


def block(row):
    gid, name, cat, exp, temp, tint, sat, con, sh, hi, fade, mono = row
    wb = (1.0 + temp * 0.12, 1.0 - tint * 0.06, 1.0 - temp * 0.12)
    lines = [
        f"//== id: {gid} | {name} | filter | {cat}",
        "vec4 effect(vec2 uv) {",
        "  vec3 c = getColor(uv).rgb;",
        f"  c *= {f(exp)} * {v3(wb)};",
        "  float l = luma(c);",
    ]
    if mono is None:
        lines.append(f"  c = mix(vec3(l), c, {f(sat)});")
    else:
        lines.append(f"  c = vec3(l) * {v3(mono)};")
    lines += [
        "  c = clamp(c, 0.0, 1.0);",
        f"  c = mix(c, c * c * (3.0 - 2.0 * c), {f(con)});",
        "  l = luma(c);",
        f"  c += {v3(sh)} * (1.0 - smoothstep(0.0, 0.55, l)) + {v3(hi)} * smoothstep(0.45, 1.0, l);",
        f"  c = {f(fade)} + c * {f(1.0 - fade)};",
        "  return vec4(clamp(c, 0.0, 1.0), 1.0);",
        "}",
        "",
    ]
    return "\n".join(lines)


ids = [r[0] for r in G]
assert len(ids) == len(set(ids))
with open(os.path.join(HERE, "grades.glsl"), "w") as out:
    out.write("// Generated by grades.py: colour grades. Edit the table there, not this file.\n\n")
    for r in G:
        out.write(block(r) + "\n")
print(len(G), "grades")
