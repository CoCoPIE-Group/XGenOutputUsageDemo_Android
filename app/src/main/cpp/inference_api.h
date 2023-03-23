#ifndef INFERENCE_API_H_
#define INFERENCE_API_H_

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL
Java_com_cocopie_mobile_xgen_example_CoCoPIEJNIExporter_CreateOpt(JNIEnv *env,
                                                               jobject instance,
                                                               jstring pbPath,
                                                               jstring dataPath);

JNIEXPORT jlong JNICALL
Java_com_cocopie_mobile_xgen_example_CoCoPIEJNIExporter_CreateFallback(JNIEnv *env,
                                                               jobject instance,
                                                               jstring fallbackPath);

JNIEXPORT jfloatArray JNICALL
Java_com_cocopie_mobile_xgen_example_CoCoPIEJNIExporter_Run(JNIEnv *env,
                                                            jobject instance,
                                                            jlong engine,
                                                            jfloatArray input);

#ifdef __cplusplus
}
#endif

#endif
