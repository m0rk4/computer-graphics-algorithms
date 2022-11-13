package com.morka.cga.viewer.utils;

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
}
