package com.cocopie.mobile.xgen.example

object CoCoPIEJNIExporter {

    init {
        try {
            System.loadLibrary("inference_api_jni")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    external fun Create(pbPath: String?, dataPath: String?): Long
    external fun Run(engine: Long, input: FloatArray?): FloatArray?
}