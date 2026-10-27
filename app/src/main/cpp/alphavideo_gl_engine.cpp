#include <jni.h>
#include <GLES2/gl2.h>
#include <cmath>

static GLuint gProgram = 0;
static GLint gTime = -1;
static GLint gResolution = -1;
static GLint gListening = -1;
static float gT = 0.0f;

static GLuint compile(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    return s;
}

static void ensureProgram() {
    if (gProgram) return;
    const char* vs = R"GLSL(
        attribute vec2 aPos;
        varying vec2 vUv;
        void main(){ vUv=aPos*0.5+0.5; gl_Position=vec4(aPos,0.0,1.0); }
    )GLSL";
    const char* fs = R"GLSL(
        precision mediump float;
        varying vec2 vUv;
        uniform float uTime;
        uniform vec2 uResolution;
        uniform float uListening;
        void main(){
            vec2 p=(vUv-0.5)*vec2(uResolution.x/uResolution.y,1.0);
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
            float edge=smoothstep(0.52,0.34,r);
            gl_FragColor=vec4(col*edge, edge*(0.78+0.22*core));
        }
    )GLSL";
    GLuint v=compile(GL_VERTEX_SHADER,vs), f=compile(GL_FRAGMENT_SHADER,fs);
    gProgram=glCreateProgram(); glAttachShader(gProgram,v); glAttachShader(gProgram,f);
    glBindAttribLocation(gProgram,0,"aPos"); glLinkProgram(gProgram);
    glDeleteShader(v); glDeleteShader(f);
    gTime=glGetUniformLocation(gProgram,"uTime");
    gResolution=glGetUniformLocation(gProgram,"uResolution");
    gListening=glGetUniformLocation(gProgram,"uListening");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_jarvis_breeno_assistant_ui_render_AlphaVideoSurfaceView_nativeCreate(JNIEnv*, jobject) {
    ensureProgram();
    return 1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvis_breeno_assistant_ui_render_AlphaVideoSurfaceView_nativeDraw(JNIEnv*, jobject, jint texture, jint matrix) {
    (void)texture; (void)matrix;
    ensureProgram();
    static const GLfloat quad[] = {-1.f,-1.f, 1.f,-1.f, -1.f,1.f, 1.f,1.f};
    glClearColor(0.f,0.f,0.f,0.f);
    glClear(GL_COLOR_BUFFER_BIT);
    glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
    glUseProgram(gProgram);
    glEnableVertexAttribArray(0); glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,0,quad);
    gT += 0.016f;
    GLint vp[4]; glGetIntegerv(GL_VIEWPORT,vp);
    glUniform1f(gTime,gT); glUniform2f(gResolution,(float)vp[2],(float)vp[3]);
    glUniform1f(gListening,0.0f);
    glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glDisableVertexAttribArray(0);
}
