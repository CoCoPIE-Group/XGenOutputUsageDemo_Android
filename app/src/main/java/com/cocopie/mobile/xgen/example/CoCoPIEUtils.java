package com.cocopie.mobile.xgen.example;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.util.Arrays;

public class CoCoPIEUtils {

    private CoCoPIEUtils() {
    }

    public static void RunModel(final float[] input) {
        long start = System.currentTimeMillis();
        float[] result = CoCoPIEJNIExporter.Run(input);
        if (result == null || result.length == 0) {
            return;
        }
        Log.e("CoCoPIEUtils", "Output:" + Arrays.toString(result));
        long costTime = System.currentTimeMillis() - start;
        WDSROutputResult outputResult = new WDSROutputResult(input, result, costTime);
        EventBus.getDefault().post(new WDSROutputEvent(outputResult));
    }
}
