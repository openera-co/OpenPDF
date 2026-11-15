// Beat tracking: a multi-band, bass-weighted log-spectral flux onset envelope, a tempo estimate from the
// averaged local autocorrelation (tempogram) with a log-normal prior, and dynamic-programming
// beat placement (Ellis 2007, as in librosa), which follows small tempo changes and lands each
// beat on a real onset. Beats are refined to sub-frame precision.
#include "audio_analysis.h"

#include <algorithm>
#include <cmath>
#include <numeric>

namespace reel {
namespace {

constexpr int kFft = 1024;
// Onset compression: log(1 + 100 * magnitude) per band.
constexpr double kCompress = 100.0;
// The lowest bands (kick and bass) weigh double in the onset envelope.
constexpr int kBassBands = 4;
constexpr float kBassWeight = 2.f;
// Bands used to check that beats sit on the low end, not between it.
constexpr int kLowBands = 5;
constexpr double kOffbeatRatio = 1.5;

struct Envelope {
    std::vector<float> v;  // onset strength per frame
    std::vector<float> low;  // onset strength of the lowest bands only (kick and bass)
    double fps = 0;        // frames per second
    double t0 = 0;         // time of frame 0, seconds
};

// Triangular filters on a mel scale between fLo and fHi.
std::vector<std::vector<std::pair<int, float>>> melBank(int sampleRate, int bands, double fLo, double fHi) {
    auto mel = [](double f) { return 2595.0 * std::log10(1.0 + f / 700.0); };
    auto hz = [](double m) { return 700.0 * (std::pow(10.0, m / 2595.0) - 1.0); };
    fHi = std::min(fHi, sampleRate / 2.0);
    std::vector<double> edges(static_cast<size_t>(bands + 2));
    for (int i = 0; i < bands + 2; ++i) edges[static_cast<size_t>(i)] = hz(mel(fLo) + (mel(fHi) - mel(fLo)) * i / (bands + 1));
    const double binHz = static_cast<double>(sampleRate) / kFft;
    std::vector<std::vector<std::pair<int, float>>> bank(static_cast<size_t>(bands));
    for (int b = 0; b < bands; ++b) {
        double lo = edges[static_cast<size_t>(b)], mid = edges[static_cast<size_t>(b + 1)], hi = edges[static_cast<size_t>(b + 2)];
        for (int k = 1; k < kFft / 2; ++k) {
            double f = k * binHz, w = 0;
            if (f > lo && f <= mid) w = (f - lo) / (mid - lo);
            else if (f > mid && f < hi) w = (hi - f) / (hi - mid);
            if (w > 0) bank[static_cast<size_t>(b)].push_back({k, static_cast<float>(w)});
        }
        // Very low bands may fall between bins: give them the nearest bin.
        if (bank[static_cast<size_t>(b)].empty()) bank[static_cast<size_t>(b)].push_back({std::max(1, static_cast<int>(std::lround(mid / binHz))), 1.f});
    }
    return bank;
}

Envelope onsetStrength(const float* samples, size_t count, int sampleRate) {
    Envelope e;
    // ~11.6 ms frames at 11 kHz; scale the hop with the rate so frame timing stays similar.
    const int hop = std::max(32, static_cast<int>(std::lround(sampleRate * 128.0 / 11025.0)));
    e.fps = static_cast<double>(sampleRate) / hop;
    e.t0 = 0;
    if (count < static_cast<size_t>(kFft)) return e;
    const int bands = 40;
    auto bank = melBank(sampleRate, bands, 30.0, 5500.0);
    std::vector<float> hann(kFft);
    for (int i = 0; i < kFft; ++i) hann[static_cast<size_t>(i)] = 0.5f - 0.5f * static_cast<float>(std::cos(2.0 * M_PI * i / kFft));
    std::vector<float> re(kFft), im(kFft), pw(kFft / 2);
    // Frames are centred on t = i * hop, like a centred STFT: pad half a window of silence.
    const long half = kFft / 2;
    const size_t frames = count / static_cast<size_t>(hop) + 1;
    std::vector<float> db(frames * bands);
    for (size_t f = 0; f < frames; ++f) {
        long start = static_cast<long>(f) * hop - half;
        for (int i = 0; i < kFft; ++i) {
            long s = start + i;
            float v = (s >= 0 && s < static_cast<long>(count)) ? samples[s] : 0.f;
            if (!std::isfinite(v)) v = 0.f;
            re[static_cast<size_t>(i)] = v * hann[static_cast<size_t>(i)];
            im[static_cast<size_t>(i)] = 0.f;
        }
        fft(re.data(), im.data(), kFft);
        for (int k = 0; k < kFft / 2; ++k) pw[static_cast<size_t>(k)] = re[static_cast<size_t>(k)] * re[static_cast<size_t>(k)] + im[static_cast<size_t>(k)] * im[static_cast<size_t>(k)];
        for (int b = 0; b < bands; ++b) {
            double acc = 0;
            for (auto& [k, w] : bank[static_cast<size_t>(b)]) acc += w * pw[static_cast<size_t>(k)];
            float d = static_cast<float>(std::log1p(kCompress * std::sqrt(acc)));
            db[f * bands + static_cast<size_t>(b)] = d;
        }
    }
    // Low bands (kick, bass) count more: in most music they carry the beat, while hats and
    // shakers often play between beats.
    std::vector<float> wt(static_cast<size_t>(bands));
    float wsum = 0;
    for (int b = 0; b < bands; ++b) { wt[static_cast<size_t>(b)] = b < kBassBands ? kBassWeight : 1.f; wsum += wt[static_cast<size_t>(b)]; }
    e.v.assign(frames, 0.f);
    e.low.assign(frames, 0.f);
    for (size_t f = 1; f < frames; ++f) {
        float acc = 0, lacc = 0;
        for (int b = 0; b < bands; ++b) {
            float d = db[f * bands + static_cast<size_t>(b)] - db[(f - 1) * bands + static_cast<size_t>(b)];
            if (d > 0) { acc += wt[static_cast<size_t>(b)] * d; if (b < kLowBands) lacc += d; }
        }
        e.v[f] = acc / wsum;
        e.low[f] = lacc;
    }
    return e;
}

// Average of the normalised local autocorrelation over the whole track (a mean tempogram).
std::vector<double> meanTempogram(const std::vector<float>& env, int maxLag, int win) {
    std::vector<double> out(static_cast<size_t>(maxLag + 1), 0.0);
    const int n = static_cast<int>(env.size());
    if (n < 4) return out;
    win = std::min(win, n);
    const int step = std::max(1, win / 8);
    int count = 0;
    std::vector<double> ac(static_cast<size_t>(maxLag + 1));
    std::vector<double> w(static_cast<size_t>(win));
    for (int i = 0; i < win; ++i) w[static_cast<size_t>(i)] = 0.5 - 0.5 * std::cos(2.0 * M_PI * i / std::max(1, win - 1));
    for (int s = 0; s + win <= n; s += step) {
        std::fill(ac.begin(), ac.end(), 0.0);
        for (int lag = 0; lag <= maxLag && lag < win; ++lag) {
            double a = 0;
            for (int i = lag; i < win; ++i) a += env[static_cast<size_t>(s + i)] * w[static_cast<size_t>(i)] * env[static_cast<size_t>(s + i - lag)] * w[static_cast<size_t>(i - lag)];
            ac[static_cast<size_t>(lag)] = a;
        }
        if (ac[0] <= 1e-12) continue;
        for (int lag = 0; lag <= maxLag; ++lag) out[static_cast<size_t>(lag)] += ac[static_cast<size_t>(lag)] / ac[0];
        ++count;
    }
    if (count > 0) for (auto& v : out) v /= count;
    return out;
}

}  // namespace

BeatResult detectBeats(const float* samples, size_t count, int sampleRate) {
    BeatResult res;
    if (samples == nullptr || sampleRate <= 0 || count < static_cast<size_t>(sampleRate)) return res;
    Envelope e = onsetStrength(samples, count, sampleRate);
    std::vector<float>& env = e.v;
    const int n = static_cast<int>(env.size());
    if (n < 32) return res;
    const double fps = e.fps;

    // Normalise the envelope by its standard deviation.
    double mean = std::accumulate(env.begin(), env.end(), 0.0) / n, var = 0;
    for (float v : env) var += (v - mean) * (v - mean);
    double sd = std::sqrt(var / n);
    if (sd < 1e-6) return res;  // silence or a pure tone
    std::vector<float> onset(static_cast<size_t>(n));
    for (int i = 0; i < n; ++i) onset[static_cast<size_t>(i)] = static_cast<float>(env[static_cast<size_t>(i)] / sd);

    // ---- tempo: mean tempogram (8 s windows) times a log-normal prior around 120 BPM.
    const int minLag = std::max(2, static_cast<int>(std::floor(fps * 60.0 / 240.0)));
    const int maxLag = std::min(n - 1, static_cast<int>(std::ceil(fps * 60.0 / 40.0)));
    if (maxLag <= minLag) return res;
    std::vector<double> tg = meanTempogram(onset, maxLag, static_cast<int>(std::lround(fps * 8.0)));
    double best = -1, sum = 0;
    int bestLag = 0, lags = 0;
    for (int lag = minLag; lag <= maxLag; ++lag) {
        double bpm = 60.0 * fps / lag;
        double prior = std::exp(-0.5 * std::pow(std::log2(bpm / 120.0), 2.0));
        double s = tg[static_cast<size_t>(lag)] * prior;
        sum += tg[static_cast<size_t>(lag)];
        ++lags;
        if (s > best) { best = s; bestLag = lag; }
    }
    if (bestLag == 0) return res;
    double period = bestLag;
    if (bestLag > minLag && bestLag < maxLag) {
        auto sc = [&](int l) { double b = 60.0 * fps / l; return tg[static_cast<size_t>(l)] * std::exp(-0.5 * std::pow(std::log2(b / 120.0), 2.0)); };
        double y0 = sc(bestLag - 1), y1 = sc(bestLag), y2 = sc(bestLag + 1), den = y0 - 2 * y1 + y2;
        if (std::fabs(den) > 1e-12) period = bestLag + std::clamp(0.5 * (y0 - y2) / den, -0.5, 0.5);
    }
    res.bpm = 60.0 * fps / period;
    // Pulse clarity: how far the chosen period stands above the average lag.
    double avg = lags > 0 ? sum / lags : 0;
    double clarity = avg > 1e-9 ? tg[static_cast<size_t>(bestLag)] / avg : 0;
    res.confidence = std::clamp((clarity - 1.0) / 2.0, 0.0, 1.0);

    // ---- dynamic-programming beat tracking.
    const int P = static_cast<int>(std::lround(period));
    std::vector<double> local(static_cast<size_t>(n), 0.0);
    {
        // Smooth the onsets with a Gaussian a little narrower than a beat.
        std::vector<double> g(static_cast<size_t>(2 * P + 1));
        for (int k = -P; k <= P; ++k) g[static_cast<size_t>(k + P)] = std::exp(-0.5 * std::pow(k * 32.0 / period, 2.0));
        for (int i = 0; i < n; ++i) {
            double a = 0;
            for (int k = -P; k <= P; ++k) {
                int j = i + k;
                if (j >= 0 && j < n) a += onset[static_cast<size_t>(j)] * g[static_cast<size_t>(k + P)];
            }
            local[static_cast<size_t>(i)] = a;
        }
    }
    const double tightness = 100.0;
    std::vector<double> cum(static_cast<size_t>(n));
    std::vector<int> back(static_cast<size_t>(n), -1);
    const int lo = static_cast<int>(std::lround(period * 2.0)), hi = static_cast<int>(std::lround(period / 2.0));
    bool firstBeat = true;
    double maxLocal = *std::max_element(local.begin(), local.end());
    for (int i = 0; i < n; ++i) {
        double bestScore = -1e300;
        int arg = -1;
        for (int j = i - lo; j <= i - hi; ++j) {
            if (j < 0) continue;
            double r = static_cast<double>(i - j) / period;
            double s = cum[static_cast<size_t>(j)] - tightness * std::pow(std::log(r), 2.0);
            if (s > bestScore) { bestScore = s; arg = j; }
        }
        // Until the first real onset there is nothing to link to.
        if (arg < 0 || (firstBeat && local[static_cast<size_t>(i)] < 0.01 * maxLocal)) {
            cum[static_cast<size_t>(i)] = local[static_cast<size_t>(i)];
            back[static_cast<size_t>(i)] = -1;
        } else {
            cum[static_cast<size_t>(i)] = local[static_cast<size_t>(i)] + bestScore;
            back[static_cast<size_t>(i)] = arg;
        }
        if (local[static_cast<size_t>(i)] >= 0.01 * maxLocal) firstBeat = false;
    }
    // Last beat: the last local maximum of the cumulative score above half the median maximum.
    std::vector<double> maxima;
    for (int i = 1; i + 1 < n; ++i)
        if (cum[static_cast<size_t>(i)] > cum[static_cast<size_t>(i - 1)] && cum[static_cast<size_t>(i)] >= cum[static_cast<size_t>(i + 1)]) maxima.push_back(cum[static_cast<size_t>(i)]);
    if (maxima.empty()) return res;
    std::vector<double> sorted = maxima;
    std::nth_element(sorted.begin(), sorted.begin() + static_cast<long>(sorted.size() / 2), sorted.end());
    double med = sorted[sorted.size() / 2];
    int last = -1;
    for (int i = n - 2; i >= 1; --i) {
        if (cum[static_cast<size_t>(i)] > cum[static_cast<size_t>(i - 1)] && cum[static_cast<size_t>(i)] >= cum[static_cast<size_t>(i + 1)] && cum[static_cast<size_t>(i)] >= 0.5 * med) { last = i; break; }
    }
    if (last < 0) return res;
    std::vector<int> beats;
    for (int i = last; i >= 0; i = back[static_cast<size_t>(i)]) beats.push_back(i);
    std::reverse(beats.begin(), beats.end());

    // Trim weak beats at the edges (fade-ins, silence): below half the RMS of the beat strengths.
    if (beats.size() > 2) {
        double rms = 0;
        for (int b : beats) rms += local[static_cast<size_t>(b)] * local[static_cast<size_t>(b)];
        rms = std::sqrt(rms / beats.size());
        size_t a = 0, z = beats.size();
        while (a < z && local[static_cast<size_t>(beats[a])] < 0.5 * rms) ++a;
        while (z > a && local[static_cast<size_t>(beats[z - 1])] < 0.5 * rms) --z;
        beats = std::vector<int>(beats.begin() + static_cast<long>(a), beats.begin() + static_cast<long>(z));
    }

    // Beats sit on the kick and bass in almost all music. If the low end hits clearly more often
    // halfway between the beats found, the tracker locked onto off-beats (hats, claps): move
    // every beat half a period, onto the nearest strong onset.
    if (beats.size() > 8) {
        auto lowNear = [&](double c) {
            float m = 0;
            int a = static_cast<int>(std::floor(c)) - 2, z = static_cast<int>(std::ceil(c)) + 2;
            for (int k = std::max(0, a); k <= std::min(n - 1, z); ++k) m = std::max(m, e.low[static_cast<size_t>(k)]);
            return static_cast<double>(m);
        };
        double on = 0, mid = 0;
        for (size_t i = 0; i + 1 < beats.size(); ++i) {
            on += lowNear(beats[i]);
            mid += lowNear((beats[i] + beats[i + 1]) / 2.0);
        }
        if (mid > kOffbeatRatio * on && mid > 0) {
            std::vector<int> shifted;
            const int reachMid = std::max(1, static_cast<int>(period * 0.1));
            for (size_t i = 0; i + 1 < beats.size(); ++i) {
                int c = (beats[i] + beats[i + 1]) / 2, bi = c;
                float bv = -1;
                for (int k = std::max(0, c - reachMid); k <= std::min(n - 1, c + reachMid); ++k)
                    if (onset[static_cast<size_t>(k)] > bv) { bv = onset[static_cast<size_t>(k)]; bi = k; }
                shifted.push_back(bi);
            }
            beats.swap(shifted);
        }
    }

    // Sub-frame timing: the onset peak nearest each beat, refined with a parabola.
    const int reach = std::max(1, static_cast<int>(std::lround(fps * 0.035)));
    for (int b : beats) {
        int c = b;
        float bv = onset[static_cast<size_t>(b)];
        for (int k = std::max(1, b - reach); k <= std::min(n - 2, b + reach); ++k)
            if (onset[static_cast<size_t>(k)] > bv) { bv = onset[static_cast<size_t>(k)]; c = k; }
        double frac = 0;
        if (c > 0 && c + 1 < n) {
            double y0 = onset[static_cast<size_t>(c - 1)], y1 = onset[static_cast<size_t>(c)], y2 = onset[static_cast<size_t>(c + 1)], den = y0 - 2 * y1 + y2;
            if (std::fabs(den) > 1e-9) frac = std::clamp(0.5 * (y0 - y2) / den, -0.5, 0.5);
        }
        // A centred window starts to see an attack before it happens; measured on tracks with
        // known beats, the flux peak runs about a quarter window early.
        double t = e.t0 + (c + frac) / fps + 0.26 * kFft / sampleRate;
        if (t >= 0) res.beatTimesSec.push_back(t);
    }
    return res;
}

}  // namespace reel
