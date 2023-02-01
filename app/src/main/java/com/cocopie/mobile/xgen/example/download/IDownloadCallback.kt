package com.cocopie.mobile.xgen.example.download

interface IDownloadCallback {

    fun onProgress(read: Long, total: Long) {}

    fun onFinish(result: DownloadResult) {}
}