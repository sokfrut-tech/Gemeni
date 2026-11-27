#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include "stable-diffusion.h"

#define LOG_TAG "JarvisSD"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_sd_mutex;
std::atomic<sd_ctx_t*> g_active_ctx{nullptr};
std::mutex g_error_mutex;
std::string g_last_error;

struct ProgressBridge {
    JNIEnv* env = nullptr;
    jobject callback = nullptr;
    jmethodID onProgress = nullptr;
};

void set_last_error(const std::string& msg) {
    std::lock_guard<std::mutex> lock(g_error_mutex);
    g_last_error = msg;
    if (!msg.empty()) {
        LOGE("%s", msg.c_str());
    }
}

void jarvis_sd_log_cb(enum sd_log_level_t level, const char* text, void* /*data*/) {
    if (!text) return;
    if (level == SD_LOG_ERROR) {
        LOGE("%s", text);
    } else if (level == SD_LOG_WARN) {
        LOGW("%s", text);
    }
}

void jarvis_sd_progress_cb(int step, int steps, float /*time*/, void* data) {
    auto* bridge = static_cast<ProgressBridge*>(data);
    if (!bridge || !bridge->env || !bridge->callback || !bridge->onProgress) return;
    bridge->env->CallVoidMethod(bridge->callback, bridge->onProgress, static_cast<jint>(step), static_cast<jint>(steps));
    if (bridge->env->ExceptionCheck()) {
        bridge->env->ExceptionClear();
    }
}

