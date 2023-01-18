#include "xgen_data.h"
#include "xgen_pb.h"
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

XGenEngine engine(d66b7555_cefa_4f_pb, d66b7555_cefa_4f_pb_len, d66b7555_cefa_4f_data, d66b7555_cefa_4f_data_len);

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_cocopie_mobile_xgen_example_CoCoPIEJNIExporter_Run(JNIEnv *env, jclass instance, jfloatArray input) {
    jfloat *input_data = env->GetFloatArrayElements(input, JNI_FALSE);
    return engine.RunInference(env, input_data);
}