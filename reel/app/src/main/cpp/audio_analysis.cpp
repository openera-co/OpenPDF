#include "audio_analysis.h"

#include <algorithm>
#include <cmath>
#include <numeric>

namespace reel {

std::vector<float> computePeaks(const float* samples, size_t count, int buckets) {
    std::vector<float> out(static_cast<size_t>(std::max(buckets, 0)), 0.f);
    if (buckets <= 0 || count == 0 || samples == nullptr) return out;
    const double per = static_cast<double>(count) / buckets;
    for (int i = 0; i < buckets; ++i) {
        size_t a = static_cast<size_t>(i * per);
        size_t b = std::min(count, static_cast<size_t>((i + 1) * per));
        if (b <= a) b = std::min(count, a + 1);
        float m = 0.f;
        for (size_t k = a; k < b; ++k) m = std::max(m, std::fabs(samples[k]));
        out[static_cast<size_t>(i)] = std::min(m, 1.f);
    }
    return out;
}

void fft(float* re, float* im, int n) {
    for (int i = 1, j = 0; i < n; ++i) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) { std::swap(re[i], re[j]); std::swap(im[i], im[j]); }
    }
    for (int len = 2; len <= n; len <<= 1) {
        double ang = -2.0 * M_PI / len;
        float wr = static_cast<float>(std::cos(ang)), wi = static_cast<float>(std::sin(ang));
        for (int i = 0; i < n; i += len) {
            float cr = 1.f, ci = 0.f;
            for (int k = 0; k < len / 2; ++k) {
                int u = i + k, v = i + k + len / 2;
                float tr = re[v] * cr - im[v] * ci;
                float ti = re[v] * ci + im[v] * cr;
                re[v] = re[u] - tr; im[v] = im[u] - ti;
                re[u] += tr; im[u] += ti;
                float ncr = cr * wr - ci * wi;
                ci = cr * wi + ci * wr;
                cr = ncr;
            }
        }
    }
}

std::vector<float> onsetEnvelope(const float* samples, size_t count, int window, int hop) {
    std::vector<float> env;
    if (count < static_cast<size_t>(window) || window <= 0 || hop <= 0) return env;
    const int bins = window / 2;
    std::vector<float> hann(static_cast<size_t>(window));
    for (int i = 0; i < window; ++i) hann[static_cast<size_t>(i)] = 0.5f - 0.5f * static_cast<float>(std::cos(2.0 * M_PI * i / (window - 1)));
    std::vector<float> re(static_cast<size_t>(window)), im(static_cast<size_t>(window));
    std::vector<float> prev(static_cast<size_t>(bins), 0.f), mag(static_cast<size_t>(bins));
    for (size_t start = 0; start + window <= count; start += static_cast<size_t>(hop)) {
        for (int i = 0; i < window; ++i) {
            float v = samples[start + i];
            if (!std::isfinite(v)) v = 0.f;  // corrupt decoder output must not poison the envelope
            re[static_cast<size_t>(i)] = v * hann[static_cast<size_t>(i)];
            im[static_cast<size_t>(i)] = 0.f;
        }
        fft(re.data(), im.data(), window);
        float flux = 0.f;
        for (int k = 1; k < bins; ++k) {
            float m = std::log1p(10.f * std::sqrt(re[k] * re[k] + im[k] * im[k]));
            float d = m - prev[static_cast<size_t>(k)];
            if (d > 0.f) flux += d;
            mag[static_cast<size_t>(k)] = m;
        }
        prev.swap(mag);
        env.push_back(flux);
    }
    if (!env.empty()) env[0] = 0.f;
    // Subtract a local mean so sustained loudness does not register as onsets.
    std::vector<float> out(env.size());
    const int half = 8;
    for (size_t i = 0; i < env.size(); ++i) {
        size_t a = i >= half ? i - half : 0, b = std::min(env.size(), i + half + 1);
        float mean = std::accumulate(env.begin() + static_cast<long>(a), env.begin() + static_cast<long>(b), 0.f) / static_cast<float>(b - a);
        out[i] = std::max(0.f, env[i] - mean);
    }
    float mx = *std::max_element(out.begin(), out.end());
    if (mx > 0.f) for (auto& v : out) v /= mx;
    return out;
}

