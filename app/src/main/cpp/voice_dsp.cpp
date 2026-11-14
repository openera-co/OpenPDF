#include "voice_dsp.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "audio_analysis.h"  // fft

namespace reel {

namespace {

constexpr float kPi = 3.14159265358979f;

float dbToGain(float db) { return std::pow(10.f, db / 20.f); }

// RBJ cookbook biquad, transposed direct form II.
struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    float z1 = 0, z2 = 0;

    enum Type { LowPass, HighPass, BandPass, Peak, LowShelf, HighShelf };

    void set(Type t, float rate, float freq, float q, float gainDb = 0.f) {
        freq = std::min(freq, rate * 0.45f);
        const float w = 2 * kPi * freq / rate;
        const float cw = std::cos(w), sw = std::sin(w);
        const float alpha = sw / (2 * q);
        const float A = std::pow(10.f, gainDb / 40.f);
        float B0, B1, B2, A0, A1, A2;
        switch (t) {
            case LowPass: B0 = (1 - cw) / 2; B1 = 1 - cw; B2 = (1 - cw) / 2; A0 = 1 + alpha; A1 = -2 * cw; A2 = 1 - alpha; break;
            case HighPass: B0 = (1 + cw) / 2; B1 = -(1 + cw); B2 = (1 + cw) / 2; A0 = 1 + alpha; A1 = -2 * cw; A2 = 1 - alpha; break;
            case BandPass: B0 = alpha; B1 = 0; B2 = -alpha; A0 = 1 + alpha; A1 = -2 * cw; A2 = 1 - alpha; break;
            case Peak: B0 = 1 + alpha * A; B1 = -2 * cw; B2 = 1 - alpha * A; A0 = 1 + alpha / A; A1 = -2 * cw; A2 = 1 - alpha / A; break;
            case LowShelf: {
                const float s = 2 * std::sqrt(A) * alpha;
                B0 = A * ((A + 1) - (A - 1) * cw + s); B1 = 2 * A * ((A - 1) - (A + 1) * cw); B2 = A * ((A + 1) - (A - 1) * cw - s);
                A0 = (A + 1) + (A - 1) * cw + s; A1 = -2 * ((A - 1) + (A + 1) * cw); A2 = (A + 1) + (A - 1) * cw - s;
                break;
            }
            case HighShelf:
            default: {
                const float s = 2 * std::sqrt(A) * alpha;
                B0 = A * ((A + 1) + (A - 1) * cw + s); B1 = -2 * A * ((A - 1) + (A + 1) * cw); B2 = A * ((A + 1) + (A - 1) * cw - s);
                A0 = (A + 1) - (A - 1) * cw + s; A1 = 2 * ((A - 1) - (A + 1) * cw); A2 = (A + 1) - (A - 1) * cw - s;
                break;
            }
        }
        b0 = B0 / A0; b1 = B1 / A0; b2 = B2 / A0; a1 = A1 / A0; a2 = A2 / A0;
    }
    float run(float x) {
        const float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
    void clear() { z1 = z2 = 0; }
};

// Feed-forward compressor on a level detector shared by all channels.
struct Compressor {
    float threshDb = -20, ratio = 3, kneeDb = 6, makeupDb = 0;
    float att = 0, rel = 0, env = 0;
    void setup(float rate, float attackMs, float releaseMs) {
        att = std::exp(-1.f / (rate * attackMs * 0.001f));
        rel = std::exp(-1.f / (rate * releaseMs * 0.001f));
    }
    // Returns the gain for this frame given its peak level.
    float gain(float level) {
        const float c = level > env ? att : rel;
        env = c * env + (1 - c) * level;
        const float db = 20.f * std::log10(std::max(env, 1e-6f));
        const float over = db - threshDb;
        float red;
        if (over <= -kneeDb / 2) red = 0;
        else if (over >= kneeDb / 2) red = over * (1 - 1 / ratio);
        else { const float x = over + kneeDb / 2; red = (1 - 1 / ratio) * x * x / (2 * kneeDb); }
        return dbToGain(makeupDb - red);
    }
    void clear() { env = 0; }
};

// Freeverb-style reverb (Jezar's public-domain design), mono in, two decorrelated outputs.
struct Reverb {
    struct Comb { std::vector<float> b; size_t i = 0; float store = 0; };
    struct Allpass { std::vector<float> b; size_t i = 0; };
    Comb comb[2][8];
    Allpass ap[2][4];
    float feedback = 0.84f, damp = 0.2f;
    void setup(float rate, float room, float damping) {
        static const int combT[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        static const int apT[4] = {556, 441, 341, 225};
        const float k = rate / 44100.f;
        for (int s = 0; s < 2; ++s) {
            for (int j = 0; j < 8; ++j) comb[s][j].b.assign(static_cast<size_t>((combT[j] + 23 * s) * k) + 1, 0.f);
            for (int j = 0; j < 4; ++j) ap[s][j].b.assign(static_cast<size_t>((apT[j] + 23 * s) * k) + 1, 0.f);
        }
        feedback = 0.7f + 0.28f * room;
        damp = damping;
    }
    void run(float in, float& l, float& r) {
        float out[2] = {0, 0};
        const float x = in * 0.015f;
        for (int s = 0; s < 2; ++s) {
            for (auto& c : comb[s]) {
                float y = c.b[c.i];
                c.store = y * (1 - damp) + c.store * damp;
                c.b[c.i] = x + c.store * feedback;
                if (++c.i >= c.b.size()) c.i = 0;
                out[s] += y;
            }
            for (auto& a : ap[s]) {
                float bo = a.b[a.i];
                float y = -out[s] + bo;
                a.b[a.i] = out[s] + bo * 0.5f;
                if (++a.i >= a.b.size()) a.i = 0;
                out[s] = y;
            }
        }
        l = out[0];
        r = out[1];
    }
    void clear() {
        for (auto& side : comb) for (auto& c : side) { std::fill(c.b.begin(), c.b.end(), 0.f); c.store = 0; c.i = 0; }
        for (auto& side : ap) for (auto& a : side) { std::fill(a.b.begin(), a.b.end(), 0.f); a.i = 0; }
    }
};

// Delay line with fractional read (for echo, vibrato, comb).
struct Delay {
    std::vector<float> b;
    size_t w = 0;
    void setup(size_t maxFrames) { b.assign(maxFrames + 4, 0.f); w = 0; }
    void push(float x) { b[w] = x; if (++w >= b.size()) w = 0; }
    // Sample from [d] frames ago (d >= 1).
    float read(float d) const {
        float pos = static_cast<float>(w) - d;
        while (pos < 0) pos += static_cast<float>(b.size());
        size_t i0 = static_cast<size_t>(pos);
        float f = pos - static_cast<float>(i0);
        size_t i1 = (i0 + 1) % b.size();
        return b[i0 % b.size()] * (1 - f) + b[i1] * f;
    }
    void clear() { std::fill(b.begin(), b.end(), 0.f); w = 0; }
};

// STFT noise reduction: minimum-statistics noise floor and a smoothed spectral gain.
struct Denoiser {
    int n = 1024, hop = 256;
    std::vector<float> win, inBuf, outBuf, re, im, power, noise, gainSm;
    int inFill = 0;
    float amount = 0;
    float noiseRise = 0;  // per-hop multiplier the floor may rise by
    bool primed = false;
    std::vector<float> ready;  // output samples ready to hand out
    size_t readyPos = 0;

