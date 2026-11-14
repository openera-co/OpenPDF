// Places the clip on the project canvas: fit/fill, keyframed transform, flip,
// chroma key, person cutout, and a blur / color background behind it.
uniform sampler2D uTex;
uniform vec4 uCrop;            // visible part of the source: x, y (GL, y up), width, height
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
  float a = keyAlpha(c) * personAlpha(suv) * inside * uOpacity;
  if (uKeyOn > 0.5) c = despill(c);
  gl_FragColor = vec4(mix(bg, c, a), 1.0);
}
