// Reel shader library. Each block starts with //== id and is compiled by validate_shaders.py
// with the same prelude/wrapper the app uses (see assets/fx/prelude.glsl, wrap_single.glsl).
// Available: getColor(uv), uTime, uDuration, uProgress, uResolution, uIntensity, rand, noise,
// luma, rgb2hsv, hsv2rgb, PI. Params become uniforms of the same name.
// kind filter = static look; effect = animated over time; anim = clip in/out (uProgress = visibility 0..1).

//== id: sketch | Sketch | filter | Art
//== param: strength float 1.0 0.2 3.0
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  float tl = luma(getColor(uv + px * vec2(-1.0, 1.0)).rgb);
  float t = luma(getColor(uv + px * vec2(0.0, 1.0)).rgb);
  float tr = luma(getColor(uv + px * vec2(1.0, 1.0)).rgb);
  float l = luma(getColor(uv + px * vec2(-1.0, 0.0)).rgb);
  float r = luma(getColor(uv + px * vec2(1.0, 0.0)).rgb);
  float bl = luma(getColor(uv + px * vec2(-1.0, -1.0)).rgb);
  float b = luma(getColor(uv + px * vec2(0.0, -1.0)).rgb);
  float br = luma(getColor(uv + px * vec2(1.0, -1.0)).rgb);
  float gx = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
  float gy = -bl - 2.0 * b - br + tl + 2.0 * t + tr;
  float e = 1.0 - clamp(length(vec2(gx, gy)) * strength, 0.0, 1.0);
  return vec4(vec3(e), 1.0);
}

//== id: toon | Toon | filter | Art
//== param: levels float 6.0 2.0 12.0
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  float edge = step(0.12, length(vec2(gx, gy)));
  vec3 q = floor(c * levels + 0.5) / levels;
  return vec4(q * (1.0 - edge), 1.0);
}

//== id: halftone | Halftone | filter | Art
//== param: size float 8.0 3.0 30.0
vec4 effect(vec2 uv) {
  vec2 p = uv * uResolution;
  float a = 0.785398;
  mat2 rot = mat2(cos(a), -sin(a), sin(a), cos(a));
  vec2 g = rot * p / size;
  vec2 cell = (floor(g) + 0.5);
  vec2 center = (vec2(cos(-a), sin(-a)) * cell.x + vec2(-sin(-a), cos(-a)) * cell.y) * size / uResolution;
  float l = luma(getColor(center).rgb);
  float d = length(fract(g) - 0.5);
  float dot_ = smoothstep(0.0, 0.08, sqrt(1.0 - l) * 0.55 - d);
  return vec4(vec3(1.0 - dot_), 1.0);
}

//== id: pixelate | Pixelate | filter | Art
//== param: size float 24.0 4.0 120.0
vec4 effect(vec2 uv) {
  vec2 cells = uResolution / size;
  return getColor((floor(uv * cells) + 0.5) / cells);
}

//== id: posterize | Posterize | filter | Art
//== param: levels float 3.0 2.0 10.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  return vec4(floor(c * levels + 0.5) / levels, 1.0);
}

//== id: emboss | Emboss | filter | Art
//== param: depth float 2.0 0.5 6.0
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  float a = luma(getColor(uv - px).rgb);
  float b = luma(getColor(uv + px).rgb);
  return vec4(vec3(0.5 + (b - a) * depth), 1.0);
}

//== id: crosshatch | Crosshatch | filter | Art
//== param: spacing float 8.0 4.0 20.0
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  vec2 p = uv * uResolution;
  float c = 1.0;
  if (l < 0.8 && mod(p.x + p.y, spacing) < 1.5) c = 0.0;
  if (l < 0.6 && mod(p.x - p.y, spacing) < 1.5) c = 0.0;
  if (l < 0.4 && mod(p.x + p.y - spacing * 0.5, spacing) < 1.5) c = 0.0;
  if (l < 0.2 && mod(p.x - p.y - spacing * 0.5, spacing) < 1.5) c = 0.0;
  return vec4(vec3(c), 1.0);
}

//== id: oilpaint | Oil paint | filter | Art
//== param: radius float 3.0 1.0 5.0
vec4 effect(vec2 uv) {
  vec2 px = radius / uResolution;
  vec3 m0 = vec3(0.0), m1 = vec3(0.0), s0 = vec3(0.0), s1 = vec3(0.0);
  for (int j = -2; j <= 0; j++) {
    for (int i = -2; i <= 0; i++) {
      vec3 c = getColor(uv + vec2(float(i), float(j)) * px * 0.5).rgb;
      m0 += c; s0 += c * c;
      vec3 d = getColor(uv + vec2(float(-i), float(-j)) * px * 0.5).rgb;
      m1 += d; s1 += d * d;
    }
  }
  m0 /= 9.0; m1 /= 9.0;
  float v0 = dot(abs(s0 / 9.0 - m0 * m0), vec3(1.0));
  float v1 = dot(abs(s1 / 9.0 - m1 * m1), vec3(1.0));
  return vec4(v0 < v1 ? m0 : m1, 1.0);
}

//== id: glasssphere | Glass sphere | filter | Lens
//== param: radius float 0.35 0.1 0.5
vec4 effect(vec2 uv) {
  float aspect = uResolution.x / uResolution.y;
  vec2 d = (uv - 0.5) * vec2(aspect, 1.0);
  float r = length(d);
  if (r > radius) return getColor(uv) * 0.85;
  float z = sqrt(radius * radius - r * r);
  vec3 n = normalize(vec3(d, z));
  vec2 refr = 0.5 - n.xy / vec2(aspect, 1.0) * 0.35;
  return vec4(getColor(refr).rgb + pow(max(0.0, dot(n, normalize(vec3(-0.3, 0.3, 1.0)))), 40.0) * 0.6, 1.0);
}

//== id: bulge | Bulge | filter | Lens
//== param: amount float 0.5 -1.0 1.0
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float r = length(d);
  float k = 1.0 - amount * smoothstep(0.5, 0.0, r) * 0.5;
  return getColor(0.5 + d * k);
}

//== id: fisheye | Fisheye | filter | Lens
//== param: amount float 0.6 0.0 1.5
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float r = length(d);
  vec2 q = 0.5 + normalize(d + 1e-6) * pow(r * 2.0, 1.0 + amount) * 0.5;
  return getColor(q);
}

//== id: mirrorx | Mirror | filter | Art
vec4 effect(vec2 uv) {
  return getColor(vec2(uv.x < 0.5 ? uv.x : 1.0 - uv.x, uv.y));
}

//== id: mirrory | Mirror vertical | filter | Art
vec4 effect(vec2 uv) {
  return getColor(vec2(uv.x, uv.y > 0.5 ? uv.y : 1.0 - uv.y));
}

//== id: quad | Quad | filter | Art
vec4 effect(vec2 uv) {
  return getColor(fract(uv * 2.0));
}

//== id: kaleido | Kaleidoscope | filter | Art
//== param: sides float 6.0 3.0 12.0
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float a = atan(d.y, d.x);
  float seg = 2.0 * PI / sides;
  a = abs(mod(a, seg) - seg * 0.5);
  return getColor(0.5 + vec2(cos(a), sin(a)) * length(d));
}

//== id: edgeglow | Neon edges | filter | Art
//== param: color color 0.2,1.0,0.9 0 1
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  float e = clamp(length(vec2(gx, gy)) * 6.0, 0.0, 1.0);
  return vec4(getColor(uv).rgb * 0.15 + color * e, 1.0);
}

//== id: invert | Invert | filter | Color
vec4 effect(vec2 uv) {
  return vec4(1.0 - getColor(uv).rgb, 1.0);
}

//== id: solarize | Solarize | filter | Color
//== param: threshold float 0.5 0.1 0.9
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  // Tones above the threshold fold back down; both halves are stretched to the full range so the
  // result stays bright, and a little of the source keeps saturated areas from going black.
  vec3 low = c / max(threshold, 0.01);
  vec3 high = (1.0 - c) / max(1.0 - threshold, 0.01);
  vec3 s = mix(low, high, step(threshold, c));
  return vec4(mix(s, c, 0.2), 1.0);
}

//== id: thermal | Thermal | filter | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  vec3 a = vec3(0.0, 0.0, 0.4), b = vec3(0.6, 0.0, 0.8), c = vec3(1.0, 0.2, 0.0), d = vec3(1.0, 1.0, 0.2);
  vec3 col = l < 0.33 ? mix(a, b, l / 0.33) : (l < 0.66 ? mix(b, c, (l - 0.33) / 0.33) : mix(c, d, (l - 0.66) / 0.34));
  return vec4(col, 1.0);
}

//== id: nightvision | Night vision | filter | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb) * 1.6;
  float v = 1.0 - smoothstep(0.35, 0.75, length(uv - 0.5));
  return vec4(vec3(0.1, 1.0, 0.2) * l * (0.4 + 0.6 * v), 1.0);
}

//== id: duotone | Duotone | filter | Color
//== param: dark color 0.12,0.08,0.35 0 1
//== param: light color 1.0,0.55,0.4 0 1
vec4 effect(vec2 uv) {
  return vec4(mix(dark, light, luma(getColor(uv).rgb)), 1.0);
}

//== id: comic | Comic | filter | Art
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 hsv = rgb2hsv(c);
  hsv.y = min(1.0, hsv.y * 1.6);
  hsv.z = floor(hsv.z * 4.0 + 0.5) / 4.0;
  vec2 p = uv * uResolution / 6.0;
  float dots = step(length(fract(p) - 0.5), 0.3 * (1.0 - hsv.z));
  return vec4(hsv2rgb(hsv) * (1.0 - dots * 0.35), 1.0);
}

//== id: threshold | Ink | filter | Color
//== param: level float 0.5 0.1 0.9
vec4 effect(vec2 uv) {
  return vec4(vec3(step(level, luma(getColor(uv).rgb))), 1.0);
}

//== id: chromatic | Chromatic | filter | Lens
//== param: amount float 0.006 0.0 0.03
vec4 effect(vec2 uv) {
  vec2 d = (uv - 0.5) * amount * 2.0;
  return vec4(getColor(uv + d).r, getColor(uv).g, getColor(uv - d).b, 1.0);
}

//== id: tiltshift | Tilt shift | filter | Lens
//== param: focus float 0.5 0.1 0.9
vec4 effect(vec2 uv) {
  float blur = smoothstep(0.08, 0.35, abs(uv.y - focus)) * 4.0 / uResolution.y;
  vec3 acc = vec3(0.0);
  for (int i = -3; i <= 3; i++) {
    for (int j = -3; j <= 3; j++) {
      acc += getColor(uv + vec2(float(i), float(j)) * blur * vec2(uResolution.y / uResolution.x, 1.0) * 2.0).rgb;
    }
  }
  vec3 c = acc / 49.0;
  vec3 hsv = rgb2hsv(c);
  hsv.y = min(1.0, hsv.y * 1.25);
  return vec4(hsv2rgb(hsv), 1.0);
}

//== id: softfocus | Soft focus | filter | Lens
//== param: glow float 0.5 0.0 1.0
vec4 effect(vec2 uv) {
  vec2 px = 3.0 / uResolution;
  vec3 b = vec3(0.0);
  for (int i = -2; i <= 2; i++) {
    for (int j = -2; j <= 2; j++) {
      b += getColor(uv + vec2(float(i), float(j)) * px).rgb;
    }
  }
  b /= 25.0;
  vec3 c = getColor(uv).rgb;
  return vec4(1.0 - (1.0 - c) * (1.0 - b * glow), 1.0);
}

//== id: mosaic | Tiles | filter | Art
//== param: size float 40.0 10.0 120.0
vec4 effect(vec2 uv) {
  vec2 cells = uResolution / size;
  vec2 cid = floor(uv * cells);
  vec2 f = fract(uv * cells);
  vec3 c = getColor((cid + 0.5) / cells).rgb;
  float border = step(0.06, f.x) * step(0.06, f.y);
  return vec4(c * (0.75 + 0.25 * border), 1.0);
}

//== id: dotscreen | Dots | filter | Art
//== param: size float 10.0 4.0 30.0
vec4 effect(vec2 uv) {
  vec2 cells = uResolution / size;
  vec2 cid = (floor(uv * cells) + 0.5) / cells;
  vec3 c = getColor(cid).rgb;
  float r = length(fract(uv * cells) - 0.5);
  return vec4(c * step(r, 0.45 * sqrt(luma(c)) + 0.05), 1.0);
}

//== id: cga | 8-bit | filter | Color
vec4 effect(vec2 uv) {
  vec2 cells = uResolution / 6.0;
  vec3 c = getColor((floor(uv * cells) + 0.5) / cells).rgb;
  vec3 pal0 = vec3(0.0), pal1 = vec3(0.33, 1.0, 1.0), pal2 = vec3(1.0, 0.33, 1.0), pal3 = vec3(1.0);
  float d0 = distance(c, pal0), d1 = distance(c, pal1), d2 = distance(c, pal2), d3 = distance(c, pal3);
  vec3 o = pal0; float m = d0;
  if (d1 < m) { m = d1; o = pal1; }
  if (d2 < m) { m = d2; o = pal2; }
  if (d3 < m) { o = pal3; }
  return vec4(o, 1.0);
}

//== id: falsecolor | False color | filter | Color
//== param: shadow color 0.1,0.0,0.4 0 1
//== param: highlight color 1.0,0.9,0.2 0 1
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  return vec4(mix(shadow, highlight, smoothstep(0.1, 0.9, l)), 1.0);
}

//== id: sharpen | Sharpen | filter | Detail
//== param: amount float 0.8 0.0 3.0
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  vec3 n = getColor(uv + vec2(px.x, 0.0)).rgb + getColor(uv - vec2(px.x, 0.0)).rgb + getColor(uv + vec2(0.0, px.y)).rgb + getColor(uv - vec2(0.0, px.y)).rgb;
  return vec4(clamp(c + (c * 4.0 - n) * amount, 0.0, 1.0), 1.0);
}

//== id: blur | Blur | filter | Detail
//== param: radius float 6.0 1.0 20.0
vec4 effect(vec2 uv) {
  vec2 px = radius / uResolution / 3.0;
  vec3 acc = vec3(0.0);
  float w = 0.0;
  for (int i = -3; i <= 3; i++) {
    for (int j = -3; j <= 3; j++) {
      float k = exp(-float(i * i + j * j) / 8.0);
      acc += getColor(uv + vec2(float(i), float(j)) * px).rgb * k;
      w += k;
    }
  }
  return vec4(acc / w, 1.0);
}

//== id: vignettefx | Vignette | filter | Detail
//== param: amount float 0.6 0.0 1.0
vec4 effect(vec2 uv) {
  float v = smoothstep(0.85, 0.25, length((uv - 0.5) * vec2(1.0, uResolution.y / uResolution.x * 0.9 + 0.1)));
  return vec4(getColor(uv).rgb * mix(1.0 - amount, 1.0, v), 1.0);
}

//== id: letterbox | Cinema bars | filter | Detail
//== param: bars float 0.12 0.0 0.3
vec4 effect(vec2 uv) {
  float m = step(bars, uv.y) * step(uv.y, 1.0 - bars);
  return vec4(getColor(uv).rgb * m, 1.0);
}

// ---------------------------------------------------------------- animated effects

//== id: lightleak | Light leak | effect | Light
//== param: speed float 1.0 0.2 3.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float t = uTime * speed;
  vec2 p1 = vec2(0.1 + 0.3 * sin(t * 0.7), 0.3 + 0.2 * cos(t * 0.5));
  vec2 p2 = vec2(0.9 + 0.2 * cos(t * 0.6), 0.7 + 0.2 * sin(t * 0.8));
  vec3 leak = vec3(1.0, 0.45, 0.2) * smoothstep(0.7, 0.0, distance(uv, p1)) + vec3(1.0, 0.8, 0.35) * smoothstep(0.6, 0.0, distance(uv, p2));
  return vec4(1.0 - (1.0 - c) * (1.0 - leak * (0.6 + 0.4 * sin(t * 1.3))), 1.0);
}

//== id: flare | Lens flare | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 src = vec2(0.2 + 0.6 * uProgress, 0.8);
  float aspect = uResolution.x / uResolution.y;
  vec2 d = (uv - src) * vec2(aspect, 1.0);
  float core = 0.02 / (length(d) + 0.02);
  float streak = 0.004 / (abs(d.y) + 0.004) * smoothstep(0.6, 0.0, abs(d.x));
  vec3 ghosts = vec3(0.0);
  for (int i = 1; i <= 4; i++) {
    vec2 g = mix(src, vec2(0.5), float(i) * 0.45);
    ghosts += vec3(0.4, 0.6, 1.0) * smoothstep(0.06, 0.0, length((uv - g) * vec2(aspect, 1.0)) - 0.02 * float(i)) * 0.25;
  }
  return vec4(c + vec3(1.0, 0.85, 0.6) * (core * 0.6 + streak * 0.5) + ghosts, 1.0);
}

//== id: shine | Shine | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float pos = fract(uTime * 0.5) * 2.4 - 0.7;
  float band = smoothstep(0.12, 0.0, abs(uv.x + uv.y * 0.4 - pos));
  return vec4(c + band * 0.5, 1.0);
}

//== id: glowpulse | Glow pulse | effect | Light
vec4 effect(vec2 uv) {
  vec2 px = 4.0 / uResolution;
  vec3 b = vec3(0.0);
  for (int i = -2; i <= 2; i++) {
    for (int j = -2; j <= 2; j++) {
      b += getColor(uv + vec2(float(i), float(j)) * px).rgb;
    }
  }
  b /= 25.0;
  float k = 0.35 + 0.35 * sin(uTime * 4.0);
  vec3 c = getColor(uv).rgb;
  return vec4(c + max(b - 0.5, 0.0) * k * 2.0, 1.0);
}

//== id: sparkle | Sparkle | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 g = uv * vec2(uResolution.x / uResolution.y, 1.0) * 18.0;
  vec2 id = floor(g);
  vec2 f = fract(g) - 0.5;
  float r = rand(id);
  float tw = pow(max(0.0, sin(uTime * (3.0 + r * 4.0) + r * 20.0)), 8.0);
  vec2 o = vec2(rand(id + 1.3), rand(id + 7.1)) - 0.5;
  vec2 q = f - o * 0.6;
  float star = (0.004 / (abs(q.x * q.y) + 0.004)) * smoothstep(0.3, 0.0, length(q));
  float show = step(0.72, r) * step(0.55, luma(c) + 0.3);
  return vec4(c + vec3(1.0, 0.95, 0.8) * star * tw * show, 1.0);
}

//== id: bokeh | Bokeh | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float aspect = uResolution.x / uResolution.y;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 12; i++) {
    float fi = float(i);
    vec2 p = vec2(rand(vec2(fi, 1.0)), fract(rand(vec2(fi, 2.0)) + uTime * (0.02 + 0.03 * rand(vec2(fi, 3.0)))));
    float s = 0.04 + 0.06 * rand(vec2(fi, 4.0));
    float d = length((uv - p) * vec2(aspect, 1.0));
    vec3 col = hsv2rgb(vec3(0.05 + 0.12 * rand(vec2(fi, 5.0)), 0.6, 1.0));
    acc += col * smoothstep(s, s * 0.85, d) * 0.25;
  }
  return vec4(1.0 - (1.0 - c) * (1.0 - acc), 1.0);
}

//== id: vhs | VHS | effect | Retro
vec4 effect(vec2 uv) {
  float t = uTime;
  float jitter = (rand(vec2(floor(uv.y * 240.0), floor(t * 30.0))) - 0.5) * 0.004;
  float wave = sin(uv.y * 30.0 + t * 8.0) * 0.0015;
  vec2 q = vec2(uv.x + jitter + wave, uv.y);
  vec3 c = vec3(getColor(q + vec2(0.004, 0.0)).r, getColor(q).g, getColor(q - vec2(0.004, 0.0)).b);
  float scan = 0.92 + 0.08 * sin(uv.y * uResolution.y * 1.5);
  float band = smoothstep(0.02, 0.0, abs(fract(uv.y - t * 0.15) - 0.5)) * 0.15;
  float n = rand(uv * uResolution + t) * 0.08;
  vec3 hsv = rgb2hsv(c);
  hsv.y *= 1.2;
  return vec4(hsv2rgb(hsv) * scan + band + n, 1.0);
}

//== id: oldfilm | Old film | effect | Retro
vec4 effect(vec2 uv) {
  float t = floor(uTime * 18.0);
  vec2 shake = (vec2(rand(vec2(t, 1.0)), rand(vec2(t, 2.0))) - 0.5) * 0.004;
  vec3 c = getColor(uv + shake).rgb;
  float l = luma(c);
  vec3 sep = vec3(l * 1.07, l * 0.95, l * 0.78);
  float flicker = 0.9 + 0.1 * rand(vec2(t, 3.0));
  float scratch = step(0.997, rand(vec2(floor(uv.x * 400.0), t))) * 0.5;
  float grain = (rand(uv * uResolution + t) - 0.5) * 0.12;
  float vig = smoothstep(0.85, 0.3, length(uv - 0.5));
  return vec4((sep * flicker + grain + scratch) * (0.55 + 0.45 * vig), 1.0);
}

//== id: grain | Film grain | effect | Retro
//== param: amount float 0.12 0.0 0.4
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float g = rand(uv * uResolution * 0.5 + floor(uTime * 24.0)) - 0.5;
  return vec4(c + g * amount * (1.0 - abs(luma(c) - 0.5)), 1.0);
}

//== id: rgbsplit | RGB split | effect | Retro
//== param: amount float 0.012 0.0 0.05
vec4 effect(vec2 uv) {
  float a = amount * (0.6 + 0.4 * sin(uTime * 6.0));
  return vec4(getColor(uv + vec2(a, 0.0)).r, getColor(uv).g, getColor(uv - vec2(a, 0.0)).b, 1.0);
}

//== id: scanlines | Scanlines | effect | Retro
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float s = 0.8 + 0.2 * sin((uv.y * uResolution.y / 3.0 + uTime * 20.0) * PI);
  return vec4(c * s, 1.0);
}

//== id: crt | CRT | effect | Retro
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  vec2 q = 0.5 + d * (1.0 + 0.12 * dot(d, d) * 4.0);
  if (q.x < 0.0 || q.x > 1.0 || q.y < 0.0 || q.y > 1.0) return vec4(0.0, 0.0, 0.0, 1.0);
  vec3 c = getColor(q).rgb;
  float mask = 0.85 + 0.15 * sin(q.x * uResolution.x * 2.1);
  float scan = 0.85 + 0.15 * sin(q.y * uResolution.y * 1.2 + uTime * 10.0);
  float vig = smoothstep(0.75, 0.35, length(d));
  return vec4(c * mask * scan * (0.4 + 0.6 * vig) * 1.15, 1.0);
}

//== id: glitch | Glitch | effect | Party
vec4 effect(vec2 uv) {
  float t = floor(uTime * 12.0);
  float block = floor(uv.y * 14.0);
  float on = step(0.75, rand(vec2(block, t)));
  float shift = (rand(vec2(block + 3.0, t)) - 0.5) * 0.12 * on;
  vec2 q = vec2(fract(uv.x + shift), uv.y);
  vec3 c = vec3(getColor(q + vec2(0.01 * on, 0.0)).r, getColor(q).g, getColor(q - vec2(0.01 * on, 0.0)).b);
  float noiseLine = step(0.985, rand(vec2(floor(uv.y * 300.0), t))) * 0.6;
  return vec4(c + noiseLine, 1.0);
}

//== id: shake | Shake | effect | Motion
//== param: strength float 0.02 0.0 0.08
vec4 effect(vec2 uv) {
  float t = uTime * 30.0;
  vec2 o = vec2(noise(vec2(t, 1.0)), noise(vec2(t, 7.0))) - 0.5;
  float s = 1.0 + strength * 2.0;
  return getColor(0.5 + (uv - 0.5) / s + o * strength * 2.0);
}

//== id: earthquake | Earthquake | effect | Motion
vec4 effect(vec2 uv) {
  float t = uTime * 40.0;
  vec2 o = vec2(rand(vec2(floor(t), 1.0)), rand(vec2(floor(t), 2.0))) - 0.5;
  float decay = 0.5 + 0.5 * sin(uTime * 3.0);
  return getColor(0.5 + (uv - 0.5) / 1.08 + o * 0.05 * decay);
}

//== id: zoompulse | Zoom pulse | effect | Motion
//== param: bpm float 120.0 60.0 180.0
vec4 effect(vec2 uv) {
  float beat = fract(uTime * bpm / 60.0);
  float z = 1.0 + 0.08 * exp(-beat * 6.0);
  return getColor(0.5 + (uv - 0.5) / z);
}

//== id: kenburns | Slow zoom | effect | Motion
//== param: zoom float 0.15 0.0 0.5
vec4 effect(vec2 uv) {
  float z = 1.0 + zoom * uProgress;
  return getColor(0.5 + (uv - 0.5) / z + vec2(0.03, 0.02) * uProgress);
}

//== id: swing | Swing | effect | Motion
vec4 effect(vec2 uv) {
  float a = sin(uTime * 3.0) * 0.06;
  vec2 d = (uv - 0.5) * vec2(uResolution.x / uResolution.y, 1.0);
  d = mat2(cos(a), -sin(a), sin(a), cos(a)) * d;
  return getColor(0.5 + d / vec2(uResolution.x / uResolution.y, 1.0) / 1.12);
}

//== id: heartbeat | Heartbeat | effect | Motion
vec4 effect(vec2 uv) {
  float p = fract(uTime * 1.2);
  float k = exp(-p * 10.0) + 0.6 * exp(-abs(p - 0.25) * 14.0);
  float z = 1.0 + 0.07 * k;
  vec3 c = getColor(0.5 + (uv - 0.5) / z).rgb;
  return vec4(c + vec3(0.25, 0.0, 0.05) * k * smoothstep(0.3, 0.8, length(uv - 0.5)), 1.0);
}

//== id: wobble | Wobble | effect | Motion
vec4 effect(vec2 uv) {
  vec2 q = uv + vec2(sin(uv.y * 10.0 + uTime * 4.0), cos(uv.x * 10.0 + uTime * 4.0)) * 0.008;
  return getColor(q);
}

//== id: ripple | Ripple | effect | Lens
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float r = length(d);
  float w = sin(r * 40.0 - uTime * 6.0) * 0.008 * smoothstep(0.7, 0.0, r);
  return getColor(uv + normalize(d + 1e-6) * w);
}

//== id: wave | Heat wave | effect | Lens
vec4 effect(vec2 uv) {
  float w = sin(uv.y * 60.0 + uTime * 5.0) * 0.003 + sin(uv.y * 23.0 - uTime * 3.0) * 0.002;
  return getColor(uv + vec2(w, 0.0));
}

//== id: raindrops | Rain on glass | effect | Weather
vec4 effect(vec2 uv) {
  float aspect = uResolution.x / uResolution.y;
  vec2 p = uv * vec2(aspect, 1.0) * 8.0;
  vec2 id = floor(p);
  vec2 f = fract(p) - 0.5;
  float r = rand(id);
  float y = fract(r * 7.0 - uTime * (0.1 + r * 0.3));
  vec2 dp = f - vec2((r - 0.5) * 0.6, y - 0.5);
  float drop = smoothstep(0.12, 0.08, length(dp)) * step(0.5, r);
  vec2 refr = uv - dp * drop * 0.05;
  vec3 c = getColor(refr).rgb;
  vec3 blurred = (getColor(uv + vec2(0.004, 0.0)).rgb + getColor(uv - vec2(0.004, 0.0)).rgb + getColor(uv + vec2(0.0, 0.004)).rgb + getColor(uv - vec2(0.0, 0.004)).rgb) * 0.25;
  return vec4(mix(blurred, c, drop) + drop * 0.08, 1.0);
}

//== id: rain | Rain | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * 0.85;
  float acc = 0.0;
  for (int i = 0; i < 3; i++) {
    float fi = float(i);
    vec2 p = vec2(uv.x * (60.0 + fi * 30.0) + uv.y * 4.0, uv.y * (3.0 + fi) + uTime * (2.5 + fi));
    vec2 id = floor(p);
    float r = rand(id + fi);
    float streak = step(0.9, r) * smoothstep(0.5, 0.0, abs(fract(p.x) - 0.5)) * smoothstep(0.0, 0.6, fract(p.y));
    acc += streak * (0.25 - fi * 0.05);
  }
  return vec4(c + acc * vec3(0.8, 0.85, 1.0), 1.0);
}

