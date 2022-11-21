package model;

import com.morka.cga.viewer.model.Matrix4D;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class Matrix4DTest {

    @Test
    public void testInvert() {
        var matrix = new Matrix4D(new float[][]{
                {-1f, 2f, 3f, 4f},
                {55f, -66f, 77f, 12f},
                {32f, -324f, 11f, 4f},
                {33f, -55f, 777f, - 44f}
        });

        final float[][] res = matrix.invert().contents();
        for (float[] re : res) {
            System.out.println(Arrays.toString(re));
        }
    }
}