    void setup(float rate, float amt) {
        n = rate > 32000 ? 1024 : 512;
        hop = n / 4;
        amount = amt;
        win.resize(n);
        for (int i = 0; i < n; ++i) win[i] = std::sqrt(0.5f - 0.5f * std::cos(2 * kPi * i / n));
        inBuf.assign(n, 0.f);
        outBuf.assign(n, 0.f);
        re.assign(n, 0.f);
        im.assign(n, 0.f);
        power.assign(n / 2 + 1, 0.f);
        noise.assign(n / 2 + 1, 0.f);
        gainSm.assign(n / 2 + 1, 1.f);
        // Floor may rise about 6 dB per second, so it follows slowly changing noise.
        noiseRise = std::pow(10.f, 0.6f * hop / rate);
        inFill = n - hop;  // start with n - hop zeros: first frame needs one hop of input
        primed = false;
        ready.clear();
        readyPos = 0;
    }
    int latency() const { return n - hop; }

    void frame() {
        for (int i = 0; i < n; ++i) { re[i] = inBuf[i] * win[i]; im[i] = 0; }
        fft(re.data(), im.data(), n);
        const int bins = n / 2 + 1;
        for (int k = 0; k < bins; ++k) {
            const float p = re[k] * re[k] + im[k] * im[k];
            power[k] = primed ? 0.6f * power[k] + 0.4f * p : p;
            if (!primed) noise[k] = power[k];
            else if (power[k] < noise[k]) noise[k] = 0.8f * noise[k] + 0.2f * power[k];
            else noise[k] = std::min(noise[k] * noiseRise, power[k]);
        }
        primed = true;
        const float over = 1.5f + 3.f * amount;                // over-subtraction
        const float floorGain = dbToGain(-(8.f + 22.f * amount));  // how far noise is pushed down
        for (int k = 0; k < bins; ++k) {
            float g = 1.f - over * noise[k] / std::max(power[k], 1e-12f);
            g = std::max(g, floorGain);
            // Fast to open (keeps speech onsets), slow to close (avoids "musical" noise).
            gainSm[k] = g > gainSm[k] ? 0.3f * gainSm[k] + 0.7f * g : 0.8f * gainSm[k] + 0.2f * g;
        }
        for (int k = 0; k < bins; ++k) {
            const float g0 = gainSm[std::max(0, k - 1)], g1 = gainSm[k], g2 = gainSm[std::min(bins - 1, k + 1)];
            const float g = 0.25f * g0 + 0.5f * g1 + 0.25f * g2;
            re[k] *= g;
            im[k] *= g;
            if (k > 0 && k < n / 2) { re[n - k] = re[k]; im[n - k] = -im[k]; }
        }
        // Inverse FFT through the forward transform of the conjugate.
        for (int i = 0; i < n; ++i) im[i] = -im[i];
        fft(re.data(), im.data(), n);
        // sqrt-Hann analysis and synthesis at 75% overlap sum to 2.
        for (int i = 0; i < n; ++i) outBuf[i] += re[i] / n * win[i] * 0.5f;
        ready.insert(ready.end(), outBuf.begin(), outBuf.begin() + hop);
        std::memmove(outBuf.data(), outBuf.data() + hop, sizeof(float) * (n - hop));
        std::fill(outBuf.end() - hop, outBuf.end(), 0.f);
        std::memmove(inBuf.data(), inBuf.data() + hop, sizeof(float) * (n - hop));
        inFill = n - hop;
    }

