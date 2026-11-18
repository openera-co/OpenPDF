#pragma once
#include <cstdint>
#include <vector>

namespace reel {

/** Motion between two frames on a coarse grid: one vector (pixels of the full frame) per cell. */
struct Flow {
    int gw = 0, gh = 0;   // grid size
    float cell = 1;       // cell size in full-frame pixels
    std::vector<float> fx, fy;
    /** The vector at a full-frame position, bilinear between cells. */
    void at(float x, float y, float& ox, float& oy) const;
};

/**
 * Estimates how each part of [a] moved to reach [b] (RGBA, w x h, stride in bytes). Works on a
 * small grey copy with coarse-to-fine block matching, then smooths out stray vectors.
 */
Flow estimateFlow(const uint8_t* a, const uint8_t* b, int w, int h, int stride);

/**
 * The frame a fraction [t] (0..1) of the way from [a] to [b]: each pixel is pulled from both
 * frames along the motion and blended. Where the two disagree (something appeared or vanished),
 * the nearer frame wins.
 */
void interpolateFrame(const uint8_t* a, const uint8_t* b, int w, int h, int stride, const Flow& flow, float t, uint8_t* out);

}  // namespace reel
