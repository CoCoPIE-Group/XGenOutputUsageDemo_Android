package com.cocopie.mobile.xgen.example;

public class WDSROutputEvent {
    WDSROutputResult data;

    public WDSROutputEvent(WDSROutputResult data) {
        this.data = data;
    }

    public WDSROutputResult getData() {
        return data;
    }
}