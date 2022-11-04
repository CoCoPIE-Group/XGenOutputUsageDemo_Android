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
        size_t input_size = XGenGetTensorSizeInBytes(input_tensor);
        LOGI("input_size:%zu", input_size);
        XGenCopyBufferToTensor(input_tensor, input_data, input_size);

        XGenRun(h);

        XGenTensor *output_tensor = XGenGetOutputTensor(h, 0);
        size_t output_size = XGenGetTensorSizeInBytes(output_tensor);
        LOGI("output_size:%zu", output_size);
        auto output_data = std::shared_ptr<float>(new float[output_size / sizeof(float)], std::default_delete<float[]>());
        XGenCopyTensorToBuffer(output_tensor, output_data.get(), output_size);

        jfloatArray jOutputData = env->NewFloatArray(output_size / sizeof(float));
        env->SetFloatArrayRegion(jOutputData, 0, (jsize) output_size / sizeof(float), output_data.get());
        return jOutputData;
    }

private:
    XGenHandle *h;
};

XGenEngine engine(d66b7555_cefa_4f_pb, d66b7555_cefa_4f_pb_len, d66b7555_cefa_4f_data, d66b7555_cefa_4f_data_len);

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_cocopie_mobile_xgen_example_CoCoPIEJNIExporter_Run(JNIEnv *env,
                                                                jclass instance,
                                                                jfloatArray input) {
    jfloat *input_data = env->GetFloatArrayElements(input, JNI_FALSE);
    return engine.RunInference(env, input_data);
}