//== id: snow | Snow | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float aspect = uResolution.x / uResolution.y;
  float acc = 0.0;
  for (int i = 0; i < 3; i++) {
    float fi = float(i);
    float scale = 10.0 + fi * 8.0;
    vec2 p = vec2(uv.x * aspect, uv.y) * scale + vec2(sin(uTime * 0.6 + fi) * 0.8, uTime * (1.0 + fi * 0.5));
    vec2 id = floor(p);
    vec2 f = fract(p) - 0.5;
    vec2 o = vec2(rand(id + fi) - 0.5, rand(id + fi + 9.0) - 0.5) * 0.6;
    acc += smoothstep(0.08 - fi * 0.015, 0.0, length(f - o)) * step(0.4, rand(id + fi * 3.0));
  }
  return vec4(c + acc * 0.9, 1.0);
}

//== id: fog | Fog | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float f = noise(uv * 3.0 + vec2(uTime * 0.08, 0.0)) * 0.6 + noise(uv * 7.0 - vec2(uTime * 0.12, 0.0)) * 0.4;
  float k = smoothstep(0.3, 0.9, f) * 0.6 + 0.15;
  return vec4(mix(c, vec3(0.85, 0.88, 0.9), k), 1.0);
}

//== id: lightning | Lightning | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float t = uTime * 2.0;
  float f = step(0.93, rand(vec2(floor(t * 6.0), 1.0)));
  vec3 storm = c * vec3(0.7, 0.75, 0.9);
  return vec4(storm + f * vec3(0.8, 0.85, 1.0) * (0.6 + 0.4 * rand(vec2(floor(t * 30.0), 2.0))), 1.0);
}

//== id: strobe | Strobe | effect | Party
//== param: bpm float 128.0 60.0 200.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float b = fract(uTime * bpm / 60.0);
  return vec4(c + vec3(1.0) * exp(-b * 18.0), 1.0);
}

//== id: rainbow | Rainbow | effect | Party
vec4 effect(vec2 uv) {
  vec3 hsv = rgb2hsv(getColor(uv).rgb);
  hsv.x = fract(hsv.x + uTime * 0.25 + uv.x * 0.2);
  hsv.y = max(hsv.y, 0.5);
  return vec4(hsv2rgb(hsv), 1.0);
}

//== id: disco | Disco | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float aspect = uResolution.x / uResolution.y;
  vec3 lights = vec3(0.0);
  for (int i = 0; i < 4; i++) {
    float fi = float(i);
    vec2 p = vec2(0.5 + 0.4 * sin(uTime * (0.8 + fi * 0.3) + fi * 2.0), 0.5 + 0.4 * cos(uTime * (0.7 + fi * 0.25) + fi));
    lights += hsv2rgb(vec3(fract(fi * 0.25 + uTime * 0.1), 0.9, 1.0)) * smoothstep(0.35, 0.0, length((uv - p) * vec2(aspect, 1.0)));
  }
  return vec4(c * 0.55 + c * lights * 1.2, 1.0);
}

//== id: kaleidospin | Kaleido spin | effect | Party
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float a = atan(d.y, d.x) + uTime * 0.6;
  float seg = 2.0 * PI / 6.0;
  a = abs(mod(a, seg) - seg * 0.5);
  return getColor(0.5 + vec2(cos(a), sin(a)) * length(d));
}

//== id: confetti | Confetti | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float aspect = uResolution.x / uResolution.y;
  vec2 p = vec2(uv.x * aspect, uv.y) * 14.0 + vec2(0.0, uTime * 2.5);
  vec2 id = floor(p);
  vec2 f = fract(p) - 0.5;
  float r = rand(id);
  float a = uTime * (2.0 + r * 4.0) + r * 6.28;
  vec2 q = mat2(cos(a), -sin(a), sin(a), cos(a)) * (f - (vec2(rand(id + 2.0), rand(id + 5.0)) - 0.5) * 0.5);
  float piece = step(abs(q.x), 0.12) * step(abs(q.y), 0.05 + 0.05 * abs(sin(a))) * step(0.55, r);
  vec3 col = hsv2rgb(vec3(rand(id + 9.0), 0.8, 1.0));
  return vec4(mix(c, col, piece), 1.0);
}

//== id: hueshift | Hue cycle | effect | Color
vec4 effect(vec2 uv) {
  vec3 hsv = rgb2hsv(getColor(uv).rgb);
  hsv.x = fract(hsv.x + uTime * 0.15);
  return vec4(hsv2rgb(hsv), 1.0);
}

//== id: blink | Blink | effect | Light
vec4 effect(vec2 uv) {
  float b = smoothstep(0.0, 0.08, abs(fract(uTime * 0.5) - 0.5) - 0.02);
  return vec4(getColor(uv).rgb * b, 1.0);
}

//== id: focuspull | Focus pull | effect | Lens
vec4 effect(vec2 uv) {
  float r = (1.0 - uProgress) * 8.0 / uResolution.y;
  vec3 acc = vec3(0.0);
  for (int i = -2; i <= 2; i++) {
    for (int j = -2; j <= 2; j++) {
      acc += getColor(uv + vec2(float(i), float(j)) * r * vec2(uResolution.y / uResolution.x, 1.0) * 1.5).rgb;
    }
  }
  return vec4(acc / 25.0, 1.0);
}

//== id: spotlight | Spotlight | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 p = vec2(0.5 + 0.3 * sin(uTime * 0.9), 0.5 + 0.25 * sin(uTime * 1.3));
  float aspect = uResolution.x / uResolution.y;
  float s = smoothstep(0.45, 0.1, length((uv - p) * vec2(aspect, 1.0)));
  return vec4(c * (0.25 + 0.95 * s), 1.0);
}

//== id: frameflash | Flash frames | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float f = step(0.85, fract(uTime * 2.0));
  return vec4(mix(c, vec3(1.0), f * 0.7), 1.0);
}

//== id: echo | Echo | effect | Motion
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 e1 = getColor(0.5 + (uv - 0.5) * 0.94).rgb;
  vec3 e2 = getColor(0.5 + (uv - 0.5) * 0.88).rgb;
  float k = 0.5 + 0.5 * sin(uTime * 3.0);
  return vec4(c * 0.6 + e1 * 0.25 * k + e2 * 0.15 * k + c * 0.4 * (1.0 - k), 1.0);
}

//== id: dreamglow | Dream | effect | Light
vec4 effect(vec2 uv) {
  vec2 px = 5.0 / uResolution;
  vec3 b = vec3(0.0);
  for (int i = -2; i <= 2; i++) {
    for (int j = -2; j <= 2; j++) {
      b += getColor(uv + vec2(float(i), float(j)) * px).rgb;
    }
  }
  b /= 25.0;
  vec3 c = getColor(uv).rgb;
  vec3 tint = vec3(1.0, 0.85, 0.95);
  return vec4(mix(c, 1.0 - (1.0 - c) * (1.0 - b * tint), 0.6 + 0.2 * sin(uTime)), 1.0);
}

//== id: pixelflow | Pixel flow | effect | Party
vec4 effect(vec2 uv) {
  float s = 6.0 + 40.0 * (0.5 + 0.5 * sin(uTime * 2.0));
  vec2 cells = uResolution / s;
  return getColor((floor(uv * cells) + 0.5) / cells);
}

// ---------------------------------------------------------------- clip in/out animations
// uProgress is visibility: 0 = fully hidden, 1 = fully shown (the app eases it).

//== id: anim_fade | Fade | anim | Basic
vec4 effect(vec2 uv) {
  return vec4(getColor(uv).rgb * uProgress, 1.0);
}

//== id: anim_zoomin | Zoom in | anim | Basic
vec4 effect(vec2 uv) {
  float z = mix(0.5, 1.0, uProgress);
  vec2 q = 0.5 + (uv - 0.5) / z;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside * uProgress, 1.0);
}

//== id: anim_zoomout | Zoom out | anim | Basic
vec4 effect(vec2 uv) {
  float z = mix(2.0, 1.0, uProgress);
  return vec4(getColor(0.5 + (uv - 0.5) / z).rgb * min(1.0, uProgress * 1.5), 1.0);
}

//== id: anim_slideleft | Slide left | anim | Slide
vec4 effect(vec2 uv) {
  vec2 q = uv + vec2(1.0 - uProgress, 0.0);
  return vec4(getColor(q).rgb * step(q.x, 1.0), 1.0);
}

//== id: anim_slideright | Slide right | anim | Slide
vec4 effect(vec2 uv) {
  vec2 q = uv - vec2(1.0 - uProgress, 0.0);
  return vec4(getColor(q).rgb * step(0.0, q.x), 1.0);
}

//== id: anim_slideup | Slide up | anim | Slide
vec4 effect(vec2 uv) {
  vec2 q = uv + vec2(0.0, 1.0 - uProgress);
  return vec4(getColor(q).rgb * step(q.y, 1.0), 1.0);
}

//== id: anim_slidedown | Slide down | anim | Slide
vec4 effect(vec2 uv) {
  vec2 q = uv - vec2(0.0, 1.0 - uProgress);
  return vec4(getColor(q).rgb * step(0.0, q.y), 1.0);
}

//== id: anim_spin | Spin | anim | Basic
vec4 effect(vec2 uv) {
  float a = (1.0 - uProgress) * 3.14159;
  float z = mix(0.3, 1.0, uProgress);
  float aspect = uResolution.x / uResolution.y;
  vec2 d = (uv - 0.5) * vec2(aspect, 1.0);
  d = mat2(cos(a), -sin(a), sin(a), cos(a)) * d / z;
  vec2 q = 0.5 + d / vec2(aspect, 1.0);
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: anim_blur | Blur in | anim | Basic
vec4 effect(vec2 uv) {
  float r = (1.0 - uProgress) * 12.0 / uResolution.y;
  vec3 acc = vec3(0.0);
  for (int i = -2; i <= 2; i++) {
    for (int j = -2; j <= 2; j++) {
      acc += getColor(uv + vec2(float(i), float(j)) * r * vec2(uResolution.y / uResolution.x, 1.0) * 2.0).rgb;
    }
  }
  return vec4(acc / 25.0 * min(1.0, 0.3 + uProgress), 1.0);
}

//== id: anim_iris | Circle | anim | Shape
vec4 effect(vec2 uv) {
  float aspect = uResolution.x / uResolution.y;
  float r = length((uv - 0.5) * vec2(aspect, 1.0));
  float maxR = length(vec2(aspect, 1.0)) * 0.5;
  float m = smoothstep(uProgress * maxR + 0.01, uProgress * maxR - 0.01, r);
  return vec4(getColor(uv).rgb * m, 1.0);
}

//== id: anim_wipe | Wipe | anim | Shape
vec4 effect(vec2 uv) {
  float m = smoothstep(uProgress + 0.02, uProgress - 0.02, uv.x);
  return vec4(getColor(uv).rgb * m, 1.0);
}

//== id: anim_flash | Flash | anim | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  return vec4(mix(vec3(1.0), c, smoothstep(0.0, 1.0, uProgress)), 1.0);
}

//== id: anim_bounce | Bounce | anim | Basic
vec4 effect(vec2 uv) {
  float p = uProgress;
  float s = p < 1.0 ? 1.0 - abs(cos(p * 7.85)) * (1.0 - p) : 1.0;
  float z = max(0.05, s);
  vec2 q = 0.5 + (uv - 0.5) / z;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: anim_glitch | Glitch in | anim | Light
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  float t = floor(uTime * 20.0);
  float block = floor(uv.y * 10.0);
  float shift = (rand(vec2(block, t)) - 0.5) * 0.3 * k;
  vec2 q = vec2(fract(uv.x + shift), uv.y);
  vec3 c = vec3(getColor(q + vec2(0.03 * k, 0.0)).r, getColor(q).g, getColor(q - vec2(0.03 * k, 0.0)).b);
  return vec4(c * min(1.0, uProgress * 2.0), 1.0);
}

//== id: anim_rise | Rise | anim | Slide
vec4 effect(vec2 uv) {
  float e = 1.0 - ((1.0 - uProgress) * (1.0 - uProgress) * (1.0 - uProgress));
  vec2 q = uv + vec2(0.0, (1.0 - e) * 0.25);
  float inside = step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside * e, 1.0);
}

// ---------------------------------------------------------------- filters, batch 2
//== id: bw | Black & white | filter | Color
//== param: contrast float 1.15 0.6 2.0
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  l = clamp((l - 0.5) * contrast + 0.5, 0.0, 1.0);
  return vec4(vec3(l), 1.0);
}

//== id: sepia | Sepia | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 s = vec3(dot(c, vec3(0.393, 0.769, 0.189)), dot(c, vec3(0.349, 0.686, 0.168)), dot(c, vec3(0.272, 0.534, 0.131)));
  return vec4(min(s, vec3(1.0)), 1.0);
}

//== id: noir | Noir | filter | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  l = smoothstep(0.12, 0.88, l);
  float v = 1.0 - 0.75 * pow(length(uv - 0.5) * 1.3, 2.2);
  return vec4(vec3(l * v), 1.0);
}

//== id: colorpop | Color pop | filter | Color
//== param: hue float 0.0 0.0 1.0
//== param: range float 0.08 0.02 0.3
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 h = rgb2hsv(c);
  float d = abs(h.x - hue);
  d = min(d, 1.0 - d);
  float keep = (1.0 - smoothstep(range, range * 1.6, d)) * smoothstep(0.15, 0.3, h.y);
  return vec4(mix(vec3(luma(c)), c, keep), 1.0);
}

//== id: crossprocess | Cross process | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 o;
  o.r = smoothstep(0.05, 0.95, c.r);
  o.g = c.g * 0.9 + 0.08 * smoothstep(0.2, 0.8, c.g);
  o.b = c.b * 0.7 + 0.15;
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: bleach | Bleach bypass | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 b = vec3(l);
  vec3 r = mix(2.0 * c * b, 1.0 - 2.0 * (1.0 - c) * (1.0 - b), step(0.5, b));
  return vec4(mix(c, r, 0.75), 1.0);
}

//== id: vintagefade | Faded | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  c = mix(vec3(luma(c)), c, 0.75);
  c = c * 0.82 + vec3(0.12, 0.1, 0.08);
  return vec4(c, 1.0);
}

//== id: cyberpunk | Cyberpunk | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 shadow = vec3(0.1, 0.05, 0.35);
  vec3 high = vec3(1.0, 0.25, 0.75);
  vec3 g = mix(shadow, high, smoothstep(0.1, 0.9, l));
  vec3 o = mix(c, g, 0.55) + vec3(0.0, 0.08, 0.12) * (1.0 - l);
  return vec4(clamp(o * 1.1, 0.0, 1.0), 1.0);
}

//== id: goldenhour | Golden hour | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  c *= vec3(1.12, 1.0, 0.78);
  c = pow(c, vec3(0.92));
  c += vec3(0.08, 0.04, 0.0) * (1.0 - uv.y);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: coldblue | Cold | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  c = mix(vec3(luma(c)), c, 0.8) * vec3(0.85, 0.97, 1.15);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: matrix | Matrix | filter | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  return vec4(l * 0.35, pow(l, 0.8), l * 0.3, 1.0);
}

//== id: popart | Pop art | filter | Art
vec4 effect(vec2 uv) {
  vec2 cell = step(0.5, uv);
  vec2 q = fract(uv * 2.0);
  float l = luma(getColor(q).rgb);
  float k = cell.x + cell.y * 2.0;
  vec3 a = k < 0.5 ? vec3(1.0, 0.2, 0.55) : k < 1.5 ? vec3(0.1, 0.8, 1.0) : k < 2.5 ? vec3(1.0, 0.85, 0.1) : vec3(0.5, 1.0, 0.3);
  vec3 b = k < 0.5 ? vec3(0.1, 0.1, 0.5) : k < 1.5 ? vec3(0.6, 0.0, 0.4) : k < 2.5 ? vec3(0.0, 0.35, 0.6) : vec3(0.7, 0.1, 0.1);
  return vec4(mix(b, a, step(0.45, l)), 1.0);
}

//== id: infrared | Infrared | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float foliage = clamp(c.g * 1.6 - c.r * 0.5 - c.b * 0.4, 0.0, 1.0);
  vec3 o = vec3(c.r * 0.4 + foliage * 0.9 + 0.1, c.r * 0.3 + foliage * 0.55, c.b * 0.8 + 0.1);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: pastel | Pastel | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 h = rgb2hsv(c);
  h.y *= 0.55;
  h.z = 0.35 + h.z * 0.7;
  return vec4(hsv2rgb(h), 1.0);
}

//== id: vivid | Vivid | filter | Color
//== param: amount float 1.8 1.0 2.5
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
  vec3 o = mix(vec3(l), c, mix(amount, 1.0, sat));
  o = (o - 0.5) * 1.12 + 0.5;
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: lomo | Lomo | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  c = smoothstep(0.0, 1.0, c);
  c.r = smoothstep(0.05, 0.95, c.r);
  c.b = c.b * 0.85 + 0.05;
  float v = 1.0 - pow(length(uv - 0.5) * 1.35, 2.5);
  return vec4(clamp(c * v, 0.0, 1.0), 1.0);
}

//== id: polaroid | Instant | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  c = c * vec3(1.05, 1.0, 0.9) + vec3(0.04, 0.03, 0.06);
  c = mix(vec3(luma(c)), c, 0.85);
  c = pow(c, vec3(0.95, 1.0, 1.1));
  float v = 1.0 - 0.3 * ((length(uv - 0.5) * 1.4) * (length(uv - 0.5) * 1.4) * (length(uv - 0.5) * 1.4));
  return vec4(clamp(c * v, 0.0, 1.0), 1.0);
}

//== id: chrome | Chrome | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  c = (c - 0.5) * 1.2 + 0.5;
  c = mix(vec3(luma(c)), c, 1.15);
  c *= vec3(1.0, 1.02, 1.06);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: watercolor | Watercolor | filter | Art
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  vec2 w = vec2(noise(uv * 40.0), noise(uv * 40.0 + 7.0)) - 0.5;
  vec2 q = uv + w * px * 6.0;
  vec3 acc = vec3(0.0);
  for (int i = -2; i <= 2; i++) {
    for (int j = -2; j <= 2; j++) {
      acc += getColor(q + vec2(float(i), float(j)) * px * 2.0).rgb;
    }
  }
  acc /= 25.0;
  acc = floor(acc * 8.0 + 0.5) / 8.0;
  float paper = 0.94 + 0.06 * noise(uv * uResolution * 0.5);
  float edge = abs(luma(getColor(q + vec2(px.x * 2.0, 0.0)).rgb) - luma(getColor(q - vec2(px.x * 2.0, 0.0)).rgb));
  return vec4(mix(acc, acc * 0.7, smoothstep(0.05, 0.2, edge)) * paper + 0.04, 1.0);
}

//== id: pencilcolor | Color pencil | filter | Art
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  float e = clamp(length(vec2(gx, gy)) * 4.0, 0.0, 1.0);
  vec3 c = getColor(uv).rgb;
  float hatch = 0.85 + 0.15 * sin((uv.x + uv.y) * uResolution.y * 0.8);
  vec3 paper = vec3(0.97, 0.95, 0.9);
  vec3 o = mix(paper, c, 0.55) * hatch;
  return vec4(o * (1.0 - e * 0.8), 1.0);
}

//== id: stainedglass | Stained glass | filter | Art
//== param: cells float 14.0 6.0 40.0
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 p = uv * asp * cells;
  vec2 ip = floor(p);
  float d1 = 9.0;
  float d2 = 9.0;
  vec2 best = vec2(0.0);
  for (int i = -1; i <= 1; i++) {
    for (int j = -1; j <= 1; j++) {
      vec2 g = ip + vec2(float(i), float(j));
      vec2 o = g + vec2(rand(g), rand(g + 3.1));
      float d = length(p - o);
      if (d < d1) { d2 = d1; d1 = d; best = o; } else if (d < d2) { d2 = d; }
    }
  }
  vec3 c = getColor(best / asp / cells).rgb;
  c = mix(vec3(luma(c)), c, 1.4);
  float lead = smoothstep(0.02, 0.08, d2 - d1);
  return vec4(clamp(c, 0.0, 1.0) * lead, 1.0);
}

//== id: hexpixel | Hex pixels | filter | Art
//== param: size float 40.0 10.0 120.0
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 p = uv * asp * size;
  vec2 r = vec2(1.0, 1.7320508);
  vec2 h = r * 0.5;
  vec2 a = mod(p, r) - h;
  vec2 b = mod(p - h, r) - h;
  vec2 g = dot(a, a) < dot(b, b) ? a : b;
  vec2 center = p - g;
  return vec4(getColor(center / asp / size).rgb, 1.0);
}

//== id: pointillism | Pointillism | filter | Art
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 p = uv * asp * 70.0;
  vec2 ip = floor(p);
  vec3 acc = vec3(0.97, 0.95, 0.9);
  for (int i = -1; i <= 1; i++) {
    for (int j = -1; j <= 1; j++) {
      vec2 g = ip + vec2(float(i), float(j));
      vec2 o = g + 0.5 + (vec2(rand(g), rand(g + 1.7)) - 0.5) * 0.6;
      vec3 c = getColor(o / asp / 70.0).rgb;
      float r = 0.55 + 0.25 * rand(g + 5.3);
      float m = 1.0 - smoothstep(r - 0.1, r, length(p - o));
      acc = mix(acc, c, m);
    }
  }
  return vec4(acc, 1.0);
}

//== id: woodcut | Woodcut | filter | Art
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  float l = luma(getColor(uv).rgb);
  l = smoothstep(0.1, 0.9, l);
  // Carved lines about 5 px apart that bend a little; thicker where the picture is dark.
  float y = uv.y * uResolution.y / 5.0 + noise(uv * 5.0) * 1.5;
  float line = abs(fract(y) - 0.5) * 2.0;
  float ink = step(line, (1.0 - l) * 1.1);
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  float edge = smoothstep(0.08, 0.25, length(vec2(gx, gy)));
  vec3 paper = vec3(0.95, 0.9, 0.8);
  return vec4(mix(paper, vec3(0.12, 0.08, 0.06), max(ink, edge)), 1.0);
}

//== id: risograph | Riso | filter | Art
vec4 effect(vec2 uv) {
  vec2 off = vec2(0.006, 0.003);
  float a = luma(getColor(uv).rgb);
  float b = luma(getColor(uv + off).rgb);
  vec2 q = uv * uResolution / 3.0;
  float grain = noise(q) * 0.25;
  float pink = smoothstep(0.75, 0.25, a + grain - 0.12);
  float blue = smoothstep(0.85, 0.35, b + grain);
  vec3 paper = vec3(0.98, 0.96, 0.92);
  vec3 c = paper * mix(vec3(1.0), vec3(1.0, 0.35, 0.6), pink) * mix(vec3(1.0), vec3(0.2, 0.45, 0.9), blue);
  return vec4(c, 1.0);
}

//== id: newspaper | Newsprint | filter | Art
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  vec2 p = uv * uResolution / 5.0;
  float a = 0.785;
  vec2 r = vec2(cos(a) * p.x - sin(a) * p.y, sin(a) * p.x + cos(a) * p.y);
  float d = length(fract(r) - 0.5);
  float dot_ = step(d, (1.0 - l) * 0.7);
  vec3 paper = vec3(0.93, 0.91, 0.85);
  return vec4(mix(paper, vec3(0.1), dot_), 1.0);
}

//== id: blueprint | Blueprint | filter | Art
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  float e = clamp(length(vec2(gx, gy)) * 5.0, 0.0, 1.0);
  vec2 g = fract(uv * uResolution / 24.0);
  float grid = step(0.95, max(g.x, g.y)) * 0.15;
  vec3 bg = vec3(0.05, 0.25, 0.55);
  return vec4(bg + vec3(0.85, 0.92, 1.0) * max(e, grid), 1.0);
}

//== id: prism | Prism | filter | Lens
vec4 effect(vec2 uv) {
  vec3 a = getColor(uv * 0.85 + vec2(0.0, 0.075)).rgb;
  vec3 b = getColor(uv * 0.85 + vec2(0.15, 0.075)).rgb;
  vec3 c = getColor(uv * 0.85 + vec2(0.075, 0.0)).rgb;
  return vec4(a.r, c.g, b.b, 1.0);
}

//== id: barrel | Barrel | filter | Lens
//== param: amount float 0.35 0.0 1.0
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float r2 = dot(d, d);
  vec2 q = 0.5 + d * (1.0 + amount * r2) / (1.0 + amount * 0.25);
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: pinch | Pinch | filter | Lens
//== param: amount float 0.5 0.0 1.0
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float r = length(d);
  float k = 1.0 + amount * (1.0 - smoothstep(0.0, 0.5, r));
  return getColor(0.5 + d * k);
}

//== id: twirl | Twirl | filter | Lens
//== param: angle float 2.5 -6.0 6.0
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float r = length(d);
  float a = angle * (1.0 - smoothstep(0.0, 0.5, r));
  float s = sin(a);
  float c = cos(a);
  d = vec2(c * d.x - s * d.y, s * d.x + c * d.y);
  return getColor(0.5 + d / asp);
}

//== id: frosted | Frosted glass | filter | Lens
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  vec2 j = vec2(rand(uv * 91.0), rand(uv * 57.0 + 3.0)) - 0.5;
  vec3 c = getColor(uv + j * px * 14.0).rgb;
  return vec4(c * 0.92 + 0.08, 1.0);
}

//== id: zoomblur | Zoom blur | filter | Lens
//== param: amount float 0.12 0.0 0.4
vec4 effect(vec2 uv) {
  vec3 acc = vec3(0.0);
  vec2 d = uv - 0.5;
  for (int i = 0; i < 16; i++) {
    float k = 1.0 - amount * float(i) / 15.0;
    acc += getColor(0.5 + d * k).rgb;
  }
  return vec4(acc / 16.0, 1.0);
}

//== id: radialblur | Spin blur | filter | Lens
//== param: amount float 0.12 0.0 0.5
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 16; i++) {
    float a = amount * (float(i) / 15.0 - 0.5);
    vec2 r = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y);
    acc += getColor(0.5 + r / asp).rgb;
  }
  return vec4(acc / 16.0, 1.0);
}

//== id: motionblur | Motion blur | filter | Lens
//== param: amount float 0.04 0.0 0.15
vec4 effect(vec2 uv) {
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 16; i++) acc += getColor(uv + vec2(amount * (float(i) / 15.0 - 0.5), 0.0)).rgb;
  return vec4(acc / 16.0, 1.0);
}

//== id: doublevision | Double vision | filter | Lens
vec4 effect(vec2 uv) {
  vec3 a = getColor(uv - vec2(0.02, 0.0)).rgb;
  vec3 b = getColor(uv + vec2(0.02, 0.01)).rgb;
  return vec4(mix(a, b, 0.5), 1.0);
}

//== id: clarity | Clarity | filter | Detail
//== param: amount float 1.0 0.0 2.5
vec4 effect(vec2 uv) {
  vec2 px = 4.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  vec3 b = (getColor(uv + vec2(px.x, 0.0)).rgb + getColor(uv - vec2(px.x, 0.0)).rgb + getColor(uv + vec2(0.0, px.y)).rgb + getColor(uv - vec2(0.0, px.y)).rgb
    + getColor(uv + px).rgb + getColor(uv - px).rgb + getColor(uv + vec2(px.x, -px.y)).rgb + getColor(uv + vec2(-px.x, px.y)).rgb) / 8.0;
  float l = luma(c);
  float mid = 1.0 - abs(l - 0.5) * 2.0;
  return vec4(clamp(c + (c - b) * amount * (0.4 + mid), 0.0, 1.0), 1.0);
}

//== id: bloom | Glow | filter | Detail
//== param: amount float 0.8 0.0 2.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 g = vec3(0.0);
  for (int i = 0; i < 12; i++) {
    float a = float(i) * 0.5236;
    for (int r = 1; r <= 3; r++) {
      vec2 o = vec2(cos(a), sin(a)) * float(r) * 0.012 * vec2(uResolution.y / uResolution.x, 1.0);
      vec3 s = getColor(uv + o).rgb;
      g += max(s - 0.6, 0.0);
    }
  }
  g /= 36.0;
  return vec4(clamp(c + g * amount * 3.0, 0.0, 1.0), 1.0);
}

