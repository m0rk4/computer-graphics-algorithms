package com.morka.cga.viewer.model;

import com.morka.cga.parser.model.Vertex;

public record Matrix4D(float[][] contents) {
    private static final int SIDE_LENGTH = 4;

    public Matrix4D {
        if (contents.length != SIDE_LENGTH || contents[0].length != SIDE_LENGTH)
            throw new IllegalArgumentException("Matrix must be 4x4!");
    }

    public Matrix4D multiply(Matrix4D other) {
        final var target = new Matrix4D(new float[4][4]);
        final var firstMatrix = other.contents();
        final var targetMatrix = target.contents();
        for (int row = 0; row < SIDE_LENGTH; row++) {
            for (int col = 0; col < SIDE_LENGTH; col++) {
                targetMatrix[row][col] = 0;
                for (int k = 0; k < SIDE_LENGTH; k++)
                    targetMatrix[row][col] += contents[row][k] * firstMatrix[k][col];
            }
        }
        return target;
    }

    public Vertex multiply(Vertex vertex) {
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