    void push(float x) {
        inBuf[inFill++] = x;
        if (inFill == n) frame();
    }
    bool pop(float& y) {
        if (readyPos >= ready.size()) {
            ready.clear();
            readyPos = 0;
            return false;
        }
        y = ready[readyPos++];
        return true;
    }
};

}  // namespace

struct VoiceChain::Impl {
    int rate, ch;
    VoiceSettings s;
    // Per channel
    std::vector<Biquad> hp, mud, presence, air;
    std::vector<Biquad> fx1, fx2, fx3, fx4;
    std::vector<Denoiser> dn;
    std::vector<Delay> delay;
    std::vector<Reverb> verb;
    Compressor comp, leveler;
    float levelEnv = 0, levelGain = 1;
    float limEnv = 1;
    double phase = 0, lfo = 0;
    float holdL = 0, holdR = 0;
    int holdCount = 0;
    int skip = 0;  // output frames still to drop to remove denoiser latency
    std::vector<float> scratch;
    std::vector<float> outQ;  // processed frames not yet handed out
    size_t outQPos = 0;
    long long inCount = 0, outCount = 0;  // frames in and handed out since the last reset

    Impl(int r, int c, const VoiceSettings& st) : rate(r), ch(std::max(1, c)), s(st) { build(); }

    void build() {
        const float R = static_cast<float>(rate);
        hp.assign(ch, {}); mud.assign(ch, {}); presence.assign(ch, {}); air.assign(ch, {});
        fx1.assign(ch, {}); fx2.assign(ch, {}); fx3.assign(ch, {}); fx4.assign(ch, {});
        for (int c = 0; c < ch; ++c) {
            const float e = s.enhance;
            hp[c].set(Biquad::HighPass, R, 70.f + 30.f * e, 0.707f);
            mud[c].set(Biquad::Peak, R, 300.f, 1.0f, -4.f * e);
            presence[c].set(Biquad::Peak, R, 3200.f, 0.9f, 5.f * e);
            air[c].set(Biquad::HighShelf, R, 9000.f, 0.707f, 3.f * e);
        }
        comp.threshDb = -24.f + 4.f * (1 - s.enhance);
        comp.ratio = 1.f + 2.5f * s.enhance;
        comp.makeupDb = 6.f * s.enhance;
        comp.setup(R, 5.f, 140.f);
        leveler.setup(R, 400.f, 1500.f);
        dn.clear();
        if (s.denoise > 0.001f) {
            dn.resize(ch);
            for (auto& d : dn) d.setup(R, s.denoise);
        }
        skip = dn.empty() ? 0 : dn[0].latency();
        setupEffect();
    }

