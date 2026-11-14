// Procedural color looks rendered into 3D lookup tables.
// Every look is defined by a small parameter set and evaluated per lattice point,
// so the library ships with no image assets and any look can be regenerated at any size.
#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace reel {

struct LookParams {
    // Tone
    float exposure = 0.f;      // stops
    float contrast = 0.f;      // -1..1, S-curve strength around 0.5
    float lift = 0.f;          // raises blacks (fade)
    float gamma = 1.f;
    float gain = 1.f;
    // Color
    float saturation = 1.f;
    float vibrance = 0.f;
    float temperature = 0.f;   // -1 cool .. 1 warm
    float tint = 0.f;          // -1 green .. 1 magenta
    float hueShift = 0.f;      // degrees
    // Split toning (applied by luminance)
    float shadowR = 0.f, shadowG = 0.f, shadowB = 0.f;
    float highR = 0.f, highG = 0.f, highB = 0.f;
    float splitBalance = 0.f;  // -1..1
    // Monochrome: 0 = color, 1 = full mono (weighted by mixR/G/B)
    float mono = 0.f;
    float monoR = 0.299f, monoG = 0.587f, monoB = 0.114f;
    // Channel crossover for film-like hue rotations (row-major 3x3, identity by default)
    float mix[9] = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    // Crush highlights / roll-off
    float highlightRolloff = 0.f;
};

struct LookInfo {
    const char* id;
    const char* name;
    const char* category;
};

// Number of built-in looks.
int lookCount();
const LookInfo& lookInfo(int index);
LookParams lookParams(int index);

// Applies a look to a single RGB triple in [0,1]. Exposed for tests.
void applyLook(const LookParams& p, float& r, float& g, float& b);

// Renders an N x N x N LUT into ARGB_8888 ints, index ((r * N + g) * N + b).
// That layout matches a bitmap of width N and height N*N where row = r*N + g, column = b.
std::vector<uint32_t> renderLut(const LookParams& p, int n);

}  // namespace reel
