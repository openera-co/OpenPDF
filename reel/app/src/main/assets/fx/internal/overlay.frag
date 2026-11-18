// Draws one premultiplied RGBA layer (text, sticker, image) with a 2D transform.
// The host enables blending (ONE, ONE_MINUS_SRC_ALPHA) after a passthrough of the frame.
uniform sampler2D uLayer;
uniform float uCanvasAspect;
uniform vec2 uCenter;      // canvas-normalized, y up
uniform vec2 uSize;        // layer size: x in canvas-height units, y in canvas heights
uniform float uRotation;   // radians, clockwise
uniform float uOpacity;

void main() {
  vec2 p = vTexSamplingCoord;
  vec2 P = vec2(p.x * uCanvasAspect, p.y);
  vec2 C = vec2(uCenter.x * uCanvasAspect, uCenter.y);
  vec2 d = P - C;
  float s = sin(uRotation), co = cos(uRotation);
  d = vec2(co * d.x - s * d.y, s * d.x + co * d.y);
  vec2 uv = d / max(uSize, vec2(0.0001)) + 0.5;
  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) discard;
  uv.y = 1.0 - uv.y;
  gl_FragColor = texture2D(uLayer, uv) * uOpacity;
}
