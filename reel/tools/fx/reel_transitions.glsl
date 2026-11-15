// Reel's own transitions. Same API as gl-transitions: transition(uv), getFromColor, getToColor,
// progress (0..1) and ratio (width / height). Built into transitions.json by build_transitions.py.

//== id: rt_zoomblur | Zoom Blur | Zoom
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159);
  vec3 a = vec3(0.0);
  vec3 b = vec3(0.0);
  vec2 d = uv - 0.5;
  for (int i = 0; i < 12; i++) {
    float s = 1.0 - k * 0.25 * float(i) / 11.0;
    a += getFromColor(0.5 + d * s * (1.0 - p * 0.3)).rgb;
    b += getToColor(0.5 + d * s * (1.3 - p * 0.3)).rgb;
  }
  return vec4(mix(a, b, smoothstep(0.35, 0.65, p)) / 12.0, 1.0);
}

//== id: rt_spinblur | Spin Blur | Zoom
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159);
  vec2 asp = vec2(ratio, 1.0);
  vec2 d = (uv - 0.5) * asp;
  vec3 acc = vec3(0.0);
  float base = (p < 0.5 ? p : p - 1.0) * 3.0;
  for (int i = 0; i < 12; i++) {
    float a = base + k * 0.5 * (float(i) / 11.0 - 0.5);
    vec2 r = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y) / asp + 0.5;
    r = clamp(r, 0.0, 1.0);
    acc += p < 0.5 ? getFromColor(r).rgb : getToColor(r).rgb;
  }
  return vec4(acc / 12.0, 1.0);
}

//== id: rt_rgbsplit | RGB Split | Glitch
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159) * 0.06;
  vec4 a = vec4(getFromColor(uv + vec2(k, 0.0)).r, getFromColor(uv).g, getFromColor(uv - vec2(k, 0.0)).b, 1.0);
  vec4 b = vec4(getToColor(uv + vec2(k, 0.0)).r, getToColor(uv).g, getToColor(uv - vec2(k, 0.0)).b, 1.0);
  return mix(a, b, smoothstep(0.4, 0.6, p));
}

//== id: rt_flashwhite | Flash White | Light
vec4 transition(vec2 uv) {
  float p = progress;
  vec4 c = p < 0.5 ? getFromColor(uv) : getToColor(uv);
  float f = 1.0 - abs(p - 0.5) * 2.0;
  return mix(c, vec4(1.0), pow(f, 1.5));
}

//== id: rt_dipblack | Dip To Black | Basic
vec4 transition(vec2 uv) {
  float p = progress;
  vec4 c = p < 0.5 ? getFromColor(uv) : getToColor(uv);
  return c * abs(p - 0.5) * 2.0;
}

//== id: rt_blurdissolve | Blur Dissolve | Basic
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159) * 0.012;
  vec3 a = vec3(0.0);
  vec3 b = vec3(0.0);
  for (int i = 0; i < 9; i++) {
    vec2 o = vec2(mod(float(i), 3.0) - 1.0, floor(float(i) / 3.0) - 1.0) * k;
    a += getFromColor(uv + o).rgb;
    b += getToColor(uv + o).rgb;
  }
  return vec4(mix(a, b, p) / 9.0, 1.0);
}

//== id: rt_whipleft | Whip Left | Slide
vec4 transition(vec2 uv) {
  float p = progress;
  float e = p * p * (3.0 - 2.0 * p);
  float k = sin(p * 3.14159) * 0.08;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 12; i++) {
    float x = uv.x + e + k * (float(i) / 11.0 - 0.5);
    acc += x < 1.0 ? getFromColor(vec2(x, uv.y)).rgb : getToColor(vec2(x - 1.0, uv.y)).rgb;
  }
  return vec4(acc / 12.0, 1.0);
}

//== id: rt_whipup | Whip Up | Slide
vec4 transition(vec2 uv) {
  float p = progress;
  float e = p * p * (3.0 - 2.0 * p);
  float k = sin(p * 3.14159) * 0.08;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 12; i++) {
    float y = uv.y - e + k * (float(i) / 11.0 - 0.5);
    acc += y > 0.0 ? getFromColor(vec2(uv.x, y)).rgb : getToColor(vec2(uv.x, y + 1.0)).rgb;
  }
  return vec4(acc / 12.0, 1.0);
}

