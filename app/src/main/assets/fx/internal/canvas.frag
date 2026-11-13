// Places the clip on the project canvas: fit/fill, keyframed transform, flip,
// chroma key, and a blur / color background behind it.
uniform sampler2D uTex;
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

float keyAlpha(vec3 c) {
  if (uKeyOn < 0.5) return 1.0;
  float y = luma(c);
  vec2 cbcr = vec2(c.b - y, c.r - y);
  float ky = luma(uKeyColor);
  vec2 kcbcr = vec2(uKeyColor.b - ky, uKeyColor.r - ky);
  float d = distance(cbcr, kcbcr);
  float sim = uKeySimilarity * 0.4;
  return smoothstep(sim, sim + max(0.001, uKeySmoothness * 0.3), d);
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
  vec3 c = texture2D(uTex, clamp(uv, 0.0, 1.0)).rgb;
  float a = keyAlpha(c) * inside * uOpacity;
  if (uKeyOn > 0.5) {
    float y = luma(c);
    c = mix(c, vec3(y), uKeySpill * (1.0 - keyAlpha(c)));
  }
  gl_FragColor = vec4(mix(bg, c, a), 1.0);
}