    void setupEffect() {
        const float R = static_cast<float>(rate);
        delay.assign(ch, {});
        verb.clear();
        for (int c = 0; c < ch; ++c) {
            switch (s.effect) {
                case VoiceEffect::Telephone:
                    fx1[c].set(Biquad::HighPass, R, 400.f, 0.7f); fx2[c].set(Biquad::HighPass, R, 400.f, 0.7f);
                    fx3[c].set(Biquad::LowPass, R, 3000.f, 0.7f); fx4[c].set(Biquad::LowPass, R, 3000.f, 0.7f);
                    break;
                case VoiceEffect::Megaphone:
                    fx1[c].set(Biquad::HighPass, R, 600.f, 0.8f); fx2[c].set(Biquad::LowPass, R, 4200.f, 0.8f);
                    fx3[c].set(Biquad::Peak, R, 1500.f, 1.2f, 7.f); fx4[c].set(Biquad::LowPass, R, 5000.f, 0.7f);
                    break;
                case VoiceEffect::Radio:
                    fx1[c].set(Biquad::HighPass, R, 250.f, 0.7f); fx2[c].set(Biquad::LowPass, R, 5500.f, 0.7f);
                    fx3[c].set(Biquad::Peak, R, 2000.f, 1.0f, 3.f); fx4[c].set(Biquad::LowShelf, R, 400.f, 0.7f, -3.f);
                    break;
                case VoiceEffect::Underwater:
                    fx1[c].set(Biquad::LowPass, R, 500.f, 0.8f); fx2[c].set(Biquad::LowPass, R, 700.f, 0.6f);
                    delay[c].setup(static_cast<size_t>(R * 0.02f));
                    break;
                case VoiceEffect::LoFi:
                    fx1[c].set(Biquad::LowPass, R, 3500.f, 0.7f); fx2[c].set(Biquad::HighPass, R, 200.f, 0.7f);
                    break;
                case VoiceEffect::Robot:
                    delay[c].setup(static_cast<size_t>(R * 0.02f));
                    break;
                case VoiceEffect::Alien:
                    fx1[c].set(Biquad::HighPass, R, 300.f, 0.7f);
                    delay[c].setup(static_cast<size_t>(R * 0.02f));
                    break;
                case VoiceEffect::Monster:
                    fx1[c].set(Biquad::LowShelf, R, 200.f, 0.7f, 6.f); fx2[c].set(Biquad::LowPass, R, 3500.f, 0.7f);
                    break;
                case VoiceEffect::Vibrato:
                    delay[c].setup(static_cast<size_t>(R * 0.02f));
                    break;
                case VoiceEffect::Echo:
                    delay[c].setup(static_cast<size_t>(R * 0.3f));
                    fx1[c].set(Biquad::LowPass, R, 3500.f, 0.7f);
                    break;
                case VoiceEffect::Cave:
                    delay[c].setup(static_cast<size_t>(R * 0.45f));
                    fx1[c].set(Biquad::LowPass, R, 2500.f, 0.7f);
                    break;
                default:
                    break;
            }
        }
        if (s.effect == VoiceEffect::Hall || s.effect == VoiceEffect::Room || s.effect == VoiceEffect::Cave || s.effect == VoiceEffect::Underwater) {
            verb.resize(1);
            const float room = s.effect == VoiceEffect::Hall ? 0.92f : s.effect == VoiceEffect::Cave ? 0.85f : s.effect == VoiceEffect::Room ? 0.5f : 0.4f;
            verb[0].setup(static_cast<float>(rate), room, s.effect == VoiceEffect::Room ? 0.45f : 0.25f);
        }
    }