//== id: rt_pushup | Push Up | Slide
vec4 transition(vec2 uv) {
  float e = 1.0 - pow(1.0 - progress, 3.0);
  float y = uv.y - e;
  return y > 0.0 ? getFromColor(vec2(uv.x, y)) : getToColor(vec2(uv.x, y + 1.0));
}

//== id: rt_pushdown | Push Down | Slide
vec4 transition(vec2 uv) {
  float e = 1.0 - pow(1.0 - progress, 3.0);
  float y = uv.y + e;
  return y < 1.0 ? getFromColor(vec2(uv.x, y)) : getToColor(vec2(uv.x, y - 1.0));
}

//== id: rt_stackup | Stack | Slide
vec4 transition(vec2 uv) {
  float e = 1.0 - pow(1.0 - progress, 3.0);
  float y = uv.y - (1.0 - e);
  float shade = 1.0 - 0.5 * e;
  return y > 0.0 ? getToColor(vec2(uv.x, y)) : getFromColor(0.5 + (uv - 0.5) * (1.0 + 0.1 * e)) * shade;
}

//== id: rt_zoompunch | Zoom Punch | Zoom
vec4 transition(vec2 uv) {
  float p = progress;
  if (p < 0.5) {
    float z = 1.0 + p * p * 8.0;
    return getFromColor(0.5 + (uv - 0.5) / z);
  }
  float q = (p - 0.5) * 2.0;
  float z = 1.0 + (1.0 - q) * (1.0 - q) * 1.5;
  return getToColor(0.5 + (uv - 0.5) / z);
}

//== id: rt_zoomout | Zoom Out Swap | Zoom
vec4 transition(vec2 uv) {
  float p = progress;
  float z = mix(1.0, 0.0, smoothstep(0.0, 0.5, p));
  vec2 q = 0.5 + (uv - 0.5) / max(z, 0.001);
  bool inside = all(greaterThanEqual(q, vec2(0.0))) && all(lessThanEqual(q, vec2(1.0)));
  float zi = smoothstep(0.5, 1.0, p);
  vec2 r = 0.5 + (uv - 0.5) * (2.0 - zi);
  vec4 to = getToColor(clamp(r, 0.0, 1.0));
  return p < 0.5 && inside ? getFromColor(q) : to;
}

//== id: rt_rotatein | Rotate In | Zoom
vec4 transition(vec2 uv) {
  float p = progress;
  float e = 1.0 - pow(1.0 - p, 3.0);
  vec2 asp = vec2(ratio, 1.0);
  float a = (1.0 - e) * 1.2;
  vec2 d = (uv - 0.5) * asp;
  d = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y) / max(e, 0.001);
  vec2 q = 0.5 + d / asp;
  bool inside = all(greaterThanEqual(q, vec2(0.0))) && all(lessThanEqual(q, vec2(1.0)));
  return inside ? getToColor(q) : getFromColor(uv) * (1.0 - 0.5 * e);
}

//== id: rt_barrelroll | Barrel Roll | Warp
vec4 transition(vec2 uv) {
  float p = progress;
  float a = p * 6.28318;
  vec2 asp = vec2(ratio, 1.0);
  float z = 1.0 + sin(p * 3.14159) * 0.8;
  vec2 d = (uv - 0.5) * asp * z;
  d = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y);
  vec2 q = fract(0.5 + d / asp);
  return p < 0.5 ? getFromColor(q) : getToColor(q);
}

//== id: rt_swingdoor | Swing Door | Slide
vec4 transition(vec2 uv) {
  float p = progress;
  float w = max(0.001, cos(p * 1.5708));
  float x = uv.x / w;
  float persp = 1.0 + (1.0 - w) * (uv.x / w) * 0.3;
  vec2 q = vec2(x, 0.5 + (uv.y - 0.5) * persp);
  bool inside = x <= 1.0 && q.y >= 0.0 && q.y <= 1.0;
  return inside ? getFromColor(q) * (0.6 + 0.4 * w) : getToColor(uv);
}

