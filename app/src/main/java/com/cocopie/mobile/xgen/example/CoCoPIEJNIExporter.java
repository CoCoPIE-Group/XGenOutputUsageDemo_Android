package com.cocopie.mobile.xgen.example;

public class CoCoPIEJNIExporter {
    static {
        try {
            System.loadLibrary("inference_api_jni");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static native long Create(String pbPath, String dataPath);

    public static native float[] Run(long engine, float[] input);
}