    // Applies the character effect to one frame (all channels). Mono-ish effects share the LFO.
    void effectFrame(float* f) {
        const float R = static_cast<float>(rate);
        switch (s.effect) {
            case VoiceEffect::None:
                return;
            case VoiceEffect::Telephone:
                for (int c = 0; c < ch; ++c) f[c] = std::tanh(2.2f * fx4[c].run(fx3[c].run(fx2[c].run(fx1[c].run(f[c]))))) * 0.7f;
                return;
            case VoiceEffect::Megaphone:
                for (int c = 0; c < ch; ++c) f[c] = fx4[c].run(std::tanh(4.f * fx3[c].run(fx2[c].run(fx1[c].run(f[c]))))) * 0.55f;
                return;
            case VoiceEffect::Radio:
                for (int c = 0; c < ch; ++c) f[c] = std::tanh(1.6f * fx4[c].run(fx3[c].run(fx2[c].run(fx1[c].run(f[c]))))) * 0.8f;
                return;
            case VoiceEffect::Monster:
                for (int c = 0; c < ch; ++c) f[c] = fx2[c].run(std::tanh(2.f * fx1[c].run(f[c]))) * 0.75f;
                return;
            case VoiceEffect::LoFi: {
                // 8 kHz sample-and-hold, 8-bit levels.
                const int every = std::max(1, rate / 8000);
                if (holdCount++ % every == 0) { holdL = f[0]; holdR = ch > 1 ? f[1] : f[0]; }
                for (int c = 0; c < ch; ++c) {
                    float v = c == 0 ? holdL : holdR;
                    v = std::round(v * 127.f) / 127.f;
                    f[c] = fx2[c].run(fx1[c].run(v));
                }
                return;
            }
            case VoiceEffect::Robot: {
                // Ring modulation plus a short resonant comb gives a metallic, buzzing voice.
                phase += 2.0 * kPi * 55.0 / R;
                const float m = static_cast<float>(std::sin(phase));
                for (int c = 0; c < ch; ++c) {
                    const float x = f[c] * (0.35f + 0.65f * m);
                    const float y = x + 0.6f * delay[c].read(R * 0.0067f);
                    delay[c].push(y);
                    f[c] = y * 1.2f;
                }
                return;
            }
            case VoiceEffect::Alien: {
                phase += 2.0 * kPi * 420.0 / R;
                lfo += 2.0 * kPi * 6.0 / R;
                const float m = static_cast<float>(std::sin(phase));
                const float d = R * (0.004f + 0.002f * static_cast<float>(std::sin(lfo)));
                for (int c = 0; c < ch; ++c) {
                    const float x = fx1[c].run(f[c]);
                    delay[c].push(x);
                    f[c] = 1.0f * x * m + 1.1f * delay[c].read(d);
                }
                return;
            }
            case VoiceEffect::Vibrato: {
                lfo += 2.0 * kPi * 5.5 / R;
                const float d = R * (0.004f + 0.0025f * static_cast<float>(std::sin(lfo)));
                for (int c = 0; c < ch; ++c) { delay[c].push(f[c]); f[c] = delay[c].read(d); }
                return;
            }
            case VoiceEffect::Underwater: {
                lfo += 2.0 * kPi * 0.8 / R;
                const float d = R * (0.006f + 0.004f * static_cast<float>(std::sin(lfo)));
                float mono = 0;
                for (int c = 0; c < ch; ++c) {
                    const float x = fx2[c].run(fx1[c].run(f[c]));
                    delay[c].push(x);
                    f[c] = 0.6f * x + 0.6f * delay[c].read(d);
                    mono += f[c];
                }
                float l, r;
                verb[0].run(mono / ch, l, r);
                for (int c = 0; c < ch; ++c) f[c] += 0.5f * (c % 2 ? r : l);
                return;
            }
            case VoiceEffect::Echo:
            case VoiceEffect::Cave: {
                const float time = s.effect == VoiceEffect::Echo ? 0.28f : 0.42f;
                const float fb = s.effect == VoiceEffect::Echo ? 0.38f : 0.5f;
                float mono = 0;
                for (int c = 0; c < ch; ++c) {
                    const float echo = delay[c].read(R * time);
                    delay[c].push(f[c] + fx1[c].run(echo) * fb);
                    f[c] = f[c] + 0.55f * echo;
                    mono += f[c];
                }
                if (!verb.empty()) {
                    float l, r;
                    verb[0].run(mono / ch, l, r);
                    for (int c = 0; c < ch; ++c) f[c] = 0.8f * f[c] + 0.6f * (c % 2 ? r : l);
                }
                return;
            }
            case VoiceEffect::Hall:
            case VoiceEffect::Room: {
                float mono = 0;
                for (int c = 0; c < ch; ++c) mono += f[c];
                float l, r;
                verb[0].run(mono / ch, l, r);
                const float wet = s.effect == VoiceEffect::Hall ? 0.75f : 0.5f;
                for (int c = 0; c < ch; ++c) f[c] = 0.85f * f[c] + wet * (ch == 1 ? 0.5f * (l + r) : (c % 2 ? r : l));
                return;
            }
        }
    }

