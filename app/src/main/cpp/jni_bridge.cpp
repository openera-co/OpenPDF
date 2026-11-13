// JNI entry points for com.reel.editor.nativebridge.NativeCore.
#include <android/bitmap.h>
#include <jni.h>

#include <vector>

#include "audio_analysis.h"
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

}  // extern "C"
