#include "captions_core.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "whisper.h"

namespace reel {

namespace {
void quietLog(enum ggml_log_level, const char*, void*) {}

struct CallbackData {
    const std::function<void(int)>* progress;
    std::atomic<bool>* cancel;
};

// Trims spaces and strips whisper's special markers such as "[_BEG_]" or "[_TT_123]".
std::string clean(const char* raw) {
    std::string s(raw ? raw : "");
    if (s.rfind("[_", 0) == 0) return {};
    size_t a = s.find_first_not_of(" \t\n");
    if (a == std::string::npos) return {};
    size_t b = s.find_last_not_of(" \t\n");
    return s.substr(a, b - a + 1);
}
}  // namespace

Transcriber::Transcriber(const std::string& modelPath) {
    whisper_log_set(quietLog, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    cp.flash_attn = false;
    ctx_ = whisper_init_from_file_with_params(modelPath.c_str(), cp);
}

Transcriber::Transcriber(whisper_model_loader* loader) {
    whisper_log_set(quietLog, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    cp.flash_attn = false;
    ctx_ = whisper_init_with_params(loader, cp);
}

Transcriber::~Transcriber() {
    if (ctx_) whisper_free(ctx_);
}

bool Transcriber::transcribe(const std::vector<float>& samples, const TranscribeOptions& opt,
                             const std::function<void(int)>& progress, std::atomic<bool>* cancel,
                             std::vector<Word>& out, std::string& detectedLanguage) {
    out.clear();
    if (!ctx_ || samples.empty()) return ctx_ != nullptr;

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads = std::max(1, opt.threads);
    p.print_progress = false;
    p.print_realtime = false;
    p.print_timestamps = false;
    p.print_special = false;
    p.translate = opt.translateToEnglish;
    p.language = opt.language.empty() ? "auto" : opt.language.c_str();
    p.detect_language = false;
    p.no_context = true;
    p.suppress_blank = true;
    p.suppress_nst = true;
    // One word per segment gives each word its own start and end.
    p.token_timestamps = true;
    p.max_len = 1;
    p.split_on_word = true;

    CallbackData data{&progress, cancel};
    p.progress_callback = [](struct whisper_context*, struct whisper_state*, int pct, void* user) {
        auto* d = static_cast<CallbackData*>(user);
        if (d->progress && *d->progress) (*d->progress)(pct);
    };
    p.progress_callback_user_data = &data;
    p.abort_callback = [](void* user) {
        auto* d = static_cast<CallbackData*>(user);
        return d->cancel != nullptr && d->cancel->load();
    };
    p.abort_callback_user_data = &data;

    int rc = whisper_full(ctx_, p, samples.data(), static_cast<int>(samples.size()));
    if (rc != 0 || (cancel && cancel->load())) return false;

    int lang = whisper_full_lang_id(ctx_);
    detectedLanguage = lang >= 0 ? whisper_lang_str(lang) : "";

    const int n = whisper_full_n_segments(ctx_);
    for (int i = 0; i < n; ++i) {
        std::string text = clean(whisper_full_get_segment_text(ctx_, i));
        if (text.empty()) continue;
        Word w;
        w.text = text;
        w.startMs = whisper_full_get_segment_t0(ctx_, i) * 10;
        w.endMs = whisper_full_get_segment_t1(ctx_, i) * 10;
        float prob = 0.f;
        int nt = whisper_full_n_tokens(ctx_, i);
        int counted = 0;
        for (int t = 0; t < nt; ++t) {
            auto td = whisper_full_get_token_data(ctx_, i, t);
            if (td.id >= whisper_token_eot(ctx_)) continue;
            prob += td.p;
            ++counted;
        }
        w.prob = counted ? prob / counted : 0.f;
        // Timestamps can run a few hundred ms past the audio; a caption must not outlast its clip.
        const int64_t audioMs = static_cast<int64_t>(samples.size()) / 16;
        w.startMs = std::min<int64_t>(w.startMs, audioMs);
        w.endMs = std::min<int64_t>(w.endMs, audioMs);
        if (w.endMs < w.startMs) w.endMs = w.startMs;
        out.push_back(std::move(w));
    }
    // Keep times monotonic; whisper can overlap neighbours by a few ms.
    for (size_t i = 1; i < out.size(); ++i) {
        if (out[i].startMs < out[i - 1].startMs) out[i].startMs = out[i - 1].startMs;
        if (out[i].endMs < out[i].startMs) out[i].endMs = out[i].startMs;
    }
    return true;
}

std::vector<float> resample(const float* in, size_t n, int fromRate, int toRate) {
    if (n == 0 || fromRate <= 0 || toRate <= 0) return {};
    if (fromRate == toRate) return std::vector<float>(in, in + n);
    const double ratio = static_cast<double>(toRate) / fromRate;
    const size_t outN = static_cast<size_t>(std::floor(n * ratio));
    std::vector<float> out(outN);
    // Cut off at the lower Nyquist so downsampling does not alias.
    const double cutoff = std::min(1.0, ratio) * 0.95;
    const int half = 16;  // taps on each side at the input rate (scaled when downsampling)
    const double scale = 1.0 / std::min(1.0, ratio);
    const int span = static_cast<int>(std::ceil(half * scale));
    for (size_t i = 0; i < outN; ++i) {
        const double center = i / ratio;
        const long c = static_cast<long>(std::floor(center));
        double acc = 0.0, wsum = 0.0;
        for (long k = c - span + 1; k <= c + span; ++k) {
            if (k < 0 || k >= static_cast<long>(n)) continue;
            const double x = (center - k) * cutoff;
            const double sinc = std::fabs(x) < 1e-9 ? 1.0 : std::sin(M_PI * x) / (M_PI * x);
            const double t = (center - k) / (span + 1);
            const double win = 0.5 * (1.0 + std::cos(M_PI * std::clamp(t, -1.0, 1.0)));  // Hann
            const double w = sinc * win;
            acc += in[k] * w;
            wsum += w;
        }
        out[i] = static_cast<float>(wsum != 0.0 ? acc / wsum : 0.0);
    }
    return out;
}

}  // namespace reel