//== id: rt_diagonal | Diagonal Wipe | Slide
vec4 transition(vec2 uv) {
  float d = (uv.x + (1.0 - uv.y)) * 0.5;
  float m = smoothstep(progress * 1.2 - 0.1, progress * 1.2, d);
  return mix(getToColor(uv), getFromColor(uv), m);
}

//== id: rt_triangles | Triangles | Shape
vec4 transition(vec2 uv) {
  vec2 asp = vec2(ratio, 1.0);
  vec2 p = uv * asp * 8.0;
  vec2 g = floor(p);
  vec2 f = fract(p);
  float tri = step(f.x, f.y);
  float delay = (g.x + g.y * 0.5 + tri * 0.5) / (8.0 * ratio + 4.0);
  float t = clamp((progress * 1.6 - delay), 0.0, 1.0);
  return t > 0.5 ? getToColor(uv) : getFromColor(uv);
}

//== id: rt_hexagons | Hexagons | Shape
vec4 transition(vec2 uv) {
  vec2 asp = vec2(ratio, 1.0);
  vec2 p = uv * asp * 10.0;
  vec2 r = vec2(1.0, 1.7320508);
  vec2 h = r * 0.5;
  vec2 a = mod(p, r) - h;
  vec2 b = mod(p - h, r) - h;
  vec2 gv = dot(a, a) < dot(b, b) ? a : b;
  vec2 id = p - gv;
  float delay = length(id / asp / 10.0 - 0.5) * 1.2;
  float t = clamp(progress * 2.0 - delay, 0.0, 1.0);
  float size = t * 0.6;
  float hex = max(abs(gv.x) * 0.866 + abs(gv.y) * 0.5, abs(gv.y));
  return hex < size ? getToColor(uv) : getFromColor(uv);
}

//== id: rt_pixelsort | Pixel Sort | Glitch
float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159);
  float col = floor(uv.x * 80.0);
  float off = rand(vec2(col, 1.0)) * k * 0.4;
  vec2 q = vec2(uv.x, fract(uv.y + off));
  return p < 0.5 ? getFromColor(q) : getToColor(q);
}

//== id: rt_rippleout | Ripple Out | Warp
vec4 transition(vec2 uv) {
  vec2 asp = vec2(ratio, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float r = length(d);
  float front = progress * 1.3;
  float w = exp(-pow((r - front) * 12.0, 2.0)) * 0.03;
  vec2 q = uv + normalize(d + 1e-5) / asp * w;
  return r < front ? getToColor(q) : getFromColor(q);
}

//== id: rt_shutter | Shutter | Shape
vec4 transition(vec2 uv) {
  float p = progress;
  float c = 1.0 - abs(p - 0.5) * 2.0;
  float bar = step(abs(uv.y - 0.5), c * 0.5);
  vec4 col = p < 0.5 ? getFromColor(uv) : getToColor(uv);
  return mix(col, vec4(0.0, 0.0, 0.0, 1.0), bar);
}

//== id: rt_lensflash | Lens Flash | Light
vec4 transition(vec2 uv) {
  float p = progress;
  vec2 asp = vec2(ratio, 1.0);
  float f = 1.0 - abs(p - 0.5) * 2.0;
  vec2 c = vec2(mix(0.1, 0.9, p), 0.35);
  float glow = exp(-length((uv - c) * asp) * 4.0) * f * 2.0;
  float streak = exp(-abs(uv.y - c.y) * 60.0) * f;
  vec4 base = mix(getFromColor(uv), getToColor(uv), smoothstep(0.35, 0.65, p));
  return base + vec4(vec3(1.0, 0.9, 0.75) * (glow + streak), 0.0);
}

//== id: rt_mirrorflip | Mirror Flip | Slide
vec4 transition(vec2 uv) {
  float p = progress;
  float w = abs(cos(p * 3.14159));
  float x = 0.5 + (uv.x - 0.5) / max(w, 0.001);
  bool inside = x >= 0.0 && x <= 1.0;
  vec2 q = vec2(p < 0.5 ? x : 1.0 - x, uv.y);
  vec4 c = p < 0.5 ? getFromColor(q) : getToColor(vec2(1.0 - q.x, q.y));
  return inside ? c * (0.5 + 0.5 * w) : vec4(0.0, 0.0, 0.0, 1.0);
}

//== id: rt_kaleidozoom | Kaleido Zoom | Warp
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159);
  vec2 asp = vec2(ratio, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float a = atan(d.y, d.x) + p * 3.0;
  float r = length(d) * (1.0 - 0.45 * k);
  float seg = 6.2831853 / 6.0;
  a = abs(mod(a, seg) - seg * 0.5);
  vec2 q = vec2(cos(a), sin(a)) * r / asp + 0.5;
  q = mix(uv, clamp(q, 0.0, 1.0), k);
  return mix(getFromColor(q), getToColor(q), smoothstep(0.4, 0.6, p));
}

