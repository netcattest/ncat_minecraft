package com.netcattest.ncatminecraft.block;

public final class RackLayout {
    public static final double MODULE_LEFT = .108D;
    public static final double MODULE_RIGHT = .892D;
    public static final double FACE = .135D;
    public static final double DEPTH = .855D;

    public static final double STATUS_LEFT = .124D;
    public static final double STATUS_RIGHT = .258D;
    public static final double BAY_LEFT = .282D;
    public static final double BAY_RIGHT = .722D;
    public static final double BRAND_LEFT = .742D;
    public static final double BRAND_RIGHT = .880D;

    private static final double PORT_FILL = .78D;
    private static final double PORT_MAX_WIDTH = .056D;
    private static final double PORT_MAX_HEIGHT = .058D;

    private RackLayout() {
    }

    public static double pitch(int count) {
        return (BAY_RIGHT - BAY_LEFT) / Math.max(1, count);
    }

    public static double portX(int index, int count) {
        return BAY_RIGHT - (index + .5D) * pitch(count);
    }

    public static double portWidth(int count) {
        return Math.min(PORT_MAX_WIDTH, pitch(count) * PORT_FILL);
    }

    public static double portHeight(double bottom, double top) {
        return Math.max(.020D, Math.min(PORT_MAX_HEIGHT, (top - bottom) * .50D));
    }

    public static double portY(double bottom, double top) {
        return bottom + (top - bottom) * .40D;
    }

    public static double ledY(double bottom, double top) {
        return portY(bottom, top) + portHeight(bottom, top) * .5D + (top - bottom) * .17D;
    }

    public static double socketDepth() {
        return FACE - .012D;
    }
}
