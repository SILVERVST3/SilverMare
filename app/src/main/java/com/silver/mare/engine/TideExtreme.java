package com.silver.mare.engine;

/** Preamar (high = true) ou baixa-mar. */
public final class TideExtreme {
    public final long time;
    public final double height;
    public final boolean high;

    public TideExtreme(long time, double height, boolean high) {
        this.time = time;
        this.height = height;
        this.high = high;
    }
}
