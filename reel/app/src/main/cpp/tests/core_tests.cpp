// Host-side tests for the native core. Run: cmake -S . -B build && cmake --build build && ctest --test-dir build
#include <cmath>
#include <cstdio>
#include <algorithm>
#include <cstdlib>
#include <set>
#include <string>
#include <vector>

#include "../audio_analysis.h"
#include "../image_ops.h"
#include "../looks.h"
#include "../interp.h"

static int failures = 0;
#define CHECK(cond, ...)                                              \
    do {                                                              \
        if (!(cond)) {                                                \
            ++failures;                                               \
            std::fprintf(stderr, "FAIL %s:%d: ", __FILE__, __LINE__); \
            std::fprintf(stderr, __VA_ARGS__);                        \
            std::fprintf(stderr, "\n");                               \
        }                                                             \
    } while (0)

static void testIdentityLut() {
    reel::LookParams p;  // defaults must be an identity transform
    const int n = 17;
    auto lut = reel::renderLut(p, n);
    CHECK(lut.size() == static_cast<size_t>(n * n * n), "size %zu", lut.size());
    int worst = 0;
    for (int r = 0; r < n; ++r)
        for (int g = 0; g < n; ++g)
            for (int b = 0; b < n; ++b) {
                uint32_t c = lut[(r * n + g) * n + b];
                int er = static_cast<int>(std::lround(r * 255.0 / (n - 1)));
                int eg = static_cast<int>(std::lround(g * 255.0 / (n - 1)));
                int eb = static_cast<int>(std::lround(b * 255.0 / (n - 1)));
                worst = std::max({worst, std::abs(static_cast<int>((c >> 16) & 255) - er), std::abs(static_cast<int>((c >> 8) & 255) - eg), std::abs(static_cast<int>(c & 255) - eb)});
                CHECK((c >> 24) == 255u, "alpha must be opaque");
            }
    CHECK(worst <= 1, "identity LUT deviates by %d levels", worst);
}

static void testLookCatalog() {
    int count = reel::lookCount();
    CHECK(count >= 55, "expected at least 55 looks, got %d", count);
    std::set<std::string> ids;
    for (int i = 0; i < count; ++i) {
        const auto& info = reel::lookInfo(i);
        CHECK(ids.insert(info.id).second, "duplicate look id %s", info.id);
        auto lut = reel::renderLut(reel::lookParams(i), 9);
        bool identity = true;
        for (int k = 0; k < 9 * 9 * 9 && identity; ++k) {
            int r = k / 81, g = (k / 9) % 9, b = k % 9;
            uint32_t c = lut[static_cast<size_t>(k)];
            if (std::abs(static_cast<int>((c >> 16) & 255) - r * 255 / 8) > 2 || std::abs(static_cast<int>((c >> 8) & 255) - g * 255 / 8) > 2 || std::abs(static_cast<int>(c & 255) - b * 255 / 8) > 2) identity = false;
        }
        CHECK(!identity, "look %s does nothing", info.id);
        // Black and white endpoints must stay ordered (no inversion).
        uint32_t black = lut[0], white = lut[9 * 9 * 9 - 1];
        auto lum = [](uint32_t c) { return ((c >> 16) & 255) * 0.2126 + ((c >> 8) & 255) * 0.7152 + (c & 255) * 0.0722; };
        CHECK(lum(white) > lum(black) + 100, "look %s collapses tonal range", info.id);
    }
}

static void testMonoLook() {
    for (int i = 0; i < reel::lookCount(); ++i) {
        if (std::string(reel::lookInfo(i).id) != "mono") continue;
        float r = 0.8f, g = 0.2f, b = 0.4f;
        reel::applyLook(reel::lookParams(i), r, g, b);
        CHECK(std::fabs(r - g) < 1e-4f && std::fabs(g - b) < 1e-4f, "mono not grey: %f %f %f", r, g, b);
        return;
    }
    CHECK(false, "mono look missing");
}