//== id: rt_waveslide | Wave Slide | Slide
vec4 transition(vec2 uv) {
  float p = progress;
  float edge = p * 1.2 - 0.1 + sin(uv.y * 18.0 + p * 10.0) * 0.04;
  vec2 q = uv + vec2(sin(uv.y * 10.0 + p * 12.0) * 0.01 * sin(p * 3.14159), 0.0);
  return uv.x < edge ? getToColor(q) : getFromColor(q);
}

//== id: rt_glitchslide | Glitch Slide | Glitch
float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }
vec4 transition(vec2 uv) {
  float p = progress;
  float row = floor(uv.y * 24.0);
  float jitter = (rand(vec2(row, floor(p * 20.0))) - 0.5) * 0.2 * sin(p * 3.14159);
  float x = uv.x + p + jitter;
  vec4 c = x < 1.0 ? getFromColor(vec2(fract(x), uv.y)) : getToColor(vec2(fract(x), uv.y));
  return c;
}

//== id: rt_stretchzoom | Stretch Zoom | Zoom
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159);
  vec2 q = vec2(0.5 + (uv.x - 0.5) / (1.0 + k * 2.0), 0.5 + (uv.y - 0.5) / (1.0 + k * 0.3));
  return p < 0.5 ? getFromColor(q) : getToColor(q);
}

//== id: rt_bubble | Bubble | Shape
vec4 transition(vec2 uv) {
  vec2 asp = vec2(ratio, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float r = progress * 1.25;
  float l = length(d);
  float lens = l < r ? (1.0 - smoothstep(r * 0.6, r, l)) * 0.15 * (1.0 - progress) : 0.0;
  vec2 q = 0.5 + d * (1.0 - lens) / asp;
  float rim = smoothstep(0.01, 0.0, abs(l - r)) * 0.5 * (1.0 - progress);
  return (l < r ? getToColor(q) : getFromColor(uv)) + vec4(vec3(rim), 0.0);
}

//== id: rt_lightleak | Light Leak Fade | Light
vec4 transition(vec2 uv) {
  float p = progress;
  float f = sin(p * 3.14159);
  vec3 leak = mix(vec3(1.0, 0.45, 0.1), vec3(1.0, 0.85, 0.4), uv.y) * smoothstep(0.0, 1.0, uv.x + p - 0.5);
  vec4 base = mix(getFromColor(uv), getToColor(uv), smoothstep(0.3, 0.7, p));
  return vec4(1.0 - (1.0 - base.rgb) * (1.0 - leak * f * 0.9), 1.0);
}

//== id: rt_shakecut | Shake Cut | Glitch
float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159);
  vec2 o = (vec2(rand(vec2(floor(p * 30.0), 1.0)), rand(vec2(floor(p * 30.0), 2.0))) - 0.5) * 0.06 * k;
  vec2 q = 0.5 + (uv - 0.5) / (1.0 + 0.1 * k) + o;
  return p < 0.5 ? getFromColor(q) : getToColor(q);
}

//== id: rt_splitreveal | Split Reveal | Slide
vec4 transition(vec2 uv) {
  float e = 1.0 - pow(1.0 - progress, 3.0);
  float half_ = e * 0.5;
  if (uv.y < 0.5 - half_) return getFromColor(vec2(uv.x, uv.y + half_));
  if (uv.y > 0.5 + half_) return getFromColor(vec2(uv.x, uv.y - half_));
  return getToColor(0.5 + (uv - 0.5) * (1.2 - 0.2 * e));
}

