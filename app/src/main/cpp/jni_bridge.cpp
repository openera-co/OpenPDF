// JNI entry points for com.reel.editor.nativebridge.NativeCore.
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <jni.h>

#include <atomic>
#include <memory>
#include <string>
#include <vector>

#include "audio_analysis.h"
#include "captions_core.h"
#include "voice_dsp.h"
#include "whisper.h"
#include "image_ops.h"
#include "looks.h"

namespace {
jstring str(JNIEnv* env, const char* s) { return env->NewStringUTF(s); }
}  // namespace

extern "C" {

JNIEXPORT jint JNICALL Java_com_reel_editor_nativebridge_NativeCore_lookCount(JNIEnv*, jobject) {
    return reel::lookCount();
}

JNIEXPORT jobjectArray JNICALL Java_com_reel_editor_nativebridge_NativeCore_lookInfo(JNIEnv* env, jobject, jint index) {
    if (index < 0 || index >= reel::lookCount()) return nullptr;
    const auto& info = reel::lookInfo(index);
    jobjectArray arr = env->NewObjectArray(3, env->FindClass("java/lang/String"), nullptr);
    env->SetObjectArrayElement(arr, 0, str(env, info.id));
    env->SetObjectArrayElement(arr, 1, str(env, info.name));
    env->SetObjectArrayElement(arr, 2, str(env, info.category));
    return arr;
}

JNIEXPORT jintArray JNICALL Java_com_reel_editor_nativebridge_NativeCore_renderLut(JNIEnv* env, jobject, jint index, jint size) {
    if (index < 0 || index >= reel::lookCount() || size < 2 || size > 64) return nullptr;
    auto lut = reel::renderLut(reel::lookParams(index), size);
    jintArray out = env->NewIntArray(static_cast<jsize>(lut.size()));
    env->SetIntArrayRegion(out, 0, static_cast<jsize>(lut.size()), reinterpret_cast<const jint*>(lut.data()));
    return out;
}

JNIEXPORT jfloatArray JNICALL Java_com_reel_editor_nativebridge_NativeCore_peaks(JNIEnv* env, jobject, jfloatArray samples, jint buckets) {
    jsize n = env->GetArrayLength(samples);
    jfloat* data = env->GetFloatArrayElements(samples, nullptr);
    auto peaks = reel::computePeaks(data, static_cast<size_t>(n), buckets);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(peaks.size()));
    env->SetFloatArrayRegion(out, 0, static_cast<jsize>(peaks.size()), peaks.data());
    return out;
}

// Returns [bpm, confidence, beat0, beat1, ...] in seconds.
JNIEXPORT jdoubleArray JNICALL Java_com_reel_editor_nativebridge_NativeCore_beats(JNIEnv* env, jobject, jfloatArray samples, jint sampleRate) {
    jsize n = env->GetArrayLength(samples);
    jfloat* data = env->GetFloatArrayElements(samples, nullptr);
    auto res = reel::detectBeats(data, static_cast<size_t>(n), sampleRate);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    std::vector<double> flat;
    flat.reserve(res.beatTimesSec.size() + 2);
    flat.push_back(res.bpm);
    flat.push_back(res.confidence);
    flat.insert(flat.end(), res.beatTimesSec.begin(), res.beatTimesSec.end());
    jdoubleArray out = env->NewDoubleArray(static_cast<jsize>(flat.size()));
    env->SetDoubleArrayRegion(out, 0, static_cast<jsize>(flat.size()), flat.data());
    return out;
}

JNIEXPORT jfloatArray JNICALL Java_com_reel_editor_nativebridge_NativeCore_pcm16ToMono(JNIEnv* env, jobject, jobject buffer, jint byteCount, jint channels) {
    auto* bytes = static_cast<const int16_t*>(env->GetDirectBufferAddress(buffer));
    if (bytes == nullptr || channels <= 0) return env->NewFloatArray(0);
    size_t samples = static_cast<size_t>(byteCount) / 2;
    std::vector<float> mono(samples / static_cast<size_t>(channels));
    size_t frames = reel::pcm16ToMono(bytes, samples, channels, mono.data());
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(frames));
    env->SetFloatArrayRegion(out, 0, static_cast<jsize>(frames), mono.data());
    return out;
}

