// Audio analysis used by the timeline: waveform peaks, onset envelope, tempo and beat grid.
#pragma once

#include <cstddef>
#include <vector>

namespace reel {

// Max absolute amplitude per bucket. Output has exactly `buckets` entries in [0,1].
std::vector<float> computePeaks(const float* samples, size_t count, int buckets);

// Onset strength envelope via half-wave rectified spectral flux.
// hop and window are in samples; window must be a power of two.
std::vector<float> onsetEnvelope(const float* samples, size_t count, int window, int hop);

struct BeatResult {
    double bpm = 0.0;
    std::vector<double> beatTimesSec;
    double confidence = 0.0;  // 0..1
};

// Estimates tempo and a beat grid aligned to the strongest onsets.
BeatResult detectBeats(const float* samples, size_t count, int sampleRate);

// RMS level in dBFS (returns -120 for silence).
double rmsDb(const float* samples, size_t count);

// In-place iterative radix-2 FFT. n must be a power of two. re/im length n.
void fft(float* re, float* im, int n);

}  // namespace reel
