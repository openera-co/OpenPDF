// Small CPU image helpers used off the render thread (histograms for the Adjust panel).
#pragma once

#include <cstddef>
#include <cstdint>

namespace reel {

// Fills hist (4 x 256: R, G, B, luma) from RGBA_8888 pixels. stride is in bytes.
void histogramRgba(const uint8_t* pixels, int width, int height, int stride, uint32_t* hist);

// Downmixes interleaved little-endian PCM16 into mono float [-1,1]. Returns frames written.
size_t pcm16ToMono(const int16_t* in, size_t samples, int channels, float* out);

}  // namespace reel
