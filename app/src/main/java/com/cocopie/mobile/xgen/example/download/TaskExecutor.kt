package com.cocopie.mobile.xgen.example.download

import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * 线程池，核心线程数量为可用的CPU核数*2
 *
 * @author markmjw
 * @date 2017-07-12
 */
class TaskExecutor private constructor() : ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2) {

    init {
        // 有些手机要crash
//        allowCoreThreadTimeOut(true);
    }

    companion object {
        val instance: TaskExecutor by lazy {
            TaskExecutor()
        }
    }
}