//== id: rt_irisblur | Iris Zoom | Zoom
vec4 transition(vec2 uv) {
  vec2 asp = vec2(ratio, 1.0);
  float r = length((uv - 0.5) * asp);
  float e = progress * progress;
  float rad = e * 1.2;
  vec4 to = getToColor(0.5 + (uv - 0.5) * (1.3 - 0.3 * progress));
  float m = smoothstep(rad, rad - 0.05, r);
  return mix(getFromColor(0.5 + (uv - 0.5) / (1.0 + progress * 0.3)), to, m);
}

//== id: rt_bars | Color Bars | Glitch
vec3 hsv2rgbLite(float h) { return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0); }
vec4 transition(vec2 uv) {
  float p = progress;
  float f = 1.0 - abs(p - 0.5) * 2.0;
  float b = floor(uv.x * 7.0);
  vec3 bar = hsv2rgbLite(b / 7.0);
  vec4 c = p < 0.5 ? getFromColor(uv) : getToColor(uv);
  return vec4(mix(c.rgb, bar, step(0.6, f) * 0.85), 1.0);
}

//== id: rt_glitchzoom | Glitch Zoom | Glitch
float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159);
  float row = floor(uv.y * 24.0);
  float shift = (rand(vec2(row, floor(p * 20.0))) - 0.5) * 0.15 * k * step(0.5, rand(vec2(row, 3.0)));
  vec2 d = uv - 0.5;
  vec2 qa = d / (1.0 + p * 0.6) + 0.5 + vec2(shift, 0.0);
  vec2 qb = d / (1.0 + (1.0 - p) * -0.3) + 0.5 + vec2(shift, 0.0);
  vec4 a = vec4(getFromColor(qa + vec2(0.01 * k, 0.0)).r, getFromColor(qa).g, getFromColor(qa - vec2(0.01 * k, 0.0)).b, 1.0);
  vec4 b = vec4(getToColor(qb + vec2(0.01 * k, 0.0)).r, getToColor(qb).g, getToColor(qb - vec2(0.01 * k, 0.0)).b, 1.0);
  return mix(a, b, step(0.5, p));
}

//== id: rt_inkdrop | Ink Drop | Shape
float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }
float vnoise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(rand(i), rand(i + vec2(1.0, 0.0)), f.x), mix(rand(i + vec2(0.0, 1.0)), rand(i + vec2(1.0, 1.0)), f.x), f.y);
}
vec4 transition(vec2 uv) {
  vec2 d = (uv - 0.5) * vec2(ratio, 1.0);
  float r = length(d) + (vnoise(uv * 6.0) - 0.5) * 0.25 + (vnoise(uv * 18.0) - 0.5) * 0.08;
  float edge = progress * 1.3 - 0.1;
  float m = smoothstep(edge, edge - 0.03, r);
  return mix(getFromColor(uv), getToColor(uv), m);
}

//== id: rt_filmroll | Film Roll | Slide
vec4 transition(vec2 uv) {
  float p = progress;
  float e = p * p * (3.0 - 2.0 * p);
  float y = uv.y + e * 1.1;
  float gap = 0.1;
  vec3 c;
  if (y < 1.0) c = getFromColor(vec2(uv.x, y)).rgb;
  else if (y < 1.0 + gap) {
    // the dark film edge between frames, with sprocket holes
    float holes = step(abs(fract(uv.x * 8.0) - 0.5), 0.2) * step(abs((y - 1.0) / gap - 0.5), 0.25);
    c = mix(vec3(0.05), vec3(0.85), holes);
  } else c = getToColor(vec2(uv.x, y - 1.0 - gap)).rgb;
  if (p >= 1.0) c = getToColor(uv).rgb;
  float blur = sin(p * 3.14159);
  return vec4(c * (1.0 - blur * 0.15), 1.0);
}

//== id: rt_scanline | Scan Line | Light
vec4 transition(vec2 uv) {
  float p = progress;
  float y = 1.0 - p * 1.1 + 0.05;
  vec3 c = uv.y > y ? getToColor(uv).rgb : getFromColor(uv).rgb;
  float line = exp(-pow((uv.y - y) * 80.0, 2.0));
  float glow = exp(-abs(uv.y - y) * 20.0) * 0.3;
  return vec4(min(c + vec3(0.6, 0.9, 1.0) * (line + glow) * sin(p * 3.14159), vec3(1.0)), 1.0);
}