JNIEXPORT jintArray JNICALL Java_com_reel_editor_nativebridge_NativeCore_histogram(JNIEnv* env, jobject, jobject bitmap) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return nullptr;
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return nullptr;
    std::vector<uint32_t> hist(1024);
    reel::histogramRgba(static_cast<const uint8_t*>(pixels), static_cast<int>(info.width), static_cast<int>(info.height), static_cast<int>(info.stride), hist.data());
    AndroidBitmap_unlockPixels(env, bitmap);
    jintArray out = env->NewIntArray(1024);
    env->SetIntArrayRegion(out, 0, 1024, reinterpret_cast<const jint*>(hist.data()));
    return out;
}

JNIEXPORT jdouble JNICALL Java_com_reel_editor_nativebridge_NativeCore_rmsDb(JNIEnv* env, jobject, jfloatArray samples) {
    jsize n = env->GetArrayLength(samples);
    jfloat* data = env->GetFloatArrayElements(samples, nullptr);
    double db = reel::rmsDb(data, static_cast<size_t>(n));
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    return db;
}

// ------------------------------------------------------------------ captions (whisper.cpp)

namespace {
struct WhisperHandle {
    std::unique_ptr<reel::Transcriber> t;
    std::atomic<bool> cancel{false};
};
}  // namespace

// Loads the speech model straight out of the APK (stored uncompressed), without copying it.
JNIEXPORT jlong JNICALL Java_com_reel_editor_nativebridge_NativeCore_speechOpenAsset(JNIEnv* env, jobject, jobject assets, jstring name) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assets);
    const char* n = env->GetStringUTFChars(name, nullptr);
    AAsset* asset = mgr ? AAssetManager_open(mgr, n, AASSET_MODE_STREAMING) : nullptr;
    env->ReleaseStringUTFChars(name, n);
    if (!asset) return 0;
    whisper_model_loader loader{};
    loader.context = asset;
    loader.read = [](void* ctx, void* out, size_t size) -> size_t {
        auto* a = static_cast<AAsset*>(ctx);
        size_t done = 0;
        while (done < size) {
            int r = AAsset_read(a, static_cast<char*>(out) + done, size - done);
            if (r <= 0) break;
            done += static_cast<size_t>(r);
        }
        return done;
    };
    loader.eof = [](void* ctx) -> bool { return AAsset_getRemainingLength64(static_cast<AAsset*>(ctx)) <= 0; };
    loader.close = [](void* ctx) { AAsset_close(static_cast<AAsset*>(ctx)); };
    auto h = std::make_unique<WhisperHandle>();
    h->t = std::make_unique<reel::Transcriber>(&loader);
    if (!h->t->ok()) return 0;
    return reinterpret_cast<jlong>(h.release());
}

JNIEXPORT void JNICALL Java_com_reel_editor_nativebridge_NativeCore_speechClose(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<WhisperHandle*>(handle);
}

JNIEXPORT void JNICALL Java_com_reel_editor_nativebridge_NativeCore_speechCancel(JNIEnv*, jobject, jlong handle) {
    if (handle) reinterpret_cast<WhisperHandle*>(handle)->cancel = true;
}

