uniform sampler2D uTex;
vec4 getColor(vec2 uv) { return texture2D(uTex, clamp(uv, 0.0, 1.0)); }
//@PARAMS
#line 1
//@CODE
void main() {
  vec4 src = texture2D(uTex, vTexSamplingCoord);
  vec4 fx = effect(vTexSamplingCoord);
  gl_FragColor = vec4(mix(src.rgb, fx.rgb, clamp(uIntensity, 0.0, 1.0)), 1.0);
}
