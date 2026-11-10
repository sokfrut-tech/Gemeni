#include <jni.h>
#include <GLES2/gl2.h>
#include <android/log.h>

#define LOG_TAG "JarvisGL"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static GLuint gProgram = 0;
static GLint gTime = -1;
static GLint gResolution = -1;
static GLint gListening = -1;
static float gT = 0.0f;
static float gWidth = 1.0f;
static float gHeight = 1.0f;
static bool gLoggedProgramFailure = false;

static GLuint compileShader(GLenum type, const char* source) {
    const GLuint shader = glCreateShader(type);
    if (shader == 0) {
        LOGE("glCreateShader(%u) returned 0 (GL error 0x%x)", type, glGetError());
        return 0;
    }
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint compiled = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (compiled != GL_TRUE) {
        GLint length = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
        if (length > 1) {
            char* log = new char[static_cast<size_t>(length)];
            glGetShaderInfoLog(shader, length, nullptr, log);
            LOGE("Shader compilation failed (%u): %s", type, log);
            delete[] log;
        } else {
            LOGE("Shader compilation failed (%u), no compiler log", type);
        }
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

static GLuint linkProgram(const char* vertexSource, const char* fragmentSource) {
    const GLuint vertex = compileShader(GL_VERTEX_SHADER, vertexSource);
    if (vertex == 0) return 0;
    const GLuint fragment = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
    if (fragment == 0) {
        glDeleteShader(vertex);
        return 0;
    }

    const GLuint program = glCreateProgram();
    if (program == 0) {
        LOGE("glCreateProgram returned 0 (GL error 0x%x)", glGetError());
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        return 0;
    }
    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    glBindAttribLocation(program, 0, "aPos");
    glLinkProgram(program);

    GLint linked = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (linked != GL_TRUE) {
        GLint length = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &length);
        if (length > 1) {
            char* log = new char[static_cast<size_t>(length)];
            glGetProgramInfoLog(program, length, nullptr, log);
            LOGE("Program link failed: %s", log);
            delete[] log;
        } else {
            LOGE("Program link failed, no linker log");
        }
        glDeleteProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        return 0;
    }

    glDetachShader(program, vertex);
    glDetachShader(program, fragment);
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    return program;
}

static void ensureProgram() {
    if (gProgram != 0 && glIsProgram(gProgram) == GL_TRUE) return;

    const char* vertex = R"GLSL(
        attribute vec2 aPos;
        varying vec2 vUv;
        void main(){ vUv=aPos*0.5+0.5; gl_Position=vec4(aPos,0.0,1.0); }
    )GLSL";
    const char* fragment = R"GLSL(
        precision mediump float;
        varying vec2 vUv;
        uniform float uTime;
        uniform vec2 uResolution;
        uniform float uListening;
        void main(){
            vec2 p=(vUv-0.5)*vec2(uResolution.x/max(uResolution.y,1.0),1.0);
            float r=length(p);
            float pulse=0.035*sin(uTime*2.7)+0.018*sin(uTime*5.1);
            float core=exp(-pow(max(r-0.22-pulse,0.0)*10.0,2.0));
            float halo=exp(-pow(max(r-0.34-pulse,0.0)*5.5,2.0));
            float ring=exp(-pow((r-0.38-0.012*sin(uTime*1.8+atan(p.y,p.x)*5.0))*55.0,2.0));
            float sweep=0.5+0.5*sin(uTime*1.3+atan(p.y,p.x)*3.0);
            vec3 cyan=vec3(0.28,0.86,1.0);
            vec3 mint=vec3(0.55,1.0,0.86);
            vec3 col=cyan*(core*0.95+halo*0.42)+mint*(ring*(0.45+0.45*sweep));
            col*=1.0+uListening*0.35;
            float edge=1.0-smoothstep(0.34,0.52,r);
            gl_FragColor=vec4(col*edge, edge*(0.78+0.22*core));
        }
    )GLSL";

    // A deliberately simple fallback guarantees visible pixels if a driver
    // rejects an optional effect in the animated shader.
    const char* fallback = R"GLSL(
        precision mediump float;
        varying vec2 vUv;
        uniform vec2 uResolution;
        uniform float uListening;
        void main(){
            vec2 p=(vUv-0.5)*vec2(uResolution.x/max(uResolution.y,1.0),1.0);
            float r=length(p);
            float glow=1.0-smoothstep(0.12,0.48,r);
            float ring=1.0-smoothstep(0.018,0.045,abs(r-0.31));
            vec3 color=vec3(0.10,0.72,1.0)*glow+vec3(0.35,1.0,0.82)*ring;
            float alpha=clamp(glow*0.8+ring*0.9,0.0,1.0);
            gl_FragColor=vec4(color*(1.0+uListening*0.3),alpha);
        }
    )GLSL";

    gProgram = linkProgram(vertex, fragment);
    if (gProgram == 0) {
        LOGE("Animated sphere shader unavailable; using fallback shader");
        gProgram = linkProgram(vertex, fallback);
    }
    if (gProgram != 0) {
        gTime = glGetUniformLocation(gProgram, "uTime");
        gResolution = glGetUniformLocation(gProgram, "uResolution");
        gListening = glGetUniformLocation(gProgram, "uListening");
        gLoggedProgramFailure = false;
        LOGI("Jarvis sphere shader linked: program=%u", gProgram);
    } else if (!gLoggedProgramFailure) {
        LOGE("No valid Jarvis GLES program; frame will be cleared only (GL error 0x%x)", glGetError());
        gLoggedProgramFailure = true;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_jarvis_breeno_assistant_ui_render_AlphaVideoSurfaceView_00024JarvisSphereRenderer_nativeCreate(JNIEnv*, jobject) {
    // GLSurfaceView may recreate its EGL context after pause/resume. Program
    // names from the previous context are invalid in the new one.
    gProgram = 0;
    gTime = gResolution = gListening = -1;
    gT = 0.0f;
    gLoggedProgramFailure = false;
    ensureProgram();
    return gProgram == 0 ? 0 : 1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvis_breeno_assistant_ui_render_AlphaVideoSurfaceView_00024JarvisSphereRenderer_nativeResize(JNIEnv*, jobject, jint width, jint height) {
    gWidth = width > 0 ? static_cast<float>(width) : 1.0f;
    gHeight = height > 0 ? static_cast<float>(height) : 1.0f;
    glViewport(0, 0, width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvis_breeno_assistant_ui_render_AlphaVideoSurfaceView_00024JarvisSphereRenderer_nativeDraw(JNIEnv*, jobject, jint listening, jint matrix) {
    (void)matrix;
    ensureProgram();
    glClearColor(0.f, 0.f, 0.f, 0.f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (gProgram == 0 || glIsProgram(gProgram) != GL_TRUE) return;

    static const GLfloat quad[] = {-1.f,-1.f, 1.f,-1.f, -1.f,1.f, 1.f,1.f};
    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glUseProgram(gProgram);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, quad);
    gT += 0.016f;
    if (gTime >= 0) glUniform1f(gTime, gT);
    if (gResolution >= 0) glUniform2f(gResolution, gWidth, gHeight);
    if (gListening >= 0) glUniform1f(gListening, listening ? 1.0f : 0.0f);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray(0);
}