static void testPeaks() {
    std::vector<float> s(1000, 0.f);
    s[10] = -0.9f;
    s[990] = 0.5f;
    auto p = reel::computePeaks(s.data(), s.size(), 10);
    CHECK(p.size() == 10, "bucket count");
    CHECK(std::fabs(p[0] - 0.9f) < 1e-6f, "first bucket %f", p[0]);
    CHECK(std::fabs(p[9] - 0.5f) < 1e-6f, "last bucket %f", p[9]);
    CHECK(p[5] == 0.f, "silent bucket");
    auto empty = reel::computePeaks(nullptr, 0, 4);
    CHECK(empty.size() == 4 && empty[0] == 0.f, "empty input");
    // More buckets than samples must not read out of bounds.
    auto many = reel::computePeaks(s.data(), 3, 8);
    CHECK(many.size() == 8, "oversampled bucket count");
}

static void testFft() {
    const int n = 256;
    std::vector<float> re(n), im(n, 0.f);
    for (int i = 0; i < n; ++i) re[i] = static_cast<float>(std::sin(2 * M_PI * 16 * i / n));
    reel::fft(re.data(), im.data(), n);
    int best = 0;
    float bv = 0;
    for (int k = 0; k < n / 2; ++k) {
        float m = std::sqrt(re[k] * re[k] + im[k] * im[k]);
        if (m > bv) { bv = m; best = k; }
    }
    CHECK(best == 16, "fft peak at %d", best);
    CHECK(std::fabs(bv - n / 2.f) < 0.5f, "fft magnitude %f", bv);
}

static std::vector<float> clickTrack(double bpm, double seconds, int sr, double offset) {
    std::vector<float> s(static_cast<size_t>(seconds * sr), 0.f);
    unsigned seed = 7;
    for (auto& v : s) { seed = seed * 1103515245u + 12345u; v = ((seed >> 16) % 1000 / 1000.f - 0.5f) * 0.02f; }
    for (double t = offset; t < seconds; t += 60.0 / bpm) {
        size_t start = static_cast<size_t>(t * sr);
        for (size_t k = 0; k < 2000 && start + k < s.size(); ++k) {
            s[start + k] += static_cast<float>(std::exp(-static_cast<double>(k) / 300.0) * std::sin(2 * M_PI * 1000 * k / sr) * 0.8);
        }
    }
    return s;
}

static void testBeats() {
    const int sr = 44100;
    for (double bpm : {90.0, 120.0, 140.0}) {
        auto s = clickTrack(bpm, 12.0, sr, 0.25);
        auto res = reel::detectBeats(s.data(), s.size(), sr);
        double ratio = res.bpm / bpm;
        bool ok = std::fabs(ratio - 1) < 0.03 || std::fabs(ratio - 2) < 0.06 || std::fabs(ratio - 0.5) < 0.02;
        CHECK(ok, "bpm %f expected %f", res.bpm, bpm);
        CHECK(res.beatTimesSec.size() > 10, "few beats: %zu", res.beatTimesSec.size());
        // Every detected beat should land near a real click when tempo matched.
        if (std::fabs(ratio - 1) < 0.03) {
            int near = 0;
            double period = 60.0 / bpm;
            for (double t : res.beatTimesSec) {
                double ph = std::fmod(t - 0.25 + 10 * period, period);
                if (ph < 0.04 || period - ph < 0.04) ++near;
            }
            CHECK(near >= static_cast<int>(res.beatTimesSec.size() * 0.9), "beats off grid: %d of %zu (bpm %f)", near, res.beatTimesSec.size(), bpm);
        }
    }
    std::vector<float> silence(sr * 3, 0.f);
    auto res = reel::detectBeats(silence.data(), silence.size(), sr);
    CHECK(res.beatTimesSec.empty() || res.confidence < 0.5, "silence produced confident beats");
    auto tiny = reel::detectBeats(silence.data(), 100, sr);
    CHECK(tiny.beatTimesSec.empty(), "too-short input must return nothing");
}

