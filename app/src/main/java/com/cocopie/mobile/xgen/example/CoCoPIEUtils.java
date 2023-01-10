package com.cocopie.mobile.xgen.example;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

public class CoCoPIEUtils {

    private CoCoPIEUtils() {
    }

    public static void RunModel(long engine, final float[] input) {
        long start = System.currentTimeMillis();
        float[] result = CoCoPIEJNIExporter.Run(engine, input);
        if (result == null || result.length == 0) {
            return;
        }
        Log.e("CoCoPIEUtils", "Output:" + Arrays.toString(result));
        long costTime = System.currentTimeMillis() - start;
        WDSROutputResult outputResult = new WDSROutputResult(input, result, costTime);
        EventBus.getDefault().post(new WDSROutputEvent(outputResult));
    }

    public static void copyAssetsFile(Context context, String dstPath, String srcPath) {
        if (new File(dstPath).exists()) {
            return;
        }
        try {
            String[] fileNames = context.getAssets().list(srcPath);
            if (fileNames.length > 0) {
                File file = new File(dstPath);
                if (!file.exists()) {
                    file.mkdirs();
                }
                for (String fileName : fileNames) {
                    if (!srcPath.equals("")) { // assets 文件夹下的目录
                        copyAssetsFile(context, srcPath + File.separator + fileName, dstPath + File.separator + fileName);
                    } else { // assets 文件夹
                        copyAssetsFile(context, fileName, dstPath + File.separator + fileName);
                    }
                }
            } else {
                File outFile = new File(dstPath);
                InputStream is = context.getAssets().open(srcPath);
                FileOutputStream fos = new FileOutputStream(outFile);
                byte[] buffer = new byte[1024];
                int byteCount;
                while ((byteCount = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, byteCount);
                }
                fos.flush();
                is.close();
                fos.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
