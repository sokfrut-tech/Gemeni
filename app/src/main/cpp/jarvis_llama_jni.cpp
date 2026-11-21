#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "JarvisLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::once_flag g_backend_once;

struct LlamaSession {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    std::atomic<bool> cancel_requested{false};
    std::mutex mutex;
};

static void jarvis_llama_log_callback(ggml_log_level level, const char* text, void* /*user_data*/) {
    if (!text) return;
    if (level == GGML_LOG_LEVEL_ERROR) {
        LOGE("%s", text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        LOGW("%s", text);
    }
}

static std::string jstring_to_utf8(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    jclass strCls = env->FindClass("java/lang/String");
    jmethodID getBytes = env->GetMethodID(strCls, "getBytes", "(Ljava/lang/String;)[B");
    jstring charset = env->NewStringUTF("UTF-8");
    auto* bytes = static_cast<jbyteArray>(env->CallObjectMethod(jstr, getBytes, charset));
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(strCls);
    if (!bytes || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (bytes) env->DeleteLocalRef(bytes);
        return "";
    }
    jsize len = env->GetArrayLength(bytes);
    std::string out(static_cast<size_t>(len), '\0');
    if (len > 0) {
        env->GetByteArrayRegion(bytes, 0, len, reinterpret_cast<jbyte*>(out.data()));
    }
    env->DeleteLocalRef(bytes);
    return out;
}

static jstring utf8_to_jstring(JNIEnv* env, const std::string& utf8) {
    if (utf8.empty()) {
        return env->NewStringUTF("");
    }
    jclass strCls = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(strCls, "<init>", "([BLjava/lang/String;)V");
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(utf8.size()));
    env->SetByteArrayRegion(
        bytes,
        0,
        static_cast<jsize>(utf8.size()),
        reinterpret_cast<const jbyte*>(utf8.data())
    );
    jstring charset = env->NewStringUTF("UTF-8");
    jstring result = static_cast<jstring>(env->NewObject(strCls, ctor, bytes, charset));
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(strCls);
    return result;
}

// Returns the byte length of the longest prefix containing only complete UTF-8 sequences.
static size_t utf8_complete_prefix_len(const std::string& buf) {
    size_t i = 0;
    const size_t n = buf.size();
    while (i < n) {
        const auto b = static_cast<unsigned char>(buf[i]);
        size_t seq_len = 1;
        if ((b & 0x80u) == 0x00u) {
            seq_len = 1;
        } else if ((b & 0xE0u) == 0xC0u) {
            seq_len = 2;
        } else if ((b & 0xF0u) == 0xE0u) {
            seq_len = 3;
        } else if ((b & 0xF8u) == 0xF0u) {
            seq_len = 4;
        } else {
            // Stray continuation/invalid byte: skip 1 byte so streaming never stalls.
            i += 1;
            continue;
        }
        if (i + seq_len > n) {
            break;
        }
        bool valid = true;
        for (size_t k = 1; k < seq_len; ++k) {
            if ((static_cast<unsigned char>(buf[i + k]) & 0xC0u) != 0x80u) {
                valid = false;
                break;
            }
        }
        i += valid ? seq_len : 1;
    }
    return i;
}

static void strip_trailing_marker(std::string& text, const std::string& marker) {
    if (text.size() >= marker.size() &&
        text.compare(text.size() - marker.size(), marker.size(), marker) == 0) {
        text.erase(text.size() - marker.size());
    }
}

static bool ends_with(const std::string& text, const std::string& suffix) {
    return text.size() >= suffix.size() &&
           text.compare(text.size() - suffix.size(), suffix.size(), suffix) == 0;
}