//== id: rt_pixelrain | Pixel Rain | Glitch
float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }
vec4 transition(vec2 uv) {
  float cols = 40.0;
  float col = floor(uv.x * cols);
  float delay = rand(vec2(col, 1.0)) * 0.5;
  float t = clamp((progress - delay) / 0.5, 0.0, 1.0);
  t = t * t;
  float fall = 1.0 - t;
  vec2 q = vec2(uv.x, uv.y - fall);
  if (q.y >= 0.0 && t > 0.0) return getToColor(q);
  return getFromColor(uv);
}

//== id: rt_lumawipe | Luma Wipe | Light
vec4 transition(vec2 uv) {
  vec3 b = getToColor(uv).rgb;
  float l = dot(b, vec3(0.2126, 0.7152, 0.0722));
  float m = smoothstep(1.0 - l - 0.1, 1.0 - l + 0.1, progress * 1.2 - 0.1);
  return vec4(mix(getFromColor(uv).rgb, b, m), 1.0);
}

//== id: rt_crossblur | Cross Blur | Basic
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159) * 0.05;
  vec3 a = vec3(0.0);
  vec3 b = vec3(0.0);
  for (int i = 0; i < 12; i++) {
    float t = (float(i) / 11.0 - 0.5) * k;
    a += getFromColor(uv + vec2(t, t * 0.3)).rgb;
    b += getToColor(uv + vec2(t, t * 0.3)).rgb;
  }
  return vec4(mix(a, b, smoothstep(0.3, 0.7, p)) / 12.0, 1.0);
}

//== id: rt_lightsweep | Light Sweep | Light
vec4 transition(vec2 uv) {
  float p = progress;
  float x = (uv.x + uv.y * 0.3) / 1.3;
  float edge = p * 1.4 - 0.2;
  vec3 c = x < edge ? getToColor(uv).rgb : getFromColor(uv).rgb;
  float band = exp(-pow((x - edge) * 12.0, 2.0)) * sin(p * 3.14159);
  return vec4(min(c + band * 0.9, vec3(1.0)), 1.0);
}

//== id: rt_tileflip | Tile Flip | Shape
vec4 transition(vec2 uv) {
  vec2 n = vec2(8.0, 8.0 / ratio);
  vec2 id = floor(uv * n);
  vec2 f = fract(uv * n);
  float delay = (id.x + id.y) / (n.x + n.y) * 0.6;
  float t = clamp((progress - delay) / 0.4, 0.0, 1.0);
  float a = t * 3.14159;
  float w = abs(cos(a));
  float fx = (f.x - 0.5) / max(w, 0.001) + 0.5;
  if (fx < 0.0 || fx > 1.0) return vec4(getToColor(uv).rgb * 0.35, 1.0);
  vec2 q = (id + vec2(fx, f.y)) / n;
  vec3 c = t < 0.5 ? getFromColor(q).rgb : getToColor(q).rgb;
  return vec4(c * (0.6 + 0.4 * w), 1.0);
}

//== id: rt_dropbounce | Drop Bounce | Slide
vec4 transition(vec2 uv) {
  float p = progress;
  float y;
  if (p < 0.6) { float t = p / 0.6; y = 1.0 - t * t; }
  else { float t = (p - 0.6) / 0.4; y = abs(sin(t * 3.14159 * 2.0)) * 0.12 * (1.0 - t); }
  vec2 q = vec2(uv.x, uv.y - y);
  if (q.y >= 0.0 && q.y <= 1.0) return getToColor(q);
  return getFromColor(uv) * (1.0 - 0.3 * (1.0 - y));
}

//== id: rt_slingshot | Slingshot | Slide
vec4 transition(vec2 uv) {
  float p = progress;
  float back = p < 0.3 ? sin(p / 0.3 * 1.5708) * 0.08 : 0.08 * (1.0 - smoothstep(0.3, 0.4, p));
  float shot = p < 0.3 ? 0.0 : pow((p - 0.3) / 0.7, 0.6);
  float x = uv.x + back - shot;
  if (x >= 0.0 && x <= 1.0 && shot < 1.0) return getFromColor(vec2(x, uv.y));
  float xb = uv.x + 1.0 - shot;
  if (xb >= 0.0 && xb <= 1.0) return getToColor(vec2(xb, uv.y));
  return getToColor(uv);
}

