#include <jni.h>
#include <android/log.h>
#include <llama.h>

#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>

jstring toJavaString(JNIEnv* env, const std::string& value) {
    std::vector<jchar> utf16;
    utf16.reserve(value.size());
    for (size_t i = 0; i < value.size();) {
        const unsigned char first = static_cast<unsigned char>(value[i]);
        uint32_t cp = 0xfffd;
        size_t count = 1;
        if (first < 0x80) { cp = first; }
        else if (first >= 0xc2 && first <= 0xdf && i + 1 < value.size()) {
            const unsigned char b1 = static_cast<unsigned char>(value[i + 1]);
            if ((b1 & 0xc0) == 0x80) { cp = ((first & 0x1f) << 6) | (b1 & 0x3f); count = 2; }
        } else if (first >= 0xe0 && first <= 0xef && i + 2 < value.size()) {
            const unsigned char b1 = static_cast<unsigned char>(value[i + 1]);
            const unsigned char b2 = static_cast<unsigned char>(value[i + 2]);
            if ((b1 & 0xc0) == 0x80 && (b2 & 0xc0) == 0x80) {
                const uint32_t candidate = ((first & 0x0f) << 12) | ((b1 & 0x3f) << 6) | (b2 & 0x3f);
                if (candidate >= 0x800 && !(candidate >= 0xd800 && candidate <= 0xdfff)) { cp = candidate; count = 3; }
            }
        } else if (first >= 0xf0 && first <= 0xf4 && i + 3 < value.size()) {
            const unsigned char b1 = static_cast<unsigned char>(value[i + 1]);
            const unsigned char b2 = static_cast<unsigned char>(value[i + 2]);
            const unsigned char b3 = static_cast<unsigned char>(value[i + 3]);
            if ((b1 & 0xc0) == 0x80 && (b2 & 0xc0) == 0x80 && (b3 & 0xc0) == 0x80) {
                const uint32_t candidate = ((first & 0x07) << 18) | ((b1 & 0x3f) << 12) | ((b2 & 0x3f) << 6) | (b3 & 0x3f);
                if (candidate >= 0x10000 && candidate <= 0x10ffff) { cp = candidate; count = 4; }
            }
        }
        i += count;
        if (cp <= 0xffff) utf16.push_back(static_cast<jchar>(cp));
        else {
            cp -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xd800 + (cp >> 10)));
            utf16.push_back(static_cast<jchar>(0xdc00 + (cp & 0x3ff)));
        }
    }
    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

