package com.cocopie.mobile.xgen.example;

public class WDSROutputResult {
    public float[] pixels;
    public float[] inputs;
    public long costTime;

    public WDSROutputResult(float[] inputs, float[] pixels, long costTime) {
        this.pixels = pixels;
        this.costTime = costTime;
        this.inputs = inputs;
    }
}
