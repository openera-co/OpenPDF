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
//== param: levels float 4.0 2.0 10.0
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
  float e = 1.0 - pow(1.0 - uProgress, 3.0);
  vec2 q = uv + vec2(0.0, (1.0 - e) * 0.25);
  float inside = step(q.y, 1.0);
  return vec4(getColor(q).rgb * inside * e, 1.0);
}