//== id: rt_squash | Squash | Shape
vec4 transition(vec2 uv) {
  float p = progress;
  if (p < 0.4) {
    float h = 1.0 - smoothstep(0.0, 0.4, p);
    h = max(h, 0.004);
    vec2 q = vec2(uv.x, (uv.y - 0.5) / h + 0.5);
    float w = 1.0 + (1.0 - h) * 0.2;
    q.x = (uv.x - 0.5) / w + 0.5;
    if (q.y < 0.0 || q.y > 1.0) return vec4(0.0, 0.0, 0.0, 1.0);
    return getFromColor(q);
  }
  float t = (p - 0.4) / 0.6;
  float h = max(1.0 - pow(1.0 - t, 3.0), 0.004);
  vec2 q = vec2(uv.x, (uv.y - 0.5) / h + 0.5);
  if (q.y < 0.0 || q.y > 1.0) return vec4(0.0, 0.0, 0.0, 1.0);
  return getToColor(q);
}

//== id: rt_spiralzoom | Spiral Zoom | Zoom
vec4 transition(vec2 uv) {
  float p = progress;
  vec2 asp = vec2(ratio, 1.0);
  vec2 d = (uv - 0.5) * asp;
  float k = sin(p * 3.14159);
  float a = k * 2.5 * (1.0 - length(d));
  vec2 r = vec2(cos(a) * d.x - sin(a) * d.y, sin(a) * d.x + cos(a) * d.y);
  float z = 1.0 + k * 0.8;
  vec2 q = r / z / asp + 0.5;
  q = clamp(q, 0.0, 1.0);
  return mix(getFromColor(q), getToColor(q), smoothstep(0.4, 0.6, p));
}

//== id: rt_rgbzoom | RGB Zoom | Zoom
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159);
  vec2 d = uv - 0.5;
  vec3 a = vec3(getFromColor(d / (1.0 + k * 0.3) + 0.5).r, getFromColor(d / (1.0 + k * 0.4) + 0.5).g, getFromColor(d / (1.0 + k * 0.5) + 0.5).b);
  vec3 b = vec3(getToColor(d / (1.0 + k * 0.3) + 0.5).r, getToColor(d / (1.0 + k * 0.4) + 0.5).g, getToColor(d / (1.0 + k * 0.5) + 0.5).b);
  return vec4(mix(a, b, smoothstep(0.4, 0.6, p)), 1.0);
}

//== id: rt_neonwipe | Neon Wipe | Light
vec4 transition(vec2 uv) {
  float p = progress;
  float edge = p * 1.2 - 0.1;
  float d = uv.x - edge;
  vec3 c = d < 0.0 ? getToColor(uv).rgb : getFromColor(uv).rgb;
  vec3 neon = mix(vec3(1.0, 0.2, 0.8), vec3(0.2, 0.9, 1.0), uv.y);
  float g = (0.004 / (abs(d) + 0.004)) * sin(p * 3.14159);
  return vec4(min(c + neon * g, vec3(1.0)), 1.0);
}

//== id: rt_scatter | Scatter | Glitch
float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }
vec4 transition(vec2 uv) {
  float p = progress;
  vec2 n = vec2(30.0, 30.0 / ratio);
  vec2 id = floor(uv * n);
  float r = rand(id);
  float k = sin(p * 3.14159);
  vec2 off = (vec2(rand(id + 3.0), rand(id + 7.0)) - 0.5) * 0.3 * k;
  vec2 q = uv + off;
  return p < r * 0.6 + 0.2 ? getFromColor(clamp(q, 0.0, 1.0)) : getToColor(clamp(q, 0.0, 1.0));
}