// A drum loop at the app's analysis rate: kick on every beat, loud open hats exactly between
// beats (the classic trap for beat trackers), a bass drone, noise, and a tempo that drifts.
static std::vector<float> drumLoop(double bpm0, double bpm1, double seconds, int sr, double offset, std::vector<double>& beats) {
    std::vector<float> s(static_cast<size_t>(seconds * sr), 0.f);
    unsigned seed = 11;
    auto noise = [&]() { seed = seed * 1103515245u + 12345u; return ((seed >> 16) % 2000 / 1000.f - 1.f); };
    for (size_t i = 0; i < s.size(); ++i) s[i] = 0.12f * static_cast<float>(std::sin(2 * M_PI * 55 * i / sr)) + 0.01f * noise();
    for (double t = offset; t < seconds - 0.5;) {
        double bpm = bpm0 + (bpm1 - bpm0) * t / seconds, period = 60.0 / bpm;
        beats.push_back(t);
        size_t k0 = static_cast<size_t>(t * sr);
        for (size_t k = 0; k < static_cast<size_t>(sr * 0.3) && k0 + k < s.size(); ++k) {
            double tt = static_cast<double>(k) / sr;
            s[k0 + k] += static_cast<float>(0.9 * std::sin(2 * M_PI * (50 + 100 * std::exp(-tt * 30)) * tt) * std::exp(-tt * 10));
        }
        size_t h0 = static_cast<size_t>((t + period / 2) * sr);
        for (size_t k = 0; k < static_cast<size_t>(sr * 0.08) && h0 + k < s.size(); ++k)
            s[h0 + k] += 0.35f * noise() * static_cast<float>(std::exp(-static_cast<double>(k) / sr * 40));
        t += period;
    }
    return s;
}

static void testBeatsOnDrums() {
    const int sr = 11025;
    struct Case { double bpm0, bpm1, offset; };
    for (Case c : {Case{128, 128, 0.7}, Case{100, 100, 1.3}, Case{112, 126, 0.4}, Case{94, 94, 2.0}}) {
        std::vector<double> truth;
        auto s = drumLoop(c.bpm0, c.bpm1, 40.0, sr, c.offset, truth);
        auto res = reel::detectBeats(s.data(), s.size(), sr);
        // Each detected beat must sit on a kick, within 15 ms: not on the hats in between.
        int hit = 0;
        double worst = 0;
        for (double t : res.beatTimesSec) {
            double d = 1e9;
            for (double b : truth) d = std::min(d, std::fabs(t - b));
            if (d <= 0.015) ++hit;
            worst = std::max(worst, d);
        }
        CHECK(res.beatTimesSec.size() >= truth.size() * 9 / 10, "drums %.0f-%.0f: %zu beats of %zu", c.bpm0, c.bpm1, res.beatTimesSec.size(), truth.size());
        CHECK(hit >= static_cast<int>(res.beatTimesSec.size() * 95 / 100), "drums %.0f-%.0f: only %d of %zu beats on the kick (worst %.0f ms)", c.bpm0, c.bpm1, hit, res.beatTimesSec.size(), worst * 1000);
    }
}

static void testRms() {
    std::vector<float> s(48000);
    for (size_t i = 0; i < s.size(); ++i) s[i] = static_cast<float>(std::sin(2 * M_PI * 440 * i / 48000.0));
    double db = reel::rmsDb(s.data(), s.size());
    CHECK(std::fabs(db + 3.0103) < 0.05, "sine rms %f", db);
    std::vector<float> z(100, 0.f);
    CHECK(reel::rmsDb(z.data(), z.size()) == -120.0, "silence rms");
}

static void testPcmAndHistogram() {
    int16_t pcm[] = {32767, -32768, 16384, 16384, 0, 0};
    float out[3];
    size_t frames = reel::pcm16ToMono(pcm, 6, 2, out);
    CHECK(frames == 3, "frames %zu", frames);
    CHECK(std::fabs(out[0]) < 1e-3f && std::fabs(out[1] - 0.5f) < 1e-3f && out[2] == 0.f, "downmix %f %f %f", out[0], out[1], out[2]);

    uint8_t px[2 * 2 * 4] = {255, 0, 0, 255, 0, 255, 0, 255, 0, 0, 255, 255, 255, 255, 255, 255};
    uint32_t hist[1024];
    reel::histogramRgba(px, 2, 2, 8, hist);
    CHECK(hist[255] == 2 && hist[0] == 2, "red histogram");
    CHECK(hist[768 + 255] == 1, "white luma bin %u", hist[768 + 255]);
    uint32_t total = 0;
    for (int i = 768; i < 1024; ++i) total += hist[i];
    CHECK(total == 4, "luma total %u", total);
}

