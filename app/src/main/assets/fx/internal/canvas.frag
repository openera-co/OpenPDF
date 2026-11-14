// Places the clip on the project canvas: fit/fill, keyframed transform, flip,
// chroma key, person cutout, and a blur / color background behind it.
uniform sampler2D uTex;
uniform vec4 uCrop;
uniform float uCmShape;        // clip mask: 0 none, 1 circle, 2 rect, 3 linear, 4 heart, 5 star
uniform vec2 uCmCenter;        // in the clip's picture, y up
uniform vec2 uCmSize;
uniform float uCmRot;
uniform float uCmFeather;
uniform float uCmInvert;            // visible part of the source: x, y (GL, y up), width, height
uniform float uCanvasAspect;   // width / height of the output
uniform float uContentAspect;  // width / height of the clip
uniform float uFill;           // 0 fit, 1 fill
uniform vec2 uCenter;          // canvas-normalized
uniform float uScale;
uniform float uRotation;       // radians, clockwise
uniform vec2 uFlip;            // 1 or -1 per axis
uniform float uOpacity;
uniform float uBgMode;         // 0 color, 1 blur
uniform vec3 uBgColor;
uniform float uBlur;           // 0..1
uniform vec3 uKeyColor;
uniform float uKeyOn;
uniform float uKeySimilarity;  // 0..1
uniform float uKeySmoothness;  // 0..1
uniform float uKeySpill;       // 0..1
uniform sampler2D uMask;       // person mask in clip coordinates (cutout)
uniform float uMaskOn;
uniform float uMaskEdge;       // 0 hard .. 1 soft
uniform float uCutMode;        // 0 color, 1 blur in place, 2 image
uniform vec3 uCutColor;
uniform float uCutBlur;        // 0..1
uniform sampler2D uCutImage;
uniform float uCutImageAspect;

vec2 contentSize(bool fill) {
  float a = uCanvasAspect, ca = uContentAspect;
  bool wide = ca > a;
  if (fill) wide = !wide;
  return wide ? vec2(a, a / ca) : vec2(ca, 1.0);
}

vec3 background(vec2 p) {
  if (uBgMode < 0.5) return uBgColor;
  vec2 size = contentSize(true);
  vec2 P = vec2(p.x * uCanvasAspect, p.y);
  vec2 uv = (P - vec2(uCanvasAspect * 0.5, 0.5)) / size + 0.5;
  float r = 0.01 + uBlur * 0.05;
  vec3 acc = vec3(0.0);
  for (int i = -2; i <= 2; i++) {
    for (int j = -2; j <= 2; j++) {
      acc += texture2D(uTex, clamp(uv + vec2(float(i), float(j)) * r, 0.0, 1.0)).rgb;
    }
  }
  return acc / 25.0 * 0.82;
}

// What shows where the cutout removed the background.
vec3 cutBackground(vec2 p, vec2 uv, vec3 canvasBg, float inside) {
  if (uCutMode < 0.5) return uCutColor;
  if (uCutMode < 1.5) {
    if (inside < 0.5) return canvasBg;
    // A wide, soft blur of the frame itself, like a portrait-mode photo.
    float r = 0.004 + uCutBlur * 0.022;
    vec3 acc = vec3(0.0);
    for (int i = -3; i <= 3; i++) {
      for (int j = -3; j <= 3; j++) {
        acc += texture2D(uTex, clamp(uv + vec2(float(i), float(j)) * r, 0.0, 1.0)).rgb;
      }
    }
    return acc / 49.0;
  }
  // Picture, filling the canvas.
  float a = uCanvasAspect, ia = max(uCutImageAspect, 0.01);
  vec2 size = ia > a ? vec2(ia, 1.0) : vec2(a, a / ia);
  vec2 P = vec2(p.x * uCanvasAspect, p.y);
  vec2 iuv = (P - vec2(uCanvasAspect * 0.5, 0.5)) / size + 0.5;
  return texture2D(uCutImage, clamp(iuv, 0.0, 1.0)).rgb;
}

float personAlpha(vec2 uv) {
  if (uMaskOn < 0.5) return 1.0;
  float m = texture2D(uMask, clamp(uv, 0.0, 1.0)).r;
  float e = 0.04 + uMaskEdge * 0.3;
  return smoothstep(0.5 - e, 0.5 + e, m);
}

