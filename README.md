## 1 Introduction

This is an Android demo app to show how to integrate the output of XGen into an android app.

## 2 Integration of XGen output into an app

Readers can see `app/src/main/cpp/inference_api.cc` to see how the output of XGen is used in an app for AI. This part gives the explanation.

#### 2.1 Import XGen SDK

Put '**xgen.h**', '**xgen_data.h**', '**xgen_pb.h**', '**libxgen.so**' into the corresponding directory of the project, and then modify **CMakeLists.txt** script. 

#### 2.2 Initialize XGen

Call the *XGenInitWithData* method to initialize XGen, the required parameters can be found in '**xgen_data.h**' and '**xgen_pb.h**'

#### 2.3 Input data preprocessing

The input data is preprocessed according to the model parameters. Here is an example of preprocessing the input image when using the CIFAR10 dataset.

``` java
  int imageWidth = 32;
  int imageHeight = 32;
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

Call the *XGenCopyBufferToTensor* method to pass the preprocessed data into XGen runtime, and then call the *XGenRun* method to let XGen runtime call the AI model to conduct an inference.

``` c
  jfloat *input_data = env->GetFloatArrayElements(input, JNI_FALSE);
  
  XGenTensor *input_tensor = XGenGetInputTensor(h, 0);
  size_t input_size = XGenGetTensorSizeInBytes(input_tensor);
  XGenCopyBufferToTensor(input_tensor, input_data, input_size);

  XGenRun(h);
```

#### 2.5 Use the AI model through XGen runtime

Call the *XGenCopyTensorToBuffer* method to copy the result into a butter, which is a float array of length 10 in the CIFAR10 example

``` c
  XGenTensor *output_tensor = XGenGetOutputTensor(h, 0);
  size_t output_size = XGenGetTensorSizeInBytes(output_tensor);
  auto output_data = std::shared_ptr<float>(new float[output_size / sizeof(float)], std::default_delete<float[]>());
  XGenCopyTensorToBuffer(output_tensor, output_data.get(), output_size);

  jfloatArray jOutputData = env->NewFloatArray(output_size / sizeof(float));
  env->SetFloatArrayRegion(jOutputData, 0, (jsize) output_size / sizeof(float), output_data.get());
```

## 3 Copyright

© 2022 CoCoPIE Inc. All Rights Reserved.

CoCoPIE Inc., its logo and certain names, product and service names reference herein may be registered trademarks, trademarks, trade names or service marks of CoCoPIE Inc. in certain jurisdictions.

The material contained herein is proprietary, privileged and confidential and owned by CoCoPIE Inc. or its third-party licensors. The information herein is provided only to be person or entity to which it is addressed, for its own use and evaluation; therefore, no disclosure of the content of this manual will be made to any third parities without specific written permission from CoCoPIE Inc.. The content herein is subject to change without further notice. Limitation of Liability – CoCoPIE Inc. shall not be liable.

All other trademarks are the property of their respective owners. Other company and brand products and service names are trademarks or registered trademarks of their respective holders.

Limitation of Liability

CoCoPIE Inc. shall not be liable.
