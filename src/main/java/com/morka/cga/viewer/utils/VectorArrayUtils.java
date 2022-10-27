package com.morka.cga.viewer.utils;

public final class VectorArrayUtils {

    private VectorArrayUtils() {
        throw new IllegalAccessError();
    }

    public static float length3(float[] a) {
        return (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
    }

    public static float[] normalize3(float[] a) {
        final var length = length3(a);
        return new float[]{a[0] / length, a[1] / length, a[2] / length};
    }

    public static float dot3(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static float[] cross3(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - b[1] * a[2],
                a[2] * b[0] - b[2] * a[0],
                a[0] * b[1] - b[0] * a[1]
        };
    }

    public static float[] subtract3(float[] a, float[] b) {
        return new float[]{
                a[0] - b[0],
                a[1] - b[1],
                a[2] - b[2],
        };
    }
}
