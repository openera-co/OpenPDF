// Host tests for the voice chain.
#include <cmath>
#include <cstdio>
#include <random>
#include <vector>

#include "../voice_dsp.h"

static int failures = 0;
#define CHECK(c)                                                                          \
    do {                                                                                  \
        if (!(c)) { std::printf("FAIL %s:%d %s\n", __FILE__, __LINE__, #c); ++failures; } \
    } while (0)

using reel::VoiceChain;
using reel::VoiceEffect;
using reel::VoiceSettings;

static const int R = 48000;

static std::vector<float> run(const VoiceSettings& s, const std::vector<float>& in, int ch, int block = 1024) {
    VoiceChain v(R, ch, s);
    std::vector<float> out;
    std::vector<float> buf;
    const int frames = static_cast<int>(in.size()) / ch;
    for (int i = 0; i < frames; i += block) {
        const int n = std::min(block, frames - i);
        buf.assign(in.begin() + static_cast<long>(i) * ch, in.begin() + static_cast<long>(i + n) * ch);
        const int w = v.process(buf.data(), n, buf.data());  // in place
        out.insert(out.end(), buf.begin(), buf.begin() + static_cast<long>(w) * ch);
    }
    std::vector<float> tail(static_cast<size_t>(v.latency() + 4096) * ch);
    const int t = v.drain(tail.data(), v.latency() + 4096);
    out.insert(out.end(), tail.begin(), tail.begin() + static_cast<long>(t) * ch);
    return out;
}

static double rms(const std::vector<float>& x, size_t a, size_t b) {
    double s = 0;
    for (size_t i = a; i < b && i < x.size(); ++i) s += x[i] * x[i];
    return std::sqrt(s / std::max<size_t>(1, b - a));
}

static double db(double r) { return 20 * std::log10(std::max(r, 1e-9)); }

int main() {
    std::mt19937 rng(3);
    std::normal_distribution<float> gauss(0.f, 1.f);

    // 1. Everything off: bit-exact passthrough (apart from the safety limiter, idle here).
    {
        std::vector<float> in(R);
        for (int i = 0; i < R; ++i) in[i] = 0.3f * std::sin(2 * M_PI * 220 * i / R);
        auto out = run({}, in, 1);
        CHECK(out.size() == in.size());
        double maxd = 0;
        for (size_t i = 0; i < in.size(); ++i) maxd = std::max(maxd, static_cast<double>(std::fabs(out[i] - in[i])));
        std::printf("passthrough max diff %.6f\n", maxd);
        CHECK(maxd < 1e-6);
    }

    // 2. Noise reduction: tone bursts in steady noise. Noise-only parts drop a lot, tones stay.
    {
        const int secs = 6;
        std::vector<float> in(R * secs);
        for (int i = 0; i < R * secs; ++i) {
            const bool tone = (i / (R / 2)) % 2 == 1;  // alternate half seconds
            in[i] = 0.03f * gauss(rng) + (tone ? 0.3f * static_cast<float>(std::sin(2 * M_PI * 440 * i / R)) : 0.f);
        }
        VoiceSettings s;
        s.denoise = 0.8f;
        auto out = run(s, in, 1);
        CHECK(out.size() == in.size());
        // Measure after 2 s, when the noise floor is learned: [4.0, 4.5) noise, [4.5, 5.0) tone.
        const double nIn = rms(in, 4 * R + 2000, 4 * R + R / 2 - 2000), nOut = rms(out, 4 * R + 2000, 4 * R + R / 2 - 2000);
        const double tIn = rms(in, 4 * R + R / 2 + 4000, 5 * R - 2000), tOut = rms(out, 4 * R + R / 2 + 4000, 5 * R - 2000);
        std::printf("denoise: noise %.1f dB -> %.1f dB, tone %.1f dB -> %.1f dB\n", db(nIn), db(nOut), db(tIn), db(tOut));
        CHECK(db(nIn) - db(nOut) >= 10.0);
        CHECK(std::fabs(db(tIn) - db(tOut)) <= 3.0);
        // Alignment: the tone's phase lines up (no delay left in the output).
        double best = -1e9;
        int bestLag = 0;
        for (int lag = -50; lag <= 50; ++lag) {
            double c = 0;
            for (int i = 4 * R + R / 2 + 4000; i < 5 * R - 4000; ++i) c += in[i] * out[i + lag];
            if (c > best) { best = c; bestLag = lag; }
        }
        // A 440 Hz tone repeats every ~109 samples; the best lag must be 0, not a later period.
        std::printf("denoise alignment lag %d\n", bestLag);
        CHECK(std::abs(bestLag) <= 1);
    }

    // 3. Enhancer: removes rumble, never clips loud input.
    {
        std::vector<float> rumble(R), loud(R);
        for (int i = 0; i < R; ++i) {
            rumble[i] = 0.3f * std::sin(2 * M_PI * 35 * i / R);
            loud[i] = 0.98f * std::sin(2 * M_PI * 3000 * i / R);
        }
        VoiceSettings s;
        s.enhance = 1.f;
        auto r = run(s, rumble, 1);
        auto l = run(s, loud, 1);
        std::printf("enhance: 35 Hz %.1f dB -> %.1f dB\n", db(rms(rumble, R / 2, R)), db(rms(r, R / 2, R)));
        CHECK(db(rms(rumble, R / 2, R)) - db(rms(r, R / 2, R)) > 6.0);
        double peak = 0;
        for (float v : l) peak = std::max(peak, static_cast<double>(std::fabs(v)));
        CHECK(peak <= 1.0);
    }

    // 4. Every effect on stereo speech-like input: finite, bounded, audible, same length.
    {
        std::vector<float> in(2 * R * 2);
        for (int i = 0; i < 2 * R; ++i) {
            const float env = 0.5f + 0.5f * static_cast<float>(std::sin(2 * M_PI * 3 * i / R));
            const float v = env * (0.3f * static_cast<float>(std::sin(2 * M_PI * 180 * i / R)) + 0.1f * static_cast<float>(std::sin(2 * M_PI * 1200 * i / R)) + 0.02f * gauss(rng));
            in[2 * i] = v;
            in[2 * i + 1] = v * 0.9f;
        }
        for (int e = 0; e <= static_cast<int>(VoiceEffect::Monster); ++e) {
            VoiceSettings s;
            s.effect = static_cast<VoiceEffect>(e);
            auto out = run(s, in, 2, 777);
            bool finite = true;
            double peak = 0;
            for (float v : out) { finite = finite && std::isfinite(v); peak = std::max(peak, static_cast<double>(std::fabs(v))); }
            const double level = db(rms(out, out.size() / 4, out.size()));
            std::printf("effect %2d: level %.1f dB peak %.2f\n", e, level, peak);
            CHECK(out.size() == in.size());
            CHECK(finite);
            CHECK(peak <= 1.0);
            CHECK(level > -40.0);
        }
    }

    // 5. Echo repeats an impulse about 280 ms later.
    {
        std::vector<float> in(R, 0.f);
        in[1000] = 0.8f;
        VoiceSettings s;
        s.effect = VoiceEffect::Echo;
        auto out = run(s, in, 1);
        int at = 0;
        float best = 0;
        for (int i = 1000 + R / 10; i < R; ++i) if (std::fabs(out[i]) > best) { best = std::fabs(out[i]); at = i; }
        std::printf("echo at %.3f s\n", (at - 1000) / static_cast<double>(R));
        CHECK(std::abs((at - 1000) - static_cast<int>(R * 0.28)) < 10);
    }

    // 6. Normalize brings quiet speech up.
    {
        std::vector<float> in(6 * R);
        for (int i = 0; i < 6 * R; ++i) in[i] = 0.015f * std::sin(2 * M_PI * 200 * i / R);
        VoiceSettings s;
        s.normalize = true;
        auto out = run(s, in, 1);
        const double gain = db(rms(out, 5 * R, 6 * R)) - db(rms(in, 5 * R, 6 * R));
        std::printf("normalize gain %.1f dB\n", gain);
        CHECK(gain > 8.0);
    }

    std::printf(failures ? "VOICE FAILED %d\n" : "VOICE ALL PASSED\n", failures);
    return failures ? 1 : 0;
}
