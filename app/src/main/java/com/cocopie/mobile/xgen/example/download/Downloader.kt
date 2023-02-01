package com.cocopie.mobile.xgen.example.download

import android.util.Log
import okhttp3.OkHttpClient
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 下载器
 *
 * 状态转换逻辑:
 * 等待状态 -> 正在下载
 * 正在下载 -> 等待状态
 * 正在下载 -> 下载结束
 */
class Downloader(private val okHttpClient: OkHttpClient = DownloadTask.DEFAULT_CLIENT) {

    companion object {
        private const val TAG = "Downloader"

        /** 默认同时下载的最大job数 */
        private const val MAX_RUNNING_JOBS = 4
    }

    /** 同时下载任务数 */
    var maxDownloadingCount: Int = MAX_RUNNING_JOBS

    /** 正在下载任务数 */
    val downloadingCount: Int
        get() = taskCount.get()

    private val taskCount = AtomicInteger(0)

    private val jobs = Collections.synchronizedList(ArrayList<DownloadJob>())

    /**
     * 添加一个下载任务，如果队列已经存在一个相同任务则复用。
     * 默认为FIFO规则依次下载。
     * 如果需要任务立即执行，可指定[immediate]=true，如果此时下载中的任务数已经达到最大并行任务数，则暂停最早开始下载的任务，并立即执行当前任务
     *
     * @param downloadable 可下载对象
     * @param callback 下载回调
     * @param immediate 是否立即执行
     */
    fun add(
        downloadable: IDownloadable,
        callback: IDownloadCallback? = null,
        immediate: Boolean = true
    ): DownloadJob {
        if (downloadable.hasCache) {
            // 如果误操作,重复下载,当判断有缓存的时候,直接回调成功了
            callback?.onFinish(DownloadResult(DownloadResult.STATE_FINISHED, downloadable.downloadUrl, downloadable.filePath))
            return DownloadJob(downloadable)
        }

        // 如果队列存在任务，则复用，否则创建一个任务并加入队列
        var job = DownloadJob(downloadable)
        val index = jobs.indexOf(job)
        if (index != -1) {
            job = jobs[index]
        } else {
            jobs.add(job)
        }

        // 将回调对象加入任务的回调列表中
        if (callback != null) job.callbacks.add(callback)

        // 如果加入任务尚未开始下载，且立即执行，同时下载数超出最大同时下载数，则取消第一个下载的任务
        if ((job.task == null || job.task?.isCanceled == true) && immediate && taskCount.get() >= maxDownloadingCount) {
            Log.w(TAG, "The number of current download jobs has reached the maximum, and the oldest job well be cancelled.")
            jobs.firstOrNullWithLock { it.task?.isCanceled == false }?.run {
                task?.cancel()
                task = null
                result.state = DownloadResult.STATE_WAITING
                updateTaskCount(-1)
                Log.d(TAG, "${this.downloadable.downloadUrl} download job waiting.")
            }
        }

        // 开始任务
        startJob(job)

        return job
    }

    fun cancel(downloadable: IDownloadable) {
        getDownloadJob(downloadable)?.run {
            cancelJob(this)
            jobs.remove(this)
        }
        startNext()
    }

    fun cancel(list: List<IDownloadable>) {
        list.mapNotNull { getDownloadJob(it) }.forEach {
            cancelJob(it)
            jobs.remove(it)
        }
        startNext()
    }

    /**
     * 是否正在下载
     *
     * @param downloadable
     * @return
     */
    fun isDownloading(downloadable: IDownloadable): Boolean {
        return jobs.anyWithLock { it.downloadable.downloadUrl == downloadable.downloadUrl && it.task?.isCanceled == false }
    }

    /**
     * 移除所有任务
     */
    fun cancelAll() {
        while (jobs.isNotEmpty()) {
            cancelJob(jobs.removeAt(0))
        }
    }

    private fun startJob(job: DownloadJob) {
        when {
            job.downloadable.hasCache -> jobFinish(job)
            job.task?.isCanceled == false -> {
                // 正在下载 Do nothing
                Log.d(TAG, "${job.downloadable.downloadUrl} download job started.")
            }
            canDownload() -> {
                updateTaskCount(1)
                job.result.state = DownloadResult.STATE_RUNNING
                Log.d(TAG, "${job.downloadable.downloadUrl} download job starting.")
                job.task = DownloadTask(job.downloadable.downloadUrl, job.downloadable.filePath, okHttpClient).apply {
                    download(object : IDownloadCallback {
                        override fun onProgress(read: Long, total: Long) {
                            job.onProgress(read, total)
                        }

                        override fun onFinish(result: DownloadResult) {
                            updateTaskCount(-1)
                            // 不管是否下载成功，都删除任务
                            jobs.remove(job)
                            if (result.state == DownloadResult.STATE_FINISHED) {
                                jobFinish(job)
                            } else {
                                jobFail(job)
                            }
                        }
                    })
                }
            }
            else -> {
                job.result.state = DownloadResult.STATE_WAITING
                Log.d(TAG, "${job.downloadable.downloadUrl} download job waiting.")
            }
        }
    }

    private fun cancelJob(job: DownloadJob) {
        Log.d(TAG, "${job.downloadable.downloadUrl} download job canceled.")
        job.task?.cancel()
        job.task = null
        job.result.state = DownloadResult.STATE_WAITING
        job.callbacks.clear()
        updateTaskCount(-1)
    }

    private fun jobFinish(job: DownloadJob) {
        Log.d(TAG, "${job.downloadable.downloadUrl} download job succeed.")
        job.result.state = DownloadResult.STATE_FINISHED
        job.onFinish()
        startNext()
    }

    private fun jobFail(job: DownloadJob) {
        Log.d(TAG, "${job.downloadable.downloadUrl} download job failed.")
        job.result.state = DownloadResult.STATE_FAILED
        job.onFinish()
        startNext()
    }

    private fun startNext() {
        // 移除下载完成的job
        jobs.removeAllWithLock { it.downloadable.hasCache }

        if (taskCount.get() < maxDownloadingCount) {
            synchronized(jobs) {
                // 开始下一个任务
                jobs.firstOrNullWithLock { it.result.state == DownloadResult.STATE_WAITING }?.run {
                    startJob(this)
                }
            }
        }
    }

    internal fun getDownloadJob(downloadable: IDownloadable): DownloadJob? {
        return jobs.firstOrNullWithLock { it.downloadable.tag == downloadable.tag }
    }

    private fun canDownload(): Boolean {
        // 不超过最大任务数
        return taskCount.get() < maxDownloadingCount
    }

    private fun updateTaskCount(delta: Int) {
        if (delta > 0) {
            taskCount.incrementAndGet()
        } else {
            if (taskCount.get() > 0) taskCount.decrementAndGet()
        }
    }

    private inline fun <T> Iterable<T>.anyWithLock(predicate: (T) -> Boolean): Boolean {
        synchronized(this) {
            if (this is Collection && isEmpty()) return false
            for (element in this) if (predicate(element)) return true
            return false
        }
    }

    private fun <T> MutableList<T>.removeAllWithLock(predicate: (T) -> Boolean): Boolean {
        synchronized(this) {
            return removeAll(predicate)
        }
    }

    private inline fun <T> Iterable<T>.firstOrNullWithLock(predicate: (T) -> Boolean): T? {
        synchronized(this) {
            for (element in this) if (predicate(element)) return element
            return null
        }
    }

}
