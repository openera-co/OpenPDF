// Host check of the caption core: resampler accuracy and a real transcription.
// Usage: captions_test <ggml-model.bin> <16-bit PCM wav>
#include <atomic>
#include <chrono>
#include <thread>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

#include "../captions_core.h"

static int failures = 0;
#define CHECK(c)                                                         \
    do {                                                                 \
        if (!(c)) { std::printf("FAIL %s:%d %s\n", __FILE__, __LINE__, #c); ++failures; } \
    } while (0)

static bool readWav(const char* path, std::vector<float>& out, int& rate) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;
    std::vector<char> d((std::istreambuf_iterator<char>(f)), {});
    if (d.size() < 44 || std::memcmp(d.data(), "RIFF", 4) != 0) return false;
    size_t pos = 12;
    int channels = 1, bits = 16;
    while (pos + 8 <= d.size()) {
        uint32_t len;
        std::memcpy(&len, &d[pos + 4], 4);
        if (std::memcmp(&d[pos], "fmt ", 4) == 0) {
            uint16_t ch, bp;
            uint32_t r;
            std::memcpy(&ch, &d[pos + 10], 2);
            std::memcpy(&r, &d[pos + 12], 4);
            std::memcpy(&bp, &d[pos + 22], 2);
            channels = ch; rate = static_cast<int>(r); bits = bp;
        } else if (std::memcmp(&d[pos], "data", 4) == 0) {
            if (bits != 16) return false;
            size_t frames = len / 2 / channels;
            out.resize(frames);
            for (size_t i = 0; i < frames; ++i) {
                float acc = 0;
                for (int c = 0; c < channels; ++c) {
                    int16_t s;
                    std::memcpy(&s, &d[pos + 8 + (i * channels + c) * 2], 2);
                    acc += s / 32768.f;
                }
                out[i] = acc / channels;
            }
            return true;
        }
        pos += 8 + len + (len & 1);
    }
    return false;
}

static void testResampler() {
    // A 440 Hz tone at 44.1 kHz resampled to 16 kHz keeps its frequency and level.
    const int from = 44100, to = 16000;
    std::vector<float> tone(from);
    for (int i = 0; i < from; ++i) tone[i] = 0.5f * std::sin(2 * M_PI * 440.0 * i / from);
    auto out = reel::resample(tone.data(), tone.size(), from, to);
    CHECK(std::abs(static_cast<int>(out.size()) - to) <= 1);
    double err = 0;
    for (int i = 100; i < to - 100; ++i) err = std::max(err, std::abs(out[i] - 0.5 * std::sin(2 * M_PI * 440.0 * i / to)));
    std::printf("resample 44.1k->16k max error %.4f\n", err);
    CHECK(err < 0.01);
    // A 12 kHz tone is above the new Nyquist (8 kHz) and must be filtered out, not aliased.
    for (int i = 0; i < from; ++i) tone[i] = 0.5f * std::sin(2 * M_PI * 12000.0 * i / from);
    out = reel::resample(tone.data(), tone.size(), from, to);
    double peak = 0;
    for (int i = 100; i < to - 100; ++i) peak = std::max(peak, static_cast<double>(std::abs(out[i])));
    std::printf("12 kHz residue after downsampling %.4f\n", peak);
    CHECK(peak < 0.05);
}

int main(int argc, char** argv) {
    testResampler();
    if (argc >= 3) {
        std::vector<float> pcm;
        int rate = 0;
        CHECK(readWav(argv[2], pcm, rate));
        auto s16 = reel::resample(pcm.data(), pcm.size(), rate, 16000);
        reel::Transcriber t(argv[1]);
        CHECK(t.ok());
        std::vector<reel::Word> words;
        std::string lang;
        int lastPct = -1;
        std::atomic<bool> cancel{false};
        auto t0 = std::chrono::steady_clock::now();
        bool ok = t.transcribe(s16, {}, [&](int p) { lastPct = p; }, &cancel, words, lang);
        double took = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
        std::printf("transcribed %.1f s of audio in %.1f s\n", s16.size() / 16000.0, took);
        CHECK(ok);
        std::string joined;
        for (auto& w : words) {
            std::printf("%6lld-%6lld %.2f %s\n", static_cast<long long>(w.startMs), static_cast<long long>(w.endMs), w.prob, w.text.c_str());
            joined += w.text + " ";
        }
        std::printf("language=%s progress=%d words=%zu\n", lang.c_str(), lastPct, words.size());
        CHECK(lang == "en");
        CHECK(words.size() >= 15);
        // speech.wav says "country"; talk.wav (NASA interview) says "San Diego".
        CHECK(joined.find("country") != std::string::npos || joined.find("San Diego") != std::string::npos);
        for (size_t i = 1; i < words.size(); ++i) CHECK(words[i].startMs >= words[i - 1].startMs);
        CHECK(!words.empty() && words.back().endMs <= static_cast<int64_t>(s16.size() / 16));
        // Cancelling stops the run.
        cancel = true;
        CHECK(!t.transcribe(s16, {}, nullptr, &cancel, words, lang));
        // Cancelling mid-run stops it promptly: the encoder checks between graph nodes, not only
        // after a whole 30 s window (on a slow phone that window takes many seconds).
        cancel = false;
        std::thread stopper([&] { std::this_thread::sleep_for(std::chrono::milliseconds(300)); cancel = true; });
        auto c0 = std::chrono::steady_clock::now();
        bool finished = t.transcribe(s16, {}, nullptr, &cancel, words, lang);
        double stopAfter = std::chrono::duration<double>(std::chrono::steady_clock::now() - c0).count();
        stopper.join();
        std::printf("cancel 0.3 s into a run returned after %.2f s (full run %.1f s)\n", stopAfter, took);
        CHECK(!finished);
        CHECK(stopAfter < 0.3 + std::max(0.5, took * 0.1));
    }
    std::printf(failures ? "FAILED %d\n" : "ALL PASSED\n", failures);
    return failures ? 1 : 0;
}