namespace {
constexpr const char* TAG = "JarvisLlama";
std::mutex g_mutex;
std::atomic<bool> g_cancelled{false};
std::once_flag g_backend_once;
llama_model* g_model = nullptr;
llama_context* g_context = nullptr;

void releaseLocked() {
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

size_t completeUtf8Prefix(const std::string& text) {
    size_t i = 0;
    while (i < text.size()) {
        const unsigned char first = static_cast<unsigned char>(text[i]);
        size_t count = first < 0x80 ? 1 : (first >= 0xc2 && first <= 0xdf ? 2
                : (first >= 0xe0 && first <= 0xef ? 3
                : (first >= 0xf0 && first <= 0xf4 ? 4 : 1)));
        if (i + count > text.size()) break;
        bool valid = true;
        for (size_t j = 1; j < count; ++j) {
            if ((static_cast<unsigned char>(text[i + j]) & 0xc0) != 0x80) valid = false;
        }
        if (!valid) count = 1;
        i += count;
    }
    return i;
}

bool appendToken(JNIEnv* env, jobject listener, jmethodID onToken, const char* bytes,
                 int length, std::string& output, size_t& emitted) {
    if (length <= 0) return true;
    output.append(bytes, static_cast<size_t>(length));
    const size_t complete = completeUtf8Prefix(output);
    if (listener == nullptr || onToken == nullptr || complete <= emitted) return true;
    jstring piece = toJavaString(env, output.substr(emitted, complete - emitted));
    emitted = complete;
    if (piece == nullptr) return false;
    const jboolean keepGoing = env->CallBooleanMethod(listener, onToken, piece);
    env->DeleteLocalRef(piece);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }
    return keepGoing == JNI_TRUE;
}

std::string formatPrompt(const llama_model* model, const std::string& userText) {
    const char* chatTemplate = llama_model_chat_template(model, nullptr);
    if (chatTemplate == nullptr || chatTemplate[0] == '\0') return userText;

    const llama_chat_message messages[] = {{"user", userText.c_str()}};
    std::vector<char> buffer(16384);
    int32_t length = llama_chat_apply_template(chatTemplate, messages, 1, true,
                                                buffer.data(), static_cast<int32_t>(buffer.size()));
    if (length <= 0 || length > 65536) return userText;
    if (static_cast<size_t>(length) >= buffer.size()) {
        buffer.resize(static_cast<size_t>(length) + 1);
        length = llama_chat_apply_template(chatTemplate, messages, 1, true,
                                            buffer.data(), static_cast<int32_t>(buffer.size()));
        if (length <= 0 || static_cast<size_t>(length) >= buffer.size()) return userText;
    }
    return std::string(buffer.data(), static_cast<size_t>(length));
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_jarvis_breeno_assistant_core_LlamaCppRuntime_nativeLoadModel(
        JNIEnv* env, jobject, jstring modelPath, jint contextTokens, jint threadCount) {
    if (modelPath == nullptr) return 0;
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) return 0;
    const std::string pathValue(path);
    env->ReleaseStringUTFChars(modelPath, path);
    if (pathValue.empty()) return 0;

    g_cancelled.store(true);
    std::lock_guard<std::mutex> lock(g_mutex);
    releaseLocked();
    std::call_once(g_backend_once, [] { llama_backend_init(); });

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0; // portable CPU baseline; GPU offload is a later measured profile
    g_model = llama_model_load_from_file(pathValue.c_str(), modelParams);
    if (g_model == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Model load failed");
        return 0;
    }

    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = static_cast<uint32_t>(std::clamp(contextTokens, 512, 8192));
    contextParams.n_batch = 512;
    contextParams.n_ubatch = 128;
    contextParams.n_threads = std::clamp(threadCount, 1, 8);
    contextParams.n_threads_batch = std::clamp(threadCount, 1, 8);
    contextParams.offload_kqv = false;
    contextParams.op_offload = false;
    contextParams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
    g_context = llama_init_from_model(g_model, contextParams);
    if (g_context == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Context creation failed");
        releaseLocked();
        return 0;
    }
    g_cancelled.store(false);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Pinned llama.cpp model loaded");
    return 1;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jarvis_breeno_assistant_core_LlamaCppRuntime_nativeGenerate(
        JNIEnv* env, jobject, jstring prompt, jint maxTokens, jobject listener) {
    if (prompt == nullptr) return env->NewStringUTF("");
    const char* promptChars = env->GetStringUTFChars(prompt, nullptr);
    if (promptChars == nullptr) return env->NewStringUTF("");
    const std::string userText(promptChars);
    env->ReleaseStringUTFChars(prompt, promptChars);
    if (userText.empty() || userText.size() > 12000) return env->NewStringUTF("");

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr || g_context == nullptr) return env->NewStringUTF("");
    g_cancelled.store(false);

    const std::string formatted = formatPrompt(g_model, userText);
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    if (vocab == nullptr) return env->NewStringUTF("");
    std::vector<llama_token> tokens(formatted.size() + 16);
    int32_t tokenCount = llama_tokenize(vocab, formatted.data(),
                                        static_cast<int32_t>(formatted.size()),
                                        tokens.data(), static_cast<int32_t>(tokens.size()),
                                        true, true);
    if (tokenCount < 0) {
        tokens.resize(static_cast<size_t>(-tokenCount));
        tokenCount = llama_tokenize(vocab, formatted.data(),
                                    static_cast<int32_t>(formatted.size()),
                                    tokens.data(), static_cast<int32_t>(tokens.size()),
                                    true, false);
    }
    if (tokenCount <= 0 || static_cast<uint32_t>(tokenCount) >= llama_n_ctx(g_context)) {
        return env->NewStringUTF("");
    }
    tokens.resize(static_cast<size_t>(tokenCount));
    if (static_cast<uint32_t>(tokenCount) + static_cast<uint32_t>(std::clamp(maxTokens, 1, 512))
            > llama_n_ctx(g_context)) {
        return env->NewStringUTF("");
    }

    llama_memory_clear(llama_get_memory(g_context), true);
    for (size_t offset = 0; offset < tokens.size();) {
        if (g_cancelled.load()) return env->NewStringUTF("");
        const int32_t count = static_cast<int32_t>(
                std::min<size_t>(512, tokens.size() - offset));
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, count);
        if (llama_decode(g_context, batch) != 0) return env->NewStringUTF("");
        offset += static_cast<size_t>(count);
    }

    llama_sampler* sampler = llama_sampler_init_greedy();
    if (sampler == nullptr) return env->NewStringUTF("");
    jclass listenerClass = listener == nullptr ? nullptr : env->GetObjectClass(listener);
    jmethodID onToken = listenerClass == nullptr ? nullptr
            : env->GetMethodID(listenerClass, "onToken", "(Ljava/lang/String;)Z");
    std::string output;
    output.reserve(2048);
    size_t emitted = 0;
    const int limit = std::clamp(maxTokens, 1, 512);
    for (int i = 0; i < limit && !g_cancelled.load(); ++i) {
        llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        char piece[512];
        const int pieceLength = llama_token_to_piece(vocab, token, piece,
                                                      static_cast<int32_t>(sizeof(piece)), 0, true);
        if (pieceLength > 0) {
            if (!appendToken(env, listener, onToken, piece, pieceLength, output, emitted)) {
                g_cancelled.store(true);
                break;
            }
        }
        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(g_context, next) != 0) break;
    }
    llama_sampler_free(sampler);
    if (listenerClass != nullptr) env->DeleteLocalRef(listenerClass);
    return toJavaString(env, output);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvis_breeno_assistant_core_LlamaCppRuntime_nativeCancel(JNIEnv*, jobject) {
    g_cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvis_breeno_assistant_core_LlamaCppRuntime_nativeUnload(JNIEnv*, jobject) {
    g_cancelled.store(true);
    std::lock_guard<std::mutex> lock(g_mutex);
    releaseLocked();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jarvis_breeno_assistant_core_LlamaCppRuntime_nativeVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF(llama_version());
}