//== id: denoise | Smooth | filter | Detail
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  vec3 acc = vec3(0.0);
  float wsum = 0.0;
  for (int i = -2; i <= 2; i++) {
    for (int j = -2; j <= 2; j++) {
      vec3 s = getColor(uv + vec2(float(i), float(j)) * px * 2.0).rgb;
      float w = exp(-dot(s - c, s - c) * 18.0) * exp(-float(i * i + j * j) * 0.2);
      acc += s * w;
      wsum += w;
    }
  }
  return vec4(acc / wsum, 1.0);
}

//== id: rgbcurves | Punch | filter | Detail
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  c = c * c * (3.0 - 2.0 * c);
  c = mix(vec3(luma(c)), c, 1.2);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: splitscreen3 | Triple | filter | Art
vec4 effect(vec2 uv) {
  float k = floor(uv.y * 3.0);
  vec2 q = vec2(uv.x, fract(uv.y * 3.0) / 3.0 + 1.0 / 3.0);
  vec3 c = getColor(q).rgb;
  vec3 tint = k < 0.5 ? vec3(1.0, 0.85, 0.85) : k < 1.5 ? vec3(1.0) : vec3(0.85, 0.9, 1.0);
  return vec4(c * tint, 1.0);
}

//== id: grid9 | Grid | filter | Art
vec4 effect(vec2 uv) {
  vec2 q = fract(uv * 3.0);
  vec2 g = fract(uv * 3.0);
  float line = step(0.985, max(g.x, g.y)) + step(max(g.x, g.y), 0.015);
  return vec4(getColor(q).rgb * (1.0 - clamp(line, 0.0, 1.0)), 1.0);
}

//== id: lineart | Line art | filter | Art
vec4 effect(vec2 uv) {
  vec2 px = 1.5 / uResolution;
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  float e = smoothstep(0.06, 0.2, length(vec2(gx, gy)));
  return vec4(vec3(1.0 - e), 1.0);
}

//== id: glasstiles | Glass tiles | filter | Lens
//== param: tiles float 12.0 4.0 30.0
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 p = uv * asp * tiles;
  vec2 f = fract(p) - 0.5;
  vec2 q = uv + f * 0.06 / tiles * 4.0;
  float edge = smoothstep(0.42, 0.5, max(abs(f.x), abs(f.y)));
  return vec4(getColor(q).rgb * (1.0 - edge * 0.35) + edge * 0.08, 1.0);
}

// ---------------------------------------------------------------- effects, batch 2
//== id: lightsweep | Light sweep | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float p = fract(uTime * 0.45) * 2.0 - 0.5;
  float d = (uv.x + uv.y * 0.5) * 0.66 - p;
  float band = exp(-d * d * 220.0);
  return vec4(c + band * 0.55, 1.0);
}

//== id: godrays | Sun rays | effect | Light
vec4 effect(vec2 uv) {
  vec2 src = vec2(0.75 + 0.05 * sin(uTime * 0.4), 0.15);
  vec3 c = getColor(uv).rgb;
  vec3 acc = vec3(0.0);
  vec2 d = (uv - src) / 24.0;
  vec2 q = uv;
  float w = 1.0;
  for (int i = 0; i < 24; i++) {
    q -= d;
    acc += max(getColor(q).rgb - 0.55, 0.0) * w;
    w *= 0.93;
  }
  return vec4(c + acc * vec3(1.0, 0.9, 0.7) * 0.25, 1.0);
}

//== id: neonflicker | Neon flicker | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float f = step(0.3, noise(vec2(uTime * 14.0, 0.0)));
  vec3 tint = mix(vec3(1.0, 0.2, 0.8), vec3(0.2, 0.9, 1.0), 0.5 + 0.5 * sin(uTime * 2.0));
  return vec4(mix(c * 0.55, c * 0.8 + tint * 0.35 * luma(c), f), 1.0);
}

//== id: fireworks | Fireworks | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * 0.85;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int k = 0; k < 4; k++) {
    float fk = float(k);
    float t = uTime * 0.6 + fk * 0.37;
    float cyc = floor(t);
    float ph = fract(t);
    vec2 center = vec2(0.2 + 0.6 * rand(vec2(cyc, fk)), 0.15 + 0.35 * rand(vec2(fk, cyc)));
    vec3 col = hsv2rgb(vec3(rand(vec2(cyc + fk, 2.0)), 0.8, 1.0));
    for (int i = 0; i < 16; i++) {
      float a = float(i) * 0.3927;
      vec2 p = center + vec2(cos(a), sin(a)) * ph * 0.22 / asp + vec2(0.0, ph * ph * 0.08);
      float d = length((uv - p) * asp);
      c += col * (1.0 - ph) * 0.0009 / (d * d + 0.0004);
    }
  }
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: starfield | Starfield | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 p = (uv - 0.5) * asp;
  float s = 0.0;
  for (int i = 0; i < 3; i++) {
    float z = fract(uTime * 0.15 + float(i) / 3.0);
    vec2 q = p / (z + 0.05) * 6.0;
    vec2 g = floor(q);
    vec2 f = fract(q) - 0.5;
    float r = rand(g + float(i) * 13.0);
    vec2 o = vec2(rand(g + 1.3), rand(g + 2.7)) - 0.5;
    s += step(0.85, r) * smoothstep(0.08, 0.0, length(f - o * 0.6)) * smoothstep(0.0, 0.3, z) * (1.0 - z);
  }
  return vec4(c + vec3(s), 1.0);
}

//== id: glitter | Glitter | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 g = floor(uv * uResolution / 3.0);
  float r = rand(g + floor(uTime * 12.0));
  float spark = step(0.992, r) * smoothstep(0.55, 0.9, luma(c));
  return vec4(c + spark * vec3(1.0, 0.95, 0.8), 1.0);
}

//== id: halo | Halo | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float r = length((uv - vec2(0.5, 0.4)) * asp);
  float ring = exp(-(((r - 0.28 - 0.02 * sin(uTime * 2.0)) * 18.0) * ((r - 0.28 - 0.02 * sin(uTime * 2.0)) * 18.0)));
  return vec4(c + ring * vec3(1.0, 0.85, 0.6) * 0.45, 1.0);
}

//== id: rainbowleak | Rainbow leak | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float x = uv.x * 0.8 + uv.y * 0.3 - uTime * 0.12;
  vec3 rb = hsv2rgb(vec3(fract(x), 0.7, 1.0));
  float m = smoothstep(0.4, 1.0, uv.x + 0.25 * sin(uTime * 0.7 + uv.y * 3.0));
  return vec4(1.0 - (1.0 - c) * (1.0 - rb * m * 0.55), 1.0);
}

//== id: super8 | Super 8 | effect | Retro
vec4 effect(vec2 uv) {
  float t = floor(uTime * 18.0);
  vec2 j = (vec2(rand(vec2(t, 1.0)), rand(vec2(t, 2.0))) - 0.5) * 0.004;
  vec3 c = getColor(uv + j).rgb;
  c = mix(vec3(luma(c)), c, 0.7) * vec3(1.08, 1.0, 0.8) + vec3(0.05, 0.03, 0.0);
  float flick = 0.94 + 0.06 * rand(vec2(t, 5.0));
  float grain = (rand(uv * uResolution + t) - 0.5) * 0.12;
  vec2 d = abs(uv - 0.5) * 2.0;
  float gate = 1.0 - smoothstep(0.82, 1.0, pow(pow(d.x, 4.0) + pow(d.y, 4.0), 0.25));
  return vec4((c * flick + grain) * gate, 1.0);
}

//== id: dust | Dust & scratches | effect | Retro
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float t = floor(uTime * 24.0);
  float x = rand(vec2(t, 3.0));
  float scratch = smoothstep(0.0015, 0.0, abs(uv.x - x)) * step(0.5, rand(vec2(t, 9.0)));
  vec2 g = floor(uv * uResolution / 4.0);
  float speck = step(0.9985, rand(g + t));
  return vec4(c * (1.0 - speck * 0.8) + scratch * 0.35, 1.0);
}

//== id: filmburn | Film burn | effect | Retro
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float n = noise(uv * 3.0 + vec2(uTime * 0.4, uTime * 0.2)) + 0.5 * noise(uv * 7.0 - uTime * 0.3);
  float edge = smoothstep(0.9, 1.4, n + (1.0 - uv.x) * 0.4);
  vec3 burn = mix(vec3(1.0, 0.45, 0.05), vec3(1.0, 0.95, 0.7), edge);
  return vec4(mix(c, burn, edge * 0.85), 1.0);
}

//== id: camcorder | Camcorder | effect | Retro
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  c = mix(vec3(luma(c)), c, 0.85);
  c += (rand(uv * uResolution + uTime) - 0.5) * 0.06;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float dotr = length((uv - vec2(0.1, 0.07)) * asp);
  float rec = smoothstep(0.022, 0.018, dotr) * step(0.5, fract(uTime));
  c = mix(c, vec3(1.0, 0.1, 0.1), rec);
  vec2 m = abs(uv - 0.5);
  float corner = step(0.42, m.x) * step(0.45, m.y) * (step(m.x, 0.435) + step(m.y, 0.465));
  float frameLine = clamp(corner, 0.0, 1.0);
  return vec4(mix(c, vec3(1.0), frameLine), 1.0);
}

//== id: sepiaflicker | Silent film | effect | Retro
vec4 effect(vec2 uv) {
  float t = floor(uTime * 16.0);
  float l = luma(getColor(uv).rgb);
  vec3 c = vec3(l) * vec3(1.05, 0.95, 0.78);
  c *= 0.85 + 0.15 * rand(vec2(t, 4.0));
  c += (rand(uv * uResolution * 0.5 + t) - 0.5) * 0.1;
  float v = 1.0 - ((length(uv - 0.5) * 1.4) * (length(uv - 0.5) * 1.4) * (length(uv - 0.5) * 1.4));
  return vec4(c * v, 1.0);
}

//== id: heartburst | Hearts | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 14; i++) {
    float fi = float(i);
    float sp = 0.08 + 0.08 * rand(vec2(fi, 1.0));
    vec2 p = vec2(rand(vec2(fi, 2.0)), 1.1 - fract(uTime * sp + rand(vec2(fi, 3.0))) * 1.25);
    p.x += 0.03 * sin(uTime * 2.0 + fi);
    float s = 0.03 + 0.03 * rand(vec2(fi, 4.0));
    vec2 q = (uv - p) * asp / s;
    q.y = -q.y;
    q.y -= sqrt(abs(q.x)) * 0.5;
    float h = smoothstep(1.05, 0.95, length(q));
    c = mix(c, vec3(1.0, 0.25, 0.45), h * 0.9);
  }
  return vec4(c, 1.0);
}

//== id: bubbles | Bubbles | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 16; i++) {
    float fi = float(i);
    vec2 p = vec2(rand(vec2(fi, 7.0)) + 0.03 * sin(uTime * 1.5 + fi), 1.1 - fract(uTime * (0.05 + 0.06 * rand(vec2(fi, 8.0))) + rand(vec2(fi, 9.0))) * 1.3);
    float r = 0.02 + 0.04 * rand(vec2(fi, 10.0));
    float d = length((uv - p) * asp);
    float rim = smoothstep(r, r - 0.004, d) - smoothstep(r - 0.006, r - 0.01, d);
    float hi = smoothstep(r * 0.35, 0.0, length((uv - p) * asp + vec2(r * 0.4, r * 0.4)));
    c += (rim * 0.5 + hi * 0.6) * vec3(0.9, 0.97, 1.0);
  }
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: spiral | Hypno | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float a = atan(d.y, d.x);
  float r = length(d);
  float s = 0.5 + 0.5 * sin(a * 3.0 + r * 30.0 - uTime * 6.0);
  return vec4(mix(c, c * hsv2rgb(vec3(fract(uTime * 0.2 + r), 0.6, 1.0)) * 1.4, s * 0.5), 1.0);
}

//== id: colorstrobe | Color strobe | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float k = floor(uTime * 8.0);
  vec3 tint = hsv2rgb(vec3(fract(k * 0.618), 0.8, 1.0));
  return vec4(mix(c, c * tint * 1.6, 0.6), 1.0);
}

//== id: partylights | Party lights | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * 0.7;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 5; i++) {
    float fi = float(i);
    vec2 p = vec2(0.5 + 0.4 * sin(uTime * (0.7 + fi * 0.2) + fi * 2.0), 0.5 + 0.4 * cos(uTime * (0.5 + fi * 0.15) + fi));
    vec3 col = hsv2rgb(vec3(fi / 5.0 + uTime * 0.05, 0.9, 1.0));
    float d = length((uv - p) * asp);
    c += col * smoothstep(0.35, 0.0, d) * 0.45;
  }
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: beatflash | Beat flash | effect | Light
//== param: bpm float 120.0 60.0 180.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float ph = fract(uTime * bpm / 60.0);
  float f = exp(-ph * 9.0);
  float z = 1.0 + 0.04 * f;
  vec3 s = getColor(0.5 + (uv - 0.5) / z).rgb;
  return vec4(s + f * 0.35, 1.0);
}

//== id: mirrorspin | Mirror spin | effect | Party
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float a = atan(d.y, d.x) + uTime * 0.6;
  float r = length(d);
  float seg = 6.2831853 / 6.0;
  a = mod(a, seg);
  a = abs(a - seg * 0.5);
  vec2 q = vec2(cos(a), sin(a)) * r;
  return getColor(0.5 + q / asp);
}

//== id: bounce | Bounce | effect | Motion
vec4 effect(vec2 uv) {
  float b = abs(sin(uTime * 4.0)) * 0.05;
  float z = 1.1;
  return getColor(0.5 + (uv - 0.5) / z + vec2(0.0, b - 0.025));
}

//== id: zoomshake | Impact | effect | Motion
vec4 effect(vec2 uv) {
  float ph = fract(uTime * 1.2);
  float k = exp(-ph * 6.0);
  float z = 1.0 + 0.12 * k;
  vec2 o = (vec2(noise(vec2(uTime * 40.0, 1.0)), noise(vec2(uTime * 40.0, 5.0))) - 0.5) * 0.04 * k;
  return getColor(0.5 + (uv - 0.5) / z + o);
}

//== id: drift | Drift | effect | Motion
vec4 effect(vec2 uv) {
  float z = 1.12;
  vec2 o = vec2(sin(uTime * 0.35), cos(uTime * 0.27)) * 0.04;
  return getColor(0.5 + (uv - 0.5) / z + o);
}

//== id: pendulum | Pendulum | effect | Motion
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float a = sin(uTime * 2.2) * 0.12;
  vec2 pivot = vec2(0.5, 0.0);
  vec2 d = (uv - pivot) * asp;
  d = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y);
  return getColor(pivot + d / asp / 1.15 + vec2(0.0, 0.06));
}

//== id: jello | Jello | effect | Motion
vec4 effect(vec2 uv) {
  float w = sin(uTime * 9.0) * exp(-fract(uTime * 0.8) * 3.0);
  vec2 q = uv;
  q.x += sin(uv.y * 6.0 + uTime * 9.0) * 0.015 * w;
  q.y += sin(uv.x * 6.0 + uTime * 7.0) * 0.015 * w;
  return getColor(0.5 + (q - 0.5) / 1.05);
}

//== id: slowspin | Slow spin | effect | Motion
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float a = uTime * 0.25;
  vec2 d = (uv - 0.5) * asp;
  d = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y);
  float z = 1.0 + length(asp) * 0.5;
  return getColor(0.5 + d / asp / z * 1.0);
}

//== id: dollyzoom | Dolly zoom | effect | Motion
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float t = 0.5 + 0.5 * sin(uTime * 0.8);
  float r = length(d);
  float k = mix(1.0, 0.75 + r * 0.6, t);
  return getColor(0.5 + d * k);
}

//== id: handheld | Handheld | effect | Motion
vec4 effect(vec2 uv) {
  float t = uTime;
  vec2 o = vec2(noise(vec2(t * 1.3, 2.0)), noise(vec2(t * 1.1, 9.0))) - 0.5;
  float a = (noise(vec2(t * 0.9, 4.0)) - 0.5) * 0.03;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  d = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y);
  return getColor(0.5 + d / asp / 1.08 + o * 0.03);
}

//== id: fireflies | Fireflies | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * 0.85;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 20; i++) {
    float fi = float(i);
    vec2 p = vec2(rand(vec2(fi, 1.0)), rand(vec2(fi, 2.0)));
    p += vec2(sin(uTime * 0.5 + fi), cos(uTime * 0.4 + fi * 1.3)) * 0.05;
    float glow = 0.5 + 0.5 * sin(uTime * (1.5 + rand(vec2(fi, 3.0))) + fi);
    float d = length((uv - p) * asp);
    c += vec3(1.0, 0.9, 0.4) * glow * 0.00025 / (d * d + 0.0002);
  }
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: embers | Embers | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 24; i++) {
    float fi = float(i);
    float sp = 0.08 + 0.1 * rand(vec2(fi, 5.0));
    vec2 p = vec2(rand(vec2(fi, 6.0)) + 0.05 * sin(uTime + fi), 1.05 - fract(uTime * sp + rand(vec2(fi, 7.0))) * 1.2);
    float d = length((uv - p) * asp);
    float life = 1.0 - fract(uTime * sp + rand(vec2(fi, 7.0)));
    c += vec3(1.0, 0.45, 0.1) * life * 0.00012 / (d * d + 0.00005);
  }
  c += vec3(0.12, 0.03, 0.0) * uv.y;
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: petals | Petals | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 18; i++) {
    float fi = float(i);
    float sp = 0.06 + 0.06 * rand(vec2(fi, 11.0));
    vec2 p = vec2(fract(rand(vec2(fi, 12.0)) + uTime * 0.03), fract(uTime * sp + rand(vec2(fi, 13.0))) * 1.2 - 0.1);
    p.x += 0.05 * sin(uTime * 1.3 + fi * 2.0);
    float a = uTime * 1.5 + fi;
    vec2 q = (uv - p) * asp;
    q = vec2(cos(a) * q.x - sin(a) * q.y, sin(a) * q.x + cos(a) * q.y);
    q.x *= 1.8;
    float s = 0.022 + 0.014 * rand(vec2(fi, 14.0));
    float m = smoothstep(s, s * 0.7, length(q));
    c = mix(c, vec3(1.0, 0.72, 0.8), m * 0.9);
  }
  return vec4(c, 1.0);
}

//== id: leaves | Autumn leaves | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 14; i++) {
    float fi = float(i);
    float sp = 0.07 + 0.05 * rand(vec2(fi, 21.0));
    vec2 p = vec2(rand(vec2(fi, 22.0)) + 0.08 * sin(uTime * 0.9 + fi), fract(uTime * sp + rand(vec2(fi, 23.0))) * 1.2 - 0.1);
    float a = uTime * 2.0 + fi;
    vec2 q = (uv - p) * asp;
    q = vec2(cos(a) * q.x - sin(a) * q.y, sin(a) * q.x + cos(a) * q.y);
    q.x *= 2.2;
    float s = 0.03 + 0.012 * rand(vec2(fi, 25.0));
    float m = smoothstep(s, s * 0.75, length(q));
    vec3 col = mix(vec3(0.85, 0.35, 0.05), vec3(0.95, 0.7, 0.1), rand(vec2(fi, 24.0)));
    c = mix(c, col, m);
  }
  return vec4(c, 1.0);
}

//== id: storm | Storm | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * vec3(0.75, 0.8, 0.9);
  vec2 q = vec2(uv.x * 80.0 + uv.y * 20.0, uv.y * 4.0 + uTime * 9.0);
  float r = step(0.97, rand(floor(q))) * fract(q.y);
  float flash = step(0.985, rand(vec2(floor(uTime * 8.0), 3.0)));
  return vec4(c + r * 0.25 + flash * 0.6, 1.0);
}

//== id: frost | Frost | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float n = noise(uv * 18.0) * 0.6 + noise(uv * 50.0) * 0.4;
  vec2 d = abs(uv - 0.5) * 2.0;
  float edge = smoothstep(0.45, 1.0, max(d.x, d.y) + n * 0.35 - 0.1 * sin(uTime * 0.5));
  vec3 ice = vec3(0.85, 0.93, 1.0);
  vec3 blurred = getColor(uv + (vec2(noise(uv * 30.0), noise(uv * 30.0 + 4.0)) - 0.5) * 0.02).rgb;
  return vec4(mix(c * vec3(0.92, 0.97, 1.05), mix(blurred, ice, 0.6), edge), 1.0);
}

//== id: mist | Mist | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float n = noise(uv * 3.0 + vec2(uTime * 0.08, 0.0)) * 0.6 + noise(uv * 7.0 - vec2(uTime * 0.12, 0.0)) * 0.4;
  float m = smoothstep(0.3, 0.9, n) * (0.4 + 0.6 * uv.y);
  return vec4(mix(c, vec3(0.88, 0.9, 0.93), m * 0.6), 1.0);
}

//== id: datamosh | Datamosh | effect | Glitch
vec4 effect(vec2 uv) {
  float t = floor(uTime * 10.0);
  vec2 blk = floor(uv * vec2(16.0, 28.0));
  float r = rand(blk + t);
  vec2 shift = (vec2(rand(blk + t + 1.0), rand(blk + t + 2.0)) - 0.5) * 0.08 * step(0.85, r);
  vec3 c = getColor(uv + shift).rgb;
  vec3 smear = getColor(vec2(uv.x, floor(uv.y * 28.0) / 28.0) + shift * 2.0).rgb;
  return vec4(mix(c, smear, step(0.93, r)), 1.0);
}

//== id: blockglitch | Block glitch | effect | Glitch
vec4 effect(vec2 uv) {
  float t = floor(uTime * 12.0);
  vec2 blk = floor(uv * vec2(8.0, 14.0));
  float on = step(0.9, rand(blk + t * 1.3));
  vec2 q = uv + vec2(rand(vec2(t, blk.y)) - 0.5, 0.0) * 0.1 * on;
  vec3 c = getColor(q).rgb;
  vec3 inv = vec3(c.g, c.b, c.r);
  return vec4(mix(c, inv, on * step(0.5, rand(blk + t + 9.0))), 1.0);
}

//== id: digitalnoise | Digital noise | effect | Glitch
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float t = floor(uTime * 24.0);
  vec2 g = floor(uv * uResolution / 2.0);
  vec3 n = vec3(rand(g + t), rand(g + t + 1.0), rand(g + t + 2.0));
  float burst = 0.15 + 0.35 * step(0.85, rand(vec2(t, 4.0)));
  return vec4(mix(c, n, burst), 1.0);
}

//== id: signalloss | Signal loss | effect | Glitch
vec4 effect(vec2 uv) {
  float t = uTime;
  float roll = fract(t * 0.3);
  vec2 q = vec2(uv.x + (rand(vec2(floor(uv.y * 120.0), floor(t * 20.0))) - 0.5) * 0.03, fract(uv.y + roll * step(0.7, fract(t * 0.5))));
  vec3 c = getColor(q).rgb;
  float bar = smoothstep(0.0, 0.02, abs(fract(uv.y - t * 0.4) - 0.5) - 0.05);
  float snow = rand(uv * uResolution + floor(t * 30.0));
  return vec4(mix(vec3(snow), c, 0.65 + 0.35 * bar), 1.0);
}

//== id: sliceshift | Slice shift | effect | Glitch
vec4 effect(vec2 uv) {
  float t = floor(uTime * 8.0);
  float row = floor(uv.y * 12.0);
  float s = (rand(vec2(row, t)) - 0.5) * 0.12 * step(0.6, rand(vec2(row + 3.0, t)));
  return getColor(vec2(fract(uv.x + s), uv.y));
}

//== id: rgbwave | RGB wave | effect | Glitch
vec4 effect(vec2 uv) {
  float w = sin(uv.y * 20.0 + uTime * 6.0) * 0.01;
  float r = getColor(uv + vec2(w, 0.0)).r;
  float g = getColor(uv).g;
  float b = getColor(uv - vec2(w, 0.0)).b;
  return vec4(r, g, b, 1.0);
}

//== id: interlace | Interlace | effect | Glitch
vec4 effect(vec2 uv) {
  float line = mod(floor(uv.y * uResolution.y / 2.0), 2.0);
  float shift = (line - 0.5) * 0.01 * (0.5 + 0.5 * sin(uTime * 3.0));
  vec3 c = getColor(uv + vec2(shift, 0.0)).rgb;
  return vec4(c * (0.88 + 0.12 * line), 1.0);
}

//== id: underwater | Underwater | effect | Lens
vec4 effect(vec2 uv) {
  vec2 q = uv + vec2(sin(uv.y * 12.0 + uTime * 1.8), cos(uv.x * 10.0 + uTime * 1.5)) * 0.006;
  vec3 c = getColor(q).rgb * vec3(0.6, 0.9, 1.05) + vec3(0.0, 0.05, 0.1);
  float caust = ((noise(uv * 10.0 + uTime * 0.6) * noise(uv * 13.0 - uTime * 0.5)) * (noise(uv * 10.0 + uTime * 0.6) * noise(uv * 13.0 - uTime * 0.5))) * 1.2;
  return vec4(c + caust * vec3(0.6, 0.9, 1.0) * (1.0 - uv.y), 1.0);
}

//== id: drunk | Dizzy | effect | Lens
vec4 effect(vec2 uv) {
  vec2 o = vec2(sin(uTime * 1.3), cos(uTime * 1.1)) * 0.015;
  vec3 a = getColor(uv + o).rgb;
  vec3 b = getColor(uv - o).rgb;
  vec2 q = 0.5 + (uv - 0.5) * (1.0 + 0.03 * sin(uTime * 0.9));
  return vec4(mix(mix(a, b, 0.5), getColor(q).rgb, 0.4), 1.0);
}

//== id: tunnelfx | Tunnel | effect | Lens
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float r = length(d);
  float a = atan(d.y, d.x);
  vec2 q = vec2(a / 6.2831853 + 0.5 + uTime * 0.05, fract(0.25 / (r + 0.05) + uTime * 0.3));
  vec3 t = getColor(q).rgb;
  vec3 c = getColor(uv).rgb;
  return vec4(mix(t * smoothstep(0.0, 0.5, r), c, smoothstep(0.35, 0.15, r)), 1.0);
}

//== id: breathe | Breathe | effect | Lens
vec4 effect(vec2 uv) {
  float z = 1.04 + 0.04 * sin(uTime * 1.6);
  vec2 d = uv - 0.5;
  vec2 q = 0.5 + d / z * (1.0 - 0.06 * dot(d, d) * sin(uTime * 1.6));
  return getColor(q);
}

//== id: chromapulse | Chroma pulse | effect | Lens
vec4 effect(vec2 uv) {
  float k = exp(-fract(uTime * 2.0) * 5.0) * 0.02;
  vec2 d = uv - 0.5;
  return vec4(getColor(uv + d * k).r, getColor(uv).g, getColor(uv - d * k).b, 1.0);
}

//== id: psychedelic | Psychedelic | effect | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv + vec2(sin(uv.y * 8.0 + uTime), cos(uv.x * 8.0 + uTime)) * 0.01).rgb;
  vec3 h = rgb2hsv(c);
  h.x = fract(h.x + uTime * 0.25 + luma(c) * 0.5);
  h.y = min(1.0, h.y * 1.6 + 0.2);
  return vec4(hsv2rgb(h), 1.0);
}

//== id: gradientcycle | Gradient map | effect | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  vec3 a = hsv2rgb(vec3(fract(uTime * 0.08), 0.8, 0.35));
  vec3 b = hsv2rgb(vec3(fract(uTime * 0.08 + 0.45), 0.6, 1.0));
  return vec4(mix(a, b, smoothstep(0.05, 0.95, l)), 1.0);
}

//== id: thermalpulse | Heat vision | effect | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb) + 0.08 * sin(uTime * 3.0 + uv.y * 6.0);
  vec3 c = l < 0.33 ? mix(vec3(0.0, 0.0, 0.3), vec3(0.6, 0.0, 0.7), l * 3.0)
    : l < 0.66 ? mix(vec3(0.6, 0.0, 0.7), vec3(1.0, 0.5, 0.0), (l - 0.33) * 3.0)
    : mix(vec3(1.0, 0.5, 0.0), vec3(1.0, 1.0, 0.6), clamp((l - 0.66) * 3.0, 0.0, 1.0));
  return vec4(c, 1.0);
}

