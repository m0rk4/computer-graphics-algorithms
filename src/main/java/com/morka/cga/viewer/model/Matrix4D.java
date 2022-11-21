package com.morka.cga.viewer.model;

public record Matrix4D(float[][] contents) {
    private static final int SIDE_LENGTH = 4;

    public Matrix4D {
        if (contents.length != SIDE_LENGTH || contents[0].length != SIDE_LENGTH)
            throw new IllegalArgumentException("Matrix must be 4x4!");
    }

    public Matrix4D multiply(Matrix4D other) {
        var target = new Matrix4D(new float[4][4]);
        var firstMatrix = other.contents();
        var targetMatrix = target.contents();
        for (var row = 0; row < SIDE_LENGTH; row++) {
            for (var col = 0; col < SIDE_LENGTH; col++) {
                targetMatrix[row][col] = 0;
                for (var k = 0; k < SIDE_LENGTH; k++)
                    targetMatrix[row][col] += contents[row][k] * firstMatrix[k][col];
            }
        }
        return target;
    }

    public Vector4D multiply(Vector4D vertex) {
        var x = vertex.x();
        var y = vertex.y();
        var z = vertex.z();
        var w = vertex.w();
        var xx = contents[0][0] * x + contents[0][1] * y + contents[0][2] * z + contents[0][3] * w;
        var yy = contents[1][0] * x + contents[1][1] * y + contents[1][2] * z + contents[1][3] * w;
        var zz = contents[2][0] * x + contents[2][1] * y + contents[2][2] * z + contents[2][3] * w;
        var ww = contents[3][0] * x + contents[3][1] * y + contents[3][2] * z + contents[3][3] * w;
        return new Vector4D(xx, yy, zz, ww);
    }

    public Vector3D multiply(Vector3D vertex) {
        var x = vertex.x();
        var y = vertex.y();
        var z = vertex.z();
        var xx = contents[0][0] * x + contents[0][1] * y + contents[0][2] * z;
        var yy = contents[1][0] * x + contents[1][1] * y + contents[1][2] * z;
        var zz = contents[2][0] * x + contents[2][1] * y + contents[2][2] * z;
        return new Vector3D(xx, yy, zz);
    }

