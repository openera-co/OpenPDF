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
