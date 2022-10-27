package com.morka.cga.viewer.buffer;

import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.nio.IntBuffer;

public class WritableImageView extends ImageView {
    private final int[] rawInts;

    private final int width;

    private final PixelBuffer<IntBuffer> pixelBuffer;

    public WritableImageView(int width, int height) {
        this.width = width;

        IntBuffer buffer = IntBuffer.allocate(width * height);
        rawInts = buffer.array();

        pixelBuffer = new PixelBuffer<>(width, height, buffer, PixelFormat.getIntArgbPreInstance());

        setSmooth(false);
//        setFitHeight(height);
//        setFitWidth(width);
        setPickOnBounds(true);
        setImage(new WritableImage(pixelBuffer));
    }

    public int[] getPixels() {
        return rawInts;
    }

    public void setPixels(int[] rawPixels) {
        System.arraycopy(rawPixels, 0, rawInts, 0, rawPixels.length);
    }

    public void setArgb(int x, int y, int colorARGB) {
        rawInts[(x % width) + (y * width)] = colorARGB;
    }

    public void updateBuffer() {
        pixelBuffer.updateBuffer(b -> null);
    }
}
