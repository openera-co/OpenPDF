#include "interp.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <thread>

namespace reel {
namespace {

struct Gray {
    int w = 0, h = 0;
    std::vector<float> p;
    float at(int x, int y) const {
        x = std::clamp(x, 0, w - 1);
        y = std::clamp(y, 0, h - 1);
        return p[y * w + x];
    }
};

/** Grey copy of an RGBA frame, box-averaged down by [step]. */
Gray toGray(const uint8_t* rgba, int w, int h, int stride, int step) {
    Gray g;
    g.w = w / step;
    g.h = h / step;
    g.p.assign(static_cast<size_t>(g.w) * g.h, 0.f);
    const float norm = 1.f / (255.f * step * step);
    for (int y = 0; y < g.h; ++y) {
        for (int x = 0; x < g.w; ++x) {
            float s = 0;
            for (int j = 0; j < step; ++j) {
                const uint8_t* row = rgba + static_cast<size_t>(y * step + j) * stride + static_cast<size_t>(x * step) * 4;
                for (int i = 0; i < step; ++i) s += 0.299f * row[i * 4] + 0.587f * row[i * 4 + 1] + 0.114f * row[i * 4 + 2];
            }
            g.p[y * g.w + x] = s * norm;
        }
    }
    return g;
}

Gray half(const Gray& g) {
    Gray o;
    o.w = std::max(1, g.w / 2);
    o.h = std::max(1, g.h / 2);
    o.p.resize(static_cast<size_t>(o.w) * o.h);
    for (int y = 0; y < o.h; ++y)
        for (int x = 0; x < o.w; ++x)
            o.p[y * o.w + x] = 0.25f * (g.at(2 * x, 2 * y) + g.at(2 * x + 1, 2 * y) + g.at(2 * x, 2 * y + 1) + g.at(2 * x + 1, 2 * y + 1));
    return o;
}

/** Sum of absolute differences of a (2r+1)^2 window at (ax,ay) in a and (bx,by) in b. */
float sad(const Gray& a, const Gray& b, int ax, int ay, int bx, int by, int r) {
    float s = 0;
    for (int j = -r; j <= r; ++j)
        for (int i = -r; i <= r; ++i) s += std::fabs(a.at(ax + i, ay + j) - b.at(bx + i, by + j));
    return s;
}

/** Best whole-pixel shift within ±range around (gx,gy) for the window at (x,y), plus sub-pixel. */
void search(const Gray& a, const Gray& b, int x, int y, int r, int range, float gx, float gy, float& ox, float& oy) {
    const int cx = static_cast<int>(std::lround(gx));
    const int cy = static_cast<int>(std::lround(gy));
    float best = 1e30f;
    int bx = 0, by = 0;
    // A small penalty for distance keeps flat areas from jumping to far matches.
    for (int dy = -range; dy <= range; ++dy) {
        for (int dx = -range; dx <= range; ++dx) {
            float s = sad(a, b, x, y, x + cx + dx, y + cy + dy, r) * (1.f + 0.01f * (std::abs(dx) + std::abs(dy)));
            if (s < best) { best = s; bx = dx; by = dy; }
        }
    }
    auto parab = [&](float m, float c, float p) { float d = m - 2 * c + p; return d > 1e-6f ? 0.5f * (m - p) / d : 0.f; };
    float sx = 0, sy = 0;
    if (bx > -range && bx < range) {
        float m = sad(a, b, x, y, x + cx + bx - 1, y + cy + by, r), p = sad(a, b, x, y, x + cx + bx + 1, y + cy + by, r);
        sx = std::clamp(parab(m, sad(a, b, x, y, x + cx + bx, y + cy + by, r), p), -0.5f, 0.5f);
    }
    if (by > -range && by < range) {
        float m = sad(a, b, x, y, x + cx + bx, y + cy + by - 1, r), p = sad(a, b, x, y, x + cx + bx, y + cy + by + 1, r);
        sy = std::clamp(parab(m, sad(a, b, x, y, x + cx + bx, y + cy + by, r), p), -0.5f, 0.5f);
    }
    ox = cx + bx + sx;
    oy = cy + by + sy;
}

void median3(std::vector<float>& v, int gw, int gh) {
    std::vector<float> o(v.size());
    for (int y = 0; y < gh; ++y)
        for (int x = 0; x < gw; ++x) {
            float n[9];
            int k = 0;
            for (int j = -1; j <= 1; ++j)
                for (int i = -1; i <= 1; ++i) n[k++] = v[std::clamp(y + j, 0, gh - 1) * gw + std::clamp(x + i, 0, gw - 1)];
            std::nth_element(n, n + 4, n + 9);
            o[y * gw + x] = n[4];
        }
    v.swap(o);
}

void blur3(std::vector<float>& v, int gw, int gh) {
    std::vector<float> o(v.size());
    for (int y = 0; y < gh; ++y)
        for (int x = 0; x < gw; ++x) {
            float s = 0, w = 0;
            for (int j = -1; j <= 1; ++j)
                for (int i = -1; i <= 1; ++i) {
                    float k = (i == 0 ? 2.f : 1.f) * (j == 0 ? 2.f : 1.f);
                    s += k * v[std::clamp(y + j, 0, gh - 1) * gw + std::clamp(x + i, 0, gw - 1)];
                    w += k;
                }
            o[y * gw + x] = s / w;
        }
    v.swap(o);
}

inline void sampleRGBA(const uint8_t* img, int w, int h, int stride, float x, float y, float out[4]) {
    x = std::clamp(x, 0.f, w - 1.001f);
    y = std::clamp(y, 0.f, h - 1.001f);
    const int x0 = static_cast<int>(x), y0 = static_cast<int>(y);
    const float fx = x - x0, fy = y - y0;
    const uint8_t* p00 = img + static_cast<size_t>(y0) * stride + static_cast<size_t>(x0) * 4;
    const uint8_t* p01 = p00 + 4;
    const uint8_t* p10 = p00 + stride;
    const uint8_t* p11 = p10 + 4;
    for (int c = 0; c < 4; ++c) {
        float top = p00[c] + (p01[c] - p00[c]) * fx;
        float bot = p10[c] + (p11[c] - p10[c]) * fx;
        out[c] = top + (bot - top) * fy;
    }
}

}  // namespace

void Flow::at(float x, float y, float& ox, float& oy) const {
    float gx = std::clamp(x / cell - 0.5f, 0.f, gw - 1.001f);
    float gy = std::clamp(y / cell - 0.5f, 0.f, gh - 1.001f);
    if (gw == 1) gx = 0;
    if (gh == 1) gy = 0;
    const int x0 = static_cast<int>(gx), y0 = static_cast<int>(gy);
    const int x1 = std::min(x0 + 1, gw - 1), y1 = std::min(y0 + 1, gh - 1);
    const float tx = gx - x0, ty = gy - y0;
    auto lerp2 = [&](const std::vector<float>& v) {
        float top = v[y0 * gw + x0] + (v[y0 * gw + x1] - v[y0 * gw + x0]) * tx;
        float bot = v[y1 * gw + x0] + (v[y1 * gw + x1] - v[y1 * gw + x0]) * tx;
        return top + (bot - top) * ty;
    };
    ox = lerp2(fx);
    oy = lerp2(fy);
}

Flow estimateFlow(const uint8_t* a, const uint8_t* b, int w, int h, int stride) {
    // Work at about 320 px wide: enough detail for motion, quick on a phone.
    const int step = std::max(1, w / 320);
    Gray a0 = toGray(a, w, h, stride, step), b0 = toGray(b, w, h, stride, step);
    Gray a1 = half(a0), b1 = half(b0);
    Gray a2 = half(a1), b2 = half(b1);

    Flow f;
    const int block = 8;  // cell size at the working scale
    f.gw = std::max(1, a0.w / block);
    f.gh = std::max(1, a0.h / block);
    f.cell = static_cast<float>(w) / f.gw;
    const size_t n = static_cast<size_t>(f.gw) * f.gh;
    f.fx.assign(n, 0.f);
    f.fy.assign(n, 0.f);
    for (int y = 0; y < f.gh; ++y) {
        for (int x = 0; x < f.gw; ++x) {
            // Cell centre at each level.
            const float c0x = (x + 0.5f) * a0.w / f.gw, c0y = (y + 0.5f) * a0.h / f.gh;
            float vx, vy;
            // Quarter size: wide search (±8 there is ±32 at the working scale).
            search(a2, b2, static_cast<int>(c0x / 4), static_cast<int>(c0y / 4), 3, 8, 0, 0, vx, vy);
            search(a1, b1, static_cast<int>(c0x / 2), static_cast<int>(c0y / 2), 4, 2, vx * 2, vy * 2, vx, vy);
            search(a0, b0, static_cast<int>(c0x), static_cast<int>(c0y), 5, 2, vx * 2, vy * 2, vx, vy);
            f.fx[y * f.gw + x] = vx * step;
            f.fy[y * f.gw + x] = vy * step;
        }
    }
    median3(f.fx, f.gw, f.gh);
    median3(f.fy, f.gw, f.gh);
    blur3(f.fx, f.gw, f.gh);
    blur3(f.fy, f.gw, f.gh);
    return f;
}

namespace {
void interpolateRows(const uint8_t* a, const uint8_t* b, int w, int h, int stride, const Flow& flow, float t, uint8_t* out, int y0, int y1) {
    float pa[4], pb[4];
    for (int y = y0; y < y1; ++y) {
        uint8_t* row = out + static_cast<size_t>(y) * stride;
        for (int x = 0; x < w; ++x) {
            float vx, vy;
            flow.at(static_cast<float>(x), static_cast<float>(y), vx, vy);
            sampleRGBA(a, w, h, stride, x - t * vx, y - t * vy, pa);
            sampleRGBA(b, w, h, stride, x + (1 - t) * vx, y + (1 - t) * vy, pb);
            // Agreeing samples blend by time; where they disagree the nearer frame takes over.
            float diff = (std::fabs(pa[0] - pb[0]) + std::fabs(pa[1] - pb[1]) + std::fabs(pa[2] - pb[2])) / (3 * 255.f);
            float k = std::clamp((diff - 0.12f) / 0.2f, 0.f, 1.f);
            float wb = t + (t < 0.5f ? -t : 1 - t) * k;
            for (int c = 0; c < 4; ++c) row[x * 4 + c] = static_cast<uint8_t>(std::clamp(pa[c] + (pb[c] - pa[c]) * wb + 0.5f, 0.f, 255.f));
        }
    }
}
}  // namespace

void interpolateFrame(const uint8_t* a, const uint8_t* b, int w, int h, int stride, const Flow& flow, float t, uint8_t* out) {
    // Rows are independent: split them over a few threads.
    const int n = std::clamp(static_cast<int>(std::thread::hardware_concurrency()), 1, 6);
    if (n == 1 || h < 64) { interpolateRows(a, b, w, h, stride, flow, t, out, 0, h); return; }
    std::vector<std::thread> pool;
    for (int i = 0; i < n; ++i) {
        const int y0 = h * i / n, y1 = h * (i + 1) / n;
        pool.emplace_back(interpolateRows, a, b, w, h, stride, std::cref(flow), t, out, y0, y1);
    }
    for (auto& th : pool) th.join();
}

}  // namespace reel