    // Everything after noise reduction, one frame at a time.
    void tail(float* f) {
        const float R = static_cast<float>(rate);
        if (s.enhance > 0.001f) {
            float peak = 0;
            for (int c = 0; c < ch; ++c) {
                f[c] = air[c].run(presence[c].run(mud[c].run(hp[c].run(f[c]))));
                peak = std::max(peak, std::fabs(f[c]));
            }
            const float g = comp.gain(peak);
            for (int c = 0; c < ch; ++c) f[c] *= g;
        }
        if (s.normalize) {
            // Slow leveler toward about -20 dBFS speech level, between -12 and +15 dB of gain.
            float sq = 0;
            for (int c = 0; c < ch; ++c) sq += f[c] * f[c];
            const float level = std::sqrt(sq / ch);
            const float a = level > levelEnv ? std::exp(-1.f / (R * 0.3f)) : std::exp(-1.f / (R * 2.0f));
            levelEnv = a * levelEnv + (1 - a) * level;
            // Only adapt on signal, not on silence.
            if (levelEnv > 0.003f) {
                const float want = std::clamp(0.1f / levelEnv, dbToGain(-12.f), dbToGain(15.f));
                levelGain += (want - levelGain) * (1.f / (R * 0.5f));
            }
            for (int c = 0; c < ch; ++c) f[c] *= levelGain;
        }
        effectFrame(f);
        // Peak limiter at about -1 dBFS with soft knee, fast attack, gentle release.
        float peak = 0;
        for (int c = 0; c < ch; ++c) peak = std::max(peak, std::fabs(f[c]));
        const float target = peak > 0.89f ? 0.89f / peak : 1.f;
        limEnv = target < limEnv ? target : limEnv + (1.f - limEnv) * (1.f / (R * 0.08f));
        for (int c = 0; c < ch; ++c) {
            float v = f[c] * limEnv;
            if (!std::isfinite(v)) v = 0;
            f[c] = std::clamp(v, -1.f, 1.f);
        }
    }