BeatResult detectBeats(const float* samples, size_t count, int sampleRate) {
    BeatResult res;
    if (sampleRate <= 0 || count < static_cast<size_t>(sampleRate)) return res;
    const int window = 1024, hop = 512;
    std::vector<float> env = onsetEnvelope(samples, count, window, hop);
    if (env.size() < 16) return res;
    const double fps = static_cast<double>(sampleRate) / hop;

    // Tempo via autocorrelation over 60..180 BPM with a mild preference near 120 BPM.
    int minLag = static_cast<int>(std::floor(fps * 60.0 / 180.0));
    int maxLag = static_cast<int>(std::ceil(fps * 60.0 / 60.0));
    maxLag = std::min<int>(maxLag, static_cast<int>(env.size()) - 1);
    double best = -1.0, total = 0.0;
    int bestLag = 0;
    for (int lag = std::max(1, minLag); lag <= maxLag; ++lag) {
        double acc = 0.0;
        for (size_t i = static_cast<size_t>(lag); i < env.size(); ++i) acc += env[i] * env[i - static_cast<size_t>(lag)];
        acc /= static_cast<double>(env.size() - static_cast<size_t>(lag));
        double bpm = 60.0 * fps / lag;
        double weight = std::exp(-0.5 * std::pow(std::log2(bpm / 120.0) / 0.9, 2.0));
        double score = acc * weight;
        total += score;
        if (score > best) { best = score; bestLag = lag; }
    }
    if (bestLag == 0) return res;
    // Refine with parabolic interpolation around the peak.
    auto ac = [&](int lag) {
        double acc = 0.0;
        for (size_t i = static_cast<size_t>(lag); i < env.size(); ++i) acc += env[i] * env[i - static_cast<size_t>(lag)];
        return acc / static_cast<double>(env.size() - static_cast<size_t>(lag));
    };
    double period = bestLag;
    if (bestLag > minLag && bestLag < maxLag) {
        double y0 = ac(bestLag - 1), y1 = ac(bestLag), y2 = ac(bestLag + 1);
        double den = y0 - 2 * y1 + y2;
        if (std::fabs(den) > 1e-12) period = bestLag + 0.5 * (y0 - y2) / den;
    }
    res.bpm = 60.0 * fps / period;
    int nLags = maxLag - std::max(1, minLag) + 1;
    res.confidence = total > 0 ? std::min(1.0, best / (total / nLags) / 6.0) : 0.0;

    // Phase: choose the offset whose grid collects the most onset energy.
    double bestPhaseScore = -1.0, phase = 0.0;
    for (int p = 0; p < static_cast<int>(std::ceil(period)); ++p) {
        double s = 0.0;
        for (double t = p; t < env.size(); t += period) s += env[static_cast<size_t>(std::lround(t)) < env.size() ? static_cast<size_t>(std::lround(t)) : env.size() - 1];
        if (s > bestPhaseScore) { bestPhaseScore = s; phase = p; }
    }
    // Snap each grid point to the strongest onset within a small window.
    const int snap = std::max(1, static_cast<int>(period * 0.1));
    for (double t = phase; t < env.size(); t += period) {
        int c = static_cast<int>(std::lround(t)), bi = c;
        float bv = -1.f;
        for (int k = std::max(0, c - snap); k <= std::min<int>(static_cast<int>(env.size()) - 1, c + snap); ++k) {
            if (env[static_cast<size_t>(k)] > bv) { bv = env[static_cast<size_t>(k)]; bi = k; }
        }
        // Frame index -> time of the frame center.
        res.beatTimesSec.push_back((bi * hop + window / 2.0) / sampleRate);
    }
    return res;
}

double rmsDb(const float* samples, size_t count) {
    if (count == 0 || samples == nullptr) return -120.0;
    double acc = 0.0;
    for (size_t i = 0; i < count; ++i) acc += static_cast<double>(samples[i]) * samples[i];
    double rms = std::sqrt(acc / count);
    return rms <= 1e-6 ? -120.0 : 20.0 * std::log10(rms);
}

}  // namespace reel
