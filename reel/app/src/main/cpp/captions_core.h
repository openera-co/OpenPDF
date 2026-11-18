// Speech to timed words with whisper.cpp. Plain C++ so it is testable on the host.
#pragma once

#include <atomic>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

struct whisper_context;
struct whisper_model_loader;

namespace reel {

struct Word {
    std::string text;  // UTF-8, leading space removed
    int64_t startMs;
    int64_t endMs;
    float prob;
};

struct TranscribeOptions {
    std::string language = "auto";  // "auto" or an ISO code such as "en"
    bool translateToEnglish = false;
    int threads = 4;
};

class Transcriber {
public:
    // Loads a ggml whisper model. Check ok() afterwards.
    explicit Transcriber(const std::string& modelPath);
    // Streams the model through [loader] (e.g. straight out of the APK). The loader is closed.
    explicit Transcriber(whisper_model_loader* loader);
    ~Transcriber();
    Transcriber(const Transcriber&) = delete;
    Transcriber& operator=(const Transcriber&) = delete;

    bool ok() const { return ctx_ != nullptr; }

    // 16 kHz mono samples in [-1, 1]. Progress is 0..100. Returns words in sample time.
    // Returns false if cancelled or whisper failed.
    bool transcribe(const std::vector<float>& samples16k, const TranscribeOptions& opt,
                    const std::function<void(int)>& progress, std::atomic<bool>* cancel,
                    std::vector<Word>& out, std::string& detectedLanguage);

private:
    ::whisper_context* ctx_ = nullptr;
};

// Band-limited resampling (windowed sinc), mono.
std::vector<float> resample(const float* in, size_t n, int fromRate, int toRate);

}  // namespace reel
