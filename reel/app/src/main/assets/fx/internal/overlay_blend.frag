// Composites one overlay (picture in picture) over the canvas: transform, blend mode,
// shape mask with feather, chroma key. Writes the result for every canvas pixel.
uniform sampler2D uDst;
uniform sampler2D uSrc;
uniform float uCanvasAspect;
uniform float uSrcAspect;
uniform float uFlipV;
uniform vec2 uCenter;      // canvas-normalized, y up
uniform vec2 uScale;       // 1 = fits the canvas; x and y stretch separately
uniform float uRotation;   // radians, clockwise
uniform float uOpacity;
uniform float uBlend;      // BlendMode ordinal
uniform float uMaskShape;  // 0 none, 1 circle, 2 rect, 3 linear, 4 heart, 5 star
uniform vec2 uMaskCenter;  // in overlay uv, y up
uniform vec2 uMaskSize;
uniform float uMaskRot;
uniform float uMaskFeather;
uniform float uMaskInvert;
uniform float uKeyOn;
uniform vec3 uKeyColor;
uniform float uKeySimilarity;
uniform float uKeySmoothness;
uniform float uKeySpill;

vec2 fitSize() {
  float a = uCanvasAspect, ca = uSrcAspect;
  return ca > a ? vec2(a, a / ca) : vec2(ca, 1.0);
}

float keyAlpha(vec3 c) {
  float y = luma(c);
  vec2 cc = vec2(c.b - y, c.r - y);
  float ky = luma(uKeyColor);
  vec2 kc = vec2(uKeyColor.b - ky, uKeyColor.r - ky);
  float d = distance(cc, kc) + abs(y - ky) * 0.15;
  float lo = uKeySimilarity * 0.45;
  return smoothstep(lo, lo + max(0.002, uKeySmoothness * 0.35), d);
}

vec3 despill(vec3 c) {
  vec3 kch = uKeyColor - luma(uKeyColor);
  float kl = max(length(kch), 1e-4);
  vec3 dir = kch / kl;
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

float maskAlpha(vec2 uv) {
  if (uMaskShape < 0.5) return 1.0;
  // Square units (the overlay's shorter side is 1) so a circle stays round on any shape of video.
  vec2 d = (uv - uMaskCenter) * vec2(uSrcAspect, 1.0) / min(uSrcAspect, 1.0);
  float s = sin(uMaskRot), co = cos(uMaskRot);
  d = vec2(co * d.x - s * d.y, s * d.x + co * d.y);
  vec2 q = d / max(uMaskSize * 0.5, vec2(0.001));
  float dist;
  if (uMaskShape < 1.5) dist = length(q) - 1.0;
  else if (uMaskShape < 2.5) dist = max(abs(q.x), abs(q.y)) - 1.0;
  else if (uMaskShape < 3.5) dist = q.y;
  else if (uMaskShape < 4.5) dist = sdHeart(q * 0.75 + vec2(0.0, 0.1));
  else dist = sdStar(q, 1.0, 0.45);
  float f = 0.01 + uMaskFeather * 0.6;
  float a = 1.0 - smoothstep(-f, f, dist);
  return uMaskInvert > 0.5 ? 1.0 - a : a;
}

vec3 blendOf(vec3 b, vec3 s) {
  if (uBlend < 0.5) return s;
  if (uBlend < 1.5) return 1.0 - (1.0 - b) * (1.0 - s);
  if (uBlend < 2.5) return b * s;
  if (uBlend < 3.5) return mix(2.0 * b * s, 1.0 - 2.0 * (1.0 - b) * (1.0 - s), step(0.5, b));
  if (uBlend < 4.5) return max(b, s);
  if (uBlend < 5.5) return min(b, s);
  if (uBlend < 6.5) return min(b + s, 1.0);
  if (uBlend < 7.5) return mix(2.0 * b * s + b * b * (1.0 - 2.0 * s), sqrt(b) * (2.0 * s - 1.0) + 2.0 * b * (1.0 - s), step(0.5, s));
  return abs(b - s);
}

void main() {
  vec2 p = vTexSamplingCoord;
  vec4 dst = texture2D(uDst, p);
  vec2 P = vec2(p.x * uCanvasAspect, p.y);
  vec2 C = vec2(uCenter.x * uCanvasAspect, uCenter.y);
  vec2 d = P - C;
  float s = sin(uRotation), co = cos(uRotation);
  d = vec2(co * d.x - s * d.y, s * d.x + co * d.y);
  vec2 uv = d / (fitSize() * max(uScale, vec2(0.001))) + 0.5;
  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) { gl_FragColor = vec4(dst.rgb, 1.0); return; }
  vec2 suv = uFlipV > 0.5 ? vec2(uv.x, 1.0 - uv.y) : uv;
  vec4 src = texture2D(uSrc, suv);
  vec3 c = src.rgb;
  float a = uOpacity * maskAlpha(uv) * src.a;
  if (uKeyOn > 0.5) { a *= keyAlpha(c); c = despill(c); }
  // Soft edge on the overlay's own border so it never looks jagged.
  vec2 e = min(uv, 1.0 - uv);
  a *= smoothstep(0.0, 0.004, min(e.x, e.y));
  gl_FragColor = vec4(mix(dst.rgb, blendOf(dst.rgb, c), clamp(a, 0.0, 1.0)), 1.0);
}