// ---------------------------------------------------------------- clip animations, batch 2
//== id: anim_pop | Pop | anim | Basic
vec4 effect(vec2 uv) {
  float p = uProgress;
  float s = 1.0 + 0.15 * sin(p * 3.14159) * (1.0 - p) * 2.0;
  float z = max(0.02, p * s);
  vec2 q = 0.5 + (uv - 0.5) / z;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: anim_drop | Drop | anim | Slide
vec4 effect(vec2 uv) {
  float p = uProgress;
  float e = 1.0 - ((1.0 - p) * (1.0 - p) * (1.0 - p));
  float b = p < 1.0 ? sin(p * 9.42) * (1.0 - p) * 0.08 : 0.0;
  vec2 q = uv + vec2(0.0, (1.0 - e) - b);
  float inside = step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: anim_swing | Swing in | anim | Basic
vec4 effect(vec2 uv) {
  float p = uProgress;
  float a = (1.0 - p) * (1.0 - p) * 1.4 * cos(p * 6.0);
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 pivot = vec2(0.5, 0.0);
  vec2 d = (uv - pivot) * asp;
  d = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y);
  vec2 q = pivot + d / asp;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside * smoothstep(0.0, 0.3, p), 1.0);
}

//== id: anim_flip | Flip | anim | Shape
vec4 effect(vec2 uv) {
  float p = uProgress;
  float w = max(0.01, abs(cos((1.0 - p) * 1.5708)));
  vec2 q = vec2(0.5 + (uv.x - 0.5) / w, uv.y);
  float inside = step(0.0, q.x) * step(q.x, 1.0);
  float shade = 0.5 + 0.5 * w;
  return vec4(getColor(q).rgb * inside * shade, 1.0);
}

//== id: anim_pixel | Pixelate in | anim | Light
vec4 effect(vec2 uv) {
  float p = uProgress;
  float cells = mix(6.0, 600.0, p * p);
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 q = (floor(uv * asp * cells) + 0.5) / cells / asp;
  return vec4(getColor(p > 0.98 ? uv : q).rgb * smoothstep(0.0, 0.2, p), 1.0);
}

//== id: anim_blinds | Blinds | anim | Shape
vec4 effect(vec2 uv) {
  float p = uProgress;
  float f = fract(uv.y * 8.0);
  float show = step(f, p);
  return vec4(getColor(uv).rgb * show, 1.0);
}

//== id: anim_diamond | Diamond | anim | Shape
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = abs(uv - 0.5) * asp;
  float r = (d.x + d.y);
  float show = smoothstep(uProgress * 1.6, uProgress * 1.6 - 0.02, r);
  return vec4(getColor(uv).rgb * show, 1.0);
}

//== id: anim_heart | Heart | anim | Shape
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  // uv.y points up: the lobes sit on top, the point below.
  vec2 q = (uv - vec2(0.5, 0.55)) * asp / max(0.001, uProgress * 1.8);
  q.y -= sqrt(abs(q.x)) * 0.5;
  float show = smoothstep(0.62, 0.58, length(q));
  if (uProgress > 0.99) show = 1.0;
  return vec4(getColor(uv).rgb * show, 1.0);
}

//== id: anim_zoomspin | Zoom spin | anim | Basic
vec4 effect(vec2 uv) {
  float p = uProgress;
  float e = 1.0 - ((1.0 - p) * (1.0 - p) * (1.0 - p));
  float a = (1.0 - e) * 3.14159;
  float z = max(0.02, e);
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  d = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y) / z;
  vec2 q = 0.5 + d / asp;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: anim_ink | Ink | anim | Light
vec4 effect(vec2 uv) {
  float n = noise(uv * 6.0) * 0.6 + noise(uv * 18.0) * 0.4;
  float r = length(uv - 0.5) * 1.2;
  float v = r * 0.6 + n * 0.5 - 0.25;
  float show = smoothstep(uProgress * 1.2, uProgress * 1.2 - 0.06, v);
  if (uProgress > 0.99) show = 1.0;
  return vec4(getColor(uv).rgb * show, 1.0);
}

//== id: anim_splitopen | Split open | anim | Shape
vec4 effect(vec2 uv) {
  float half_ = uProgress * 0.5;
  float show = step(abs(uv.x - 0.5), half_);
  return vec4(getColor(uv).rgb * show, 1.0);
}

//== id: anim_stretch | Stretch | anim | Basic
vec4 effect(vec2 uv) {
  float p = uProgress;
  float e = 1.0 - ((1.0 - p) * (1.0 - p));
  float sx = max(0.02, e);
  float sy = 1.0 + (1.0 - e) * 1.5;
  vec2 q = vec2(0.5 + (uv.x - 0.5) / sx, 0.5 + (uv.y - 0.5) / sy);
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: anim_shake | Shake in | anim | Light
vec4 effect(vec2 uv) {
  float p = uProgress;
  float k = (1.0 - p);
  vec2 o = vec2(sin(p * 60.0), cos(p * 47.0)) * 0.04 * k;
  return vec4(getColor(uv + o).rgb * smoothstep(0.0, 0.25, p), 1.0);
}

//== id: anim_twirl | Twirl in | anim | Basic
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float r = length(d);
  float a = (1.0 - uProgress) * 8.0 * (1.0 - smoothstep(0.0, 0.8, r));
  d = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y);
  return vec4(getColor(0.5 + d / asp).rgb * smoothstep(0.0, 0.4, uProgress), 1.0);
}

//== id: anim_slidezoom | Push in | anim | Slide
vec4 effect(vec2 uv) {
  float p = uProgress;
  float e = 1.0 - ((1.0 - p) * (1.0 - p) * (1.0 - p));
  float z = mix(1.6, 1.0, e);
  vec2 q = 0.5 + (uv - 0.5) / z + vec2(1.0 - e, 0.0) * 0.6;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: retrogreen | Retro green | filter | Color
vec4 effect(vec2 uv) {
  vec2 cell = vec2(160.0, 160.0 * uResolution.y / uResolution.x);
  vec2 q = (floor(uv * cell) + 0.5) / cell;
  float l = luma(getColor(q).rgb);
  float s = floor(l * 3.99) / 3.0;
  vec3 dark = vec3(0.06, 0.22, 0.06);
  vec3 light = vec3(0.61, 0.74, 0.06);
  return vec4(mix(dark, light, s), 1.0);
}

//== id: sunset | Sunset | filter | Color
//== param: amount float 0.8 0.0 1.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 g = mix(vec3(0.95, 0.45, 0.35), vec3(1.0, 0.8, 0.45), uv.y);
  vec3 o = c * mix(vec3(1.0), g * 1.25, amount);
  o = mix(o, o * vec3(1.05, 0.9, 1.1), 0.3 * (1.0 - uv.y));
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: tealorange | Teal and orange | filter | Color
//== param: amount float 0.85 0.0 1.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 shadow = vec3(0.0, 0.42, 0.5);
  vec3 high = vec3(1.0, 0.62, 0.3);
  vec3 tone = mix(shadow, high, smoothstep(0.15, 0.85, l));
  vec3 o = mix(c, tone * (l * 1.6 + 0.1), 0.55 * amount);
  o = (o - 0.5) * 1.1 + 0.5;
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: moody | Moody | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 o = mix(vec3(l), c, 0.55);
  o = o * vec3(0.92, 0.98, 1.06);
  o = pow(o, vec3(1.25)) * 0.95 + 0.03;
  float v = 1.0 - 0.6 * dot(uv - 0.5, uv - 0.5);
  return vec4(clamp(o * v, 0.0, 1.0), 1.0);
}

//== id: xray | X-ray | filter | Color
vec4 effect(vec2 uv) {
  float l = 1.0 - luma(getColor(uv).rgb);
  l = smoothstep(0.1, 0.95, l);
  return vec4(vec3(0.75, 0.9, 1.0) * l + vec3(0.0, 0.03, 0.08), 1.0);
}

//== id: rose | Rose | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 o = mix(c, vec3(l) * vec3(1.15, 0.85, 0.92), 0.5) + vec3(0.06, 0.0, 0.03);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: mint | Mint | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 o = mix(c, vec3(l) * vec3(0.85, 1.12, 1.0), 0.5) + vec3(0.0, 0.05, 0.03);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: splittone | Split tone | filter | Color
//== param: balance float 0.5 0.2 0.8
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 shadow = vec3(0.25, 0.3, 0.75);
  vec3 high = vec3(1.0, 0.8, 0.45);
  vec3 tint = mix(shadow, high, smoothstep(balance - 0.3, balance + 0.3, l));
  vec3 o = mix(c, tint * l * 1.4, 0.45);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: tritone | Tritone | filter | Art
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  vec3 a = vec3(0.12, 0.05, 0.3);
  vec3 b = vec3(0.95, 0.25, 0.45);
  vec3 d = vec3(1.0, 0.9, 0.55);
  vec3 o = l < 0.5 ? mix(a, b, l * 2.0) : mix(b, d, l * 2.0 - 1.0);
  return vec4(o, 1.0);
}

//== id: charcoal | Charcoal | filter | Art
vec4 effect(vec2 uv) {
  vec2 px = 1.5 / uResolution;
  float tl = luma(getColor(uv + vec2(-px.x, px.y)).rgb);
  float tr = luma(getColor(uv + px).rgb);
  float bl = luma(getColor(uv - px).rgb);
  float br = luma(getColor(uv + vec2(px.x, -px.y)).rgb);
  float l = luma(getColor(uv).rgb);
  float e = abs(tl - br) + abs(tr - bl);
  float grainv = rand(floor(uv * uResolution / 2.0));
  float shade = smoothstep(0.0, 0.75, l) * 0.75 + 0.25;
  float stroke = 1.0 - 0.85 * smoothstep(0.08, 0.4, e * 3.0);
  float o = shade * stroke * (0.85 + 0.15 * grainv);
  return vec4(vec3(o) * vec3(0.98, 0.96, 0.92), 1.0);
}

//== id: crayon | Crayon | filter | Art
vec4 effect(vec2 uv) {
  vec2 cell = uResolution / 3.0;
  vec2 q = uv + (vec2(noise(uv * cell * 0.3), noise(uv.yx * cell * 0.3)) - 0.5) * 0.006;
  vec3 c = getColor(q).rgb;
  c = floor(c * 5.0 + 0.5) / 5.0;
  float paper = noise(uv * uResolution * vec2(0.15, 0.6));
  float wax = smoothstep(0.25, 0.75, paper + luma(c) * 0.4);
  vec3 o = mix(vec3(0.97, 0.95, 0.9), c * 1.1, wax);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: bricks | Bricks | filter | Art
//== param: size float 28.0 12.0 60.0
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 g = uv * asp * size;
  vec2 id = floor(g);
  vec2 f = fract(g) - 0.5;
  vec3 c = getColor((id + 0.5) / (asp * size)).rgb;
  c = floor(c * 6.0 + 0.5) / 6.0;
  float stud = smoothstep(0.32, 0.26, length(f));
  float rim = smoothstep(0.26, 0.32, length(f + vec2(0.04, -0.04))) * stud;
  float edge = smoothstep(0.5, 0.44, max(abs(f.x), abs(f.y)));
  vec3 o = c * (0.7 + 0.3 * edge);
  o += stud * 0.12 - rim * 0.12;
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: crossstitch | Cross stitch | filter | Art
//== param: size float 70.0 30.0 140.0
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 g = uv * asp * size;
  vec2 id = floor(g);
  vec2 f = fract(g) - 0.5;
  vec3 c = getColor((id + 0.5) / (asp * size)).rgb;
  float d = min(abs(f.x - f.y), abs(f.x + f.y));
  float x = smoothstep(0.16, 0.08, d) * step(max(abs(f.x), abs(f.y)), 0.42);
  vec3 cloth = vec3(0.93, 0.9, 0.84);
  return vec4(mix(cloth, c * (0.8 + 0.4 * (0.16 - d) * 3.0), x), 1.0);
}

//== id: textart | Text art | filter | Art
//== param: size float 60.0 30.0 120.0
vec4 effect(vec2 uv) {
  // cells never smaller than 6 pixels, or the glyphs turn to noise on small frames
  float cellPx = max(6.0, uResolution.y / size);
  vec2 g = uv * uResolution / cellPx;
  vec2 id = floor(g);
  vec2 f = fract(g);
  vec3 c = getColor((id + 0.5) * cellPx / uResolution).rgb;
  float l = luma(c);
  // five glyph shapes from dot to filled block, picked by brightness
  float dotg = step(length(f - 0.5), 0.12);
  float dash = step(abs(f.y - 0.5), 0.09) * step(abs(f.x - 0.5), 0.32);
  float plus = max(dash, step(abs(f.x - 0.5), 0.09) * step(abs(f.y - 0.5), 0.32));
  float hashg = max(step(abs(fract(f.x * 2.0) - 0.5), 0.12), step(abs(fract(f.y * 2.0) - 0.5), 0.12)) * step(abs(f.x - 0.5), 0.4) * step(abs(f.y - 0.5), 0.4);
  float block = step(abs(f.x - 0.5), 0.38) * step(abs(f.y - 0.5), 0.42);
  float g1 = l < 0.2 ? 0.0 : l < 0.35 ? dotg : l < 0.5 ? dash : l < 0.65 ? plus : l < 0.8 ? hashg : block;
  return vec4(c * g1 * 1.3, 1.0);
}

//== id: brokenglass | Broken glass | filter | Lens
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 p = uv * asp * 5.0;
  vec2 ip = floor(p);
  float best = 9.0;
  float second = 9.0;
  vec2 bestId = vec2(0.0);
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      vec2 n = ip + vec2(float(i), float(j));
      vec2 pt = n + vec2(rand(n), rand(n + 7.0));
      float d = length(p - pt);
      if (d < best) { second = best; best = d; bestId = n; }
      else if (d < second) { second = d; }
    }
  }
  vec2 off = (vec2(rand(bestId + 3.0), rand(bestId + 5.0)) - 0.5) * 0.04;
  vec3 c = getColor(uv + off).rgb * (0.9 + 0.2 * rand(bestId + 9.0));
  float crack = smoothstep(0.06, 0.0, second - best);
  return vec4(mix(c, vec3(1.0), crack * 0.7), 1.0);
}

//== id: lowpoly | Low poly | filter | Art
//== param: size float 14.0 6.0 30.0
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 g = uv * asp * size;
  vec2 id = floor(g);
  vec2 f = fract(g);
  // each square splits into two triangles along a diagonal picked per cell
  float flip = step(0.5, rand(id));
  float upper = flip > 0.5 ? step(f.x, f.y) : step(1.0 - f.x, f.y);
  vec2 centre = flip > 0.5 ? (upper > 0.5 ? vec2(0.33, 0.67) : vec2(0.67, 0.33)) : (upper > 0.5 ? vec2(0.67, 0.67) : vec2(0.33, 0.33));
  vec3 c = getColor((id + centre) / (asp * size)).rgb;
  float shade = 0.92 + 0.16 * rand(id + upper * 3.1);
  return vec4(clamp(c * shade, 0.0, 1.0), 1.0);
}

//== id: canvas | Canvas | filter | Art
vec4 effect(vec2 uv) {
  vec2 px = uv * uResolution;
  vec2 q = uv + (vec2(noise(uv * 40.0), noise(uv * 40.0 + 9.0)) - 0.5) * 0.008;
  vec3 c = getColor(q).rgb;
  c = mix(c, floor(c * 8.0 + 0.5) / 8.0, 0.5);
  float weave = 0.5 + 0.25 * sin(px.x * 1.6) + 0.25 * sin(px.y * 1.6);
  vec3 o = c * (0.88 + 0.16 * weave);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: cinematic | Cinematic | filter | Color
vec4 effect(vec2 uv) {
  float bar = 0.12;
  if (uv.y < bar || uv.y > 1.0 - bar) return vec4(0.0, 0.0, 0.0, 1.0);
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 o = mix(c, mix(vec3(0.05, 0.25, 0.3), vec3(1.0, 0.75, 0.5), l) * (l + 0.2), 0.35);
  o = (o - 0.5) * 1.12 + 0.5;
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: coderain | Code rain | effect | Glitch
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 g = uv * asp * 40.0;
  vec2 id = floor(g);
  float speed = 0.6 + rand(vec2(id.x, 3.0)) * 0.8;
  float head = fract(rand(vec2(id.x, 1.0)) - uTime * speed * 0.35);
  float y = 1.0 - uv.y;
  float trail = fract(head - y * 0.5 + 1.0);
  float glyph = step(0.35, rand(id + floor(uTime * 8.0)));
  float bright = smoothstep(0.75, 1.0, trail) * glyph;
  vec3 o = c * 0.45 + vec3(0.2, 1.0, 0.4) * bright * 0.9;
  return vec4(min(o, vec3(1.0)), 1.0);
}

//== id: lasers | Lasers | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * 0.75;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 6; i++) {
    float fi = float(i);
    vec2 o = vec2(fi < 3.0 ? 0.0 : asp.x, 1.05);
    float a = (fi < 3.0 ? -0.6 : -2.55) + sin(uTime * (0.9 + fi * 0.2) + fi) * 0.5 + mod(fi, 3.0) * 0.12;
    vec2 dir = vec2(cos(a), sin(a));
    vec2 d = uv * asp - o;
    float along = dot(d, dir);
    float dist = abs(d.x * dir.y - d.y * dir.x);
    vec3 col = hsv2rgb(vec3(fract(fi * 0.17 + uTime * 0.1), 0.8, 1.0));
    c += col * step(0.0, along) * (0.0016 / (dist + 0.002));
  }
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: aurora | Aurora | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float t = uTime * 0.3;
  float band = 0.0;
  vec3 col = vec3(0.0);
  for (int i = 0; i < 3; i++) {
    float fi = float(i);
    float y = 0.75 + 0.08 * sin(uv.x * (3.0 + fi) + t * (1.0 + fi * 0.3) + fi * 2.0) + fi * 0.05;
    float b = exp(-(((uv.y - y) * 9.0) * ((uv.y - y) * 9.0))) * (0.6 + 0.4 * noise(vec2(uv.x * 8.0 + t * 2.0, fi)));
    col += b * mix(vec3(0.1, 1.0, 0.5), vec3(0.5, 0.3, 1.0), fi / 2.0);
  }
  return vec4(min(c + col * 0.7, vec3(1.0)), 1.0);
}

//== id: lensdirt | Lens dirt | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 light = vec2(0.3 + 0.4 * sin(uTime * 0.4), 0.7);
  float glow = 0.12 / (length((uv - light) * asp) + 0.15);
  float dirt = 0.0;
  for (int i = 0; i < 14; i++) {
    float fi = float(i);
    vec2 p = vec2(rand(vec2(fi, 4.0)), rand(vec2(fi, 8.0)));
    float r = 0.03 + rand(vec2(fi, 2.0)) * 0.06;
    dirt += smoothstep(r, r * 0.6, length((uv - p) * asp)) * (0.4 + 0.6 * rand(vec2(fi, 5.0)));
  }
  vec3 o = c + vec3(1.0, 0.85, 0.6) * glow * (0.3 + dirt * 0.9) * 0.6;
  return vec4(min(o, vec3(1.0)), 1.0);
}

//== id: shockwave | Shockwave | effect | Lens
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float t = fract(uTime * 0.6);
  vec2 d = (uv - 0.5) * asp;
  float r = length(d);
  float w = r - t * 1.1;
  float ring = exp(-w * w * 300.0) * (1.0 - t);
  vec2 q = uv - normalize(d + 1e-4) / asp * ring * 0.05;
  vec3 c = getColor(q).rgb + ring * 0.25;
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: vhsrewind | VHS rewind | effect | Retro
vec4 effect(vec2 uv) {
  float t = uTime;
  float roll = fract(uv.y + t * 1.5);
  float band = smoothstep(0.0, 0.06, roll) * smoothstep(0.16, 0.08, roll);
  float jitter = (rand(vec2(floor(uv.y * 120.0), floor(t * 20.0))) - 0.5) * 0.02;
  vec2 q = vec2(fract(uv.x + jitter + band * 0.06 + t * 0.4), uv.y);
  vec3 c = vec3(getColor(q + vec2(0.006, 0.0)).r, getColor(q).g, getColor(q - vec2(0.006, 0.0)).b);
  c = mix(c, vec3(rand(uv * 300.0 + t)), band * 0.6);
  float lines = 0.92 + 0.08 * sin(uv.y * uResolution.y * 1.5);
  return vec4(c * lines, 1.0);
}

//== id: pixelscatter | Pixel scatter | effect | Glitch
vec4 effect(vec2 uv) {
  vec2 cell = vec2(48.0, 48.0 * uResolution.y / uResolution.x);
  vec2 id = floor(uv * cell);
  float k = ((0.5 + 0.5 * sin(uTime * 2.0)) * (0.5 + 0.5 * sin(uTime * 2.0)) * (0.5 + 0.5 * sin(uTime * 2.0)));
  vec2 off = (vec2(rand(id), rand(id + 4.0)) - 0.5) * 0.2 * k * step(0.6, rand(id + 9.0));
  vec3 c = getColor(uv + off).rgb;
  return vec4(c, 1.0);
}

//== id: tilt3d | Tilt | effect | Motion
vec4 effect(vec2 uv) {
  float a = sin(uTime * 1.2) * 0.25;
  vec2 d = uv - 0.5;
  float z = 1.0 + d.x * sin(a) * 0.9;
  vec2 q = vec2(d.x * cos(a), d.y) / z * 0.9 + 0.5;
  if (q.x < 0.0 || q.x > 1.0 || q.y < 0.0 || q.y > 1.0) return vec4(0.0, 0.0, 0.0, 1.0);
  return vec4(getColor(q).rgb * (1.0 - abs(d.x * sin(a)) * 0.6), 1.0);
}

//== id: orbit | Orbit | effect | Motion
vec4 effect(vec2 uv) {
  vec2 o = vec2(cos(uTime * 1.5), sin(uTime * 1.5)) * 0.03;
  vec2 q = (uv - 0.5) / 1.08 + 0.5 + o;
  return vec4(getColor(q).rgb, 1.0);
}

//== id: camroll | Camera roll | effect | Motion
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float a = sin(uTime * 0.9) * 0.12;
  vec2 d = (uv - 0.5) * asp;
  d = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y);
  vec2 q = d / asp / 1.2 + 0.5;
  return vec4(getColor(q).rgb, 1.0);
}

//== id: anamorphic | Anamorphic flare | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 src = vec2(0.5 + 0.3 * sin(uTime * 0.5), 0.6 + 0.1 * cos(uTime * 0.7));
  float dy = uv.y - src.y;
  float streak = exp(-dy * dy * 4000.0) * (0.4 / (abs(uv.x - src.x) * 3.0 + 0.3));
  float core = 0.004 / (dot((uv - src) * vec2(1.0, 1.8), (uv - src) * vec2(1.0, 1.8)) + 0.004);
  vec3 o = c + vec3(0.35, 0.6, 1.0) * streak + vec3(0.8, 0.9, 1.0) * core * 0.5;
  return vec4(min(o, vec3(1.0)), 1.0);
}

//== id: prismflare | Prism flare | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float p = fract(uTime * 0.3) * 1.6 - 0.3;
  float d = (uv.x - uv.y * 0.4) - p;
  vec3 o = c;
  for (int i = 0; i < 3; i++) {
    float off = (float(i) - 1.0) * 0.03;
    float b = exp(-pow((d + off) * 14.0, 2.0));
    o[i] += b * 0.5;
  }
  return vec4(min(o, vec3(1.0)), 1.0);
}

//== id: sandstorm | Sandstorm | effect | Weather
vec4 effect(vec2 uv) {
  vec2 q = uv + vec2(uTime * 0.6, sin(uTime + uv.x * 3.0) * 0.02);
  float n = noise(q * vec2(4.0, 8.0)) * 0.6 + noise(q * 20.0) * 0.4;
  float grains = step(0.97, rand(floor(uv * uResolution / 2.0) + floor(uTime * 24.0)));
  vec3 c = getColor(uv + (n - 0.5) * 0.01).rgb;
  vec3 dust = vec3(0.85, 0.65, 0.4);
  vec3 o = mix(c, dust, 0.25 + n * 0.35) + grains * 0.25;
  return vec4(min(o, vec3(1.0)), 1.0);
}

//== id: smoke | Smoke | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 q = uv * 3.0 + vec2(0.0, -uTime * 0.25);
  float n = noise(q) * 0.5 + noise(q * 2.1 + uTime * 0.1) * 0.3 + noise(q * 4.3) * 0.2;
  float m = smoothstep(0.35, 0.8, n) * (1.0 - uv.y * 0.6);
  return vec4(mix(c, vec3(0.82), m * 0.7), 1.0);
}

//== id: cloudshadow | Cloud shadows | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 q = uv * 2.0 + vec2(uTime * 0.12, uTime * 0.05);
  float n = noise(q) * 0.6 + noise(q * 2.3) * 0.4;
  float shade = mix(1.05, 0.55, smoothstep(0.45, 0.7, n));
  return vec4(c * shade, 1.0);
}

//== id: balloons | Balloons | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 10; i++) {
    float fi = float(i);
    float x = rand(vec2(fi, 1.0)) * asp.x;
    float y = fract(rand(vec2(fi, 2.0)) + uTime * (0.08 + 0.05 * rand(vec2(fi, 3.0)))) * 1.4 - 0.2;
    vec2 p = vec2(x + sin(uTime + fi) * 0.03, y);
    vec2 d = uv * asp - p;
    float b = smoothstep(0.07, 0.065, length(d * vec2(1.0, 0.85)));
    float str = smoothstep(0.004, 0.0, abs(d.x + sin(d.y * 30.0) * 0.005)) * step(d.y, -0.06) * step(-0.25, d.y);
    vec3 col = hsv2rgb(vec3(rand(vec2(fi, 6.0)), 0.75, 0.95));
    float hl = smoothstep(0.025, 0.0, length(d - vec2(-0.02, 0.025)));
    c = mix(c, col + hl * 0.5, b);
    c = mix(c, vec3(0.9), str * 0.7);
  }
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: starburst | Star burst | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float t = fract(uTime * 0.5);
  float cyc = floor(uTime * 0.5);
  vec2 centre = vec2(0.3 + 0.4 * rand(vec2(cyc, 1.0)), 0.4 + 0.3 * rand(vec2(cyc, 2.0))) * asp;
  vec2 d = uv * asp - centre;
  float a = atan(d.y, d.x);
  float r = length(d);
  float rays = pow(0.5 + 0.5 * cos(a * 12.0), 8.0);
  float reach = t * 0.8;
  float m = rays * smoothstep(reach, reach - 0.15, r) * smoothstep(0.0, 0.05, r) * (1.0 - t);
  vec3 col = hsv2rgb(vec3(rand(vec2(cyc, 3.0)), 0.6, 1.0));
  return vec4(min(c + col * m * 1.2, vec3(1.0)), 1.0);
}

//== id: neonglow | Neon glow | effect | Color
vec4 effect(vec2 uv) {
  vec2 px = 2.0 / uResolution;
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  float e = smoothstep(0.05, 0.3, length(vec2(gx, gy)));
  vec3 col = hsv2rgb(vec3(fract(uv.x * 0.5 + uv.y * 0.3 + uTime * 0.2), 0.9, 1.0));
  vec3 c = getColor(uv).rgb * 0.35;
  return vec4(min(c + col * e * 1.3, vec3(1.0)), 1.0);
}

//== id: projector | Projector | effect | Retro
vec4 effect(vec2 uv) {
  float t = uTime;
  vec2 weave = vec2(sin(t * 13.0), cos(t * 11.0)) * 0.002;
  vec3 c = getColor(uv + weave).rgb;
  float flick = 0.9 + 0.1 * rand(vec2(floor(t * 24.0), 1.0));
  float hot = 1.0 - 0.7 * dot(uv - 0.5, uv - 0.5) * 2.0;
  float l = luma(c);
  vec3 o = mix(c, vec3(l) * vec3(1.08, 1.0, 0.85), 0.35) * flick * hot;
  float spec = step(0.9985, rand(floor(uv * uResolution / 2.0) + floor(t * 24.0)));
  return vec4(min(o + spec * 0.6, vec3(1.0)), 1.0);
}

