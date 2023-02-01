package com.cocopie.mobile.xgen.example.download

import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 扩展下载队列支持协程
 *
 * @param downloadable
 * @param immediate
 * @param onProgress
 */
suspend fun Downloader.download(
    downloadable: IDownloadable,
    immediate: Boolean = true,
    onProgress: (read: Long, total: Long) -> Unit = { _, _ -> }
): DownloadResult = suspendCancellableCoroutine {
    val callback = object : IDownloadCallback {
        override fun onProgress(read: Long, total: Long) {
            if (it.isActive) onProgress(read, total)
        }

        override fun onFinish(result: DownloadResult) {
            it.resumeWith(Result.success(result))
        }
    }
    it.invokeOnCancellation {
        getDownloadJob(downloadable)?.callbacks?.run {
            // 将当前回调从下载队列的对象中移除
            remove(callback)
            // 此时回调列表如果为空，认为应该需要取消下载任务【注：这里保证了每次download都会创建一个callback，因此断言有效】
            if (isEmpty()) {
                cancel(downloadable)
            }
        }
    }
    add(downloadable, callback, immediate)
}