// A textured scene (soft blobs) sampled with the picture moved by (ox, oy) pixels.
static std::vector<uint8_t> scene(int w, int h, float ox, float oy) {
    std::vector<uint8_t> img(static_cast<size_t>(w) * h * 4);
    uint32_t seed = 7;
    auto rnd = [&]() { seed = seed * 1664525u + 1013904223u; return (seed >> 8) / 16777216.f; };
    struct B { float x, y, r, c[3]; };
    std::vector<B> blobs(260);
    for (auto& b : blobs) { b.x = rnd() * (w + 200) - 100; b.y = rnd() * (h + 200) - 100; b.r = 6 + rnd() * 16; for (float& c : b.c) c = rnd(); }
    for (int y = 0; y < h; ++y)
        for (int x = 0; x < w; ++x) {
            float px = x - ox, py = y - oy;
            float c[3] = {0.2f, 0.2f, 0.25f};
            for (const auto& b : blobs) {
                float dx = px - b.x, dy = py - b.y;
                float k = std::exp(-(dx * dx + dy * dy) / (2 * b.r * b.r));
                for (int i = 0; i < 3; ++i) c[i] += k * b.c[i] * 0.7f;
            }
            uint8_t* p = &img[(static_cast<size_t>(y) * w + x) * 4];
            for (int i = 0; i < 3; ++i) p[i] = static_cast<uint8_t>(std::clamp(c[i], 0.f, 1.f) * 255);
            p[3] = 255;
        }
    return img;
}

static double meanDiff(const std::vector<uint8_t>& a, const std::vector<uint8_t>& b, int w, int h, int margin) {
    double s = 0; long n = 0;
    for (int y = margin; y < h - margin; ++y)
        for (int x = margin; x < w - margin; ++x)
            for (int c = 0; c < 3; ++c) { s += std::abs(a[(static_cast<size_t>(y) * w + x) * 4 + c] - b[(static_cast<size_t>(y) * w + x) * 4 + c]); ++n; }
    return s / n;
}

static void testInterpolation() {
    const int w = 640, h = 360;
    for (auto [dx, dy] : {std::pair<float, float>{12.f, -6.f}, {-30.f, 10.f}, {3.5f, 2.f}}) {
        auto a = scene(w, h, 0, 0), b = scene(w, h, dx, dy);
        reel::Flow f = reel::estimateFlow(a.data(), b.data(), w, h, w * 4);
        float vx, vy;
        f.at(w / 2.f, h / 2.f, vx, vy);
        CHECK(std::fabs(vx - dx) < 1.0f && std::fabs(vy - dy) < 1.0f, "flow (%.2f, %.2f) for (%.1f, %.1f)", vx, vy, dx, dy);
        std::vector<uint8_t> mid(a.size());
        reel::interpolateFrame(a.data(), b.data(), w, h, w * 4, f, 0.5f, mid.data());
        auto truth = scene(w, h, dx / 2, dy / 2);
        double e = meanDiff(mid, truth, w, h, 40);
        double blend = 0;
        {
            std::vector<uint8_t> cross(a.size());
            for (size_t i = 0; i < a.size(); ++i) cross[i] = static_cast<uint8_t>((a[i] + b[i] + 1) / 2);
            blend = meanDiff(cross, truth, w, h, 40);
        }
        std::printf("interp (%.1f,%.1f): error %.2f, plain crossfade %.2f\n", dx, dy, e, blend);
        CHECK(e < 2.5 && e < blend * 0.35, "interpolated frame error %.2f (crossfade %.2f)", e, blend);
        // The ends are the frames themselves.
        std::vector<uint8_t> t0(a.size());
        reel::interpolateFrame(a.data(), b.data(), w, h, w * 4, f, 0.f, t0.data());
        CHECK(meanDiff(t0, a, w, h, 0) < 0.6, "t=0 is frame A (%.2f)", meanDiff(t0, a, w, h, 0));
    }
}

int main() {
    testIdentityLut();
    testLookCatalog();
    testMonoLook();
    testPeaks();
    testFft();
    testBeats();
    testBeatsOnDrums();
    testRms();
    testPcmAndHistogram();
    testInterpolation();
    if (failures) {
        std::fprintf(stderr, "%d failure(s)\n", failures);
        return 1;
    }
    std::printf("all native tests passed (%d looks)\n", reel::lookCount());
    return 0;
}