//== id: rt_burstrays | Burst Rays | Shape
vec4 transition(vec2 uv) {
  vec2 d = (uv - 0.5) * vec2(ratio, 1.0);
  float a = atan(d.y, d.x);
  float rays = 0.5 + 0.5 * sin(a * 16.0);
  float r = length(d);
  float edge = progress * 1.6 - rays * 0.3;
  float m = smoothstep(edge, edge - 0.04, r - 0.05);
  if (progress >= 1.0) m = 1.0;
  return mix(getFromColor(uv), getToColor(uv), m);
}

//== id: rt_wavezoom | Wave Zoom | Warp
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159);
  vec2 d = uv - 0.5;
  float r = length(d);
  vec2 q = uv + normalize(d + 1e-4) * sin(r * 40.0 - p * 20.0) * 0.02 * k;
  q = (q - 0.5) / (1.0 + k * 0.2) + 0.5;
  q = clamp(q, 0.0, 1.0);
  return mix(getFromColor(q), getToColor(q), smoothstep(0.3, 0.7, p));
}

//== id: rt_dreamblur | Dream Blur | Light
vec4 transition(vec2 uv) {
  float p = progress;
  float k = sin(p * 3.14159);
  vec3 a = vec3(0.0);
  vec3 b = vec3(0.0);
  for (int i = 0; i < 8; i++) {
    float ang = float(i) * 0.785398;
    vec2 o = vec2(cos(ang), sin(ang)) * 0.02 * k;
    a += getFromColor(uv + o).rgb;
    b += getToColor(uv + o).rgb;
  }
  vec3 c = mix(a, b, smoothstep(0.25, 0.75, p)) / 8.0;
  c = mix(c, c * 1.2 + 0.1, k * 0.6);
  return vec4(min(c, vec3(1.0)), 1.0);
}

//== id: rt_cardflip | Card Flip | Shape
vec4 transition(vec2 uv) {
  // eased so the halfway frame already shows the incoming side, not the card edge-on
  float a = pow(progress, 0.6) * 3.14159;
  float w = cos(a);
  float aw = max(abs(w), 0.001);
  float x = (uv.x - 0.5) / aw + 0.5;
  float persp = 1.0 + (uv.x - 0.5) * sin(a) * (w > 0.0 ? 0.4 : -0.4);
  float y = (uv.y - 0.5) * persp + 0.5;
  if (x < 0.0 || x > 1.0 || y < 0.0 || y > 1.0) return vec4(0.0, 0.0, 0.0, 1.0);
  vec2 q = vec2(x, y);
  vec3 c = w > 0.0 ? getFromColor(q).rgb : getToColor(q).rgb;
  return vec4(c * (0.55 + 0.45 * abs(w)), 1.0);
}

//== id: rt_cubezoom | Zoom Through | Zoom
vec4 transition(vec2 uv) {
  float p = progress;
  vec2 d = uv - 0.5;
  if (p < 0.5) {
    float z = 1.0 + pow(p * 2.0, 3.0) * 6.0;
    return getFromColor(d / z + 0.5) + vec4(vec3(pow(p * 2.0, 4.0) * 0.6), 0.0);
  }
  float t = (p - 0.5) * 2.0;
  float z = 0.4 + 0.6 * (1.0 - pow(1.0 - t, 3.0));
  vec2 q = d / z + 0.5;
  float fl = pow(1.0 - t, 4.0) * 0.6;
  if (q.x < 0.0 || q.x > 1.0 || q.y < 0.0 || q.y > 1.0) return vec4(vec3(fl), 1.0);
  return getToColor(q) + vec4(vec3(fl), 0.0);
}

//== id: rt_blindsrotate | Venetian | Shape
vec4 transition(vec2 uv) {
  float n = 10.0;
  float f = fract(uv.y * n);
  float id = floor(uv.y * n);
  float t = clamp(progress * 1.4 - id / n * 0.4, 0.0, 1.0);
  float a = t * 3.14159;
  float h = abs(cos(a));
  float fy = (f - 0.5) / max(h, 0.001) + 0.5;
  if (fy < 0.0 || fy > 1.0) return vec4(getToColor(uv).rgb * 0.35, 1.0);
  vec2 q = vec2(uv.x, (id + fy) / n);
  vec3 c = t < 0.5 ? getFromColor(q).rgb : getToColor(q).rgb;
  return vec4(c * (0.55 + 0.45 * h), 1.0);
}
