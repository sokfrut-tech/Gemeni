#include <jni.h>
#include <GLES2/gl2.h>
#include <EGL/egl.h>
extern "C" JNIEXPORT jlong JNICALL Java_com_jarvis_breeno_assistant_ui_render_AlphaVideoSurfaceView_nativeCreate(JNIEnv*,jobject){return 0;}
extern "C" JNIEXPORT void JNICALL Java_com_jarvis_breeno_assistant_ui_render_AlphaVideoSurfaceView_nativeDraw(JNIEnv*,jobject,jint texture,jint matrix){glClear(GL_COLOR_BUFFER_BIT);}