    public Matrix4D invert() {
        float[][] val = contents();
        float det = val[3][0] * val[2][1] * val[1][2] * val[0][3] - val[2][0] * val[3][1] * val[1][2] * val[0][3]
                - val[3][0] * val[1][1] * val[2][2] * val[0][3] + val[1][0] * val[3][1] * val[2][2] * val[0][3]
                + val[2][0] * val[1][1] * val[3][2] * val[0][3] - val[1][0] * val[2][1] * val[3][2] * val[0][3]
                - val[3][0] * val[2][1] * val[0][2] * val[1][3] + val[2][0] * val[3][1] * val[0][2] * val[1][3]
                + val[3][0] * val[0][1] * val[2][2] * val[1][3] - val[0][0] * val[3][1] * val[2][2] * val[1][3]
                - val[2][0] * val[0][1] * val[3][2] * val[1][3] + val[0][0] * val[2][1] * val[3][2] * val[1][3]
                + val[3][0] * val[1][1] * val[0][2] * val[2][3] - val[1][0] * val[3][1] * val[0][2] * val[2][3]
                - val[3][0] * val[0][1] * val[1][2] * val[2][3] + val[0][0] * val[3][1] * val[1][2] * val[2][3]
                + val[1][0] * val[0][1] * val[3][2] * val[2][3] - val[0][0] * val[1][1] * val[3][2] * val[2][3]
                - val[2][0] * val[1][1] * val[0][2] * val[3][3] + val[1][0] * val[2][1] * val[0][2] * val[3][3]
                + val[2][0] * val[0][1] * val[1][2] * val[3][3] - val[0][0] * val[2][1] * val[1][2] * val[3][3]
                - val[1][0] * val[0][1] * val[2][2] * val[3][3] + val[0][0] * val[1][1] * val[2][2] * val[3][3];
        if (det == 0f)
            throw new RuntimeException("non-invertible matrix");
        float m00 = val[1][2] * val[2][3] * val[3][1] - val[1][3] * val[2][2] * val[3][1] + val[1][3] * val[2][1] * val[3][2]
                - val[1][1] * val[2][3] * val[3][2] - val[1][2] * val[2][1] * val[3][3] + val[1][1] * val[2][2] * val[3][3];
        float m01 = val[0][3] * val[2][2] * val[3][1] - val[0][2] * val[2][3] * val[3][1] - val[0][3] * val[2][1] * val[3][2]
                + val[0][1] * val[2][3] * val[3][2] + val[0][2] * val[2][1] * val[3][3] - val[0][1] * val[2][2] * val[3][3];
        float m02 = val[0][2] * val[1][3] * val[3][1] - val[0][3] * val[1][2] * val[3][1] + val[0][3] * val[1][1] * val[3][2]
                - val[0][1] * val[1][3] * val[3][2] - val[0][2] * val[1][1] * val[3][3] + val[0][1] * val[1][2] * val[3][3];
        float m03 = val[0][3] * val[1][2] * val[2][1] - val[0][2] * val[1][3] * val[2][1] - val[0][3] * val[1][1] * val[2][2]
                + val[0][1] * val[1][3] * val[2][2] + val[0][2] * val[1][1] * val[2][3] - val[0][1] * val[1][2] * val[2][3];
        float m10 = val[1][3] * val[2][2] * val[3][0] - val[1][2] * val[2][3] * val[3][0] - val[1][3] * val[2][0] * val[3][2]
                + val[1][0] * val[2][3] * val[3][2] + val[1][2] * val[2][0] * val[3][3] - val[1][0] * val[2][2] * val[3][3];
        float m11 = val[0][2] * val[2][3] * val[3][0] - val[0][3] * val[2][2] * val[3][0] + val[0][3] * val[2][0] * val[3][2]
                - val[0][0] * val[2][3] * val[3][2] - val[0][2] * val[2][0] * val[3][3] + val[0][0] * val[2][2] * val[3][3];
        float m12 = val[0][3] * val[1][2] * val[3][0] - val[0][2] * val[1][3] * val[3][0] - val[0][3] * val[1][0] * val[3][2]
                + val[0][0] * val[1][3] * val[3][2] + val[0][2] * val[1][0] * val[3][3] - val[0][0] * val[1][2] * val[3][3];
        float m13 = val[0][2] * val[1][3] * val[2][0] - val[0][3] * val[1][2] * val[2][0] + val[0][3] * val[1][0] * val[2][2]
                - val[0][0] * val[1][3] * val[2][2] - val[0][2] * val[1][0] * val[2][3] + val[0][0] * val[1][2] * val[2][3];
        float m20 = val[1][1] * val[2][3] * val[3][0] - val[1][3] * val[2][1] * val[3][0] + val[1][3] * val[2][0] * val[3][1]
                - val[1][0] * val[2][3] * val[3][1] - val[1][1] * val[2][0] * val[3][3] + val[1][0] * val[2][1] * val[3][3];
        float m21 = val[0][3] * val[2][1] * val[3][0] - val[0][1] * val[2][3] * val[3][0] - val[0][3] * val[2][0] * val[3][1]
                + val[0][0] * val[2][3] * val[3][1] + val[0][1] * val[2][0] * val[3][3] - val[0][0] * val[2][1] * val[3][3];
        float m22 = val[0][1] * val[1][3] * val[3][0] - val[0][3] * val[1][1] * val[3][0] + val[0][3] * val[1][0] * val[3][1]
                - val[0][0] * val[1][3] * val[3][1] - val[0][1] * val[1][0] * val[3][3] + val[0][0] * val[1][1] * val[3][3];
        float m23 = val[0][3] * val[1][1] * val[2][0] - val[0][1] * val[1][3] * val[2][0] - val[0][3] * val[1][0] * val[2][1]
                + val[0][0] * val[1][3] * val[2][1] + val[0][1] * val[1][0] * val[2][3] - val[0][0] * val[1][1] * val[2][3];
        float m30 = val[1][2] * val[2][1] * val[3][0] - val[1][1] * val[2][2] * val[3][0] - val[1][2] * val[2][0] * val[3][1]
                + val[1][0] * val[2][2] * val[3][1] + val[1][1] * val[2][0] * val[3][2] - val[1][0] * val[2][1] * val[3][2];
        float m31 = val[0][1] * val[2][2] * val[3][0] - val[0][2] * val[2][1] * val[3][0] + val[0][2] * val[2][0] * val[3][1]
                - val[0][0] * val[2][2] * val[3][1] - val[0][1] * val[2][0] * val[3][2] + val[0][0] * val[2][1] * val[3][2];
        float m32 = val[0][2] * val[1][1] * val[3][0] - val[0][1] * val[1][2] * val[3][0] - val[0][2] * val[1][0] * val[3][1]
                + val[0][0] * val[1][2] * val[3][1] + val[0][1] * val[1][0] * val[3][2] - val[0][0] * val[1][1] * val[3][2];
        float m33 = val[0][1] * val[1][2] * val[2][0] - val[0][2] * val[1][1] * val[2][0] + val[0][2] * val[1][0] * val[2][1]
                - val[0][0] * val[1][2] * val[2][1] - val[0][1] * val[1][0] * val[2][2] + val[0][0] * val[1][1] * val[2][2];
        float invDet = 1.0f / det;
        return new Matrix4D(new float[][]{
                {m00 * invDet, m01 * invDet, m02 * invDet, m03 * invDet},
                {m10 * invDet, m11 * invDet, m12 * invDet, m13 * invDet},
                {m20 * invDet, m21 * invDet, m22 * invDet, m23 * invDet},
                {m30 * invDet, m31 * invDet, m32 * invDet, m33 * invDet},
        });
    }
}
