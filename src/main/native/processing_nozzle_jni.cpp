#include <jni.h>
#include <string>
#include "nozzle/nozzle_c.h"

extern "C" JNIEXPORT jstring JNICALL
Java_processing_nozzle_ProcessingNozzleNative_nativeVersion(JNIEnv *env, jclass) {
    std::string text = "processing-nozzle-jni; nozzle_c_error_ok=" + std::to_string(static_cast<int>(NOZZLE_OK))
        + "; rgba8=" + std::to_string(static_cast<int>(NOZZLE_FORMAT_RGBA8_UNORM));
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_processing_nozzle_ProcessingNozzleNative_nativeSelfTest(JNIEnv *, jclass) {
    if (NOZZLE_OK != 0) {
        return 1;
    }
    if (NOZZLE_FORMAT_RGBA8_UNORM != 4) {
        return 2;
    }
    return 0;
}