//== id: kaleidopulse | Kaleido pulse | effect | Party
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float r = length(d);
  float a = atan(d.y, d.x) + uTime * 0.3;
  float seg = 6.2831853 / 6.0;
  a = abs(mod(a, seg) - seg * 0.5);
  float z = 1.0 + 0.15 * sin(uTime * 2.0);
  vec2 q = vec2(cos(a), sin(a)) * r / z / asp + 0.5;
  return vec4(getColor(clamp(q, 0.0, 1.0)).rgb, 1.0);
}

//== id: anim_unfold | Unfold | anim | Shape
vec4 effect(vec2 uv) {
  float p = uProgress;
  float e = 1.0 - ((1.0 - p) * (1.0 - p) * (1.0 - p));
  float h = max(e, 0.001);
  vec2 q = vec2(uv.x, (uv.y - 0.5) / h + 0.5);
  float inside = step(0.0, q.y) * step(q.y, 1.0);
  if (p > 0.99) return vec4(getColor(uv).rgb, 1.0);
  return vec4(getColor(clamp(q, 0.0, 1.0)).rgb * inside * (0.6 + 0.4 * e), 1.0);
}

//== id: anim_clock | Clock | anim | Shape
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float a = atan(d.x, d.y) / 6.2831853 + 0.5;
  float show = smoothstep(a - 0.01, a + 0.01, uProgress * 1.02);
  if (uProgress > 0.99) show = 1.0;
  return vec4(getColor(uv).rgb * show, 1.0);
}

//== id: anim_wavein | Wave in | anim | Basic
vec4 effect(vec2 uv) {
  float k = ((1.0 - uProgress) * (1.0 - uProgress));
  vec2 q = uv + vec2(sin(uv.y * 20.0 + uProgress * 12.0), cos(uv.x * 16.0 + uProgress * 10.0)) * 0.04 * k;
  return vec4(getColor(clamp(q, 0.0, 1.0)).rgb * (1.0 - k * 0.6), 1.0);
}

//== id: anim_rgbin | RGB in | anim | Light
vec4 effect(vec2 uv) {
  float k = ((1.0 - uProgress) * (1.0 - uProgress)) * 0.12;
  vec3 c = vec3(getColor(uv + vec2(k, 0.0)).r, getColor(uv + vec2(0.0, k * 0.5)).g, getColor(uv - vec2(k, 0.0)).b);
  return vec4(c * min(1.0, uProgress * 3.0), 1.0);
}

//== id: anim_dissolve | Dissolve | anim | Light
vec4 effect(vec2 uv) {
  float n = noise(uv * 18.0) * 0.6 + rand(floor(uv * uResolution / 3.0)) * 0.4;
  float show = smoothstep(n - 0.05, n + 0.05, uProgress * 1.1);
  if (uProgress > 0.99) show = 1.0;
  return vec4(getColor(uv).rgb * show, 1.0);
}

//== id: anim_star | Star | anim | Shape
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float a = atan(d.y, d.x) + uProgress * 1.5;
  float r = length(d);
  float star = 0.62 + 0.38 * cos(a * 5.0);
  float size = uProgress * 2.2;
  float show = smoothstep(size * star, size * star - 0.02, r);
  if (uProgress > 0.99) show = 1.0;
  return vec4(getColor(uv).rgb * show, 1.0);
}

//== id: anim_slideblur | Slide blur | anim | Slide
vec4 effect(vec2 uv) {
  float p = uProgress;
  float e = 1.0 - (((1.0 - p) * (1.0 - p)) * ((1.0 - p) * (1.0 - p)));
  float off = 1.0 - e;
  float speed = 4.0 * ((1.0 - p) * (1.0 - p) * (1.0 - p)) * 0.08;
  vec3 acc = vec3(0.0);
  float w = 0.0;
  for (int i = 0; i < 10; i++) {
    float t = float(i) / 9.0;
    vec2 q = uv + vec2(off + speed * t, 0.0);
    float inside = step(q.x, 1.0);
    acc += getColor(clamp(q, 0.0, 1.0)).rgb * inside;
    w += 1.0;
  }
  return vec4(acc / w, 1.0);
}

//== id: anim_tvon | TV on | anim | Light
vec4 effect(vec2 uv) {
  float p = uProgress;
  float h = smoothstep(0.15, 0.7, p);
  float wdt = smoothstep(0.0, 0.2, p);
  vec2 d = uv - 0.5;
  float inside = step(abs(d.y), max(h * 0.5, 0.004)) * step(abs(d.x), wdt * 0.5);
  vec2 q = vec2(uv.x, d.y / max(h, 0.008) + 0.5);
  vec3 c = getColor(clamp(q, 0.0, 1.0)).rgb;
  float flash = (1.0 - smoothstep(0.1, 0.8, p)) * 1.5;
  if (p > 0.99) return vec4(getColor(uv).rgb, 1.0);
  return vec4(min(c + flash, vec3(1.0)) * inside, 1.0);
}

//== id: anim_scalebounce | Elastic | anim | Basic
vec4 effect(vec2 uv) {
  float p = uProgress;
  float s = p >= 1.0 ? 1.0 : 1.0 - pow(2.0, -10.0 * p) * cos(p * 20.0);
  s = max(s, 0.01);
  vec2 q = (uv - 0.5) / s + 0.5;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  if (p > 0.99) return vec4(getColor(uv).rgb, 1.0);
  return vec4(getColor(clamp(q, 0.0, 1.0)).rgb * inside, 1.0);
}

//== id: vaporwave | Vaporwave | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 g = mix(vec3(0.35, 0.1, 0.55), vec3(1.0, 0.45, 0.8), smoothstep(0.1, 0.6, l));
  g = mix(g, vec3(0.4, 1.0, 0.95), smoothstep(0.65, 1.0, l));
  vec3 o = mix(c, g, 0.6);
  return vec4(clamp(o * 1.05, 0.0, 1.0), 1.0);
}

//== id: rednoir | Red noir | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 hsv = rgb2hsv(c);
  float red = (1.0 - smoothstep(0.03, 0.08, min(hsv.x, 1.0 - hsv.x))) * smoothstep(0.25, 0.5, hsv.y);
  float l = luma(c);
  float g = clamp((l - 0.5) * 1.6 + 0.5, 0.0, 1.0);
  vec3 o = mix(vec3(g), c * vec3(1.2, 0.6, 0.6), red);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: littleplanet | Little planet | filter | Lens
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float r = length(d);
  float a = atan(d.x, -d.y);
  // the picture wraps round a circle: the bottom edge in the middle, the sky outside
  float y = clamp(1.0 - r * 1.25, 0.0, 1.0);
  float x = fract(a / 6.2831853 + 0.5);
  return vec4(getColor(vec2(x, y)).rgb, 1.0);
}

//== id: softdream | Soft dream | filter | Detail
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 b = vec3(0.0);
  for (int i = 0; i < 8; i++) {
    float a = float(i) * 0.785398;
    b += getColor(uv + vec2(cos(a), sin(a)) * 0.012).rgb;
  }
  b /= 8.0;
  vec3 o = max(c, b) * 0.6 + c * 0.4;
  o = mix(o, vec3(1.0, 0.92, 0.95), 0.12) + 0.03;
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: nightcity | Night city | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 o = mix(c * vec3(0.55, 0.75, 0.95), c * vec3(1.15, 0.95, 0.8), smoothstep(0.55, 0.9, l));
  o += smoothstep(0.75, 1.0, l) * vec3(0.15, 0.08, 0.0);
  o = (o - 0.5) * 1.15 + 0.45;
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: matte | Matte | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 o = c * 0.82 + 0.1;
  o = mix(vec3(luma(o)), o, 0.85);
  return vec4(o * vec3(1.0, 0.98, 0.95), 1.0);
}

//== id: speedlines | Speed lines | effect | Motion
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float a = atan(d.y, d.x);
  float r = length(d);
  float k = floor(a * 40.0);
  float flick = floor(uTime * 16.0);
  float on = step(0.55, rand(vec2(k, flick)));
  float w = abs(fract(a * 40.0) - 0.5);
  float line = on * smoothstep(0.25, 0.0, w) * smoothstep(0.25, 0.7, r);
  return vec4(mix(c, vec3(1.0), line * 0.85), 1.0);
}

//== id: filmstrip | Film strip | effect | Retro
vec4 effect(vec2 uv) {
  float edge = 0.09;
  float roll = fract(uTime * 0.6);
  vec2 q = vec2((uv.x - edge) / (1.0 - 2.0 * edge), fract(uv.y + roll));
  float gap = step(0.97, q.y);
  vec3 c = getColor(vec2(q.x, q.y / 0.97)).rgb;
  if (uv.x < edge || uv.x > 1.0 - edge) {
    float side = uv.x < edge ? uv.x / edge : (1.0 - uv.x) / edge;
    float holes = step(abs(fract((uv.y + roll) * 12.0) - 0.5), 0.2) * step(abs(side - 0.5), 0.22);
    return vec4(mix(vec3(0.06, 0.05, 0.04), vec3(0.9), holes), 1.0);
  }
  return vec4(mix(c, vec3(0.06, 0.05, 0.04), gap), 1.0);
}

//== id: hologram | Hologram | effect | Glitch
vec4 effect(vec2 uv) {
  float t = uTime;
  float jitter = step(0.93, rand(vec2(floor(t * 12.0), 1.0))) * (rand(vec2(floor(uv.y * 30.0), floor(t * 12.0))) - 0.5) * 0.04;
  vec3 c = getColor(uv + vec2(jitter, 0.0)).rgb;
  float l = luma(c);
  float lines = 0.75 + 0.25 * sin((uv.y + t * 0.1) * uResolution.y * 1.2);
  float band = smoothstep(0.0, 0.05, abs(fract(uv.y - t * 0.3) - 0.5) - 0.42);
  vec3 o = vec3(0.25, 0.85, 1.0) * (l * 1.3 + 0.1) * lines + (1.0 - band) * 0.15;
  o *= 0.9 + 0.1 * sin(t * 30.0);
  return vec4(min(o, vec3(1.0)), 1.0);
}

//== id: liquid | Liquid | effect | Lens
vec4 effect(vec2 uv) {
  float t = uTime * 0.8;
  vec2 q = uv;
  q.x += 0.012 * sin(uv.y * 14.0 + t * 2.0) + 0.008 * sin(uv.y * 31.0 - t * 3.0);
  q.y += 0.012 * cos(uv.x * 12.0 + t * 1.7) + 0.008 * cos(uv.x * 27.0 + t * 2.3);
  return vec4(getColor(clamp(q, 0.0, 1.0)).rgb, 1.0);
}

//== id: pixelsortfx | Pixel sort | effect | Glitch
vec4 effect(vec2 uv) {
  float t = floor(uTime * 6.0);
  float row = floor(uv.y * 60.0);
  float sorting = step(0.6, rand(vec2(row, t)));
  vec3 c = getColor(uv).rgb;
  if (sorting < 0.5) return vec4(c, 1.0);
  // bright pixels smear to the right along the row
  vec3 acc = c;
  float best = luma(c);
  for (int i = 1; i < 12; i++) {
    vec3 s = getColor(uv - vec2(float(i) * 0.012, 0.0)).rgb;
    float l = luma(s);
    if (l > best && l > 0.45) { best = l; acc = s; }
  }
  return vec4(acc, 1.0);
}

//== id: infinitezoom | Infinite zoom | effect | Lens
vec4 effect(vec2 uv) {
  float z = pow(2.0, fract(uTime * 0.35));
  vec2 d = (uv - 0.5) / z;
  // the frame shows itself inset at half size, again and again
  vec3 c = vec3(0.0);
  float s = 1.0;
  for (int i = 0; i < 4; i++) {
    vec2 q = d / s + 0.5;
    if (max(abs(q.x - 0.5), abs(q.y - 0.5)) <= 0.5) c = getColor(q).rgb * (1.0 - 0.06 * float(i));
    s *= 0.5;
  }
  return vec4(c, 1.0);
}

//== id: heartframe | Heart frame | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 q = (uv - vec2(0.5, 0.55)) * asp / (0.9 + 0.04 * sin(uTime * 6.0));
  q.y -= sqrt(abs(q.x)) * 0.5;
  float d = length(q);
  float inside = smoothstep(0.62, 0.58, d);
  vec3 pink = vec3(1.0, 0.45, 0.65);
  float rim = smoothstep(0.66, 0.62, d) - inside;
  vec3 o = mix(mix(c * 0.4 + pink * 0.35, c, inside), vec3(1.0, 0.75, 0.85), rim);
  return vec4(o, 1.0);
}

//== id: flames | Flames | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 q = vec2(uv.x * 6.0, uv.y * 3.0 - uTime * 1.6);
  float n = noise(q) * 0.55 + noise(q * 2.1 + 3.0) * 0.3 + noise(q * 4.3) * 0.15;
  float h = 0.45 * n + 0.08;
  float f = smoothstep(h, h - 0.18, uv.y);
  vec3 fire = mix(vec3(1.0, 0.25, 0.0), vec3(1.0, 0.9, 0.35), smoothstep(0.2, 0.8, f));
  return vec4(min(c * (1.0 - f * 0.6) + fire * f, vec3(1.0)), 1.0);
}

//== id: glowstars | Twinkle | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 16; i++) {
    float fi = float(i);
    vec2 p = vec2(rand(vec2(fi, 11.0)), rand(vec2(fi, 13.0)));
    float tw = pow(max(0.0, sin(uTime * (2.0 + rand(vec2(fi, 3.0)) * 3.0) + fi * 1.7)), 6.0);
    vec2 d = (uv - p) * asp;
    float cross = 0.0006 / (abs(d.x * d.y) + 0.0004) * exp(-dot(d, d) * 300.0);
    float core = 0.0003 / (dot(d, d) + 0.0003);
    c += vec3(1.0, 0.95, 0.85) * (cross * 0.4 + core * 0.6) * tw;
  }
  return vec4(min(c, vec3(1.0)), 1.0);
}

// ---------------------------------------------------------------- filters, batch 3

//== id: kodak | Slide Film | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  c = smoothstep(0.02, 0.98, c);
  float l = luma(c);
  c = mix(vec3(l), c, 1.35);
  c *= vec3(1.06, 1.0, 0.9);
  c.b += (1.0 - l) * 0.05;
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: film400 | Film 400 | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  c = mix(c, c * vec3(0.92, 1.04, 1.0), 1.0 - l);
  c = mix(c, c * vec3(1.05, 1.0, 0.94), l);
  c = 0.04 + c * 0.92;
  c += (rand(uv * uResolution) - 0.5) * 0.03;
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: ice | Ice | filter | Color
//== param: amount float 0.7 0.0 1.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 ice = mix(vec3(0.05, 0.12, 0.2), vec3(0.85, 0.95, 1.0), l);
  return vec4(mix(c, ice, amount), 1.0);
}

//== id: autumn | Autumn | filter | Color
vec4 effect(vec2 uv) {
  vec3 h = rgb2hsv(getColor(uv).rgb);
  float green = smoothstep(0.12, 0.2, h.x) * smoothstep(0.5, 0.38, h.x);
  h.x = mix(h.x, 0.07, green * 0.8);
  h.y = min(1.0, h.y * 1.15);
  vec3 c = hsv2rgb(h) * vec3(1.05, 0.98, 0.88);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: spring | Spring | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 h = rgb2hsv(c);
  h.y = min(1.0, h.y * 1.2);
  c = hsv2rgb(h);
  c = c * vec3(0.98, 1.06, 0.98) + vec3(0.03, 0.04, 0.05);
  c = mix(c, vec3(1.0, 0.85, 0.9), 0.08);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: desert | Desert | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  c = mix(vec3(l), c, 0.75);
  c *= vec3(1.12, 1.0, 0.78);
  c = c * 0.9 + vec3(0.06, 0.04, 0.0);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: forest | Forest | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  c = mix(c, c * vec3(0.85, 1.05, 0.9), 0.8);
  c = mix(vec3(l), c, 1.1);
  c = mix(c, vec3(0.02, 0.08, 0.05), (1.0 - l) * 0.25);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: neonnight | Neon Night | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 shadow = vec3(0.12, 0.0, 0.3);
  vec3 hi = vec3(0.1, 1.0, 0.95);
  vec3 g = mix(shadow, mix(vec3(1.0, 0.1, 0.7), hi, smoothstep(0.45, 0.85, l)), smoothstep(0.05, 0.5, l));
  return vec4(mix(c, g, 0.6) * 1.05, 1.0);
}

//== id: candy | Candy | filter | Color
vec4 effect(vec2 uv) {
  vec3 h = rgb2hsv(getColor(uv).rgb);
  h.y = min(1.0, h.y * 1.6 + 0.08);
  h.z = 0.25 + h.z * 0.8;
  vec3 c = hsv2rgb(h);
  c = mix(c, vec3(1.0, 0.6, 0.85), 0.1);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: cyanotype | Cyanotype | filter | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  vec3 c = mix(vec3(0.02, 0.15, 0.35), vec3(0.92, 0.96, 1.0), smoothstep(0.05, 0.95, l));
  return vec4(c, 1.0);
}

//== id: selenium | Selenium | filter | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  l = smoothstep(0.03, 0.97, l);
  vec3 c = mix(vec3(0.18, 0.08, 0.15), vec3(1.0, 0.97, 0.93), l);
  c = mix(c, vec3(l), 0.35);
  return vec4(c, 1.0);
}

//== id: highkey | High Key | filter | Color
//== param: amount float 0.6 0.0 1.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 b = 1.0 - (1.0 - c) * (1.0 - c);
  b = mix(vec3(luma(b)), b, 0.8);
  return vec4(mix(c, b, amount), 1.0);
}

//== id: lowkey | Low Key | filter | Color
//== param: amount float 0.7 0.0 1.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 d = c * c * 1.1;
  float v = 1.0 - smoothstep(0.3, 0.9, length(uv - 0.5));
  d *= 0.6 + 0.4 * v;
  return vec4(mix(c, d, amount), 1.0);
}

//== id: washed | Washed | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  c = mix(vec3(l), c, 0.55);
  c = 0.15 + c * 0.75;
  c *= vec3(1.0, 0.99, 0.96);
  return vec4(c, 1.0);
}

//== id: hdr | HDR | filter | Detail
//== param: strength float 0.6 0.2 2.0
vec4 effect(vec2 uv) {
  vec2 px = 3.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  vec3 b = vec3(0.0);
  for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) b += getColor(uv + vec2(float(x), float(y)) * px).rgb;
  b /= 25.0;
  vec3 d = c + (c - b) * 1.2 * strength;
  float l = luma(d);
  d = mix(vec3(l), d, 1.15);
  d = mix(d, d / (d + 0.35) * 1.35, 0.7);
  return vec4(clamp(d, 0.0, 1.0), 1.0);
}

//== id: grit | Grit | filter | Detail
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  vec3 b = (getColor(uv + vec2(px.x, 0.0)).rgb + getColor(uv - vec2(px.x, 0.0)).rgb + getColor(uv + vec2(0.0, px.y)).rgb + getColor(uv - vec2(0.0, px.y)).rgb) * 0.25;
  vec3 d = c + (c - b) * 3.0;
  float l = luma(d);
  d = mix(vec3(l), d, 0.55);
  d = smoothstep(0.05, 0.95, d);
  d += (rand(uv * uResolution) - 0.5) * 0.06;
  return vec4(clamp(d, 0.0, 1.0), 1.0);
}

//== id: crisp | Crisp | filter | Detail
//== param: amount float 1.2 0.2 3.0
vec4 effect(vec2 uv) {
  vec2 px = 2.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  vec3 b = vec3(0.0);
  for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) b += getColor(uv + vec2(float(x), float(y)) * px).rgb;
  b /= 9.0;
  vec3 d = c + (c - b) * amount * 1.5;
  d = mix(vec3(luma(d)), d, 1.1);
  return vec4(clamp(d * 1.03, 0.0, 1.0), 1.0);
}

//== id: haze | Haze | filter | Lens
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 px = 6.0 / uResolution;
  vec3 b = vec3(0.0);
  for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) b += getColor(uv + vec2(float(x), float(y)) * px).rgb;
  b /= 25.0;
  vec3 o = 1.0 - (1.0 - c) * (1.0 - b * 0.6);
  o = mix(o, vec3(0.95, 0.93, 0.9), 0.12);
  return vec4(o, 1.0);
}

//== id: orton | Orton | filter | Lens
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 px = 5.0 / uResolution;
  vec3 b = vec3(0.0);
  for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) b += getColor(uv + vec2(float(x), float(y)) * px).rgb;
  b /= 25.0;
  b = b * b * 1.3;
  vec3 o = 1.0 - (1.0 - c * 0.85) * (1.0 - b);
  o = mix(vec3(luma(o)), o, 1.15);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: portraitblur | Portrait Blur | filter | Lens
//== param: radius float 0.18 0.05 0.6
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float d = length((uv - vec2(0.5, 0.55)) * asp);
  float k = smoothstep(radius, radius + 0.25, d);
  vec2 px = 1.0 / uResolution * 14.0 * k;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 16; i++) {
    float a = float(i) * 2.39996;
    float r = sqrt(float(i) / 16.0);
    acc += getColor(uv + vec2(cos(a), sin(a)) * r * px).rgb;
  }
  acc /= 16.0;
  acc = mix(acc, acc * vec3(1.03, 1.0, 0.97) * (1.0 - 0.25 * k), k);
  return vec4(acc, 1.0);
}

//== id: fringe | Fringe | filter | Lens
//== param: amount float 0.03 0.005 0.08
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float k = dot(d, d) * 4.0;
  vec2 o = d * amount * k * 3.0;
  return vec4(getColor(uv + o).r, getColor(uv).g, getColor(uv - o).b, 1.0);
}

//== id: splitfocus | Split Focus | filter | Lens
vec4 effect(vec2 uv) {
  float k = smoothstep(0.45, 0.8, uv.y);
  vec2 px = 7.0 * k / uResolution;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 12; i++) {
    float a = float(i) * 2.39996;
    float r = sqrt(float(i) / 12.0);
    acc += getColor(uv + vec2(cos(a), sin(a)) * r * px).rgb;
  }
  return vec4(acc / 12.0, 1.0);
}

//== id: reededglass | Reeded Glass | filter | Lens
//== param: ribs float 24.0 6.0 80.0
vec4 effect(vec2 uv) {
  float f = fract(uv.x * ribs);
  float off = (f - 0.5) * 1.6 / ribs;
  vec3 c = getColor(vec2(uv.x + off, uv.y)).rgb;
  c *= 0.9 + 0.15 * sin(f * PI);
  return vec4(c, 1.0);
}

//== id: whitevignette | White Edges | filter | Lens
//== param: size float 0.55 0.2 0.9
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float v = smoothstep(size * 0.6, size, length((uv - 0.5) * vec2(1.0, 0.9)) * 1.3);
  return vec4(mix(c, vec3(1.0), v), 1.0);
}

//== id: dither | Dither | filter | Art
//== param: scale float 2.0 1.0 6.0
vec4 effect(vec2 uv) {
  vec2 p = floor(uv * uResolution / scale);
  float b = 0.0;
  // 4x4 Bayer matrix, built bit by bit.
  vec2 q = mod(p, 4.0);
  vec2 q1 = mod(q, 2.0);
  vec2 q2 = floor(q / 2.0);
  float m1 = q1.x * 2.0 + abs(q1.x - q1.y);
  float m2 = q2.x * 2.0 + abs(q2.x - q2.y);
  b = (m1 * 4.0 + m2 + 0.5) / 16.0;
  float l = luma(getColor((p + 0.5) * scale / uResolution).rgb);
  return vec4(vec3(step(b, l)), 1.0);
}

//== id: pocket | Pocket | filter | Art
//== param: size float 3.0 1.0 8.0
vec4 effect(vec2 uv) {
  vec2 cells = uResolution / size;
  vec2 q = (floor(uv * cells) + 0.5) / cells;
  float l = luma(getColor(q).rgb);
  float lv = floor(clamp(l, 0.0, 0.999) * 4.0);
  vec3 c = vec3(0.06, 0.22, 0.06);
  c = mix(c, vec3(0.19, 0.38, 0.19), step(1.0, lv));
  c = mix(c, vec3(0.55, 0.67, 0.06), step(2.0, lv));
  c = mix(c, vec3(0.61, 0.74, 0.06), step(3.0, lv));
  return vec4(c, 1.0);
}

//== id: cells | Cells | filter | Art
//== param: size float 28.0 8.0 80.0
vec4 effect(vec2 uv) {
  vec2 p = uv * uResolution / size;
  vec2 ip = floor(p);
  float best = 9.0;
  float second = 9.0;
  vec2 bestPt = vec2(0.0);
  for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
    vec2 c = ip + vec2(float(x), float(y));
    vec2 pt = c + vec2(rand(c), rand(c + 7.1));
    float d = length(pt - p);
    if (d < best) { second = best; best = d; bestPt = pt; } else if (d < second) { second = d; }
  }
  vec3 col = getColor(bestPt * size / uResolution).rgb;
  float edge = smoothstep(0.0, 0.08, second - best);
  return vec4(col * (0.75 + 0.25 * edge), 1.0);
}

//== id: inkwash | Ink Wash | filter | Art
vec4 effect(vec2 uv) {
  vec2 px = 2.0 / uResolution;
  float l = 0.0;
  for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) l += luma(getColor(uv + vec2(float(x), float(y)) * px).rgb);
  l /= 9.0;
  float e = abs(luma(getColor(uv).rgb) - l) * 8.0;
  float ink = smoothstep(0.15, 0.75, l);
  ink = floor(ink * 4.0) / 4.0 * 0.85 + 0.15;
  vec3 paper = vec3(0.96, 0.94, 0.88) - noise(uv * 90.0) * 0.05;
  return vec4(paper * ink * (1.0 - clamp(e, 0.0, 0.7)), 1.0);
}

//== id: engraving | Engraving | filter | Art
//== param: lines float 90.0 30.0 200.0
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  float y = uv.y * lines + sin(uv.x * 20.0) * 0.6 + l * 1.5;
  float w = 0.5 * (1.0 - l) + 0.05;
  float line = smoothstep(w, w - 0.12, abs(fract(y) - 0.5));
  vec3 ink = vec3(0.12, 0.1, 0.09);
  return vec4(mix(vec3(0.95, 0.92, 0.85), ink, line), 1.0);
}

//== id: triangles | Triangles | filter | Art
//== param: size float 30.0 8.0 100.0
vec4 effect(vec2 uv) {
  vec2 p = uv * uResolution / size;
  vec2 cell = floor(p);
  vec2 f = fract(p);
  float upper = step(f.x, f.y);
  vec2 center = cell + mix(vec2(0.667, 0.333), vec2(0.333, 0.667), upper);
  return getColor(center * size / uResolution);
}

//== id: weave | Weave | filter | Art
//== param: size float 10.0 4.0 30.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 p = uv * uResolution / size;
  vec2 cell = floor(p);
  vec2 f = fract(p);
  float horiz = mod(cell.x + cell.y, 2.0);
  float t = mix(f.y, f.x, horiz);
  float shade = 0.75 + 0.25 * sin(t * PI);
  float gap = smoothstep(0.0, 0.08, min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y)));
  return vec4(c * shade * (0.7 + 0.3 * gap), 1.0);
}

//== id: mirrordiag | Mirror Diagonal | filter | Art
vec4 effect(vec2 uv) {
  vec2 q = uv;
  if (uv.x + uv.y > 1.0) q = vec2(1.0 - uv.y, 1.0 - uv.x);
  return getColor(q);
}

//== id: shredded | Shredded | filter | Art
//== param: strips float 14.0 4.0 40.0
vec4 effect(vec2 uv) {
  float s = floor(uv.x * strips);
  float off = (rand(vec2(s, 1.0)) - 0.5) * 0.08;
  float f = fract(uv.x * strips);
  float edge = smoothstep(0.0, 0.06, f) * smoothstep(1.0, 0.94, f);
  vec3 c = getColor(vec2(uv.x, uv.y + off)).rgb;
  return vec4(mix(vec3(0.95), c, edge), 1.0);
}

//== id: oilslick | Oil Slick | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 iri = hsv2rgb(vec3(fract(l * 1.6 + uv.x * 0.3 + uv.y * 0.2), 0.7, 1.0));
  return vec4(mix(c, c * iri * 1.3, 0.55), 1.0);
}

