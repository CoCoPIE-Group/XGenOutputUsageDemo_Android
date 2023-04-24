## 1 Introduction

This is an Android demo app to show how to integrate the output of XGen into an android app.

## 2 Integration of XGen output into an app

Readers can see `app/src/main/cpp/inference_api.cc` to see how the output of XGen is used in an app for AI. This part gives the explanation.

#### 2.1 Import XGen SDK

Put '**xgen.h**', '**{model name}.tflite**', '**libxgen.so**' into the corresponding directory of the project, and then modify **CMakeLists.txt** script.

#### 2.2 Initialize XGen

Call the _XGenInitWithFallbackFiles_ method to initialize XGen.

#### 2.3 Input data preprocessing

The input data is preprocessed according to the model parameters. Here is an example of preprocessing the input image when using the mobilenet model.

```java
  int imageWidth = 224;
        int imageHeight = 224;
        int imageChannel = 3;
        float[] modelMean = new float[]{0.485f, 0.456f, 0.406f};
        float[] modelStd = new float[]{0.229f, 0.224f, 0.225f};
        int[] intValues = new int[imageHeight * imageWidth];
        float[] floatValues = new float[imageHeight * imageWidth * imageChannel];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int idx = 0;
        for (int value : intValues) {
        floatValues[idx++] = (((value >> 16) & 0xFF) / 255.f - modelMean[0]) / modelStd[0];
        floatValues[idx++] = (((value >> 8) & 0xFF) / 255.f - modelMean[1]) / modelStd[1];
        floatValues[idx++] = ((value & 0xFF) / 255.f - modelMean[2]) / modelStd[2];
        }
```

#### 2.4 Run XGen

Call the _XGenCopyBufferToTensor_ method to pass the preprocessed data into XGen runtime, and then call the _XGenRun_ method to let XGen runtime call the AI model to conduct an inference.

```c
  XGenTensor *input_tensor = XGenGetInputTensor(h, 0);
  size_t input_size_in_bytes = XGenGetTensorSizeInBytes(input_tensor);
  size_t input_size = input_size_in_bytes / sizeof(float);

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

  XGenRun(h);
```

#### 2.5 Use the AI model through XGen runtime

Call the _XGenCopyTensorToBuffer_ method to copy the result into a butter, which is a float array of length 1000 in the mobilenet example

```c
  XGenTensor *output_tensor = XGenGetOutputTensor(h, 0);
  size_t output_size_in_bytes = XGenGetTensorSizeInBytes(output_tensor);
  size_t output_size = output_size_in_bytes / sizeof(float);

  auto output_data = std::shared_ptr<float>(new float[output_size], std::default_delete<float[]>());
  XGenCopyTensorToBuffer(output_tensor, output_data.get(), output_size_in_bytes);
```

#### 2.6 Performance comparison

Faster and smaller model files with almost constant accuracy!

| Model                              | Top-1 Accuracy | # MACs | Terminal Latency (ms) | Demo FPS | Size (MB) |
| ---------------------------------- | -------------- | ------ | --------------------- | -------- | --------- |
| Original_TFLite(Pytorch, 1000 cls) | 71.8           | 300M   | 10.5                  | 96       | 13.3      |
| Original_TFLite(MTK, 1000 cls)     | 71.8           | 300M   | 7.3                   | -        | -         |
| Compressed_TFLite                  | 71.6           | 180M   | 8.4                   | 120      | 19.1      |
| XGen_Auto                          | 64.158         | 210M   | 5.674                 | 110      | 7.5       |
| XGen_Manual                        | 71.6           | 180M   | 6.594                 | 90       | 9.5       |

## 3 Copyright

© 2022 CoCoPIE Inc. All Rights Reserved.

CoCoPIE Inc., its logo and certain names, product and service names reference herein may be registered trademarks, trademarks, trade names or service marks of CoCoPIE Inc. in certain jurisdictions.

The material contained herein is proprietary, privileged and confidential and owned by CoCoPIE Inc. or its third-party licensors. The information herein is provided only to be person or entity to which it is addressed, for its own use and evaluation; therefore, no disclosure of the content of this manual will be made to any third parities without specific written permission from CoCoPIE Inc.. The content herein is subject to change without further notice. Limitation of Liability – CoCoPIE Inc. shall not be liable.

All other trademarks are the property of their respective owners. Other company and brand products and service names are trademarks or registered trademarks of their respective holders.

Limitation of Liability

CoCoPIE Inc. shall not be liable.
