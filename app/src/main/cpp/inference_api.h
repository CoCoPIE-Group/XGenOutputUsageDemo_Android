#ifndef INFERENCE_API_H_
#define INFERENCE_API_H_

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jfloatArray JNICALL
Java_com_cocopie_mobile_xgen_example_CoCoPIEJNIExporter_Run(JNIEnv *env,
                                                                jclass instance,
                                                                jfloatArray input);

#ifdef __cplusplus
}
#endif

#endif
