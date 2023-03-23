#include "xgen.h"
#include "src/main/cpp/inference_api.h"

#include <algorithm>
#include <android/log.h>
#include <jni.h>
#include <map>
#include <functional>
#include <memory>
#include <numeric>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

#define LOG_TAG "inference_api"
#define LOGI(...) \
  ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))

class XGenEngine {
public:
    XGenEngine(const unsigned char *model_base, size_t model_size) {
        h = XGenInit(model_base, model_size);
    }

    XGenEngine(const unsigned char *model_base, size_t model_size, const unsigned char *extra_data, size_t data_size) {
        h = XGenInitWithData(model_base, model_size, extra_data, data_size);
    }

    XGenEngine(const char *pb_file, const char *data_file) {
        LOGI("pb_file:%s data_file:%s", pb_file, data_file);
        h = XGenInitWithFiles(pb_file, data_file, XGenPowerDefault);
    }

    XGenEngine(const char *fallback_file) {
        LOGI("fallback_file:%s", fallback_file);
        h = XGenInitWithFallbackFiles(fallback_file);
    }

    ~XGenEngine() {
        XGenShutdown(h);
        h = nullptr;
    }

    template<typename T>
    jfloatArray RunInference(JNIEnv *env, const T *input_data) {
        XGenTensor *input_tensor = XGenGetInputTensor(h, 0);
        size_t input_size_in_bytes = XGenGetTensorSizeInBytes(input_tensor);
        size_t input_size = input_size_in_bytes / sizeof(float);
        LOGI("input_size:%zu", input_size);
        auto buffer_nchw = std::shared_ptr<float>(new float[input_size], std::default_delete<float[]>());
        int input_channel_size = input_size / 3;
        for (int i = 0; i < input_channel_size; ++i) {
            for (int j = 0; j < 3; ++j) {
                int src_idx = i * 3 + j;
                int dst_idx = i + input_channel_size * j;
                buffer_nchw.get()[dst_idx] = input_data[src_idx];
            }
        }
        XGenCopyBufferToTensor(input_tensor, buffer_nchw.get(), input_size_in_bytes);

        if (XGenRun(h) != XGenOk) {
            LOGI("FATAL ERROR: XGen inference failed.");
            return nullptr;
        }

        XGenTensor *output_tensor = XGenGetOutputTensor(h, 0);
        size_t output_size_in_bytes = XGenGetTensorSizeInBytes(output_tensor);
        size_t output_size = output_size_in_bytes / sizeof(float);
        LOGI("output_size:%zu", output_size);
        auto output_data = std::shared_ptr<float>(new float[output_size], std::default_delete<float[]>());
        XGenCopyTensorToBuffer(output_tensor, output_data.get(), output_size_in_bytes);

        jfloatArray jOutputData = env->NewFloatArray(output_size);
        env->SetFloatArrayRegion(jOutputData, 0, (jsize) output_size, output_data.get());
        return jOutputData;
    }

private:
    XGenHandle *h;
};

static jlong jptr(XGenEngine *engine) {
    return (jlong) engine;
}

static struct XGenEngine *native(jlong ptr) {
    return (XGenEngine *) ptr;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_cocopie_mobile_xgen_example_CoCoPIEJNIExporter_CreateOpt(JNIEnv *env, jobject instance, jstring pbPath, jstring dataPath) {
    auto *engine = new XGenEngine(env->GetStringUTFChars(pbPath, nullptr), env->GetStringUTFChars(dataPath, nullptr));
    return jptr(engine);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_cocopie_mobile_xgen_example_CoCoPIEJNIExporter_CreateFallback(JNIEnv *env, jobject instance, jstring fallbackPath) {
    auto *engine = new XGenEngine(env->GetStringUTFChars(fallbackPath, nullptr));
    return jptr(engine);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_cocopie_mobile_xgen_example_CoCoPIEJNIExporter_Run(JNIEnv *env, jobject instance, jlong engine, jfloatArray input) {
    XGenEngine *xgen = native(engine);
    jfloat *input_data = env->GetFloatArrayElements(input, JNI_FALSE);
    return xgen->RunInference(env, input_data);
}