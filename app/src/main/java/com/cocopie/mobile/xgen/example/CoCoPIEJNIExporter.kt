package com.cocopie.mobile.xgen.example

object CoCoPIEJNIExporter {

    init {
        try {
            System.loadLibrary("inference_api_jni")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    external fun CreateOpt(pbPath: String?, dataPath: String?): Long
    external fun CreateFallback(fallbackPath: String?): Long
    external fun Run(engine: Long, input: FloatArray?): FloatArray?
}