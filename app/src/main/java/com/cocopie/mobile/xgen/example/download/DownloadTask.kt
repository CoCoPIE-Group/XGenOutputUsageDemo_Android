package com.cocopie.mobile.xgen.example.download

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import okhttp3.internal.closeQuietly
import okio.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 文件下载Task，可跟随页面生命周期，可指定线程池
 *
 * @author like
 * @date 2017-12-11
 */
class DownloadTask constructor(
    private val url: String,
    private val filePath: String,
    private val client: OkHttpClient = DEFAULT_CLIENT,
    private val executor: ExecutorService = TaskExecutor.instance
) : Runnable {

    companion object {
        private const val TAG = "DownloadTask"
        private const val MAX_RETRY = 3

        private const val BUFFER_SIZE = 8192L

        val DEFAULT_CLIENT: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    }

    private var handler = Handler(Looper.getMainLooper())
    private var cancel = AtomicBoolean(false)
    val isCanceled: Boolean
        get() = cancel.get()

    private val result: DownloadResult = DownloadResult(DownloadResult.STATE_UNKNOWN, url)
    private var callback: IDownloadCallback? = null

    private var totalSize: Long = 0
    private var readSize: Long = 0
    private val tempFilePath: String
        get() = "$filePath.download"

    private var downloadCount = 0

    private var lastUpdateProgressTime = 0L

    override fun run() {
        // 执行异步任务
        val result = download()
        if (!isCanceled && callback != null) {
            handler.post {
                callback?.onFinish(result)
            }
        }
    }

    fun download(callback: IDownloadCallback) {
        this.callback = callback
        executor.submit(this)
    }

    fun download(): DownloadResult {
        var flag = false
        while (!isCanceled && downloadCount < MAX_RETRY) {
            flag = internalDownload(url, tempFilePath)
            if (flag && File(tempFilePath).renameTo(File(filePath))) {
                Log.i(TAG, "$url 下载成功")
                break
            } else {
                if (isCanceled) {
                    Log.i(TAG, "$url 下载取消")
                } else {
                    Log.w(TAG, "$url 下载失败：$downloadCount")
                }
            }
        }
        if (flag) {
            result.state = DownloadResult.STATE_FINISHED
            result.path = filePath
        } else {
            result.state = DownloadResult.STATE_FAILED
        }
        return result
    }

    fun cancel() {
        cancel.set(true)
        // 当任务被取消后，不再回调listener
        callback = null
    }

    private fun internalDownload(url: String, filePath: String): Boolean {
        if (downloadCount >= MAX_RETRY) {
            return false
        }
        downloadCount++

        Log.i(TAG, "下载地址：$url")
        var flag: Boolean
        var response: Response? = null
        try {
            result.state = DownloadResult.STATE_RUNNING
            Log.i(TAG, "存储路径：${this.filePath}")
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                readSize = file.length()
            } else {
                readSize = 0
                val fileParentDir = file.parentFile
                if (fileParentDir?.exists() == false) {
                    fileParentDir.mkdirs()
                }
                file.createNewFile()
            }

            val request = Request.Builder()
                .url(url)
                .header("RANGE", "bytes=$readSize-")
                .header("Connection", "close")
                .build()
            response = client.newCall(request).execute()
            val body = response.body
            if (response.isSuccessful && null != body) {
                val headers = response.headers
                if (headers["Content-Range"].isNullOrEmpty() && headers["Accept-Ranges"] != "bytes" && readSize > 0) {
                    // 不支持断点续传，需要删除临时文件
                    file.delete()
                    file.createNewFile()
                    readSize = 0
                }

                // 总大小 = 已下载大小 + 未下载大小
                totalSize = readSize + body.contentLength()
                if (readSize > 0) {
                    Log.i(TAG, "文件大小：$totalSize，已下载：$readSize，开始续传...")
                } else {
                    Log.i(TAG, "文件大小：$totalSize，开始下载...")
                }
                flag = if (readSize > totalSize) {
                    Log.i(TAG, "临时文件大小比文件总大小还大，删除重新下载")
                    file.delete()
                    // 再次调用download方法
                    internalDownload(url, filePath)
                } else if (readSize == totalSize && totalSize != 0L) {
                    Log.i(TAG, "临时文件大小与文件总大小相等，不重复下载")
                    updateProgress()
                    true
                } else {
                    file.sink(append = true).use { sink ->
                        body.source().use { source ->
                            val buffer = sink.buffer().buffer
                            var len = 0L
                            while (!isCanceled && source.read(buffer, BUFFER_SIZE).also { len = it } != -1L) {
                                sink.write(buffer, len)
                                readSize += len
                                updateProgress()
                            }
                        }
                    }
                    !isCanceled
                }
            } else {
                Log.i(TAG, "下载失败：$url [${response.code} ${response.message}]")
                if (response.code == 416) {
                    // 416错误说明断点续传的RANGE设置有误，需要删除临时文件
                    file.delete()
                    file.createNewFile()
                    readSize = 0
                }
                flag = false
            }
        } catch (e: Exception) {
            if (!e.message.equals("canceled", ignoreCase = true)) {
                Log.w(TAG, e)
            }
            flag = false
        } finally {
            response?.closeQuietly()
        }

        return flag
    }

    private fun updateProgress() {
        if (!isCanceled && callback != null) {
            // 200ms更新一次
            if (System.currentTimeMillis() - lastUpdateProgressTime >= 200) {
                lastUpdateProgressTime = System.currentTimeMillis()
                handler.post {
                    callback?.onProgress(readSize, totalSize)
                }
            }
        }
    }
}