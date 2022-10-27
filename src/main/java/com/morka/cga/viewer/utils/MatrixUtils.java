package com.morka.cga.viewer.utils;

import com.morka.cga.parser.model.Vertex;

public final class MatrixUtils {

    private MatrixUtils() {
        throw new IllegalAccessError();
    }

    public static float[][] mul(float[][] a, float[][] b) {
        final var aRows = a[0].length;
        final var bRows = b[0].length;
        final var aCols = a.length;
        final var bCols = b.length;
        final var newMatrix = new float[bCols][aRows];

        if (aCols != bRows)
            throw new IllegalArgumentException("Matrix a's column count doesn't match matrix b's row count");

        for (var i = 0; i < aRows; ++i)
            for (var j = 0; j < aCols; ++j) {
                newMatrix[i][j] = 0.f;
                for (var k = 0; k < bCols; ++k)
                    newMatrix[k][i] += a[j][i] * b[k][j];
            }
        return newMatrix;
    }

    public static float[] mulVector4(float[][] contents, float[] vector) {
        var x = contents[0][0] * vector[0] + contents[0][1] * vector[1] + contents[0][2] * vector[2] + contents[0][3] * vector[3];
        var y = contents[1][0] * vector[0] + contents[1][1] * vector[1] + contents[1][2] * vector[2] + contents[1][3] * vector[3];
        var z = contents[2][0] * vector[0] + contents[2][1] * vector[1] + contents[2][2] * vector[2] + contents[2][3] * vector[3];
        var w = contents[3][0] * vector[0] + contents[3][1] * vector[1] + contents[3][2] * vector[2] + contents[3][3] * vector[3];
        return new float[]{x, y, z, w};
    }

    public static Vertex mulVertex(float[][] contents, Vertex vertex) {
        var x = vertex.getX();
        var y = vertex.getY();
        var z = vertex.getZ();
        var w = vertex.getW();
        var xx = contents[0][0] * x + contents[0][1] * y + contents[0][2] * z + contents[0][3] * w;
        var yy = contents[1][0] * x + contents[1][1] * y + contents[1][2] * z + contents[1][3] * w;
        var zz = contents[2][0] * x + contents[2][1] * y + contents[2][2] * z + contents[2][3] * w;
        var ww = contents[3][0] * x + contents[3][1] * y + contents[3][2] * z + contents[3][3] * w;
        return Vertex.builder().x(xx).y(yy).z(zz).w(ww).build();
    }
}
