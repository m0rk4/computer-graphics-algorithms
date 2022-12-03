package com.morka.cga.viewer.utils;

import com.morka.cga.viewer.model.Vector3D;
import javafx.scene.paint.Color;

public final class ColorUtils {

    private ColorUtils() {
        throw new IllegalAccessError();
    }

    public static int toArgb(Color color) {
        var red = (int) (color.getRed() * 255);
        var green = (int) (color.getGreen() * 255);
        var blue = (int) (color.getBlue() * 255);
        var opacity = (int) (color.getOpacity() * 255);
        return opacity << 24 | red << 16 | green << 8 | blue;
    }

    public static int toArgb(Vector3D color) {
        var red = (int) (Math.min(color.x(), 1) * 255);
        var green = (int) (Math.min(color.y(), 1) * 255);
        var blue = (int) (Math.min(color.z(), 1) * 255);
        return 255 << 24 | red << 16 | green << 8 | blue;
    }

    public static Vector3D toVector(int pixel) {
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = (pixel) & 0xff;
        return new Vector3D((float) red / 255f, (float) green / 255f, (float) blue / 255f);
    }

    public static Vector3D toVector(Color color, double coeff) {
        var vector3D = new Vector3D((float) color.getRed(), (float) color.getGreen(), (float) color.getBlue());
        return vector3D.mul((float) coeff);
    }
}