float keyAlpha(vec3 c) {
  if (uKeyOn < 0.5) return 1.0;
  // Distance in the chroma plane plus a little luminance, so shadows on the screen still key.
  float y = luma(c);
  vec2 cbcr = vec2(c.b - y, c.r - y);
  float ky = luma(uKeyColor);
  vec2 kcbcr = vec2(uKeyColor.b - ky, uKeyColor.r - ky);
  float d = distance(cbcr, kcbcr) + abs(y - ky) * 0.15;
  float sim = uKeySimilarity * 0.45;
  return smoothstep(sim, sim + max(0.002, uKeySmoothness * 0.35), d);
}

// Removes the key colour's tint from what is left (green fringes on hair and edges).
vec3 despill(vec3 c) {
  vec3 kch = uKeyColor - luma(uKeyColor);
  vec3 dir = kch / max(length(kch), 1e-4);
  float along = max(dot(c - luma(c), dir), 0.0);
  return c - dir * along * uKeySpill;
}

float sdHeart(vec2 p) {
  p.x = abs(p.x);
  p.y = -p.y + 0.5;
  if (p.y + p.x > 1.0) return sqrt(dot(p - vec2(0.25, 0.75), p - vec2(0.25, 0.75))) - sqrt(2.0) / 4.0;
  vec2 a = p - vec2(0.0, 1.0);
  vec2 b = p - 0.5 * max(p.x + p.y, 0.0);
  return sqrt(min(dot(a, a), dot(b, b))) * sign(p.x - p.y);
}
float sdStar(vec2 p, float r, float rf) {
  const vec2 k1 = vec2(0.809016994375, -0.587785252292);
  const vec2 k2 = vec2(-k1.x, k1.y);
  p.x = abs(p.x);
  p -= 2.0 * max(dot(k1, p), 0.0) * k1;
  p -= 2.0 * max(dot(k2, p), 0.0) * k2;
  p.x = abs(p.x);
  p.y -= r;
  vec2 ba = rf * vec2(-k1.y, k1.x) - vec2(0.0, 1.0);
  float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, r);
  return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
}

/** Clip mask in the cropped picture's square units, so a circle stays round. */
float clipMask(vec2 uv) {
  if (uCmShape < 0.5) return 1.0;
  float ca = uContentAspect;
  vec2 d = (uv - uCmCenter) * vec2(ca, 1.0) / min(ca, 1.0);
  float s = sin(uCmRot), co = cos(uCmRot);
  d = vec2(co * d.x - s * d.y, s * d.x + co * d.y);
  vec2 q = d / max(uCmSize * 0.5, vec2(0.001));
  float dist;
  if (uCmShape < 1.5) dist = length(q) - 1.0;
  else if (uCmShape < 2.5) dist = max(abs(q.x), abs(q.y)) - 1.0;
  else if (uCmShape < 3.5) dist = q.y;
  else if (uCmShape < 4.5) dist = sdHeart(q * 0.75 + vec2(0.0, 0.1));
  else dist = sdStar(q, 1.0, 0.45);
  float f = 0.01 + uCmFeather * 0.6;
  float a = 1.0 - smoothstep(-f, f, dist);
  return uCmInvert > 0.5 ? 1.0 - a : a;
}

void main() {
  vec2 p = vTexSamplingCoord;
  vec2 P = vec2(p.x * uCanvasAspect, p.y);
  vec2 C = vec2(uCenter.x * uCanvasAspect, uCenter.y);
  vec2 d = P - C;
  float s = sin(uRotation), co = cos(uRotation);
  d = vec2(co * d.x - s * d.y, s * d.x + co * d.y);
  vec2 size = contentSize(uFill > 0.5) * max(uScale, 0.001);
  vec2 uv = d / size * uFlip + 0.5;
  vec3 bg = background(p);
  float inside = step(0.0, uv.x) * step(uv.x, 1.0) * step(0.0, uv.y) * step(uv.y, 1.0);
  // uv spans the cropped picture; suv is the same point in the whole source frame.
  vec2 suv = uCrop.xy + clamp(uv, 0.0, 1.0) * uCrop.zw;
  vec3 c = texture2D(uTex, suv).rgb;
  if (uMaskOn > 0.5) bg = cutBackground(p, suv, bg, inside);
  float a = keyAlpha(c) * personAlpha(suv) * inside * uOpacity * clipMask(uv);
  if (uKeyOn > 0.5) c = despill(c);
  gl_FragColor = vec4(mix(bg, c, a), 1.0);
}
