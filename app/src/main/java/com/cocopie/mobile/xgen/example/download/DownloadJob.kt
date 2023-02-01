package com.cocopie.mobile.xgen.example.download

import java.util.concurrent.CopyOnWriteArrayList

class DownloadJob(
    val downloadable: IDownloadable,
    var result: DownloadResult = DownloadResult(DownloadResult.STATE_UNKNOWN, downloadable.downloadUrl)
) {
    var task: DownloadTask? = null

    val callbacks: MutableList<IDownloadCallback> = CopyOnWriteArrayList()

    fun onProgress(read: Long, total: Long) {
        callbacks.forEach { it.onProgress(read, total) }
    }

    fun onFinish() {
        callbacks.forEach { it.onFinish(result) }
        callbacks.clear()
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + downloadable.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (javaClass != other.javaClass) {
            return false
        }
        val other1 = other as DownloadJob?
        return downloadable.tag == other1?.downloadable?.tag
    }
}
