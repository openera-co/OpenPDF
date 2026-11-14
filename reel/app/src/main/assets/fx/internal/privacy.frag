// Blurs or pixelates an oval on the canvas (faces, plates), feathered at its edge.
uniform sampler2D uTex;
uniform float uCanvasAspect;
uniform vec2 uCenter;     // canvas-normalized, y up
uniform vec2 uSize;       // width and height in canvas-height units
uniform float uMode;      // 0 blur, 1 pixelate, 2 solid
uniform float uStrength;  // 0..1

void main() {
  vec2 p = vTexSamplingCoord;
  vec4 orig = texture2D(uTex, p);
  vec2 d = vec2((p.x - uCenter.x) * uCanvasAspect, p.y - uCenter.y);
  float e = length(d / max(uSize * 0.5, vec2(0.001)));
  float a = 1.0 - smoothstep(0.82, 1.0, e);
  if (a <= 0.0) { gl_FragColor = orig; return; }
  vec3 c;
  if (uMode < 0.5) {
    float r = 0.004 + uStrength * 0.03;
    vec3 acc = vec3(0.0);
    for (int i = -3; i <= 3; i++) {
      for (int j = -3; j <= 3; j++) {
        acc += texture2D(uTex, p + vec2(float(i) * r / uCanvasAspect, float(j) * r)).rgb;
      }
    }
    c = acc / 49.0;
  } else if (uMode < 1.5) {
    float cell = 0.006 + uStrength * 0.05;
    vec2 cells = vec2(cell / uCanvasAspect, cell);
    c = texture2D(uTex, (floor(p / cells) + 0.5) * cells).rgb;
  } else {
    c = vec3(0.0);
  }
  gl_FragColor = vec4(mix(orig.rgb, c, a), 1.0);
}
