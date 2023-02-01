package com.cocopie.mobile.xgen.example

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.FileOutputStream
import java.util.*

object CoCoPIEUtils {

    fun RunModel(engine: Long, input: FloatArray?) {
        val start = System.currentTimeMillis()
        val result = CoCoPIEJNIExporter.Run(engine, input)
        if (result == null || result.isEmpty()) {
            return
        }
        Log.e("CoCoPIEUtils", "Output:" + Arrays.toString(result))
        val costTime = System.currentTimeMillis() - start
        val outputResult = WDSROutputResult(input, result, costTime)
        EventBus.getDefault().post(WDSROutputEvent(outputResult))
    }

    fun copyAssetsFile(context: Context, dstPath: String, srcPath: String) {
        if (File(dstPath).exists()) {
            return
        }
        try {
            val fileNames = context.assets.list(srcPath)
            if (!fileNames.isNullOrEmpty()) {
                val file = File(dstPath)
                if (!file.exists()) {
                    file.mkdirs()
                }
                for (fileName in fileNames) {
                    if (srcPath != "") { // assets 文件夹下的目录
                        copyAssetsFile(context, srcPath + File.separator + fileName, dstPath + File.separator + fileName)
                    } else { // assets 文件夹
                        copyAssetsFile(context, fileName, dstPath + File.separator + fileName)
                    }
                }
            } else {
                val outFile = File(dstPath)
                val `is` = context.assets.open(srcPath)
                val fos = FileOutputStream(outFile)
                val buffer = ByteArray(1024)
                var byteCount: Int
                while (`is`.read(buffer).also { byteCount = it } != -1) {
                    fos.write(buffer, 0, byteCount)
                }
                fos.flush()
                `is`.close()
                fos.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun File.writeBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 100) {
    outputStream().use { out ->
        bitmap.compress(format, quality, out)
        out.flush()
    }
}