package com.cocopie.mobile.xgen.example.download

import java.io.File

interface IDownloadable {

    val downloadUrl: String

    val filePath: String

    val hasCache: Boolean
        get() = File(filePath).exists()

    /** 用于判断两个下载对象是否相同，默认使用url */
    val tag: String
        get() = downloadUrl
}
