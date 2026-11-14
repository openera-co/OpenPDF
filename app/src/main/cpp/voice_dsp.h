// Voice processing for clips and voiceovers: vocal enhancer, noise reduction, loudness
// leveling and character effects. Interleaved float PCM in [-1, 1], any sample rate.
#pragma once

#include <memory>
#include <vector>

namespace reel {

// Effects done here; pitch effects (chipmunk, deep...) are done by Media3's Sonic before this.
enum class VoiceEffect : int {
    None = 0,
    Robot = 1,
    Alien = 2,
    Telephone = 3,
    Megaphone = 4,
    Radio = 5,
    Echo = 6,
    Cave = 7,
    Hall = 8,
    Room = 9,
    Underwater = 10,
    LoFi = 11,
    Vibrato = 12,
    Monster = 13,
};

struct VoiceSettings {
    float enhance = 0.f;   // 0..1 vocal enhancer amount
    float denoise = 0.f;   // 0..1 noise reduction amount
    bool normalize = false;  // slow automatic gain toward a steady speech level
    VoiceEffect effect = VoiceEffect::None;
};

class VoiceChain {
public:
    VoiceChain(int sampleRate, int channels, const VoiceSettings& s);
    ~VoiceChain();
    VoiceChain(const VoiceChain&) = delete;
    VoiceChain& operator=(const VoiceChain&) = delete;

    // Processes [frames] interleaved frames from [in] and writes the output to [out] (which may
    // equal [in]). Output is time-aligned with input: any internal delay is removed, so fewer
    // frames than given may come out at the start. Returns frames written.
    int process(const float* in, int frames, float* out);
    // At end of stream: writes the frames still held back by internal delay. Returns frames.
    int drain(float* out, int maxFrames);
    // Clears all state (after a seek).
    void reset();
    // Frames held back by the noise reducer.
    int latency() const;

private:
    struct Impl;
    std::unique_ptr<Impl> d;
};

}  // namespace reel