//== id: gold | Gold | filter | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  vec3 c = mix(vec3(0.2, 0.12, 0.02), vec3(1.0, 0.84, 0.4), smoothstep(0.05, 0.85, l));
  c += vec3(1.0, 0.95, 0.75) * smoothstep(0.8, 1.0, l) * 0.4;
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: silver | Silver | filter | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  l = smoothstep(0.0, 1.0, l);
  vec3 c = mix(vec3(0.1, 0.11, 0.13), vec3(0.93, 0.95, 0.98), l);
  c += 0.12 * sin(l * 9.0) * vec3(1.0);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: twostrip | Two Strip | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float r = c.r;
  float gb = (c.g + c.b) * 0.5;
  vec3 o = vec3(r, gb * 0.95, gb);
  o = mix(vec3(luma(o)), o, 1.2);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: ultraviolet | Ultraviolet | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 uvc = mix(vec3(0.08, 0.0, 0.18), vec3(0.75, 0.4, 1.0), l);
  uvc += vec3(0.6, 1.0, 0.9) * smoothstep(0.75, 1.0, c.b + c.g * 0.3) * 0.4;
  return vec4(min(uvc, vec3(1.0)), 1.0);
}

//== id: moonlight | Moonlight | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 m = vec3(l) * vec3(0.55, 0.7, 1.0) * 0.85;
  m = mix(m, vec3(0.0, 0.02, 0.08), (1.0 - l) * 0.3);
  return vec4(m, 1.0);
}

//== id: neonlines | Neon Lines | filter | Art
//== param: glow float 3.0 1.0 8.0
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  vec3 gx = getColor(uv + vec2(px.x, 0.0)).rgb - getColor(uv - vec2(px.x, 0.0)).rgb;
  vec3 gy = getColor(uv + vec2(0.0, px.y)).rgb - getColor(uv - vec2(0.0, px.y)).rgb;
  float e = clamp(length(vec2(length(gx), length(gy))) * glow, 0.0, 1.0);
  vec3 hue = hsv2rgb(vec3(fract(uv.x * 0.6 + uv.y * 0.4), 0.8, 1.0));
  return vec4(hue * e + c * 0.08, 1.0);
}

//== id: overexpose | Overexposed | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * 1.6 + 0.05;
  c = c / (1.0 + c * 0.15);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: bleachgreen | Matrix Bleach | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 o = mix(vec3(l), c, 0.4) * vec3(0.88, 1.08, 0.9);
  o = smoothstep(0.04, 0.96, o);
  return vec4(o, 1.0);
}

//== id: sunkissed | Sunkissed | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  c = mix(c, c * vec3(1.12, 1.02, 0.86), 0.9);
  c += vec3(1.0, 0.6, 0.25) * smoothstep(1.2, 0.0, length(uv - vec2(0.85, 0.9))) * 0.18;
  c = mix(vec3(l), c, 1.1);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

// ---------------------------------------------------------------- effects, batch 3

//== id: heathaze | Heat Haze | effect | Weather
//== param: amount float 0.006 0.001 0.02
vec4 effect(vec2 uv) {
  float n = noise(vec2(uv.x * 14.0, uv.y * 10.0 - uTime * 3.0)) - 0.5;
  float m = noise(vec2(uv.x * 25.0 + 4.0, uv.y * 18.0 - uTime * 4.5)) - 0.5;
  float k = smoothstep(0.9, 0.2, uv.y);
  vec3 c = getColor(uv + vec2(n, m) * amount * 2.0 * k).rgb;
  return vec4(c * vec3(1.03, 1.0, 0.95), 1.0);
}

//== id: blizzard | Blizzard | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  c = mix(c, vec3(0.85, 0.9, 0.95), 0.25);
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float s = 0.0;
  for (int l = 0; l < 3; l++) {
    float fl = float(l);
    float sc = 18.0 + fl * 14.0;
    vec2 p = uv * asp * sc + vec2(uTime * (6.0 + fl * 3.0), uTime * (3.0 + fl));
    vec2 cell = floor(p);
    vec2 f = fract(p) - 0.5;
    vec2 o = vec2(rand(cell), rand(cell + 3.3)) - 0.5;
    float r = 0.05 + rand(cell + 1.7) * 0.08;
    s += smoothstep(r, r * 0.3, length(f - o * 0.6)) * (0.6 + 0.4 * fl / 2.0);
  }
  return vec4(min(c + s, vec3(1.0)), 1.0);
}

//== id: ashfall | Ash Fall | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  c = mix(c, vec3(luma(c)) * vec3(1.0, 0.92, 0.85), 0.35);
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float s = 0.0;
  for (int l = 0; l < 2; l++) {
    float sc = 12.0 + float(l) * 10.0;
    vec2 p = uv * asp * sc + vec2(sin(uTime + float(l)) * 0.8, uTime * (0.8 + float(l) * 0.5));
    vec2 cell = floor(p);
    vec2 f = fract(p) - 0.5;
    vec2 o = vec2(rand(cell + 9.0), rand(cell + 2.0)) - 0.5;
    s += smoothstep(0.06, 0.02, length(f - o * 0.6)) * step(0.4, rand(cell + 5.0));
  }
  return vec4(mix(c, vec3(0.55, 0.52, 0.5), s * 0.85), 1.0);
}

//== id: sunshower | Sun Shower | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * vec3(1.08, 1.0, 0.88);
  c += vec3(1.0, 0.75, 0.4) * smoothstep(0.9, 0.0, length(uv - vec2(0.9, 1.0))) * 0.3;
  vec2 p = vec2(uv.x * 80.0 + uv.y * 8.0, uv.y * 3.0 + uTime * 6.0);
  float col = floor(p.x);
  float drop = fract(p.y + rand(vec2(col, 1.0)) * 10.0);
  float streak = smoothstep(0.0, 0.08, drop) * smoothstep(0.25, 0.08, drop) * smoothstep(0.5, 0.4, abs(fract(p.x) - 0.5)) * step(0.6, rand(vec2(col, 3.0)));
  return vec4(min(c + streak * 0.45, vec3(1.0)), 1.0);
}

//== id: windstreaks | Wind | effect | Weather
vec4 effect(vec2 uv) {
  vec2 off = vec2((noise(vec2(uv.y * 8.0, uTime * 2.0)) - 0.5) * 0.01, 0.0);
  vec3 c = getColor(uv + off).rgb;
  float row = floor(uv.y * 60.0);
  float x = fract(uv.x * 0.6 - uTime * (1.5 + rand(vec2(row, 2.0)) * 2.0) + rand(vec2(row, 1.0)));
  float streak = smoothstep(0.0, 0.2, x) * smoothstep(0.35, 0.2, x) * step(0.85, rand(vec2(row, 7.0)));
  streak *= smoothstep(0.5, 0.2, abs(fract(uv.y * 60.0) - 0.5));
  return vec4(min(c + streak * 0.35, vec3(1.0)), 1.0);
}

//== id: scantear | Scan Tear | effect | Glitch
vec4 effect(vec2 uv) {
  float t = floor(uTime * 8.0);
  float band = rand(vec2(t, 1.0));
  float h = 0.04 + rand(vec2(t, 2.0)) * 0.12;
  float inBand = step(band, uv.y) * step(uv.y, band + h);
  float shift = (rand(vec2(t, 3.0)) - 0.5) * 0.25 * inBand;
  vec3 c = getColor(vec2(fract(uv.x + shift), uv.y)).rgb;
  c = mix(c, c.gbr, inBand * 0.5);
  return vec4(c, 1.0);
}

//== id: linejitter | Line Jitter | effect | Glitch
//== param: amount float 0.02 0.005 0.08
vec4 effect(vec2 uv) {
  float t = floor(uTime * 20.0);
  float line = floor(uv.y * uResolution.y / 2.0);
  float j = (rand(vec2(line, t)) - 0.5) * amount * step(0.7, rand(vec2(line * 0.1, t)));
  return getColor(vec2(uv.x + j, uv.y));
}

//== id: bytecrush | Byte Crush | effect | Glitch
vec4 effect(vec2 uv) {
  float t = floor(uTime * 6.0);
  vec2 block = floor(uv * vec2(16.0, 9.0));
  float r = rand(block + t);
  vec3 c = getColor(uv).rgb;
  if (r > 0.88) {
    vec2 q = (block + 0.5) / vec2(16.0, 9.0);
    c = floor(getColor(q).rgb * 3.0) / 3.0;
    c = mix(c, c.brg, step(0.95, r));
  }
  return vec4(c, 1.0);
}

//== id: rgbjitter | RGB Jitter | effect | Glitch
vec4 effect(vec2 uv) {
  float t = floor(uTime * 12.0);
  vec2 o1 = (vec2(rand(vec2(t, 1.0)), rand(vec2(t, 2.0))) - 0.5) * 0.03;
  vec2 o2 = (vec2(rand(vec2(t, 3.0)), rand(vec2(t, 4.0))) - 0.5) * 0.03;
  return vec4(getColor(uv + o1).r, getColor(uv).g, getColor(uv + o2).b, 1.0);
}

//== id: glitchpulse | Glitch Pulse | effect | Glitch
vec4 effect(vec2 uv) {
  float beat = fract(uTime * 1.5);
  float k = smoothstep(0.25, 0.0, beat);
  float row = floor(uv.y * 24.0);
  float shift = (rand(vec2(row, floor(uTime * 1.5))) - 0.5) * 0.15 * k;
  vec2 q = vec2(uv.x + shift, uv.y);
  vec3 c = vec3(getColor(q + vec2(0.02 * k, 0.0)).r, getColor(q).g, getColor(q - vec2(0.02 * k, 0.0)).b);
  return vec4(c * (1.0 + k * 0.3), 1.0);
}

//== id: bulgepulse | Bulge Pulse | effect | Lens
vec4 effect(vec2 uv) {
  float k = 0.35 + 0.35 * sin(uTime * 3.0);
  vec2 d = uv - 0.5;
  float r = length(d);
  vec2 q = 0.5 + d * mix(1.0, smoothstep(0.0, 0.5, r) / max(r * 2.0, 0.001), k * smoothstep(0.5, 0.0, r));
  return getColor(q);
}

//== id: swirlfx | Swirl | effect | Lens
//== param: strength float 2.5 0.5 8.0
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float r = length(d);
  float a = strength * sin(uTime * 1.2) * smoothstep(0.6, 0.0, r);
  vec2 q = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y) / asp + 0.5;
  return getColor(q);
}

//== id: zoomburst | Zoom Burst | effect | Lens
vec4 effect(vec2 uv) {
  float k = smoothstep(0.4, 0.0, fract(uTime * 0.8)) * 0.12;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 12; i++) {
    float s = 1.0 - k * float(i) / 11.0;
    acc += getColor(0.5 + (uv - 0.5) * s).rgb;
  }
  return vec4(acc / 12.0, 1.0);
}

//== id: tiltshiftmove | Miniature | effect | Lens
vec4 effect(vec2 uv) {
  float center = 0.5 + 0.2 * sin(uTime * 0.7);
  float k = smoothstep(0.08, 0.35, abs(uv.y - center));
  vec2 px = 7.0 * k / uResolution;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 12; i++) {
    float a = float(i) * 2.39996;
    float r = sqrt(float(i) / 12.0);
    acc += getColor(uv + vec2(cos(a), sin(a)) * r * px).rgb;
  }
  vec3 c = acc / 12.0;
  c = mix(vec3(luma(c)), c, 1.3);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: neonborder | Neon Border | effect | Light
//== param: tint color 1.0,0.1,0.8 0 1
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = min(uv, 1.0 - uv) * asp;
  float e = min(d.x, d.y);
  float pulse = 0.7 + 0.3 * sin(uTime * 4.0);
  float glow = 0.004 / (abs(e - 0.04) + 0.004) * pulse;
  float run = 0.5 + 0.5 * sin((uv.x + uv.y) * 12.0 - uTime * 6.0);
  vec3 col = mix(tint, tint.bgr, run);
  return vec4(min(c + col * glow * 0.6, vec3(1.0)), 1.0);
}

//== id: edgepulse | Edge Pulse | effect | Light
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  float e = clamp(length(vec2(gx, gy)) * 4.0, 0.0, 1.0);
  float pulse = 0.5 + 0.5 * sin(uTime * 5.0);
  vec3 col = hsv2rgb(vec3(fract(uTime * 0.2), 0.7, 1.0));
  return vec4(min(c * (0.75 + 0.25 * (1.0 - pulse)) + col * e * pulse * 1.5, vec3(1.0)), 1.0);
}

//== id: candle | Candlelight | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float f = 0.8 + 0.12 * noise(vec2(uTime * 6.0, 1.0)) + 0.08 * noise(vec2(uTime * 13.0, 4.0));
  float v = smoothstep(1.0, 0.1, length(uv - vec2(0.5, 0.35)));
  vec3 warm = c * vec3(1.15, 0.92, 0.65);
  return vec4(clamp(warm * mix(0.35, 1.05, v) * f, 0.0, 1.0), 1.0);
}

//== id: sunrise | Sunrise | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float p = clamp(uTime / max(uDuration, 0.5), 0.0, 1.0);
  vec2 sun = vec2(0.75, mix(-0.1, 0.8, p));
  float d = length((uv - sun) * vec2(uResolution.x / uResolution.y, 1.0));
  vec3 glow = vec3(1.0, 0.6, 0.25) * 0.08 / (d + 0.08);
  c = c * mix(vec3(0.55, 0.5, 0.7), vec3(1.05, 0.98, 0.9), p);
  return vec4(min(c + glow * 0.5, vec3(1.0)), 1.0);
}

//== id: caustics | Caustics | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 p = uv * vec2(uResolution.x / uResolution.y, 1.0) * 6.0;
  float t = uTime * 0.8;
  vec2 i = p;
  float s = 0.0;
  for (int n = 0; n < 4; n++) {
    float fn = float(n) + 1.0;
    i = p + vec2(cos(t / fn + i.y * 1.3) + sin(t + i.x * 0.9), sin(t / fn - i.x * 1.1) + cos(t + i.y * 0.7));
    s += 1.0 / length(vec2(sin(i.x + t) / 0.02, cos(i.y + t) / 0.02));
  }
  s = clamp(s / 4.0 * 3.0, 0.0, 1.0);
  float light = sqrt(s) * 0.55;
  return vec4(min(c * vec3(0.82, 0.96, 1.06) + vec3(0.6, 0.9, 1.0) * light * 0.9, vec3(1.0)), 1.0);
}

//== id: spin360 | Spin 360 | effect | Motion
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float a = uTime * 1.6;
  vec2 d = (uv - 0.5) * asp * 1.2;
  vec2 q = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y) / asp + 0.5;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  // Outside the frame, a darker mirrored copy instead of black corners.
  vec2 m = 1.0 - abs(1.0 - mod(q, 2.0));
  return vec4(getColor(m).rgb * mix(0.45, 1.0, inside), 1.0);
}

//== id: panfx | Pan | effect | Motion
vec4 effect(vec2 uv) {
  float x = 0.15 * sin(uTime * 0.9);
  return getColor(0.5 + (uv - 0.5) / 1.35 + vec2(x, 0.0));
}

//== id: floaty | Float | effect | Motion
vec4 effect(vec2 uv) {
  vec2 o = vec2(sin(uTime * 1.3) * 0.012, sin(uTime * 2.1) * 0.02);
  float a = sin(uTime * 1.1) * 0.02;
  vec2 d = (uv - 0.5) / 1.08;
  vec2 q = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y) + 0.5 + o;
  return getColor(q);
}

//== id: squashfx | Squash | effect | Motion
vec4 effect(vec2 uv) {
  float s = sin(uTime * 6.0);
  vec2 sc = vec2(1.0 + s * 0.08, 1.0 - s * 0.08);
  vec2 q = vec2(0.5, 0.0) + (uv - vec2(0.5, 0.0)) / sc;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: flipfx | Flip | effect | Motion
vec4 effect(vec2 uv) {
  float a = uTime * 2.2;
  float c = cos(a);
  float x = (uv.x - 0.5) / max(abs(c), 0.02);
  float persp = 1.0 + sin(a) * x * 0.3;
  vec2 q = vec2(x * sign(c) + 0.5, (uv.y - 0.5) * persp + 0.5);
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  vec3 bg = getColor(0.5 + (uv - 0.5) * 0.9).rgb * 0.25;
  return vec4(mix(bg, getColor(q).rgb * (0.6 + 0.4 * abs(c)), inside), 1.0);
}

//== id: mirrorball | Mirror Ball | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * 0.6;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 p = uv * asp * 10.0 + vec2(uTime * 1.5, 0.0);
  vec2 cell = floor(p);
  vec2 f = fract(p) - 0.5;
  vec2 o = vec2(rand(cell), rand(cell + 2.0)) - 0.5;
  float spot = smoothstep(0.18, 0.0, length(f - o * 0.5)) * step(0.45, rand(cell + 4.0));
  vec3 col = hsv2rgb(vec3(rand(cell + 6.0), 0.3, 1.0));
  return vec4(min(c + col * spot * 0.9, vec3(1.0)), 1.0);
}

//== id: equalizer | Equalizer | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float bars = 24.0;
  float b = floor(uv.x * bars);
  float h = 0.08 + 0.25 * abs(sin(uTime * (3.0 + rand(vec2(b, 1.0)) * 5.0) + b));
  float inBar = step(uv.y, h) * step(0.15, fract(uv.x * bars)) * step(fract(uv.x * bars), 0.85);
  float seg = step(0.3, fract(uv.y * 60.0));
  vec3 col = mix(vec3(0.1, 1.0, 0.5), vec3(1.0, 0.2, 0.3), uv.y / 0.35);
  return vec4(mix(c, col, inBar * seg * 0.85), 1.0);
}

//== id: neonrings | Neon Rings | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * 0.8;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float r = length((uv - 0.5) * asp);
  float w = fract(r * 4.0 - uTime * 0.8);
  float ring = 0.02 / (abs(w - 0.5) + 0.02) - 0.04;
  vec3 col = hsv2rgb(vec3(fract(r - uTime * 0.2), 0.8, 1.0));
  return vec4(min(c + col * max(ring, 0.0) * 0.5, vec3(1.0)), 1.0);
}

//== id: rave | Rave | effect | Party
vec4 effect(vec2 uv) {
  float beat = fract(uTime * 2.0);
  float k = smoothstep(0.3, 0.0, beat);
  vec2 q = 0.5 + (uv - 0.5) / (1.0 + k * 0.06);
  vec3 c = getColor(q).rgb;
  vec3 h = rgb2hsv(c);
  h.x = fract(h.x + floor(uTime * 2.0) * 0.25);
  h.y = min(1.0, h.y * 1.4);
  return vec4(hsv2rgb(h) * (1.0 + k * 0.35), 1.0);
}

//== id: discofloor | Disco Floor | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 cell = floor(uv * vec2(8.0, 6.0));
  float t = floor(uTime * 3.0);
  vec3 col = hsv2rgb(vec3(rand(cell + t), 0.8, 1.0));
  float on = step(0.5, rand(cell + t + 9.0));
  vec2 f = fract(uv * vec2(8.0, 6.0));
  float frame = smoothstep(0.0, 0.08, min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y)));
  return vec4(mix(c, c * 0.5 + col * 0.5, on * frame * 0.6), 1.0);
}

//== id: filmjump | Film Jump | effect | Retro
vec4 effect(vec2 uv) {
  float t = floor(uTime * 12.0);
  float jump = step(0.85, rand(vec2(t, 1.0))) * (rand(vec2(t, 2.0)) - 0.5) * 0.1;
  vec2 q = vec2(uv.x, fract(uv.y + jump));
  vec3 c = getColor(q).rgb * vec3(1.05, 0.98, 0.88);
  float bar = smoothstep(0.012, 0.0, abs(fract(uv.y + jump) - 0.0)) + smoothstep(0.012, 0.0, abs(fract(uv.y + jump) - 1.0));
  c *= 1.0 - bar * step(0.001, abs(jump));
  return vec4(c, 1.0);
}

//== id: arcadefx | Arcade | effect | Retro
vec4 effect(vec2 uv) {
  vec2 cells = uResolution / 4.0;
  vec2 q = (floor(uv * cells) + 0.5) / cells;
  vec3 c = floor(getColor(q).rgb * 5.0 + 0.5) / 5.0;
  float scan = 0.75 + 0.25 * sin(uv.y * uResolution.y * 1.57);
  float flick = 0.95 + 0.05 * sin(uTime * 50.0);
  return vec4(c * scan * flick * 1.1, 1.0);
}

//== id: teletext | Teletext | effect | Retro
vec4 effect(vec2 uv) {
  vec2 cells = vec2(40.0, 24.0);
  vec2 cell = floor(uv * cells);
  vec2 f = fract(uv * cells);
  vec2 sub = floor(f * vec2(2.0, 3.0));
  vec2 q = (cell + (sub + 0.5) / vec2(2.0, 3.0)) / cells;
  vec3 c = step(0.5, getColor(q).rgb);
  float row = cell.y;
  float blink = step(0.5, fract(uTime * 1.2 + rand(vec2(row, 3.0))));
  c = mix(c, c * 0.0 + step(0.5, getColor(q).rgb).brg, step(0.9, rand(vec2(row, 5.0))) * blink);
  return vec4(c, 1.0);
}

//== id: kinescope | Kinescope | effect | Retro
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  vec2 q = 0.5 + d * (1.0 + dot(d, d) * 0.25);
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  float l = luma(getColor(q).rgb) * 0.8 + luma(getColor(q - vec2(0.012, 0.0)).rgb) * 0.2;
  l *= 0.9 + 0.1 * sin(uTime * 9.0 + q.y * 3.0);
  l *= 0.85 + 0.15 * sin(q.y * uResolution.y * 1.2);
  float v = smoothstep(0.75, 0.3, length(d));
  return vec4(vec3(l * v * inside) * vec3(0.9, 1.0, 0.95), 1.0);
}

//== id: colorwave | Color Wave | effect | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 h = rgb2hsv(c);
  h.x = fract(h.x + 0.25 * sin(uv.x * 6.0 + uv.y * 3.0 - uTime * 3.0));
  h.y = min(1.0, h.y * 1.2);
  return vec4(hsv2rgb(h), 1.0);
}

//== id: duocycle | Duotone Cycle | effect | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  vec3 a = hsv2rgb(vec3(fract(uTime * 0.15), 0.8, 0.35));
  vec3 b = hsv2rgb(vec3(fract(uTime * 0.15 + 0.45), 0.5, 1.0));
  return vec4(mix(a, b, smoothstep(0.05, 0.95, l)), 1.0);
}

//== id: satpulse | Saturation Pulse | effect | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float k = 0.5 + 0.5 * sin(uTime * 3.0);
  float l = luma(c);
  return vec4(clamp(mix(vec3(l), c, mix(0.0, 1.8, k)), 0.0, 1.0), 1.0);
}

//== id: colordrain | Color Drain | effect | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float p = clamp(uTime / max(uDuration, 0.5), 0.0, 1.0);
  float edge = p * 1.4 - 0.2;
  float k = smoothstep(edge - 0.15, edge + 0.15, 1.0 - uv.y);
  return vec4(mix(vec3(luma(c)), c, k), 1.0);
}

//== id: colorbloom | Color Bloom | effect | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float p = clamp(uTime / max(uDuration, 0.5), 0.0, 1.0);
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float d = length((uv - 0.5) * asp);
  float k = smoothstep(p * 1.1 + 0.05, p * 1.1 - 0.05, d);
  return vec4(mix(vec3(luma(c)), c, k), 1.0);
}

// ---------------------------------------------------------------- clip animations, batch 3

//== id: anim_curtain | Curtain | anim | Shape
vec4 effect(vec2 uv) {
  float open = uProgress * 0.5;
  float show = step(0.5 - open, uv.x) * step(uv.x, 0.5 + open);
  float fold = 0.85 + 0.15 * sin(uv.x * 80.0);
  vec3 curtain = vec3(0.45, 0.04, 0.06) * fold;
  return vec4(mix(curtain * (1.0 - uProgress), getColor(uv).rgb, show), 1.0);
}

//== id: anim_doors | Doors | anim | Shape
vec4 effect(vec2 uv) {
  float p = 1.0 - uProgress;
  float left = step(uv.x, 0.5);
  vec2 q = uv;
  q.x = left > 0.5 ? uv.x + p * 0.5 : uv.x - p * 0.5;
  float inside = left > 0.5 ? step(q.x, 0.5) : step(0.5, q.x);
  return vec4(getColor(clamp(q, 0.0, 1.0)).rgb * inside * mix(0.5, 1.0, uProgress), 1.0);
}

//== id: anim_square | Square | anim | Shape
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = abs(uv - 0.5) * asp;
  float s = uProgress * 0.5 * max(asp.x, 1.0) * 1.05;
  float inside = step(max(d.x, d.y), s);
  if (uProgress >= 1.0) inside = 1.0;
  return vec4(getColor(uv).rgb * inside, 1.0);
}

//== id: anim_triangle | Triangle | anim | Shape
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 p = (uv - vec2(0.5, 0.4)) * asp;
  float s = uProgress * 2.2;
  float d = max(abs(p.x) * 0.866 + p.y * 0.5, -p.y);
  float inside = step(d, s * 0.5);
  if (uProgress >= 1.0) inside = 1.0;
  return vec4(getColor(uv).rgb * inside, 1.0);
}

//== id: anim_cross | Cross | anim | Shape
vec4 effect(vec2 uv) {
  vec2 d = abs(uv - 0.5);
  float w = uProgress * 0.5;
  float inside = max(step(d.x, w * 0.5 + uProgress * uProgress * 0.5), step(d.y, w * 0.5 + uProgress * uProgress * 0.5));
  inside *= step(min(d.x, d.y), w * 1.02);
  if (uProgress >= 1.0) inside = 1.0;
  return vec4(getColor(uv).rgb * inside, 1.0);
}

//== id: anim_sweep | Light Sweep | anim | Light
vec4 effect(vec2 uv) {
  float edge = uProgress * 1.4 - 0.2;
  float x = uv.x + uv.y * 0.2;
  float shown = smoothstep(edge + 0.02, edge - 0.02, x);
  float bar = exp(-pow(abs(x - edge) * 14.0, 2.0)) * (1.0 - uProgress);
  vec3 c = getColor(uv).rgb * shown + vec3(1.0, 0.97, 0.9) * bar;
  if (uProgress >= 1.0) c = getColor(uv).rgb;
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: anim_flare | Flare In | anim | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float k = 1.0 - uProgress;
  vec2 d = (uv - 0.5) * vec2(uResolution.x / uResolution.y, 1.0);
  float glow = 0.05 / (length(d) + 0.05) * k;
  float streak = 0.01 / (abs(d.y) + 0.01) * k * smoothstep(0.8, 0.0, abs(d.x));
  c = c * uProgress + vec3(1.0, 0.85, 0.6) * (glow + streak * 0.5) + k * 0.3;
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: anim_overexpose | Overexpose | anim | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float k = 1.0 - uProgress;
  vec3 b = c * (1.0 + k * 4.0) + k * 0.6;
  return vec4(min(b, vec3(1.0)), 1.0);
}

//== id: anim_burn | Burn In | anim | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float n = noise(uv * 6.0) * 0.6 + noise(uv * 14.0) * 0.4;
  float t = uProgress * 1.3 - 0.15;
  float shown = smoothstep(t + 0.02, t - 0.02, n);
  float rim = smoothstep(0.08, 0.0, abs(n - t)) * (1.0 - uProgress);
  vec3 dark = c * vec3(0.35, 0.18, 0.08);
  vec3 o = mix(dark, c, shown) + vec3(1.0, 0.5, 0.1) * rim * 1.5;
  if (uProgress >= 1.0) o = c;
  return vec4(min(o, vec3(1.0)), 1.0);
}

//== id: anim_slidediag | Slide diagonal | anim | Slide
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  k = k * k;
  vec2 q = uv + vec2(k, k);
  float inside = step(q.x, 1.0) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: anim_whip | Whip | anim | Slide
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  float off = k * k * k;
  vec3 acc = vec3(0.0);
  float n = 0.0;
  for (int i = 0; i < 10; i++) {
    vec2 q = uv + vec2(off + k * 0.15 * float(i) / 9.0, 0.0);
    float inside = step(q.x, 1.0);
    acc += getColor(q).rgb * inside;
    n += 1.0;
  }
  return vec4(acc / n, 1.0);
}

