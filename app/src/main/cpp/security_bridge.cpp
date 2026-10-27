#include <jni.h>
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_jarvis_breeno_assistant_security_NativeSecurity_sha256(JNIEnv* env,jobject,jbyteArray data){
 jclass md=env->FindClass("java/security/MessageDigest"); jmethodID get=env->GetStaticMethodID(md,"getInstance","(Ljava/lang/String;)Ljava/security/MessageDigest;"); jstring n=env->NewStringUTF("SHA-256"); jobject d=env->CallStaticObjectMethod(md,get,n); jmethodID dig=env->GetMethodID(md,"digest","([B)[B"); jobject out=env->CallObjectMethod(d,dig,data); env->DeleteLocalRef(n); return (jbyteArray)out;
}
