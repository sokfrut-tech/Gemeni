#include <jni.h>
#include <GLES2/gl2.h>
#include <android/log.h>
#include <cmath>

static const char* TAG = "JarvisOrb";
static GLuint gProgram = 0;
static GLint gTime = -1;
static GLint gResolution = -1;
static GLint gListening = -1;
static float gT = 0.0f;
static float gWidth = 1.0f;
static float gHeight = 1.0f;

static GLuint compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint ok = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (ok != GL_TRUE) {
        GLint length = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
        if (length > 1) {
            char* log = new char[length];
            glGetShaderInfoLog(shader, length, nullptr, log);
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Orb shader compile failed: %s", log);
            delete[] log;
        }
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

static void ensureProgram() {
    if (gProgram != 0) return;
    const char* vertex = R"GLSL(
        attribute vec2 aPos;
        varying vec2 vUv;
        void main(){ vUv=aPos*0.5+0.5; gl_Position=vec4(aPos,0.0,1.0); }
    )GLSL";
    // Three tilted orbital planes, a luminous core, fine orbit traces and
    // small moving particles. Kept in one GLES2 fragment shader: no textures,
    // external assets or high-cost full-screen geometry are required.
    const char* fragment = R"GLSL(
        precision mediump float;
        varying vec2 vUv;
        uniform float uTime;
        uniform vec2 uResolution;
        uniform float uListening;

        float lineRing(vec2 p, float tilt, float spin, float radius, float flatten) {
            float a = tilt + spin;
            float c = cos(a), s = sin(a);
            vec2 q = vec2(c*p.x-s*p.y, s*p.x+c*p.y);
            float d = abs(length(vec2(q.x, q.y*flatten))-radius);
            return exp(-d*95.0);
        }
        float orbitDots(vec2 p, float tilt, float spin, float radius, float flatten) {
            float a = tilt + spin;
            float c = cos(a), s = sin(a);
            vec2 q = vec2(c*p.x-s*p.y, s*p.x+c*p.y);
            float angle = atan(q.y*flatten, q.x);
            float phase = angle*9.0-uTime*1.25;
            float radial = abs(length(vec2(q.x,q.y*flatten))-radius);
            float dots = pow(max(0.0,cos(phase)),24.0);
            return dots*exp(-radial*42.0);
        }
        void main(){
            vec2 p=(vUv-0.5)*vec2(uResolution.x/uResolution.y,1.0);
            float r=length(p);
            float t=uTime;
            float breathe=0.5+0.5*sin(t*1.35);
            float pulse=0.018*sin(t*2.1)+uListening*0.018;
            float core=exp(-r*r*115.0);
            float inner=exp(-pow(r-0.105-pulse,2.0)*360.0);
            float halo=exp(-pow(r-0.23-pulse,2.0)*32.0);
            float ringA=lineRing(p,0.20,t*0.20,0.34,0.58);
            float ringB=lineRing(p,1.12,-t*0.16,0.39,0.42);
            float ringC=lineRing(p,2.18,t*0.12,0.29,0.72);
            float dots=orbitDots(p,0.20,t*0.20,0.34,0.58)
                     +orbitDots(p,1.12,-t*0.16,0.39,0.42)
                     +orbitDots(p,2.18,t*0.12,0.29,0.72);
            float rays=0.0;
            float angle=atan(p.y,p.x);
            for(int i=0;i<48;i++){
                float a=float(i)*6.2831853/48.0+t*0.035;
                float da=abs(atan(sin(angle-a),cos(angle-a)));
                float spoke=exp(-da*150.0);
                float band=step(0.23,r)*step(r,0.48);
                rays=max(rays,spoke*band*(0.25+0.75*fract(float(i)*0.618)));
            }
            vec3 amber=vec3(1.0,0.43,0.12);
            vec3 cyan=vec3(0.10,0.78,1.0);
            vec3 ice=vec3(0.55,0.93,1.0);
            vec3 gold=amber*(ringA*0.72+ringC*0.36+dots*0.72);
            vec3 blue=cyan*(ringB*0.78+rays*0.16+halo*0.22);
            vec3 col=gold+blue+ice*(core*1.6+inner*0.7);
            col*=0.82+0.18*breathe+uListening*0.20;
            float alpha=clamp(core+inner*0.62+halo*0.24+(ringA+ringB+ringC)*0.62+dots*0.8+rays*0.12,0.0,1.0);
            float vignette=1.0-smoothstep(0.43,0.62,r);
            gl_FragColor=vec4(col*vignette,alpha*vignette);
        }
    )GLSL";
    GLuint v=compileShader(GL_VERTEX_SHADER,vertex);
    GLuint f=compileShader(GL_FRAGMENT_SHADER,fragment);
    if (v == 0 || f == 0) {
        if (v) glDeleteShader(v);
        if (f) glDeleteShader(f);
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Orb shader unavailable; renderer will use fallback clear");
        return;
    }
    GLuint program=glCreateProgram();
    glAttachShader(program,v); glAttachShader(program,f);
    glBindAttribLocation(program,0,"aPos");
    glLinkProgram(program);
    GLint linked=GL_FALSE;
    glGetProgramiv(program,GL_LINK_STATUS,&linked);
    if (linked != GL_TRUE) {
        GLint length=0; glGetProgramiv(program,GL_INFO_LOG_LENGTH,&length);
        if (length>1) {
            char* log=new char[length];
            glGetProgramInfoLog(program,length,nullptr,log);
            __android_log_print(ANDROID_LOG_ERROR,TAG,"Orb program link failed: %s",log);
            delete[] log;
        }
        glDeleteProgram(program);
        program=0;
    }
    glDeleteShader(v); glDeleteShader(f);
    gProgram=program;
    if (gProgram != 0) {
        gTime=glGetUniformLocation(gProgram,"uTime");
        gResolution=glGetUniformLocation(gProgram,"uResolution");
        gListening=glGetUniformLocation(gProgram,"uListening");
        __android_log_print(ANDROID_LOG_INFO,TAG,"Orb renderer ready (GLES2 orbital visual)");
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_jarvis_breeno_assistant_ui_render_AlphaVideoSurfaceView_00024JarvisSphereRenderer_nativeCreate(JNIEnv*, jobject) {
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
    glClearColor(0.f,0.f,0.f,0.f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (gProgram == 0) return;
    static const GLfloat quad[] = {-1.f,-1.f, 1.f,-1.f, -1.f,1.f, 1.f,1.f};
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
    glUseProgram(gProgram);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,0,quad);
    gT += 0.016f;
    glUniform1f(gTime,gT);
    glUniform2f(gResolution,gWidth,gHeight);
    glUniform1f(gListening,listening ? 1.0f : 0.0f);
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray(0);
}