//== id: anim_tilt | Tilt in | anim | Basic
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  float a = k * k * 0.6;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - vec2(0.0, 0.0)) * asp;
  vec2 q = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y) / asp;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside * min(1.0, uProgress * 2.0), 1.0);
}

//== id: anim_tiles | Tiles | anim | Basic
vec4 effect(vec2 uv) {
  vec2 cell = floor(uv * vec2(8.0, 6.0));
  float order = rand(cell);
  float shown = step(order, uProgress * 1.05);
  if (uProgress >= 1.0) shown = 1.0;
  return vec4(getColor(uv).rgb * shown, 1.0);
}

//== id: anim_focus | Focus in | anim | Basic
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  vec2 px = 14.0 * k / uResolution;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 16; i++) {
    float a = float(i) * 2.39996;
    float r = sqrt(float(i) / 16.0);
    acc += getColor(0.5 + (uv - 0.5) / (1.0 + k * 0.15) + vec2(cos(a), sin(a)) * r * px).rgb;
  }
  return vec4(acc / 16.0 * min(1.0, uProgress * 1.5 + 0.2), 1.0);
}

//== id: anim_vhsin | VHS in | anim | Basic
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  float row = floor(uv.y * 90.0);
  float j = (rand(vec2(row, floor(uProgress * 20.0))) - 0.5) * 0.2 * k;
  vec2 q = vec2(uv.x + j, uv.y);
  vec3 c = vec3(getColor(q + vec2(0.03 * k, 0.0)).r, getColor(q).g, getColor(q - vec2(0.03 * k, 0.0)).b);
  float roll = step(abs(fract(uv.y + k * 2.0) - 0.5), 0.04) * k;
  c = c * min(1.0, uProgress * 1.6 + 0.1) + roll * 0.5 + (rand(uv * uResolution + uProgress) - 0.5) * 0.4 * k;
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

// ---------------------------------------------------------------- filters, batch 4

//== id: ascii2 | Terminal | filter | Art
//== param: size float 8.0 4.0 20.0
vec4 effect(vec2 uv) {
  vec2 cells = uResolution / size;
  vec2 cell = floor(uv * cells);
  vec2 f = fract(uv * cells);
  float l = luma(getColor((cell + 0.5) / cells).rgb);
  // Glyph density from simple shapes: dot, dash, plus, box.
  float d = 0.0;
  if (l > 0.2) d = step(length(f - 0.5), 0.15);
  if (l > 0.4) d = max(d, step(abs(f.y - 0.5), 0.08) * step(abs(f.x - 0.5), 0.35));
  if (l > 0.6) d = max(d, step(abs(f.x - 0.5), 0.08) * step(abs(f.y - 0.5), 0.35));
  if (l > 0.8) d = max(d, 1.0 - step(0.12, min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y))));
  return vec4(vec3(0.2, 1.0, 0.4) * d, 1.0);
}

//== id: mosaicround | Round Tiles | filter | Art
//== param: size float 16.0 6.0 50.0
vec4 effect(vec2 uv) {
  vec2 cells = uResolution / size;
  vec2 cell = floor(uv * cells);
  vec2 f = fract(uv * cells) - 0.5;
  vec3 c = getColor((cell + 0.5) / cells).rgb;
  float m = smoothstep(0.48, 0.42, length(f));
  return vec4(c * m * (1.05 - length(f) * 0.4) + vec3(0.06) * (1.0 - m), 1.0);
}

//== id: legobricks | Brick Toy | filter | Art
//== param: size float 18.0 8.0 50.0
vec4 effect(vec2 uv) {
  vec2 cells = uResolution / size;
  vec2 cell = floor(uv * cells);
  vec2 f = fract(uv * cells) - 0.5;
  vec3 c = floor(getColor((cell + 0.5) / cells).rgb * 6.0 + 0.5) / 6.0;
  float stud = smoothstep(0.3, 0.26, length(f));
  float rim = smoothstep(0.3, 0.26, length(f + vec2(0.04, -0.04)));
  float edge = smoothstep(0.5, 0.44, max(abs(f.x), abs(f.y)));
  vec3 o = c * (0.85 + 0.15 * edge);
  o = mix(o, c * 1.2, stud * (1.0 - rim) + rim * 0.0);
  o = mix(o, c * 0.75, (1.0 - stud) * step(length(f), 0.33));
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: pencilhatch | Pencil Hatch | filter | Art
vec4 effect(vec2 uv) {
  vec2 p = uv * uResolution;
  float l = luma(getColor(uv).rgb);
  float h1 = step(0.5, fract((p.x + p.y) / 6.0)) * step(l, 0.75);
  float h2 = step(0.5, fract((p.x - p.y) / 6.0)) * step(l, 0.5);
  float h3 = step(0.5, fract(p.y / 4.0)) * step(l, 0.25);
  float ink = max(max(h1, h2), h3) * 0.7;
  vec3 paper = vec3(0.97, 0.96, 0.92);
  return vec4(paper * (1.0 - ink), 1.0);
}

//== id: marker | Marker | filter | Art
vec4 effect(vec2 uv) {
  vec2 px = 1.5 / uResolution;
  vec3 c = vec3(0.0);
  for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) c += getColor(uv + vec2(float(x), float(y)) * px).rgb;
  c /= 9.0;
  vec3 h = rgb2hsv(c);
  h.y = min(1.0, h.y * 1.5);
  h.z = floor(h.z * 4.0 + 0.5) / 4.0;
  vec3 o = hsv2rgb(h);
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  o *= 1.0 - smoothstep(0.08, 0.2, length(vec2(gx, gy)));
  return vec4(o, 1.0);
}

//== id: pastelchalk | Chalk Pastel | filter | Art
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float grain = noise(uv * uResolution * 0.35) * 0.6 + noise(uv * uResolution * 0.9) * 0.4;
  vec3 h = rgb2hsv(c);
  h.y *= 0.7;
  h.z = 0.35 + h.z * 0.7;
  vec3 o = hsv2rgb(h) * (0.8 + 0.3 * grain);
  return vec4(mix(vec3(0.15, 0.15, 0.18), o, smoothstep(0.2, 0.5, grain + luma(c) * 0.5)), 1.0);
}

//== id: chalkboard | Chalkboard | filter | Art
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  float e = clamp(length(vec2(gx, gy)) * 5.0, 0.0, 1.0);
  e *= 0.6 + 0.4 * noise(uv * uResolution * 0.5);
  vec3 board = vec3(0.12, 0.2, 0.16) + noise(uv * 30.0) * 0.03;
  return vec4(mix(board, vec3(0.92, 0.94, 0.9), e), 1.0);
}

//== id: thermal2 | Heat Map | filter | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  vec3 c = mix(vec3(0.0, 0.0, 0.3), vec3(0.0, 0.8, 1.0), smoothstep(0.0, 0.3, l));
  c = mix(c, vec3(0.2, 1.0, 0.2), smoothstep(0.3, 0.5, l));
  c = mix(c, vec3(1.0, 1.0, 0.0), smoothstep(0.5, 0.7, l));
  c = mix(c, vec3(1.0, 0.1, 0.0), smoothstep(0.7, 0.95, l));
  return vec4(c, 1.0);
}

//== id: sobelcolor | Outline Color | filter | Art
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  float e = smoothstep(0.05, 0.18, length(vec2(gx, gy)));
  vec3 flat_ = floor(c * 5.0 + 0.5) / 5.0;
  return vec4(mix(mix(vec3(1.0), flat_, 0.6), vec3(0.05), e), 1.0);
}

//== id: sticker | Sticker | filter | Art
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 h = rgb2hsv(c);
  h.y = min(1.0, h.y * 1.4 + 0.1);
  h.z = floor(h.z * 3.0 + 0.7) / 3.0;
  vec3 o = hsv2rgb(h);
  vec2 px = 2.0 / uResolution;
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  float e = smoothstep(0.1, 0.25, length(vec2(gx, gy)));
  return vec4(mix(o, vec3(1.0), e), 1.0);
}

//== id: knit | Knit | filter | Art
//== param: size float 12.0 6.0 30.0
vec4 effect(vec2 uv) {
  vec2 p = uv * uResolution / size;
  vec2 cell = floor(p);
  vec2 f = fract(p);
  vec3 c = getColor((cell + 0.5) * size / uResolution).rgb;
  float x = f.x - 0.5;
  float v = abs(x) * 2.0 - (f.y - 0.5) * sign(x) * 1.2;
  float stitch = smoothstep(0.9, 0.3, abs(v - 0.3));
  return vec4(c * (0.55 + 0.6 * stitch), 1.0);
}

//== id: tvwall | TV Wall | filter | Art
vec4 effect(vec2 uv) {
  vec2 g = uv * vec2(4.0, 3.0);
  vec2 f = fract(g);
  vec2 cell = floor(g);
  vec3 c = getColor(f).rgb;
  vec3 h = rgb2hsv(c);
  h.x = fract(h.x + rand(cell) * 0.4);
  c = hsv2rgb(h) * (0.75 + 0.35 * rand(cell + 2.0));
  float frame = smoothstep(0.0, 0.04, min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y)));
  return vec4(c * frame, 1.0);
}

//== id: puzzle | Puzzle | filter | Art
//== param: pieces float 6.0 3.0 14.0
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 p = uv * asp * pieces;
  vec2 f = fract(p) - 0.5;
  vec2 cell = floor(p);
  float knobX = length(vec2(abs(f.x) - 0.5, f.y)) - 0.14;
  float knobY = length(vec2(f.x, abs(f.y) - 0.5)) - 0.14;
  float edge = min(0.5 - max(abs(f.x), abs(f.y)), min(abs(knobX), abs(knobY)));
  vec3 c = getColor(uv).rgb;
  float line = smoothstep(0.02, 0.0, edge);
  float shade = 0.92 + 0.08 * rand(cell);
  return vec4(c * shade * (1.0 - line * 0.6) + line * 0.15, 1.0);
}

//== id: oldphoto | Old Photo | filter | Color
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv).rgb);
  vec3 c = mix(vec3(0.2, 0.14, 0.08), vec3(0.95, 0.88, 0.72), smoothstep(0.0, 1.0, l));
  float stain = noise(uv * 4.0) * 0.12 + noise(uv * 40.0) * 0.05;
  float v = smoothstep(0.8, 0.3, length(uv - 0.5));
  float crease = smoothstep(0.004, 0.0, abs(uv.x - 0.62 - uv.y * 0.05)) * 0.3;
  return vec4(c * (0.75 + 0.25 * v) - stain + crease, 1.0);
}

//== id: newsprint2 | Comic Print | filter | Art
//== param: size float 5.0 3.0 12.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 p = uv * uResolution / size;
  vec3 k = 1.0 - c;
  float ink = min(min(k.r, k.g), k.b);
  vec3 cmy = (k - ink) / max(1.0 - ink, 0.001);
  vec3 dots;
  float a = 0.26;
  for (int i = 0; i < 3; i++) {
    float ang = a + float(i) * 0.52;
    vec2 q = vec2(cos(ang) * p.x - sin(ang) * p.y, sin(ang) * p.x + cos(ang) * p.y);
    float d = length(fract(q) - 0.5);
    float v = i == 0 ? cmy.r : (i == 1 ? cmy.g : cmy.b);
    float dt = smoothstep(0.05, 0.0, d - sqrt(v) * 0.5);
    if (i == 0) dots.r = dt; else if (i == 1) dots.g = dt; else dots.b = dt;
  }
  vec3 o = vec3(1.0) - dots;
  o *= 1.0 - smoothstep(0.55, 0.85, ink);
  return vec4(o * vec3(1.0, 0.98, 0.93), 1.0);
}

//== id: glass3d | Anaglyph | filter | Lens
//== param: depth float 0.012 0.002 0.04
vec4 effect(vec2 uv) {
  float l = luma(getColor(uv - vec2(depth, 0.0)).rgb);
  float r = luma(getColor(uv + vec2(depth, 0.0)).rgb);
  return vec4(l, r * 0.85, r, 1.0);
}

//== id: macro | Macro | filter | Lens
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float d = length((uv - 0.5) * asp);
  float k = smoothstep(0.12, 0.45, d);
  vec2 px = 16.0 * k / uResolution;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 16; i++) {
    float a = float(i) * 2.39996;
    float r = sqrt(float(i) / 16.0);
    acc += getColor(0.5 + (uv - 0.5) * 0.85 + vec2(cos(a), sin(a)) * r * px).rgb;
  }
  acc /= 16.0;
  return vec4(mix(vec3(luma(acc)), acc, 1.2) * (1.0 - k * 0.15), 1.0);
}

//== id: lensbubble | Bubble Lens | filter | Lens
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float r = length(d);
  float R = 0.42;
  vec3 c;
  if (r < R) {
    float z = sqrt(R * R - r * r);
    vec2 q = 0.5 + d / asp * (0.55 + 0.45 * (1.0 - z / R));
    c = getColor(q).rgb * (0.85 + 0.25 * z / R);
    c += smoothstep(0.08, 0.0, length(d - vec2(-0.13, 0.15))) * 0.5;
  } else {
    c = getColor(uv).rgb * 0.45;
  }
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: wideangle | Wide Angle | filter | Lens
//== param: amount float 0.35 0.05 0.8
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float r2 = dot(d, d);
  vec2 q = 0.5 + d * (1.0 - amount * 0.5 + amount * r2 * 1.4) ;
  float v = 1.0 - smoothstep(0.35, 0.75, sqrt(r2)) * 0.4;
  return vec4(getColor(q).rgb * v, 1.0);
}

//== id: lensdirt2 | Smudged Lens | filter | Lens
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 px = 10.0 / uResolution;
  vec3 b = vec3(0.0);
  for (int i = 0; i < 12; i++) {
    float a = float(i) * 2.39996;
    float r = sqrt(float(i) / 12.0);
    b += getColor(uv + vec2(cos(a), sin(a)) * r * px).rgb;
  }
  b /= 12.0;
  float smudge = smoothstep(0.35, 0.75, noise(uv * 3.0) * 0.7 + noise(uv * 9.0) * 0.3);
  vec3 o = mix(c, b, smudge);
  o += b * smudge * 0.25;
  return vec4(min(o, vec3(1.0)), 1.0);
}

//== id: vignettecolor | Color Vignette | filter | Lens
//== param: tint color 0.4,0.0,0.6 0 1
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float v = smoothstep(0.3, 0.85, length((uv - 0.5) * vec2(1.0, 0.9)) * 1.25);
  return vec4(mix(c, c * 0.3 + tint * 0.6, v), 1.0);
}

//== id: microcontrast | Micro Contrast | filter | Detail
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  vec3 b = vec3(0.0);
  for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) b += getColor(uv + vec2(float(x), float(y)) * px * 1.5).rgb;
  b /= 25.0;
  vec3 d = c + (c - b) * 2.4;
  return vec4(clamp(d, 0.0, 1.0), 1.0);
}

//== id: dehaze | Dehaze | filter | Detail
//== param: amount float 0.6 0.1 1.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float dark = min(min(c.r, c.g), c.b);
  vec3 air = vec3(0.92);
  float t = max(1.0 - dark * 0.95 * amount, 0.25);
  vec3 o = (c - air) / t + air;
  o = mix(vec3(luma(o)), o, 1.1);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: shadowlift | Shadow Lift | filter | Detail
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  float lift = (1.0 - smoothstep(0.0, 0.5, l)) * 0.25;
  vec3 o = c + lift * (0.6 + c);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: highlightsave | Highlight Save | filter | Detail
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  float k = smoothstep(0.55, 1.0, l);
  vec3 o = mix(c, c * 0.78 + 0.1, k);
  o = mix(vec3(luma(o)), o, 1.12);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: texturepop | Texture | filter | Detail
vec4 effect(vec2 uv) {
  vec2 px = 2.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  vec3 b = (getColor(uv + vec2(px.x, px.y)).rgb + getColor(uv - vec2(px.x, px.y)).rgb + getColor(uv + vec2(px.x, -px.y)).rgb + getColor(uv - vec2(px.x, -px.y)).rgb) * 0.25;
  float l = luma(c);
  vec3 d = c + (c - b) * 3.0 * smoothstep(0.1, 0.4, l) * smoothstep(0.95, 0.6, l);
  return vec4(clamp(d, 0.0, 1.0), 1.0);
}

//== id: sharpenedge | Edge Sharpen | filter | Detail
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  vec3 n = getColor(uv + vec2(0.0, px.y)).rgb + getColor(uv - vec2(0.0, px.y)).rgb + getColor(uv + vec2(px.x, 0.0)).rgb + getColor(uv - vec2(px.x, 0.0)).rgb;
  vec3 d = c * 5.0 - n;
  return vec4(clamp(mix(c, d, 0.9), 0.0, 1.0), 1.0);
}

//== id: rgbshift | RGB Shift | filter | Lens
//== param: amount float 0.008 0.002 0.03
vec4 effect(vec2 uv) {
  return vec4(getColor(uv + vec2(amount, 0.0)).r, getColor(uv + vec2(0.0, amount * 0.5)).g, getColor(uv - vec2(amount, 0.0)).b, 1.0);
}

//== id: mirrorquad | Mirror Four | filter | Art
vec4 effect(vec2 uv) {
  // Each quarter holds the whole picture, mirrored so the four copies meet in the middle.
  vec2 q = 1.0 - abs(uv * 2.0 - 1.0);
  return getColor(q);
}

//== id: hexmirror | Hex Kaleido | filter | Art
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float a = atan(d.y, d.x);
  float r = length(d);
  float seg = PI / 3.0;
  a = mod(a, seg);
  a = abs(a - seg * 0.5);
  vec2 q = vec2(cos(a), sin(a)) * r / asp + 0.5;
  return getColor(q);
}

//== id: droste | Picture in Picture | filter | Art
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float m = max(abs(d.x), abs(d.y));
  float level = floor(log2(0.5 / max(m, 0.01)));
  level = min(level, 4.0);
  vec2 q = 0.5 + d * pow(2.0, level);
  vec3 c = getColor(q).rgb;
  vec2 f = abs(q - 0.5);
  float frame = step(0.485, max(f.x, f.y)) * step(0.5, level + 0.5);
  return vec4(mix(c, vec3(1.0), frame), 1.0);
}

//== id: splitcolor | Split Color | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 o = uv.x < 0.5 ? c : vec3(l);
  float line = smoothstep(0.003, 0.0, abs(uv.x - 0.5));
  return vec4(mix(o, vec3(1.0), line), 1.0);
}

//== id: redonly | Keep Red | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 h = rgb2hsv(c);
  float red = smoothstep(0.1, 0.02, min(h.x, 1.0 - h.x)) * smoothstep(0.2, 0.4, h.y);
  return vec4(mix(vec3(luma(c)), c, red), 1.0);
}

//== id: blueonly | Keep Blue | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 h = rgb2hsv(c);
  float blue = smoothstep(0.12, 0.04, abs(h.x - 0.6)) * smoothstep(0.15, 0.35, h.y);
  return vec4(mix(vec3(luma(c)), c, blue), 1.0);
}

//== id: greenonly | Keep Green | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 h = rgb2hsv(c);
  float g = smoothstep(0.12, 0.04, abs(h.x - 0.33)) * smoothstep(0.15, 0.35, h.y);
  return vec4(mix(vec3(luma(c)), c, g), 1.0);
}

//== id: yellowonly | Keep Yellow | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec3 h = rgb2hsv(c);
  float y = smoothstep(0.08, 0.03, abs(h.x - 0.14)) * smoothstep(0.2, 0.4, h.y);
  return vec4(mix(vec3(luma(c)), c, y), 1.0);
}

//== id: hueinvert | Hue Flip | filter | Color
vec4 effect(vec2 uv) {
  vec3 h = rgb2hsv(getColor(uv).rgb);
  h.x = fract(h.x + 0.5);
  return vec4(hsv2rgb(h), 1.0);
}

//== id: channelswap | Channel Swap | filter | Color
vec4 effect(vec2 uv) {
  return vec4(getColor(uv).brg, 1.0);
}

//== id: lavender | Lavender | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 o = mix(c, vec3(l) * vec3(0.95, 0.85, 1.1), 0.55) + vec3(0.04, 0.02, 0.07);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: emerald | Emerald | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 o = mix(vec3(0.0, 0.12, 0.08), vec3(0.75, 1.0, 0.85), smoothstep(0.0, 1.0, l));
  return vec4(mix(c, o, 0.6), 1.0);
}

//== id: crimson | Crimson | filter | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 o = mix(vec3(0.15, 0.0, 0.03), vec3(1.0, 0.75, 0.7), smoothstep(0.0, 1.0, l));
  return vec4(mix(c, o, 0.6), 1.0);
}

// ---------------------------------------------------------------- effects, batch 4

//== id: hearts2 | Floating Hearts | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 10; i++) {
    float fi = float(i);
    float sp = 0.12 + rand(vec2(fi, 1.0)) * 0.15;
    vec2 p = vec2(rand(vec2(fi, 2.0)) + sin(uTime * 1.3 + fi) * 0.03, fract(rand(vec2(fi, 3.0)) + uTime * sp) * 1.3 - 0.15);
    if (abs(uv.x - p.x) > 0.12 || abs(uv.y - p.y) > 0.14) continue;
    vec2 d = (uv - p) * asp / (0.07 + rand(vec2(fi, 4.0)) * 0.06);
    d.y = -d.y + 0.3;
    float a = atan(d.x, d.y) / PI;
    float r = length(d);
    float h = abs(a);
    float shape = (13.0 * h - 22.0 * h * h + 10.0 * h * h * h) / (6.0 - 5.0 * h);
    float m = smoothstep(0.05, 0.0, r - shape * 0.5);
    c = mix(c, vec3(1.0, 0.25 + 0.2 * rand(vec2(fi, 5.0)), 0.45), m * 0.9);
  }
  return vec4(c, 1.0);
}

//== id: sparkletrail | Sparkle Trail | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 14; i++) {
    float fi = float(i);
    float t = uTime * 0.8 - fi * 0.04;
    vec2 p = vec2(0.5 + 0.38 * sin(t * 1.7), 0.5 + 0.32 * sin(t * 2.3 + 1.0));
    float d = length((uv - p) * asp);
    float s = (1.0 - fi / 14.0);
    c += vec3(1.0, 0.9, 0.6) * 0.0009 / (d * d + 0.0009) * s * 0.6;
  }
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: lensflare2 | Sun Flare | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 sun = vec2(0.5 + 0.35 * sin(uTime * 0.5), 0.8);
  vec2 d = (uv - sun) * asp;
  float core = 0.02 / (length(d) + 0.02);
  vec3 f = vec3(1.0, 0.85, 0.6) * core * 0.7;
  vec2 axis = (vec2(0.5) - sun);
  for (int i = 1; i < 6; i++) {
    float fi = float(i);
    vec2 g = sun + axis * fi * 0.4;
    float r = 0.02 + fi * 0.012;
    float ring = smoothstep(r, r * 0.6, length((uv - g) * asp));
    f += hsv2rgb(vec3(0.08 * fi, 0.6, 1.0)) * ring * 0.18;
  }
  return vec4(min(c + f, vec3(1.0)), 1.0);
}

//== id: discoball2 | Spotlights | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * 0.45;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 4; i++) {
    float fi = float(i);
    vec2 p = vec2(0.5 + 0.4 * sin(uTime * (0.7 + fi * 0.2) + fi * 2.0), 0.5 + 0.35 * cos(uTime * (0.9 + fi * 0.15) + fi));
    float d = length((uv - p) * asp);
    vec3 col = hsv2rgb(vec3(fi * 0.25 + uTime * 0.05, 0.6, 1.0));
    c += getColor(uv).rgb * col * smoothstep(0.22, 0.1, d) * 0.8;
  }
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: glitchwave | Glitch Wave | effect | Glitch
vec4 effect(vec2 uv) {
  float w = sin(uv.y * 30.0 + uTime * 8.0) * 0.008 + sin(uv.y * 7.0 - uTime * 3.0) * 0.012;
  float burst = step(0.8, rand(vec2(floor(uTime * 6.0), 1.0)));
  w *= 1.0 + burst * 3.0;
  return vec4(getColor(uv + vec2(w * 1.3, 0.0)).r, getColor(uv + vec2(w, 0.0)).g, getColor(uv + vec2(w * 0.7, 0.0)).b, 1.0);
}

//== id: pixeldrift | Pixel Drift | effect | Glitch
vec4 effect(vec2 uv) {
  float size = 4.0 + 20.0 * (0.5 + 0.5 * sin(uTime * 2.0));
  vec2 cells = uResolution / size;
  vec2 q = (floor(uv * cells) + 0.5) / cells;
  float drift = (rand(vec2(floor(uv.y * cells.y), floor(uTime * 4.0))) - 0.5) * 0.03;
  return getColor(q + vec2(drift, 0.0));
}

//== id: corrupt | Corrupt | effect | Glitch
vec4 effect(vec2 uv) {
  float t = floor(uTime * 10.0);
  vec2 block = floor(uv * vec2(8.0, 20.0));
  float r = rand(block + t * 0.37);
  vec2 q = uv;
  if (r > 0.92) q.x = fract(q.x + (rand(block + t) - 0.5) * 0.4);
  vec3 c = getColor(q).rgb;
  if (r > 0.97) c = vec3(step(0.5, c.r), step(0.5, c.g), step(0.5, c.b));
  if (r < 0.02) c = 1.0 - c;
  return vec4(c, 1.0);
}

//== id: tvstatic | TV Static | effect | Glitch
//== param: amount float 0.35 0.05 1.0
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float n = rand(uv * uResolution + fract(uTime * 37.0) * 100.0);
  float band = smoothstep(0.1, 0.0, abs(fract(uv.y - uTime * 0.3) - 0.5)) * 0.4;
  return vec4(mix(c, vec3(n), amount + band), 1.0);
}

//== id: hologlitch | Holo Glitch | effect | Glitch
vec4 effect(vec2 uv) {
  float line = sin(uv.y * uResolution.y * 0.8 + uTime * 20.0) * 0.5 + 0.5;
  float j = step(0.9, rand(vec2(floor(uTime * 15.0), floor(uv.y * 30.0)))) * 0.03;
  vec3 c = getColor(uv + vec2(j, 0.0)).rgb;
  float l = luma(c);
  vec3 holo = vec3(0.2, 0.9, 1.0) * (l * 1.2 + 0.1) * (0.75 + 0.25 * line);
  return vec4(mix(c, holo, 0.7), 1.0);
}

//== id: framedrop | Frame Drop | effect | Glitch
vec4 effect(vec2 uv) {
  float t = floor(uTime * 5.0);
  float frozen = step(0.6, rand(vec2(t, 2.0)));
  vec2 q = uv + vec2((rand(vec2(t, 4.0)) - 0.5) * 0.02, 0.0) * frozen;
  vec3 c = getColor(q).rgb;
  c = mix(c, floor(c * 8.0) / 8.0, frozen);
  return vec4(c * (1.0 - 0.15 * frozen), 1.0);
}

//== id: zoomwobble | Zoom Wobble | effect | Motion
vec4 effect(vec2 uv) {
  float z = 1.08 + 0.06 * sin(uTime * 2.5);
  float a = 0.03 * sin(uTime * 1.7);
  vec2 d = (uv - 0.5) / z;
  vec2 q = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y) + 0.5;
  return getColor(q);
}

//== id: punchzoom | Punch Zoom | effect | Motion
vec4 effect(vec2 uv) {
  float beat = fract(uTime * 1.2);
  float z = 1.0 + 0.15 * exp(-beat * 8.0);
  return getColor(0.5 + (uv - 0.5) / z);
}

//== id: slowzoomout | Slow Zoom Out | effect | Motion
vec4 effect(vec2 uv) {
  float p = clamp(uTime / max(uDuration, 0.5), 0.0, 1.0);
  float z = mix(1.25, 1.0, p);
  return getColor(0.5 + (uv - 0.5) / z);
}

//== id: slowzoomin | Slow Zoom In | effect | Motion
vec4 effect(vec2 uv) {
  float p = clamp(uTime / max(uDuration, 0.5), 0.0, 1.0);
  float z = mix(1.0, 1.25, p * p * (3.0 - 2.0 * p));
  return getColor(0.5 + (uv - 0.5) / z);
}

