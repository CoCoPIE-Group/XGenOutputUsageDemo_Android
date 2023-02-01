package com.cocopie.mobile.xgen.example.download

data class DownloadResult(var state: Int, var url: String = "", var path: String = "") {
    companion object {
        /** 默认状态 */
        const val STATE_UNKNOWN = -1

        /** 等待下载 */
        const val STATE_WAITING = 0

        /** 正在下载 */
        const val STATE_RUNNING = 1

        /** 下载完成 */
        const val STATE_FINISHED = 3

        /** 下载失败 */
        const val STATE_FAILED = 4
    }
}