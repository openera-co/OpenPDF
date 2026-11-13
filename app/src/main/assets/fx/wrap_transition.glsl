uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float progress;
uniform float ratio;
vec4 getFromColor(vec2 uv) { return texture2D(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture2D(uTo, uv); }
#line 1
//@CODE
void main() {
  gl_FragColor = vec4(transition(vTexSamplingCoord).rgb, 1.0);
}
