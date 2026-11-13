// Color grade: adjust sliders, tone curves and a look LUT in one pass.
uniform sampler2D uTex;
uniform sampler2D uLut;      // N x N*N, row = r*N + g, column = b
uniform float uLutSize;
uniform float uLutAmount;    // 0 disables
uniform sampler2D uCurves;   // 256 x 2: row 0 RGB = red, green, blue curves; row 1 = master
uniform float uCurvesOn;
uniform float uBrightness;   // -1..1
uniform float uContrast;     // -1..1
uniform float uSaturation;   // -1..1
uniform float uVibrance;     // -1..1
uniform float uExposure;     // -1..1 (stops x2)
uniform float uTemperature;  // -1..1
uniform float uTint;         // -1..1
uniform float uHighlights;   // -1..1
uniform float uShadows;      // -1..1
uniform float uFade;         // 0..1
uniform float uHue;          // -1..1 (x 180 degrees)
uniform float uVignette;     // 0..1
uniform float uSharpen;      // 0..1
uniform float uGrain;        // 0..1

vec3 lookup(vec3 c) {
  float n = uLutSize;
  vec3 s = clamp(c, 0.0, 1.0) * (n - 1.0);
  float r0 = floor(s.r);
  float r1 = min(r0 + 1.0, n - 1.0);
  float fr = s.r - r0;
  float x = (s.b + 0.5) / n;
  float y0 = (r0 * n + s.g + 0.5) / (n * n);
  float y1 = (r1 * n + s.g + 0.5) / (n * n);
  return mix(texture2D(uLut, vec2(x, y0)).rgb, texture2D(uLut, vec2(x, y1)).rgb, fr);
}

float curve(float v, float channel) {
  float x = clamp(v, 0.0, 1.0) * (255.0 / 256.0) + 0.5 / 256.0;
  if (channel > 2.5) return texture2D(uCurves, vec2(x, 0.75)).r;
  vec3 t = texture2D(uCurves, vec2(x, 0.25)).rgb;
  return channel < 0.5 ? t.r : (channel < 1.5 ? t.g : t.b);
}

void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTex, uv).rgb;
  if (uSharpen > 0.0) {
    vec2 px = 1.0 / uResolution;
    vec3 n4 = texture2D(uTex, uv + vec2(px.x, 0.0)).rgb + texture2D(uTex, uv - vec2(px.x, 0.0)).rgb + texture2D(uTex, uv + vec2(0.0, px.y)).rgb + texture2D(uTex, uv - vec2(0.0, px.y)).rgb;
    c = clamp(c + (c * 4.0 - n4) * uSharpen * 0.8, 0.0, 1.0);
  }
  c *= pow(2.0, uExposure * 2.0);
  c.r *= 1.0 + 0.2 * uTemperature;
  c.b *= 1.0 - 0.2 * uTemperature;
  c.g *= 1.0 - 0.15 * uTint;
  c += uBrightness * 0.3;
  float l = luma(c);
  c += uShadows * 0.35 * (1.0 - smoothstep(0.0, 0.5, l));
  c += uHighlights * 0.35 * smoothstep(0.5, 1.0, l);
  c = (c - 0.5) * (1.0 + uContrast) + 0.5;
  c = clamp(c, 0.0, 1.0);
  if (uHue != 0.0 || uSaturation != 0.0 || uVibrance != 0.0) {
    vec3 hsv = rgb2hsv(c);
    hsv.x = fract(hsv.x + uHue * 0.5);
    hsv.y *= 1.0 + uSaturation;
    hsv.y += uVibrance * (1.0 - hsv.y) * hsv.y;
    c = hsv2rgb(vec3(hsv.x, clamp(hsv.y, 0.0, 1.0), hsv.z));
  }
  if (uCurvesOn > 0.5) {
    c = vec3(curve(c.r, 3.0), curve(c.g, 3.0), curve(c.b, 3.0));
    c = vec3(curve(c.r, 0.0), curve(c.g, 1.0), curve(c.b, 2.0));
  }
  if (uLutAmount > 0.0) c = mix(c, lookup(c), uLutAmount);
  c = mix(c, vec3(0.08) + c * 0.84, uFade);
  if (uVignette > 0.0) {
    float v = smoothstep(0.85, 0.25, length(uv - 0.5));
    c *= mix(1.0 - uVignette * 0.8, 1.0, v);
  }
  if (uGrain > 0.0) c += (rand(uv * uResolution + uTime) - 0.5) * uGrain * 0.25;
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