//== id: vertpan | Tilt Pan | effect | Motion
vec4 effect(vec2 uv) {
  float y = 0.12 * sin(uTime * 0.8);
  return getColor(0.5 + (uv - 0.5) / 1.3 + vec2(0.0, y));
}

//== id: rockfx | Rock | effect | Motion
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float a = 0.06 * sin(uTime * 3.0);
  vec2 d = (uv - vec2(0.5, 0.0)) * asp / 1.12;
  vec2 q = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y) / asp + vec2(0.5, 0.04);
  return getColor(q);
}

//== id: tremble | Tremble | effect | Motion
vec4 effect(vec2 uv) {
  vec2 o = (vec2(noise(vec2(uTime * 30.0, 1.0)), noise(vec2(uTime * 30.0, 5.0))) - 0.5) * 0.012;
  return getColor(0.5 + (uv - 0.5) / 1.03 + o);
}

//== id: bassdrop | Bass Drop | effect | Motion
vec4 effect(vec2 uv) {
  float beat = fract(uTime * 2.0);
  float k = exp(-beat * 10.0);
  vec2 q = 0.5 + (uv - 0.5) / (1.0 + k * 0.1);
  q += vec2(0.0, k * 0.02 * sin(uTime * 60.0));
  vec3 c = vec3(getColor(q + vec2(k * 0.01, 0.0)).r, getColor(q).g, getColor(q - vec2(k * 0.01, 0.0)).b);
  return vec4(c * (1.0 + k * 0.2), 1.0);
}

//== id: rotatebeat | Beat Tilt | effect | Motion
vec4 effect(vec2 uv) {
  float b = floor(uTime * 2.0);
  float a = (mod(b, 2.0) * 2.0 - 1.0) * 0.06 * exp(-fract(uTime * 2.0) * 5.0);
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp / 1.1;
  vec2 q = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y) / asp + 0.5;
  return getColor(q);
}

//== id: motionecho | Motion Echo | effect | Motion
vec4 effect(vec2 uv) {
  vec3 acc = vec3(0.0);
  float w = 0.0;
  for (int i = 0; i < 6; i++) {
    float fi = float(i);
    vec2 o = vec2(sin(uTime * 2.0 - fi * 0.15), cos(uTime * 1.5 - fi * 0.15)) * 0.02;
    float k = 1.0 - fi / 6.0;
    acc += getColor(uv + o).rgb * k;
    w += k;
  }
  return vec4(acc / w, 1.0);
}

//== id: rainheavy | Downpour | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * vec3(0.8, 0.85, 0.92);
  float s = 0.0;
  for (int l = 0; l < 3; l++) {
    float fl = float(l);
    vec2 p = vec2(uv.x * (120.0 + fl * 60.0) + uv.y * 10.0, uv.y * (2.0 + fl) + uTime * (8.0 + fl * 3.0));
    float col = floor(p.x);
    float d = fract(p.y + rand(vec2(col, fl)) * 10.0);
    s += smoothstep(0.0, 0.05, d) * smoothstep(0.2, 0.05, d) * step(0.55, rand(vec2(col, fl + 4.0))) * smoothstep(0.5, 0.3, abs(fract(p.x) - 0.5));
  }
  return vec4(min(c + s * 0.3, vec3(1.0)), 1.0);
}

//== id: hail | Hail | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 p = uv * asp * 20.0 + vec2(0.0, uTime * 18.0);
  vec2 cell = floor(p);
  vec2 f = fract(p) - 0.5;
  vec2 o = vec2(rand(cell), rand(cell + 1.0)) - 0.5;
  float h = smoothstep(0.1, 0.05, length(f - o * 0.6)) * step(0.7, rand(cell + 3.0));
  return vec4(mix(c * 0.9, vec3(0.95, 0.97, 1.0), h), 1.0);
}

//== id: sunbeams | Sunbeams | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 src = vec2(0.2, 1.1);
  vec2 d = uv - src;
  float a = atan(d.x, -d.y);
  float rays = noise(vec2(a * 12.0, uTime * 0.4)) * noise(vec2(a * 30.0, uTime * 0.2 + 3.0));
  float fall = smoothstep(1.4, 0.2, length(d));
  return vec4(min(c + vec3(1.0, 0.9, 0.7) * rays * fall * 0.6, vec3(1.0)), 1.0);
}

//== id: fogroll | Rolling Fog | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 q = uv * vec2(3.0, 2.0) + vec2(uTime * 0.12, 0.0);
  float f = noise(q) * 0.5 + noise(q * 2.1 + uTime * 0.05) * 0.3 + noise(q * 4.3) * 0.2;
  float k = smoothstep(0.3, 0.8, f) * smoothstep(1.0, 0.2, uv.y);
  return vec4(mix(c, vec3(0.85, 0.87, 0.9), k * 0.75), 1.0);
}

//== id: snowlight | Light Snow | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  float s = 0.0;
  for (int l = 0; l < 2; l++) {
    float sc = 10.0 + float(l) * 8.0;
    vec2 p = uv * asp * sc + vec2(sin(uTime * 0.6 + float(l)) * 0.5, uTime * (0.6 + float(l) * 0.3));
    vec2 cell = floor(p);
    vec2 f = fract(p) - 0.5;
    vec2 o = vec2(rand(cell + 4.0), rand(cell + 8.0)) - 0.5;
    s += smoothstep(0.06, 0.0, length(f - o * 0.6)) * step(0.5, rand(cell + 11.0));
  }
  return vec4(min(c + s * 0.9, vec3(1.0)), 1.0);
}

//== id: thunder | Thunder | effect | Weather
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * vec3(0.6, 0.65, 0.8);
  float t = uTime * 0.7;
  float flash = step(0.85, rand(vec2(floor(t * 3.0), 1.0))) * (1.0 - fract(t * 3.0));
  float x = 0.3 + rand(vec2(floor(t * 3.0), 2.0)) * 0.4 + (noise(vec2(uv.y * 20.0, floor(t * 3.0))) - 0.5) * 0.1;
  float bolt = 0.002 / (abs(uv.x - x) + 0.002) * step(0.45, uv.y) * flash;
  return vec4(min(c + flash * 0.5 + vec3(0.8, 0.85, 1.0) * bolt, vec3(1.0)), 1.0);
}

//== id: confetti2 | Streamers | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 18; i++) {
    float fi = float(i);
    float x = rand(vec2(fi, 1.0));
    float y = fract(1.0 - (uTime * (0.15 + rand(vec2(fi, 2.0)) * 0.2) + rand(vec2(fi, 3.0))));
    if (abs(uv.x - x) > 0.03 || uv.y - y < 0.0 || uv.y - y > 0.12) continue;
    float wx = x + sin(uTime * 3.0 + fi + uv.y * 20.0) * 0.015;
    float d = abs((uv.x - wx) * asp.x);
    float len = smoothstep(0.0, 0.02, uv.y - y) * smoothstep(0.12, 0.08, uv.y - y);
    float m = smoothstep(0.006, 0.002, d) * len;
    c = mix(c, hsv2rgb(vec3(rand(vec2(fi, 4.0)), 0.75, 1.0)), m);
  }
  return vec4(c, 1.0);
}

//== id: popcorn | Pop | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 8; i++) {
    float fi = float(i);
    float t = fract(uTime * 0.8 + rand(vec2(fi, 1.0)));
    vec2 p = vec2(rand(vec2(fi, floor(uTime * 0.8 + rand(vec2(fi, 1.0))))), rand(vec2(fi + 9.0, floor(uTime * 0.8 + rand(vec2(fi, 1.0))))));
    float r = t * 0.15;
    float ring = smoothstep(0.012, 0.0, abs(length((uv - p) * asp) - r)) * (1.0 - t);
    c += hsv2rgb(vec3(rand(vec2(fi, 7.0)), 0.6, 1.0)) * ring;
  }
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: rainbowspin | Rainbow Swirl | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 d = (uv - 0.5) * vec2(uResolution.x / uResolution.y, 1.0);
  float a = atan(d.y, d.x) / (2.0 * PI) + uTime * 0.2 + length(d);
  vec3 rb = hsv2rgb(vec3(fract(a), 0.7, 1.0));
  return vec4(mix(c, c * rb * 1.5, 0.5), 1.0);
}

//== id: smiley | Glow Rings | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  for (int i = 0; i < 5; i++) {
    float fi = float(i);
    vec2 p = vec2(0.15 + fi * 0.18, 0.5 + 0.3 * sin(uTime * 2.0 + fi));
    float d = length((uv - p) * asp);
    c += hsv2rgb(vec3(fi * 0.2, 0.7, 1.0)) * smoothstep(0.012, 0.0, abs(d - 0.06)) * 0.9;
  }
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: vhsplay | VHS Play | effect | Retro
vec4 effect(vec2 uv) {
  float row = floor(uv.y * 240.0);
  float j = (noise(vec2(row * 0.1, uTime * 5.0)) - 0.5) * 0.006;
  vec2 q = uv + vec2(j, 0.0);
  vec3 c = vec3(getColor(q + vec2(0.004, 0.0)).r, getColor(q).g, getColor(q - vec2(0.004, 0.0)).b);
  c = mix(vec3(luma(c)), c, 0.8) * vec3(1.02, 0.98, 1.04);
  float track = smoothstep(0.02, 0.0, abs(fract(uv.y * 0.5 + uTime * 0.07) - 0.95)) * 0.4;
  c += (rand(uv * uResolution + uTime) - 0.5) * 0.08 + track * rand(vec2(uv.x * 100.0, uTime));
  float play = step(0.04, uv.x) * step(uv.x, 0.12) * step(0.86, uv.y) * step(uv.y, 0.94) * step(0.5, fract(uTime * 0.7));
  return vec4(clamp(c + play * 0.6, 0.0, 1.0), 1.0);
}

//== id: oldtv2 | Tube TV | effect | Retro
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  vec2 q = 0.5 + d * (1.0 + dot(d, d) * 0.35);
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  vec3 c = getColor(q).rgb;
  float scan = 0.8 + 0.2 * sin(q.y * uResolution.y * 1.4);
  float mask = 0.85 + 0.15 * sin(q.x * uResolution.x * 2.1);
  float roll = 0.95 + 0.05 * sin((q.y - uTime * 0.3) * 6.0);
  float v = smoothstep(0.75, 0.35, length(d));
  return vec4(c * scan * mask * roll * v * inside * 1.15, 1.0);
}

//== id: projector2 | Home Movie | effect | Retro
vec4 effect(vec2 uv) {
  float t = floor(uTime * 18.0);
  vec2 q = uv + vec2(rand(vec2(t, 1.0)) - 0.5, rand(vec2(t, 2.0)) - 0.5) * 0.004;
  vec3 c = getColor(q).rgb;
  c = mix(vec3(luma(c)), c, 0.75) * vec3(1.08, 1.0, 0.85);
  c *= 0.9 + 0.1 * rand(vec2(t, 3.0));
  float v = smoothstep(0.9, 0.3, length((uv - 0.5) * vec2(1.2, 1.0)));
  float hair = smoothstep(0.003, 0.0, abs(uv.x - 0.7 - sin(uv.y * 8.0) * 0.02)) * step(0.7, rand(vec2(floor(uTime * 2.0), 9.0)));
  c += (rand(uv * uResolution + t) - 0.5) * 0.1;
  return vec4(clamp(c * v - hair * 0.5, 0.0, 1.0), 1.0);
}

//== id: gameboy2 | Pixel Game | effect | Retro
vec4 effect(vec2 uv) {
  vec2 cells = vec2(160.0, 144.0);
  vec2 q = (floor(uv * cells) + 0.5) / cells;
  float l = luma(getColor(q).rgb);
  l += (rand(floor(uv * cells)) - 0.5) * 0.08;
  float lv = floor(clamp(l, 0.0, 0.999) * 4.0);
  vec3 c = vec3(0.06, 0.22, 0.06);
  c = mix(c, vec3(0.19, 0.38, 0.19), step(1.0, lv));
  c = mix(c, vec3(0.55, 0.67, 0.06), step(2.0, lv));
  c = mix(c, vec3(0.61, 0.74, 0.06), step(3.0, lv));
  c *= 0.95 + 0.05 * sin(uTime * 12.0);
  return vec4(c, 1.0);
}

//== id: dvd | Bouncing Logo | effect | Retro
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * 0.85;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 sz = vec2(0.18, 0.1);
  vec2 room = 1.0 - sz;
  vec2 tt = uTime * vec2(0.23, 0.17);
  vec2 p = abs(mod(tt, 2.0 * room) - room) + sz * 0.5;
  vec2 d = abs(uv - p) / sz;
  float box = step(max(d.x, d.y), 0.5);
  float hit = floor(tt.x / room.x) + floor(tt.y / room.y);
  vec3 col = hsv2rgb(vec3(fract(hit * 0.27), 0.8, 1.0));
  float ellipse = smoothstep(0.52, 0.46, length((uv - p) / sz * vec2(1.0, 1.6)));
  return vec4(mix(c, col, box * ellipse), 1.0);
}

//== id: lomopulse | Lomo Pulse | effect | Retro
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  c = mix(vec3(luma(c)), c, 1.3);
  float v = smoothstep(0.75 + 0.1 * sin(uTime * 2.0), 0.25, length(uv - 0.5));
  c = c * mix(0.35, 1.08, v);
  c = c * vec3(1.05, 1.0, 0.92);
  return vec4(clamp(c, 0.0, 1.0), 1.0);
}

//== id: lightbars | Light Bars | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float bars = 0.0;
  for (int i = 0; i < 3; i++) {
    float fi = float(i);
    float x = fract(uTime * (0.15 + fi * 0.07) + fi * 0.33) * 1.6 - 0.3;
    bars += exp(-pow(abs(uv.x + uv.y * 0.3 - x) * 18.0, 2.0)) * (0.6 - fi * 0.15);
  }
  return vec4(min(c + bars * vec3(1.0, 0.95, 0.85) * 0.6, vec3(1.0)), 1.0);
}

//== id: rimlight | Rim Light | effect | Light
vec4 effect(vec2 uv) {
  vec2 px = 2.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  vec2 dir = vec2(cos(uTime), sin(uTime));
  float l0 = luma(c);
  float l1 = luma(getColor(uv - dir * px * 3.0).rgb);
  float rim = clamp((l0 - l1) * 6.0, 0.0, 1.0);
  return vec4(min(c + vec3(1.0, 0.85, 0.6) * rim * 0.8, vec3(1.0)), 1.0);
}

//== id: glowbreath | Soft Glow Pulse | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 px = 6.0 / uResolution;
  vec3 b = vec3(0.0);
  for (int i = 0; i < 12; i++) {
    float a = float(i) * 2.39996;
    float r = sqrt(float(i) / 12.0);
    b += getColor(uv + vec2(cos(a), sin(a)) * r * px).rgb;
  }
  b /= 12.0;
  float k = 0.5 + 0.5 * sin(uTime * 2.0);
  return vec4(1.0 - (1.0 - c) * (1.0 - b * b * k * 0.9), 1.0);
}

//== id: prismrain | Prism Light | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float x = uv.x * 1.5 + uv.y * 0.5 - uTime * 0.2;
  float band = smoothstep(0.15, 0.0, abs(fract(x) - 0.5) - 0.2);
  vec3 rb = hsv2rgb(vec3(fract(x * 2.0), 0.6, 1.0));
  return vec4(min(c + rb * band * 0.25, vec3(1.0)), 1.0);
}

//== id: flashlight | Flashlight | effect | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 p = vec2(0.5 + 0.3 * sin(uTime * 0.9), 0.5 + 0.25 * sin(uTime * 1.3));
  float d = length((uv - p) * asp);
  float lit = smoothstep(0.32, 0.12, d);
  return vec4(c * (0.12 + lit * 1.0), 1.0);
}

//== id: club | Club Lights | effect | Party
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float t = floor(uTime * 4.0);
  vec3 col = hsv2rgb(vec3(rand(vec2(t, 1.0)), 0.8, 1.0));
  vec2 d = uv - vec2(rand(vec2(t, 2.0)), 1.1);
  float a = atan(d.x, -d.y);
  float beam = smoothstep(0.08, 0.0, abs(a - sin(uTime * 2.0) * 0.6));
  return vec4(min(c * 0.6 + c * col * 0.4 + col * beam * 0.5, vec3(1.0)), 1.0);
}

//== id: glitchcolor | Color Glitch | effect | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float t = floor(uTime * 8.0);
  float band = step(rand(vec2(t, 1.0)), uv.y) * step(uv.y, rand(vec2(t, 1.0)) + 0.2);
  vec3 h = rgb2hsv(c);
  h.x = fract(h.x + band * rand(vec2(t, 3.0)));
  return vec4(hsv2rgb(h), 1.0);
}

//== id: tonecycle | Tone Cycle | effect | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float l = luma(c);
  vec3 tone = 0.5 + 0.5 * cos(6.2831 * (l + uTime * 0.2 + vec3(0.0, 0.33, 0.67)));
  return vec4(mix(c, tone * (0.3 + l), 0.55), 1.0);
}

//== id: warmcool | Warm to Cool | effect | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float k = 0.5 + 0.5 * sin(uTime * 1.2);
  vec3 o = c * mix(vec3(1.15, 1.0, 0.82), vec3(0.85, 1.0, 1.18), k);
  return vec4(clamp(o, 0.0, 1.0), 1.0);
}

//== id: posterpulse | Poster Pulse | effect | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float lv = 2.0 + floor(4.0 * (0.5 + 0.5 * sin(uTime * 3.0)));
  return vec4(floor(c * lv + 0.5) / lv, 1.0);
}

//== id: invertflash | Invert Flash | effect | Color
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float on = step(0.75, fract(uTime * 1.5));
  return vec4(mix(c, 1.0 - c, on), 1.0);
}

//== id: lenswipe | Lens Wipe | effect | Lens
vec4 effect(vec2 uv) {
  float x = fract(uTime * 0.35) * 1.6 - 0.3;
  float k = smoothstep(0.25, 0.0, abs(uv.x - x));
  vec2 px = 12.0 * k / uResolution;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 12; i++) {
    float a = float(i) * 2.39996;
    float r = sqrt(float(i) / 12.0);
    acc += getColor(uv + vec2(cos(a), sin(a)) * r * px).rgb;
  }
  return vec4(acc / 12.0, 1.0);
}

//== id: fisheyepulse | Fisheye Pulse | effect | Lens
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float r = length(d);
  float k = 0.5 + 0.5 * sin(uTime * 2.5);
  vec2 q = 0.5 + d * mix(1.0, 0.6 + r * 0.9, k);
  return getColor(q);
}

//== id: depthblur | Depth Breathe | effect | Lens
vec4 effect(vec2 uv) {
  float k = 0.5 + 0.5 * sin(uTime * 1.5);
  float edge = smoothstep(0.15, 0.55, length(uv - 0.5));
  vec2 px = 10.0 * edge * k / uResolution;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 12; i++) {
    float a = float(i) * 2.39996;
    float r = sqrt(float(i) / 12.0);
    acc += getColor(uv + vec2(cos(a), sin(a)) * r * px).rgb;
  }
  return vec4(acc / 12.0, 1.0);
}

//== id: chromawobble | Chroma Wobble | effect | Lens
vec4 effect(vec2 uv) {
  vec2 d = uv - 0.5;
  float k = 0.012 * sin(uTime * 3.0);
  return vec4(getColor(0.5 + d * (1.0 + k)).r, getColor(uv).g, getColor(0.5 + d * (1.0 - k)).b, 1.0);
}

//== id: mirrorwave | Mirror Wave | effect | Lens
vec4 effect(vec2 uv) {
  vec2 q = uv;
  q.x = 0.5 - abs(uv.x - 0.5);
  q.y += sin(q.x * 12.0 + uTime * 3.0) * 0.02;
  return getColor(q);
}

//== id: digitalrain2 | Glyph Rain | effect | Glitch
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb * 0.35;
  vec2 cells = vec2(48.0, 30.0);
  vec2 cell = floor(uv * cells);
  float speed = 0.5 + rand(vec2(cell.x, 1.0)) * 1.5;
  float head = fract(-uTime * speed * 0.3 + rand(vec2(cell.x, 2.0)));
  float y = 1.0 - cell.y / cells.y;
  float tail = fract(y - head);
  float glyph = step(0.4, rand(cell + floor(uTime * 8.0)));
  vec2 f = fract(uv * cells);
  float px = step(0.15, f.x) * step(f.x, 0.85) * step(0.1, f.y) * step(f.y, 0.9);
  float b = smoothstep(0.4, 0.0, tail) * glyph * px;
  return vec4(c + vec3(0.2, 1.0, 0.35) * b + vec3(0.8) * step(tail, 0.02) * px, 1.0);
}

// ---------------------------------------------------------------- clip animations, batch 4

//== id: anim_zoomblur | Zoom blur in | anim | Basic
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 10; i++) {
    float s = 1.0 + k * 0.6 * float(i) / 9.0;
    acc += getColor(0.5 + (uv - 0.5) / s).rgb;
  }
  return vec4(acc / 10.0 * min(1.0, uProgress * 1.6 + 0.1), 1.0);
}

//== id: anim_spinin | Spin in | anim | Basic
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  float e = k * k;
  float a = e * 6.2831;
  float s = max(1.0 - e, 0.001);
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp / s;
  vec2 q = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y) / asp + 0.5;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: anim_fall | Fall in | anim | Slide
vec4 effect(vec2 uv) {
  float p = uProgress;
  float b = 1.0 - abs(cos(p * PI * 1.5)) * (1.0 - p);
  vec2 q = uv - vec2(0.0, (1.0 - b) * 0.9);
  float inside = step(0.0, q.y);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: anim_peek | Peek | anim | Slide
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  k = k * k * k;
  vec2 q = uv + vec2(0.0, -k);
  float inside = step(0.0, q.y);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: anim_slidebounce | Slide bounce | anim | Slide
vec4 effect(vec2 uv) {
  float p = uProgress;
  float e = 1.0 - pow(2.0, -8.0 * p) * cos(p * 10.0);
  if (p >= 1.0) e = 1.0;
  vec2 q = uv + vec2(1.0 - e, 0.0);
  float inside = step(q.x, 1.0) * step(0.0, q.x);
  return vec4(getColor(clamp(q, 0.0, 1.0)).rgb * inside, 1.0);
}

//== id: anim_rgbsplit | RGB split in | anim | Light
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  vec2 o = vec2(0.08 * k, 0.0);
  vec3 c = vec3(getColor(uv + o).r, getColor(uv).g, getColor(uv - o).b);
  return vec4(c * min(1.0, uProgress * 2.0), 1.0);
}

//== id: anim_neon | Neon in | anim | Light
vec4 effect(vec2 uv) {
  vec2 px = 1.0 / uResolution;
  vec3 c = getColor(uv).rgb;
  float gx = luma(getColor(uv + vec2(px.x, 0.0)).rgb) - luma(getColor(uv - vec2(px.x, 0.0)).rgb);
  float gy = luma(getColor(uv + vec2(0.0, px.y)).rgb) - luma(getColor(uv - vec2(0.0, px.y)).rgb);
  float e = clamp(length(vec2(gx, gy)) * 6.0, 0.0, 1.0);
  float k = smoothstep(0.0, 0.6, uProgress);
  float fill = smoothstep(0.5, 1.0, uProgress);
  vec3 neon = vec3(0.2, 0.9, 1.0) * e * k;
  return vec4(mix(neon + c * 0.1 * k, c, fill), 1.0);
}

//== id: anim_sunrise | Sunrise in | anim | Light
vec4 effect(vec2 uv) {
  vec3 c = getColor(uv).rgb;
  float edge = 1.0 - uProgress * 1.3;
  float shown = smoothstep(edge + 0.15, edge - 0.15, 1.0 - uv.y);
  vec3 warm = c * vec3(1.2, 0.9, 0.6);
  vec3 o = mix(warm * 0.15, mix(warm, c, uProgress), shown);
  if (uProgress >= 1.0) o = c;
  return vec4(o, 1.0);
}

//== id: anim_shutter | Shutter | anim | Shape
vec4 effect(vec2 uv) {
  float open = uProgress * 0.5;
  float shown = step(abs(uv.y - 0.5), open);
  if (uProgress >= 1.0) shown = 1.0;
  return vec4(getColor(uv).rgb * shown, 1.0);
}

//== id: anim_circles | Circles | anim | Shape
vec4 effect(vec2 uv) {
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 g = uv * asp * 6.0;
  vec2 f = fract(g) - 0.5;
  float delay = rand(floor(g)) * 0.5;
  float r = clamp((uProgress - delay) / 0.5, 0.0, 1.0) * 0.75;
  float shown = step(length(f), r);
  if (uProgress >= 1.0) shown = 1.0;
  return vec4(getColor(uv).rgb * shown, 1.0);
}

//== id: anim_stripesin | Stripes | anim | Shape
vec4 effect(vec2 uv) {
  float i = floor(uv.y * 12.0);
  float dir = mod(i, 2.0) * 2.0 - 1.0;
  float k = 1.0 - uProgress;
  vec2 q = uv + vec2(dir * k * k, 0.0);
  float inside = step(0.0, q.x) * step(q.x, 1.0);
  return vec4(getColor(clamp(q, 0.0, 1.0)).rgb * inside, 1.0);
}

//== id: anim_corner | Corner | anim | Shape
vec4 effect(vec2 uv) {
  float r = uProgress * 1.45;
  float shown = step(length(uv), r);
  if (uProgress >= 1.0) shown = 1.0;
  return vec4(getColor(uv).rgb * shown, 1.0);
}

//== id: anim_typewriter | Scan in | anim | Basic
vec4 effect(vec2 uv) {
  float rows = 12.0;
  float row = floor((1.0 - uv.y) * rows);
  float pos = uProgress * rows;
  float shown = step(row + 1.0, pos) + step(row, pos) * step(pos, row + 1.0) * step(uv.x, fract(pos));
  if (uProgress >= 1.0) shown = 1.0;
  return vec4(getColor(uv).rgb * min(shown, 1.0), 1.0);
}

//== id: anim_mosaic | Mosaic in | anim | Basic
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  float size = 1.0 + k * k * 60.0;
  vec2 cells = uResolution / size;
  vec2 q = (floor(uv * cells) + 0.5) / cells;
  if (uProgress >= 1.0) q = uv;
  return vec4(getColor(q).rgb * min(1.0, uProgress * 3.0), 1.0);
}

//== id: anim_jelly | Jelly | anim | Basic
vec4 effect(vec2 uv) {
  float p = uProgress;
  float w = pow(2.0, -8.0 * p) * sin(p * 25.0) * 0.25;
  vec2 sc = vec2(max(p * 0.5 + 0.5 + w, 0.001), max(p * 0.5 + 0.5 - w, 0.001));
  if (p >= 1.0) sc = vec2(1.0);
  vec2 q = 0.5 + (uv - 0.5) / sc;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside, 1.0);
}

//== id: anim_3dturn | Turn in | anim | Basic
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  float a = k * 1.4;
  float c = cos(a);
  float x = (uv.x - 0.5) / max(c, 0.05);
  float persp = 1.0 + sin(a) * x * 0.4;
  vec2 q = vec2(x + 0.5, (uv.y - 0.5) * persp + 0.5);
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside * (0.5 + 0.5 * c), 1.0);
}

//== id: anim_swirlin | Swirl in | anim | Basic
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  vec2 asp = vec2(uResolution.x / uResolution.y, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float a = k * k * 8.0 * smoothstep(0.7, 0.0, length(d));
  vec2 q = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y) / asp + 0.5;
  return vec4(getColor(q).rgb * min(1.0, uProgress * 2.0), 1.0);
}

//== id: anim_glitchblock | Block glitch in | anim | Light
vec4 effect(vec2 uv) {
  float k = 1.0 - uProgress;
  vec2 block = floor(uv * vec2(10.0, 14.0));
  float r = rand(block + floor(uProgress * 12.0));
  float shown = step(r, uProgress * 1.2);
  vec2 off = vec2((rand(block + 5.0) - 0.5) * 0.2 * k, 0.0);
  vec3 c = getColor(uv + off * step(0.6, r)).rgb;
  if (uProgress >= 1.0) { shown = 1.0; c = getColor(uv).rgb; }
  return vec4(c * shown, 1.0);
}
