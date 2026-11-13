#include "image_ops.h"

#include <cstring>

namespace reel {

void histogramRgba(const uint8_t* pixels, int width, int height, int stride, uint32_t* hist) {
    std::memset(hist, 0, sizeof(uint32_t) * 1024);
    if (pixels == nullptr || width <= 0 || height <= 0) return;
    for (int y = 0; y < height; ++y) {
        const uint8_t* row = pixels + static_cast<size_t>(y) * stride;
        for (int x = 0; x < width; ++x) {
            const uint8_t* p = row + x * 4;
            uint32_t r = p[0], g = p[1], b = p[2];
            hist[r]++;
            hist[256 + g]++;
            hist[512 + b]++;
            hist[768 + ((r * 54 + g * 183 + b * 19) >> 8)]++;
        }
    }
}

size_t pcm16ToMono(const int16_t* in, size_t samples, int channels, float* out) {
    if (in == nullptr || out == nullptr || channels <= 0) return 0;
    const size_t frames = samples / static_cast<size_t>(channels);
    const float scale = 1.f / (32768.f * channels);
    for (size_t f = 0; f < frames; ++f) {
        int acc = 0;
        for (int c = 0; c < channels; ++c) acc += in[f * channels + c];
        out[f] = acc * scale;
    }
    return frames;
}

}  // namespace reel
