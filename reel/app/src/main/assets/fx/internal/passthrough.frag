uniform sampler2D uTex;
void main() {
  gl_FragColor = vec4(texture2D(uTex, vTexSamplingCoord).rgb, 1.0);
}