// Returns [String[] words, long[] startMs, long[] endMs, float[] prob, String language], or null if
// cancelled or failed. The listener's onProgress(int) is called on this thread.
JNIEXPORT jobjectArray JNICALL Java_com_reel_editor_nativebridge_NativeCore_speechTranscribe(
    JNIEnv* env, jobject, jlong handle, jfloatArray samples, jstring language, jboolean translate, jint threads, jobject listener) {
    auto* h = reinterpret_cast<WhisperHandle*>(handle);
    if (!h) return nullptr;
    h->cancel = false;
    jsize n = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(n));
    env->GetFloatArrayRegion(samples, 0, n, pcm.data());
    reel::TranscribeOptions opt;
    const char* lang = env->GetStringUTFChars(language, nullptr);
    opt.language = lang;
    env->ReleaseStringUTFChars(language, lang);
    opt.translateToEnglish = translate;
    opt.threads = threads;
    jmethodID onProgress = nullptr;
    if (listener) onProgress = env->GetMethodID(env->GetObjectClass(listener), "onProgress", "(I)V");
    std::function<void(int)> progress = [&](int pct) {
        if (listener && onProgress) env->CallVoidMethod(listener, onProgress, pct);
    };
    std::vector<reel::Word> words;
    std::string detected;
    if (!h->t->transcribe(pcm, opt, progress, &h->cancel, words, detected)) return nullptr;

    jclass strCls = env->FindClass("java/lang/String");
    jsize count = static_cast<jsize>(words.size());
    jobjectArray texts = env->NewObjectArray(count, strCls, nullptr);
    std::vector<jlong> starts(words.size()), ends(words.size());
    std::vector<jfloat> probs(words.size());
    for (jsize i = 0; i < count; ++i) {
        jstring s = env->NewStringUTF(words[i].text.c_str());
        env->SetObjectArrayElement(texts, i, s);
        env->DeleteLocalRef(s);
        starts[i] = words[i].startMs;
        ends[i] = words[i].endMs;
        probs[i] = words[i].prob;
    }
    jlongArray js = env->NewLongArray(count);
    env->SetLongArrayRegion(js, 0, count, starts.data());
    jlongArray je = env->NewLongArray(count);
    env->SetLongArrayRegion(je, 0, count, ends.data());
    jfloatArray jp = env->NewFloatArray(count);
    env->SetFloatArrayRegion(jp, 0, count, probs.data());
    jobjectArray out = env->NewObjectArray(5, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(out, 0, texts);
    env->SetObjectArrayElement(out, 1, js);
    env->SetObjectArrayElement(out, 2, je);
    env->SetObjectArrayElement(out, 3, jp);
    env->SetObjectArrayElement(out, 4, env->NewStringUTF(detected.c_str()));
    return out;
}

JNIEXPORT jfloatArray JNICALL Java_com_reel_editor_nativebridge_NativeCore_resample(JNIEnv* env, jobject, jfloatArray samples, jint fromRate, jint toRate) {
    jsize n = env->GetArrayLength(samples);
    std::vector<float> in(static_cast<size_t>(n));
    env->GetFloatArrayRegion(samples, 0, n, in.data());
    auto out = reel::resample(in.data(), in.size(), fromRate, toRate);
    jfloatArray arr = env->NewFloatArray(static_cast<jsize>(out.size()));
    env->SetFloatArrayRegion(arr, 0, static_cast<jsize>(out.size()), out.data());
    return arr;
}

// ------------------------------------------------------------------ voice processing

JNIEXPORT jlong JNICALL Java_com_reel_editor_nativebridge_NativeCore_voiceCreate(JNIEnv*, jobject, jint rate, jint channels, jfloat enhance, jfloat denoise, jboolean normalize, jint effect) {
    reel::VoiceSettings s;
    s.enhance = enhance;
    s.denoise = denoise;
    s.normalize = normalize;
    s.effect = static_cast<reel::VoiceEffect>(effect);
    return reinterpret_cast<jlong>(new reel::VoiceChain(rate, channels, s));
}

JNIEXPORT void JNICALL Java_com_reel_editor_nativebridge_NativeCore_voiceFree(JNIEnv*, jobject, jlong h) {
    delete reinterpret_cast<reel::VoiceChain*>(h);
}

JNIEXPORT void JNICALL Java_com_reel_editor_nativebridge_NativeCore_voiceReset(JNIEnv*, jobject, jlong h) {
    reinterpret_cast<reel::VoiceChain*>(h)->reset();
}

// [io] is a direct buffer of interleaved floats, processed in place. Returns frames written.
JNIEXPORT jint JNICALL Java_com_reel_editor_nativebridge_NativeCore_voiceProcess(JNIEnv* env, jobject, jlong h, jobject io, jint frames) {
    auto* buf = static_cast<float*>(env->GetDirectBufferAddress(io));
    if (!buf) return 0;
    return reinterpret_cast<reel::VoiceChain*>(h)->process(buf, frames, buf);
}

JNIEXPORT jint JNICALL Java_com_reel_editor_nativebridge_NativeCore_voiceDrain(JNIEnv* env, jobject, jlong h, jobject out, jint maxFrames) {
    auto* buf = static_cast<float*>(env->GetDirectBufferAddress(out));
    if (!buf) return 0;
    return reinterpret_cast<reel::VoiceChain*>(h)->drain(buf, maxFrames);
}

JNIEXPORT jint JNICALL Java_com_reel_editor_nativebridge_NativeCore_voiceLatency(JNIEnv*, jobject, jlong h) {
    return reinterpret_cast<reel::VoiceChain*>(h)->latency();
}

}  // extern "C"