    bool active() const {
        return s.enhance > 0.001f || !dn.empty() || s.normalize || s.effect != VoiceEffect::None;
    }

    // Runs [frames] through the chain and appends the results to outQ.
    void run(const float* in, int frames) {
        if (scratch.size() < static_cast<size_t>(ch)) scratch.resize(ch);
        for (int i = 0; i < frames; ++i) {
            const float* src = in + static_cast<size_t>(i) * ch;
            if (dn.empty()) {
                std::memcpy(scratch.data(), src, sizeof(float) * ch);
                tail(scratch.data());
                outQ.insert(outQ.end(), scratch.begin(), scratch.begin() + ch);
                continue;
            }
            for (int c = 0; c < ch; ++c) dn[c].push(src[c]);
            // Each denoiser emits in lockstep (same hop), so read one frame across channels.
            while (true) {
                bool got = true;
                for (int c = 0; c < ch; ++c) got = dn[c].pop(scratch[c]) && got;
                if (!got) break;
                if (skip > 0) { --skip; continue; }
                tail(scratch.data());
                outQ.insert(outQ.end(), scratch.begin(), scratch.begin() + ch);
            }
        }
    }

    int take(float* out, int capacity) {
        const size_t avail = (outQ.size() - outQPos) / ch;
        const size_t n = std::min(avail, static_cast<size_t>(std::max(0, capacity)));
        outCount += static_cast<long long>(n);
        std::memcpy(out, outQ.data() + outQPos, sizeof(float) * n * ch);
        outQPos += n * ch;
        if (outQPos == outQ.size()) { outQ.clear(); outQPos = 0; }
        else if (outQPos > (1u << 16)) { outQ.erase(outQ.begin(), outQ.begin() + static_cast<long>(outQPos)); outQPos = 0; }
        return static_cast<int>(n);
    }

    int process(const float* in, int frames, float* out, int cap) {
        run(in, frames);
        inCount += frames;
        return take(out, cap);
    }

    int drain(float* out, int maxFrames) {
        if (!dn.empty()) {
            // Push silence so every held-back input frame comes out...
            const int lat = dn[0].latency() + dn[0].hop;
            std::vector<float> zeros(static_cast<size_t>(lat) * ch, 0.f);
            run(zeros.data(), lat);
        }
        // ...but hand out exactly as many frames as came in.
        const long long left = std::max<long long>(0, inCount - outCount);
        const int n = take(out, static_cast<int>(std::min<long long>(maxFrames, left)));
        outQ.clear();
        outQPos = 0;
        return n;
    }
};

VoiceChain::VoiceChain(int sampleRate, int channels, const VoiceSettings& s) : d(std::make_unique<Impl>(sampleRate, channels, s)) {}
VoiceChain::~VoiceChain() = default;
int VoiceChain::process(const float* in, int frames, float* out, int outCapacity) { return d->process(in, frames, out, outCapacity); }
int VoiceChain::drain(float* out, int maxFrames) { return d->drain(out, maxFrames); }
void VoiceChain::reset() { d->build(); d->comp.clear(); d->levelEnv = 0; d->levelGain = 1; d->limEnv = 1; d->phase = d->lfo = 0; d->outQ.clear(); d->outQPos = 0; d->inCount = d->outCount = 0; }
int VoiceChain::latency() const { return d->dn.empty() ? 0 : d->dn[0].latency(); }

}  // namespace reel
