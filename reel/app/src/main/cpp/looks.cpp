#include "looks.h"

#include <algorithm>
#include <cmath>
#include <functional>

namespace reel {
namespace {

inline float clamp01(float v) { return v < 0.f ? 0.f : (v > 1.f ? 1.f : v); }
inline float lerp(float a, float b, float t) { return a + (b - a) * t; }
inline float luma(float r, float g, float b) { return 0.2126f * r + 0.7152f * g + 0.0722f * b; }
inline float smoothstep(float e0, float e1, float x) {
    float t = clamp01((x - e0) / (e1 - e0));
    return t * t * (3.f - 2.f * t);
}

void rgbToHsl(float r, float g, float b, float& h, float& s, float& l) {
    float mx = std::max(r, std::max(g, b)), mn = std::min(r, std::min(g, b));
    l = (mx + mn) * 0.5f;
    if (mx - mn < 1e-6f) { h = 0.f; s = 0.f; return; }
    float d = mx - mn;
    s = l > 0.5f ? d / (2.f - mx - mn) : d / (mx + mn);
    if (mx == r) h = (g - b) / d + (g < b ? 6.f : 0.f);
    else if (mx == g) h = (b - r) / d + 2.f;
    else h = (r - g) / d + 4.f;
    h /= 6.f;
}

float hueToRgb(float p, float q, float t) {
    if (t < 0.f) t += 1.f;
    if (t > 1.f) t -= 1.f;
    if (t < 1.f / 6.f) return p + (q - p) * 6.f * t;
    if (t < 0.5f) return q;
    if (t < 2.f / 3.f) return p + (q - p) * (2.f / 3.f - t) * 6.f;
    return p;
}

void hslToRgb(float h, float s, float l, float& r, float& g, float& b) {
    if (s < 1e-6f) { r = g = b = l; return; }
    float q = l < 0.5f ? l * (1.f + s) : l + s - l * s;
    float p = 2.f * l - q;
    r = hueToRgb(p, q, h + 1.f / 3.f);
    g = hueToRgb(p, q, h);
    b = hueToRgb(p, q, h - 1.f / 3.f);
}

// S-curve that keeps 0 and 1 fixed; k in [-1,1].
inline float sCurve(float x, float k) {
    if (k == 0.f) return x;
    float c = x - 0.5f;
    if (k > 0.f) {
        float s = 1.f / (1.f + std::exp(-c * (4.f + 10.f * k)));
        float s0 = 1.f / (1.f + std::exp(0.5f * (4.f + 10.f * k)));
        float s1 = 1.f / (1.f + std::exp(-0.5f * (4.f + 10.f * k)));
        return lerp(x, (s - s0) / (s1 - s0), k);
    }
    // Negative: flatten toward mid grey.
    return lerp(x, 0.5f + c * 0.6f, -k);
}

struct Entry {
    LookInfo info;
    std::function<void(LookParams&)> set;
};

void tone(LookParams& p, float r, float g, float b, float hr, float hg, float hb) {
    p.shadowR = r; p.shadowG = g; p.shadowB = b;
    p.highR = hr; p.highG = hg; p.highB = hb;
}

void crossMix(LookParams& p, std::initializer_list<float> m) {
    int i = 0;
    for (float v : m) p.mix[i++] = v;
}

const std::vector<Entry>& table() {
    static const std::vector<Entry> t = {
        // Life
        {{"fresh", "Fresh", "Life"}, [](LookParams& p) { p.exposure = 0.1f; p.vibrance = 0.35f; p.contrast = 0.15f; }},
        {{"bright", "Bright", "Life"}, [](LookParams& p) { p.exposure = 0.3f; p.lift = 0.03f; p.saturation = 1.08f; }},
        {{"glow", "Glow", "Life"}, [](LookParams& p) { p.exposure = 0.2f; p.highlightRolloff = 0.35f; p.temperature = 0.15f; p.lift = 0.04f; }},
        {{"clear", "Clear", "Life"}, [](LookParams& p) { p.contrast = 0.25f; p.vibrance = 0.2f; p.temperature = -0.05f; }},
        {{"peach", "Peach", "Life"}, [](LookParams& p) { p.temperature = 0.25f; p.tint = 0.12f; p.lift = 0.03f; p.saturation = 1.05f; }},
        {{"milk", "Milk", "Life"}, [](LookParams& p) { p.lift = 0.1f; p.contrast = -0.25f; p.saturation = 0.85f; p.exposure = 0.15f; }},
        {{"soft", "Soft", "Life"}, [](LookParams& p) { p.contrast = -0.15f; p.highlightRolloff = 0.3f; p.saturation = 0.95f; }},
        {{"pop", "Pop", "Life"}, [](LookParams& p) { p.saturation = 1.35f; p.contrast = 0.2f; }},
        // Food
        {{"tasty", "Tasty", "Food"}, [](LookParams& p) { p.temperature = 0.2f; p.vibrance = 0.4f; p.contrast = 0.12f; }},
        {{"crisp", "Crisp", "Food"}, [](LookParams& p) { p.contrast = 0.3f; p.exposure = 0.1f; p.vibrance = 0.25f; }},
        {{"brunch", "Brunch", "Food"}, [](LookParams& p) { p.exposure = 0.25f; p.temperature = 0.1f; p.lift = 0.05f; }},
        // Travel
        {{"azure", "Azure", "Travel"}, [](LookParams& p) { p.temperature = -0.15f; p.vibrance = 0.3f; p.contrast = 0.1f; }},
        {{"desert", "Desert", "Travel"}, [](LookParams& p) { p.temperature = 0.35f; p.saturation = 0.9f; p.contrast = 0.15f; }},
        {{"tropic", "Tropic", "Travel"}, [](LookParams& p) { p.hueShift = -6.f; p.vibrance = 0.45f; p.contrast = 0.1f; }},
        {{"alpine", "Alpine", "Travel"}, [](LookParams& p) { p.temperature = -0.25f; p.exposure = 0.15f; p.contrast = 0.2f; }},
        {{"golden", "Golden hour", "Travel"}, [](LookParams& p) { p.temperature = 0.45f; p.tint = 0.05f; p.highlightRolloff = 0.25f; tone(p, 0.f, -0.02f, -0.04f, 0.06f, 0.03f, -0.04f); }},
        {{"seaside", "Seaside", "Travel"}, [](LookParams& p) { tone(p, -0.02f, 0.03f, 0.06f, 0.04f, 0.03f, 0.f); p.vibrance = 0.2f; }},
        // Film
        {{"kodachrome", "K-Chrome", "Film"}, [](LookParams& p) { p.contrast = 0.3f; p.saturation = 1.15f; crossMix(p, {1.05f, -0.03f, -0.02f, -0.02f, 1.02f, 0.f, 0.f, -0.05f, 1.05f}); p.temperature = 0.1f; }},
        {{"portra", "Portrait 4", "Film"}, [](LookParams& p) { p.contrast = -0.1f; p.lift = 0.04f; p.temperature = 0.12f; p.saturation = 0.9f; tone(p, 0.f, 0.01f, 0.02f, 0.03f, 0.01f, -0.02f); }},
        {{"velvia", "Vivid 50", "Film"}, [](LookParams& p) { p.saturation = 1.4f; p.contrast = 0.35f; p.lift = -0.02f; }},
        {{"fuji400", "Super 400", "Film"}, [](LookParams& p) { crossMix(p, {0.98f, 0.02f, 0.f, 0.f, 1.02f, -0.02f, 0.f, 0.04f, 0.96f}); tone(p, 0.f, 0.02f, 0.02f, 0.f, 0.01f, -0.02f); p.lift = 0.03f; }},
        {{"ektar", "Ektar", "Film"}, [](LookParams& p) { p.saturation = 1.25f; p.contrast = 0.25f; p.temperature = 0.08f; }},
        {{"cine800", "Cine 800", "Film"}, [](LookParams& p) { p.temperature = -0.2f; tone(p, 0.f, 0.03f, 0.05f, 0.08f, 0.02f, -0.06f); p.highlightRolloff = 0.4f; p.contrast = 0.1f; }},
        {{"faded", "Faded", "Film"}, [](LookParams& p) { p.lift = 0.12f; p.contrast = -0.2f; p.saturation = 0.75f; }},
        {{"expired", "Expired", "Film"}, [](LookParams& p) { p.hueShift = 8.f; p.lift = 0.08f; tone(p, 0.03f, 0.f, 0.06f, 0.06f, 0.05f, -0.03f); p.saturation = 0.85f; }},
        {{"instant", "Instant", "Film"}, [](LookParams& p) { p.lift = 0.09f; p.temperature = 0.18f; p.tint = -0.06f; p.contrast = -0.05f; p.highlightRolloff = 0.3f; }},
        // Cinematic
        {{"tealorange", "Teal Orange", "Cinematic"}, [](LookParams& p) { tone(p, -0.03f, 0.03f, 0.07f, 0.08f, 0.03f, -0.05f); p.contrast = 0.25f; p.saturation = 1.05f; }},
        {{"blockbuster", "Blockbuster", "Cinematic"}, [](LookParams& p) { tone(p, -0.04f, 0.04f, 0.08f, 0.1f, 0.04f, -0.06f); p.contrast = 0.35f; p.lift = -0.02f; }},
        {{"noirgreen", "Matrix", "Cinematic"}, [](LookParams& p) { crossMix(p, {0.85f, 0.1f, 0.f, 0.05f, 1.05f, 0.f, 0.f, 0.15f, 0.8f}); p.contrast = 0.2f; p.saturation = 0.8f; }},
        {{"bleach", "Bleach bypass", "Cinematic"}, [](LookParams& p) { p.saturation = 0.45f; p.contrast = 0.4f; p.highlightRolloff = 0.1f; }},
        {{"dune", "Dune", "Cinematic"}, [](LookParams& p) { p.temperature = 0.5f; p.saturation = 0.7f; p.contrast = 0.2f; p.lift = 0.02f; }},
        {{"arctic", "Arctic", "Cinematic"}, [](LookParams& p) { p.temperature = -0.5f; p.saturation = 0.7f; p.contrast = 0.15f; p.exposure = 0.1f; }},
        {{"neonnight", "Neon night", "Cinematic"}, [](LookParams& p) { tone(p, 0.04f, -0.02f, 0.08f, 0.06f, -0.01f, 0.06f); p.saturation = 1.3f; p.contrast = 0.3f; }},
        {{"moody", "Moody", "Cinematic"}, [](LookParams& p) { p.exposure = -0.2f; p.saturation = 0.75f; p.contrast = 0.2f; tone(p, 0.f, 0.02f, 0.04f, 0.02f, 0.01f, 0.f); }},
        {{"western", "Western", "Cinematic"}, [](LookParams& p) { p.temperature = 0.4f; p.contrast = 0.3f; p.saturation = 0.8f; p.lift = 0.03f; }},
        // Mood
        {{"dreamy", "Dreamy", "Mood"}, [](LookParams& p) { p.lift = 0.08f; p.highlightRolloff = 0.45f; p.tint = 0.1f; p.saturation = 0.9f; }},
        {{"melancholy", "Blue hour", "Mood"}, [](LookParams& p) { p.temperature = -0.35f; p.exposure = -0.1f; p.saturation = 0.8f; }},
        {{"romance", "Rose", "Mood"}, [](LookParams& p) { p.tint = 0.25f; p.temperature = 0.1f; p.lift = 0.05f; }},
        {{"lavender", "Lavender", "Mood"}, [](LookParams& p) { tone(p, 0.03f, 0.f, 0.06f, 0.04f, 0.02f, 0.05f); p.saturation = 0.85f; p.lift = 0.04f; }},
        {{"mint", "Mint", "Mood"}, [](LookParams& p) { tone(p, 0.f, 0.05f, 0.03f, 0.f, 0.03f, 0.02f); p.saturation = 0.9f; }},
        {{"storm", "Storm", "Mood"}, [](LookParams& p) { p.saturation = 0.55f; p.temperature = -0.2f; p.contrast = 0.25f; p.exposure = -0.15f; }},
        {{"sunset", "Sunset", "Mood"}, [](LookParams& p) { tone(p, 0.06f, -0.01f, 0.04f, 0.1f, 0.04f, -0.05f); p.temperature = 0.2f; p.saturation = 1.1f; }},
        {{"forest", "Forest", "Mood"}, [](LookParams& p) { crossMix(p, {0.95f, 0.05f, 0.f, 0.f, 1.05f, -0.05f, 0.f, 0.05f, 0.95f}); p.contrast = 0.15f; p.temperature = 0.05f; }},
        // Retro
        {{"vhs", "VHS", "Retro"}, [](LookParams& p) { p.saturation = 1.2f; p.hueShift = -5.f; p.lift = 0.06f; tone(p, 0.03f, 0.f, 0.05f, 0.04f, 0.02f, -0.02f); }},
        {{"seventies", "70s", "Retro"}, [](LookParams& p) { p.temperature = 0.35f; p.saturation = 0.85f; p.lift = 0.08f; p.tint = 0.05f; }},
        {{"eighties", "80s", "Retro"}, [](LookParams& p) { tone(p, 0.05f, 0.f, 0.08f, 0.06f, 0.f, 0.06f); p.saturation = 1.25f; p.contrast = 0.15f; }},
        {{"polaroid", "Polaroid", "Retro"}, [](LookParams& p) { p.lift = 0.1f; p.temperature = 0.15f; p.tint = 0.08f; p.contrast = -0.1f; p.saturation = 0.85f; }},
        {{"sepia", "Sepia", "Retro"}, [](LookParams& p) { p.mono = 1.f; tone(p, 0.08f, 0.04f, -0.02f, 0.1f, 0.06f, 0.f); p.lift = 0.03f; }},
        {{"crossprocess", "Cross process", "Retro"}, [](LookParams& p) { crossMix(p, {1.1f, -0.05f, 0.f, 0.f, 1.1f, -0.1f, -0.1f, 0.05f, 0.9f}); p.contrast = 0.3f; p.saturation = 1.2f; }},
        {{"lomo", "Lomo", "Retro"}, [](LookParams& p) { p.contrast = 0.45f; p.saturation = 1.3f; tone(p, 0.f, 0.f, 0.06f, 0.06f, 0.04f, 0.f); }},
        // Mono
        {{"mono", "Mono", "Mono"}, [](LookParams& p) { p.mono = 1.f; }},
        {{"noir", "Noir", "Mono"}, [](LookParams& p) { p.mono = 1.f; p.contrast = 0.5f; p.lift = -0.03f; }},
        {{"silver", "Silver", "Mono"}, [](LookParams& p) { p.mono = 1.f; p.contrast = 0.2f; p.lift = 0.05f; tone(p, 0.f, 0.f, 0.02f, 0.f, 0.f, 0.01f); }},
        {{"redfilter", "Red filter", "Mono"}, [](LookParams& p) { p.mono = 1.f; p.monoR = 0.7f; p.monoG = 0.25f; p.monoB = 0.05f; p.contrast = 0.3f; }},
        {{"bluefilter", "Blue filter", "Mono"}, [](LookParams& p) { p.mono = 1.f; p.monoR = 0.1f; p.monoG = 0.3f; p.monoB = 0.6f; p.contrast = 0.2f; }},
        {{"selenium", "Selenium", "Mono"}, [](LookParams& p) { p.mono = 1.f; tone(p, 0.04f, 0.f, 0.05f, 0.03f, 0.02f, 0.f); p.contrast = 0.15f; }},
        {{"cyanotype", "Cyanotype", "Mono"}, [](LookParams& p) { p.mono = 1.f; tone(p, -0.06f, 0.02f, 0.12f, -0.02f, 0.04f, 0.1f); p.lift = 0.04f; }},
        {{"fadedmono", "Faded mono", "Mono"}, [](LookParams& p) { p.mono = 1.f; p.lift = 0.12f; p.contrast = -0.2f; }},
    };
    return t;
}

}  // namespace

int lookCount() { return static_cast<int>(table().size()); }

const LookInfo& lookInfo(int index) { return table().at(static_cast<size_t>(index)).info; }

LookParams lookParams(int index) {
    LookParams p;
    table().at(static_cast<size_t>(index)).set(p);
    return p;
}

void applyLook(const LookParams& p, float& r, float& g, float& b) {
    // Exposure in linear-ish space
    float e = std::pow(2.f, p.exposure);
    r *= e; g *= e; b *= e;

    // Channel crossover
    float mr = p.mix[0] * r + p.mix[1] * g + p.mix[2] * b;
    float mg = p.mix[3] * r + p.mix[4] * g + p.mix[5] * b;
    float mb = p.mix[6] * r + p.mix[7] * g + p.mix[8] * b;
    r = mr; g = mg; b = mb;

    // White balance: temperature pushes R/B, tint pushes G
    r *= 1.f + 0.18f * p.temperature;
    b *= 1.f - 0.18f * p.temperature;
    g *= 1.f - 0.12f * p.tint;

    // Highlight roll-off (soft shoulder)
    if (p.highlightRolloff > 0.f) {
        auto shoulder = [&](float x) {
            float k = p.highlightRolloff;
            return x < 0.6f ? x : 0.6f + (1.f - std::exp(-(x - 0.6f) / (0.4f * (1.f - 0.6f * k) + 1e-3f))) * 0.4f;
        };
        r = shoulder(r); g = shoulder(g); b = shoulder(b);
    }

    r = clamp01(r); g = clamp01(g); b = clamp01(b);

    // Lift / gamma / gain
    auto lgg = [&](float x) {
        x = x * p.gain;
        x = p.lift + x * (1.f - p.lift);
        x = clamp01(x);
        return std::pow(x, 1.f / p.gamma);
    };
    r = lgg(r); g = lgg(g); b = lgg(b);

    // Contrast
    r = clamp01(sCurve(r, p.contrast));
    g = clamp01(sCurve(g, p.contrast));
    b = clamp01(sCurve(b, p.contrast));

    // Hue and saturation
    if (p.hueShift != 0.f || p.saturation != 1.f || p.vibrance != 0.f) {
        float h, s, l;
        rgbToHsl(r, g, b, h, s, l);
        h += p.hueShift / 360.f;
        h -= std::floor(h);
        s *= p.saturation;
        s += p.vibrance * (1.f - s) * s;  // boosts muted colors most
        hslToRgb(h, clamp01(s), l, r, g, b);
    }

    // Monochrome
    if (p.mono > 0.f) {
        float y = clamp01(p.monoR * r + p.monoG * g + p.monoB * b);
        r = lerp(r, y, p.mono); g = lerp(g, y, p.mono); b = lerp(b, y, p.mono);
    }

    // Split toning weighted by luminance
    float y = luma(r, g, b);
    float hw = smoothstep(0.35f + 0.2f * p.splitBalance, 1.f, y);
    float sw = 1.f - smoothstep(0.f, 0.65f + 0.2f * p.splitBalance, y);
    r += p.shadowR * sw + p.highR * hw;
    g += p.shadowG * sw + p.highG * hw;
    b += p.shadowB * sw + p.highB * hw;

    r = clamp01(r); g = clamp01(g); b = clamp01(b);
}

std::vector<uint32_t> renderLut(const LookParams& p, int n) {
    std::vector<uint32_t> out(static_cast<size_t>(n) * n * n);
    const float inv = 1.f / static_cast<float>(n - 1);
    for (int ri = 0; ri < n; ++ri) {
        for (int gi = 0; gi < n; ++gi) {
            for (int bi = 0; bi < n; ++bi) {
                float r = ri * inv, g = gi * inv, b = bi * inv;
                applyLook(p, r, g, b);
                auto q = [](float v) { return static_cast<uint32_t>(std::lround(clamp01(v) * 255.f)); };
                out[(static_cast<size_t>(ri) * n + gi) * n + bi] = 0xFF000000u | (q(r) << 16) | (q(g) << 8) | q(b);
            }
        }
    }
    return out;
}

}  // namespace reel