static bool contains_stop_marker(const std::string& text) {
    return ends_with(text, "<|im_end|>") || ends_with(text, "<|endoftext|>");
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_jarvis_breeno_assistant_core_LlamaNativeBridge_nativeBackendInit(JNIEnv* /*env*/, jclass /*clazz*/) {
    std::call_once(g_backend_once, []() {
        llama_log_set(jarvis_llama_log_callback, nullptr);
        llama_backend_init();
        LOGI("llama.cpp backend initialized");
    });
}

JNIEXPORT jlong JNICALL
Java_com_jarvis_breeno_assistant_core_LlamaNativeBridge_nativeLoadModel(
    JNIEnv* env,
    jclass /*clazz*/,
    jstring jmodel_path,
    jint n_ctx,
    jint n_threads
) {
    Java_com_jarvis_breeno_assistant_core_LlamaNativeBridge_nativeBackendInit(env, nullptr);
    const std::string model_path = jstring_to_utf8(env, jmodel_path);
    if (model_path.empty()) {
        LOGE("Empty model path");
        return 0L;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    llama_model* model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (!model) {
        LOGE("Failed to load GGUF model: %s", model_path.c_str());
        return 0L;
    }

    const int32_t safe_ctx = std::clamp(static_cast<int32_t>(n_ctx), 256, 4096);
    const int32_t safe_threads = std::clamp(static_cast<int32_t>(n_threads), 1, 6);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(safe_ctx);
    ctx_params.n_batch = 256;
    ctx_params.n_ubatch = 256;
    ctx_params.n_threads = safe_threads;
    ctx_params.n_threads_batch = safe_threads;

    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create llama_context");
        llama_model_free(model);
        return 0L;
    }

    auto* session = new LlamaSession();
    session->model = model;
    session->ctx = ctx;
    LOGI("Model loaded successfully (ctx=%d, threads=%d)", safe_ctx, safe_threads);
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT jstring JNICALL
Java_com_jarvis_breeno_assistant_core_LlamaNativeBridge_nativeGenerate(
    JNIEnv* env,
    jclass /*clazz*/,
    jlong handle,
    jstring jprompt,
    jint max_tokens,
    jfloat temperature,
    jfloat top_p,
    jobject callback
) {
    if (handle == 0L) {
        return utf8_to_jstring(env, "");
    }
    auto* session = reinterpret_cast<LlamaSession*>(handle);
    std::lock_guard<std::mutex> lock(session->mutex);
    if (!session->model || !session->ctx) {
        return utf8_to_jstring(env, "");
    }

    session->cancel_requested.store(false);
    llama_memory_clear(llama_get_memory(session->ctx), true);

    const std::string prompt = jstring_to_utf8(env, jprompt);
    const llama_vocab* vocab = llama_model_get_vocab(session->model);
    if (prompt.empty() || !vocab) {
        return utf8_to_jstring(env, "");
    }

    int32_t required = -llama_tokenize(
        vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        nullptr,
        0,
        true,
        true
    );
    if (required <= 0) {
        required = static_cast<int32_t>(prompt.size()) + 16;
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(required));
    int32_t n_prompt = llama_tokenize(
        vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        prompt_tokens.data(),
        static_cast<int32_t>(prompt_tokens.size()),
        true,
        true
    );
    if (n_prompt <= 0) {
        LOGE("Tokenization failed (%d)", n_prompt);
        return utf8_to_jstring(env, "");
    }
    prompt_tokens.resize(static_cast<size_t>(n_prompt));

    const int32_t n_ctx = static_cast<int32_t>(llama_n_ctx(session->ctx));
    const int32_t max_prompt = std::max(64, n_ctx - 64);
    if (static_cast<int32_t>(prompt_tokens.size()) > max_prompt) {
        const size_t drop_count = prompt_tokens.size() - static_cast<size_t>(max_prompt);
        prompt_tokens.erase(prompt_tokens.begin(), prompt_tokens.begin() + static_cast<std::ptrdiff_t>(drop_count));
        LOGW("Prompt truncated to %d tokens for context budget", max_prompt);
    }

    const int32_t batch_cap = 256;
    for (size_t pos = 0; pos < prompt_tokens.size(); pos += static_cast<size_t>(batch_cap)) {
        if (session->cancel_requested.load()) {
            return utf8_to_jstring(env, "");
        }
        const int32_t chunk = static_cast<int32_t>(
            std::min(static_cast<size_t>(batch_cap), prompt_tokens.size() - pos)
        );
        llama_batch batch = llama_batch_get_one(prompt_tokens.data() + pos, chunk);
        if (llama_decode(session->ctx, batch) != 0) {
            LOGE("llama_decode prefill failed at pos=%zu", pos);
            return utf8_to_jstring(env, "");
        }
    }

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(
        smpl,
        llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1.12f, 0.0f, 0.0f)
    );
    if (temperature <= 0.05f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(std::clamp(top_p, 0.1f, 1.0f), 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    jmethodID onTokenMethod = nullptr;
    if (callback) {
        jclass cbClass = env->GetObjectClass(callback);
        if (cbClass) {
            onTokenMethod = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
            env->DeleteLocalRef(cbClass);
        }
    }

    const int32_t budget_tokens = std::clamp(
        static_cast<int32_t>(max_tokens),
        16,
        std::max(16, n_ctx - static_cast<int32_t>(prompt_tokens.size()) - 4)
    );

    std::string output;
    std::string pending_utf8;
    char piece_buf[256];

    for (int32_t step = 0; step < budget_tokens; ++step) {
        if (session->cancel_requested.load()) {
            break;
        }
        llama_token token = llama_sampler_sample(smpl, session->ctx, -1);
        llama_sampler_accept(smpl, token);

        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        const int32_t n_chars = llama_token_to_piece(vocab, token, piece_buf, sizeof(piece_buf), 0, false);
        if (n_chars > 0) {
            pending_utf8.append(piece_buf, static_cast<size_t>(n_chars));
            const size_t complete_len = utf8_complete_prefix_len(pending_utf8);
            if (complete_len > 0) {
                std::string chunk = pending_utf8.substr(0, complete_len);
                pending_utf8.erase(0, complete_len);
                output.append(chunk);

                if (contains_stop_marker(output)) {
                    strip_trailing_marker(output, "<|im_end|>");
                    strip_trailing_marker(output, "<|endoftext|>");
                    break;
                }

                if (callback && onTokenMethod && !chunk.empty()) {
                    jstring jpiece = utf8_to_jstring(env, chunk);
                    env->CallVoidMethod(callback, onTokenMethod, jpiece);
                    env->DeleteLocalRef(jpiece);
                    if (env->ExceptionCheck()) {
                        env->ExceptionClear();
                        break;
                    }
                }
            }
        }

        llama_batch next_batch = llama_batch_get_one(&token, 1);
        if (llama_decode(session->ctx, next_batch) != 0) {
            LOGE("llama_decode generation failed at step=%d", step);
            break;
        }
    }

    llama_sampler_free(smpl);
    strip_trailing_marker(output, "<|im_end|>");
    strip_trailing_marker(output, "<|endoftext|>");
    return utf8_to_jstring(env, output);
}

JNIEXPORT void JNICALL
Java_com_jarvis_breeno_assistant_core_LlamaNativeBridge_nativeCancel(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong handle
) {
    if (handle == 0L) return;
    auto* session = reinterpret_cast<LlamaSession*>(handle);
    session->cancel_requested.store(true);
}

JNIEXPORT void JNICALL
Java_com_jarvis_breeno_assistant_core_LlamaNativeBridge_nativeUnload(
    JNIEnv* /*env*/,
    jclass /*clazz*/,
    jlong handle
) {
    if (handle == 0L) return;
    auto* session = reinterpret_cast<LlamaSession*>(handle);
    session->cancel_requested.store(true);
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        if (session->ctx) {
            llama_free(session->ctx);
            session->ctx = nullptr;
        }
        if (session->model) {
            llama_model_free(session->model);
            session->model = nullptr;
        }
    }
    delete session;
    LOGI("Model session unloaded");
}

} // extern "C"