std::string jstring_to_utf8(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* raw = env->GetStringUTFChars(jstr, nullptr);
    if (!raw) return "";
    std::string out(raw);
    env->ReleaseStringUTFChars(jstr, raw);
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_jarvis_breeno_assistant_media_StableDiffusionNativeBridge_nativeGetLastError(
    JNIEnv* env,
    jclass /*clazz*/
) {
    std::lock_guard<std::mutex> lock(g_error_mutex);
    return env->NewStringUTF(g_last_error.c_str());
}

JNIEXPORT void JNICALL
Java_com_jarvis_breeno_assistant_media_StableDiffusionNativeBridge_nativeCancel(
    JNIEnv* /*env*/,
    jclass /*clazz*/
) {
    sd_ctx_t* ctx = g_active_ctx.load();
    if (ctx) {
        sd_cancel_generation(ctx, SD_CANCEL_ALL);
    }
}

JNIEXPORT jintArray JNICALL
Java_com_jarvis_breeno_assistant_media_StableDiffusionNativeBridge_nativeGenerateImageArgb(
    JNIEnv* env,
    jclass /*clazz*/,
    jstring jModelPath,
    jboolean isStandaloneDiffusion,
    jstring jVaePath,
    jstring jClipLPath,
    jstring jClipGPath,
    jstring jT5xxlPath,
    jstring jPrompt,
    jstring jNegativePrompt,
    jint width,
    jint height,
    jint steps,
    jfloat cfgScale,
    jint clipSkip,
    jlong seed,
    jint nThreads,
    jobject progressCallback
) {
    std::lock_guard<std::mutex> lock(g_sd_mutex);
    set_last_error("");
    sd_set_log_callback(jarvis_sd_log_cb, nullptr);

    const std::string modelPath = jstring_to_utf8(env, jModelPath);
    const std::string vaePath = jstring_to_utf8(env, jVaePath);
    const std::string clipLPath = jstring_to_utf8(env, jClipLPath);
    const std::string clipGPath = jstring_to_utf8(env, jClipGPath);
    const std::string t5xxlPath = jstring_to_utf8(env, jT5xxlPath);
    const std::string prompt = jstring_to_utf8(env, jPrompt);
    const std::string negativePrompt = jstring_to_utf8(env, jNegativePrompt);

    if (modelPath.empty()) {
        set_last_error("Не указан путь к локальной модели (.gguf / .safetensors)");
        return nullptr;
    }

    sd_ctx_opt_t* ctxOpt = sd_ctx_opt_create();
    sd_img_gen_opt_t* imgOpt = nullptr;
    sd_ctx_t* ctx = nullptr;
    sd_result_t* result = nullptr;
    jintArray outArray = nullptr;

    ProgressBridge progressBridge;
    if (progressCallback) {
        jclass cbCls = env->GetObjectClass(progressCallback);
        if (cbCls) {
            progressBridge.env = env;
            progressBridge.callback = progressCallback;
            progressBridge.onProgress = env->GetMethodID(cbCls, "onProgress", "(II)V");
            env->DeleteLocalRef(cbCls);
        }
    }

    auto failWithSdError = [&](const char* stage) {
        const char* err = sd_get_last_error();
        std::string msg = std::string(stage) + ": " + (err && err[0] ? err : "неизвестная ошибка stable-diffusion.cpp");
        set_last_error(msg);
    };

    if (!ctxOpt) {
        set_last_error("Не удалось выделить sd_ctx_opt_t");
        return nullptr;
    }

    const int safeThreads = std::clamp(static_cast<int>(nThreads), 1, 6);
    const char* modelKey = (isStandaloneDiffusion == JNI_TRUE) ? "diffusion_model_path" : "model_path";

    if (sd_opt_set_string(ctxOpt, modelKey, modelPath.c_str()) != SD_STATUS_OK ||
        sd_opt_set_int(ctxOpt, "n_threads", safeThreads) != SD_STATUS_OK) {
        failWithSdError("Ошибка параметров модели");
        goto cleanup;
    }

    if (!vaePath.empty() && sd_opt_set_string(ctxOpt, "vae_path", vaePath.c_str()) != SD_STATUS_OK) {
        failWithSdError("Ошибка параметра vae_path");
        goto cleanup;
    }
    if (!clipLPath.empty() && sd_opt_set_string(ctxOpt, "clip_l_path", clipLPath.c_str()) != SD_STATUS_OK) {
        failWithSdError("Ошибка параметра clip_l_path");
        goto cleanup;
    }
    if (!clipGPath.empty() && sd_opt_set_string(ctxOpt, "clip_g_path", clipGPath.c_str()) != SD_STATUS_OK) {
        failWithSdError("Ошибка параметра clip_g_path");
        goto cleanup;
    }
    if (!t5xxlPath.empty() && sd_opt_set_string(ctxOpt, "t5xxl_path", t5xxlPath.c_str()) != SD_STATUS_OK) {
        failWithSdError("Ошибка параметра t5xxl_path");
        goto cleanup;
    }

    if (sd_ctx_create(ctxOpt, &ctx) != SD_STATUS_OK || !ctx) {
        failWithSdError("Не удалось загрузить локальную модель");
        goto cleanup;
    }

    g_active_ctx.store(ctx);
    sd_set_progress_callback(jarvis_sd_progress_cb, &progressBridge);

    imgOpt = sd_img_gen_opt_create();
    if (!imgOpt) {
        set_last_error("Не удалось выделить sd_img_gen_opt_t");
        goto cleanup;
    }

    {
        const int safeW = std::clamp(static_cast<int>(width), 256, 1024);
        const int safeH = std::clamp(static_cast<int>(height), 256, 1024);
        const int safeSteps = std::clamp(static_cast<int>(steps), 1, 60);
        const double safeCfg = std::clamp(static_cast<double>(cfgScale), 1.0, 20.0);

        if (sd_opt_set_string(imgOpt, "prompt", prompt.c_str()) != SD_STATUS_OK ||
            sd_opt_set_string(imgOpt, "negative_prompt", negativePrompt.c_str()) != SD_STATUS_OK ||
            sd_opt_set_int(imgOpt, "width", safeW) != SD_STATUS_OK ||
            sd_opt_set_int(imgOpt, "height", safeH) != SD_STATUS_OK ||
            sd_opt_set_int(imgOpt, "seed", static_cast<int64_t>(seed)) != SD_STATUS_OK ||
            sd_opt_set_int(imgOpt, "sample.steps", safeSteps) != SD_STATUS_OK ||
            sd_opt_set_float(imgOpt, "sample.guidance.txt_cfg", safeCfg) != SD_STATUS_OK ||
            sd_opt_set_string(imgOpt, "sample.method", "auto") != SD_STATUS_OK ||
            sd_opt_set_string(imgOpt, "sample.scheduler", "auto") != SD_STATUS_OK ||
            sd_opt_set_bool(imgOpt, "vae_tiling.enabled", true) != SD_STATUS_OK) {
            failWithSdError("Ошибка параметров генерации");
            goto cleanup;
        }
        if (clipSkip > 0) {
            sd_opt_set_int(imgOpt, "clip_skip", static_cast<int64_t>(clipSkip));
        }
    }

    LOGI("Starting on-device SD generation (%dx%d, steps=%d, threads=%d)", width, height, steps, safeThreads);
    {
        const sd_status_t st = sd_generate_image(ctx, imgOpt, &result);
        if (st == SD_STATUS_CANCELLED) {
            set_last_error("Генерация отменена пользователем");
            goto cleanup;
        }
        if (st != SD_STATUS_OK || !result || sd_result_get_image_count(result) == 0) {
            failWithSdError("Ошибка генерации изображения");
            goto cleanup;
        }
    }

    {
        const sd_tensor_t* img = sd_result_get_image(result, 0);
        if (!img || sd_tensor_rank(img) < 3) {
            set_last_error("Некорректный тензор изображения");
            goto cleanup;
        }
        const int64_t channels = sd_tensor_dim(img, 0);
        const int64_t outW = sd_tensor_dim(img, 1);
        const int64_t outH = sd_tensor_dim(img, 2);
        const auto* bytes = static_cast<const uint8_t*>(sd_tensor_data(img));
        if (!bytes || outW <= 0 || outH <= 0 || channels < 3) {
            set_last_error("Пустой буфер пикселей");
            goto cleanup;
        }

        const size_t pixelCount = static_cast<size_t>(outW) * static_cast<size_t>(outH);
        // Pack [outW, outH, ARGB pixels...] so Kotlin knows exact aligned dimensions.
        std::vector<jint> packed(pixelCount + 2);
        packed[0] = static_cast<jint>(outW);
        packed[1] = static_cast<jint>(outH);
        for (size_t i = 0; i < pixelCount; ++i) {
            const size_t base = i * static_cast<size_t>(channels);
            const uint8_t r = bytes[base + 0];
            const uint8_t g = bytes[base + 1];
            const uint8_t b = bytes[base + 2];
            const uint8_t a = (channels >= 4) ? bytes[base + 3] : 255;
            packed[i + 2] = static_cast<jint>(
                (static_cast<uint32_t>(a) << 24) |
                (static_cast<uint32_t>(r) << 16) |
                (static_cast<uint32_t>(g) << 8) |
                static_cast<uint32_t>(b)
            );
        }

        outArray = env->NewIntArray(static_cast<jsize>(packed.size()));
        if (outArray) {
            env->SetIntArrayRegion(outArray, 0, static_cast<jsize>(packed.size()), packed.data());
        }
    }

cleanup:
    sd_set_progress_callback(nullptr, nullptr);
    g_active_ctx.store(nullptr);
    if (result) sd_result_free(result);
    if (ctx) sd_ctx_free(ctx);
    if (imgOpt) sd_opt_free(imgOpt);
    if (ctxOpt) sd_opt_free(ctxOpt);
    return outArray;
}

} // extern "C"
