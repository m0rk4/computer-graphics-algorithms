package com.morka.cga.viewer.utils;

import com.morka.cga.parser.model.Vertex;
import com.morka.cga.viewer.model.Matrix4D;
import com.morka.cga.viewer.model.Vector3D;

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

    public static Matrix4D buildProjectionMatrix(float W, float H, float deg, float near, float far) {
        final var aspect = W / H;
        final var fov = (float) Math.toRadians(deg);
        final var invTanHalfFov = (float) (1.f / Math.tan(fov / 2.f));
        final var invRange = 1.f / (near - far);
        return new Matrix4D(new float[][]{
                {invTanHalfFov / aspect, 0.f, 0.f, 0.f},
                {0.f, invTanHalfFov, 0.f, 0.f},
                {0.f, 0.f, far * invRange, far * near * invRange},
                {0.f, 0.f, -1.f, 0.f}
        });
    }

    public static Matrix4D buildViewportMatrix(float width, float height) {
        return new Matrix4D(new float[][]{
                {width / 2.f, 0.f, 0.f, width / 2.f},
                {0.f, -height / 2.f, 0.f, height / 2.f},
                {0.f, 0.f, 1.f, 0.f},
                {0.f, 0.f, 0.f, 1.f}
        });
    }

    public static Matrix4D getXRotationMatrix(Vector3D vector) {
        float[][] matrix = new float[][]{
                {1f, 0, 0, 0},
                {0, (float) Math.cos(vector.x()), (float) (-1 * Math.sin(vector.x())), 0},
                {0, (float) Math.sin(vector.x()), (float) Math.cos(vector.x()), 0},
                {0, 0, 0, 1f}
        };
        return new Matrix4D(matrix);
    }

    public static Matrix4D getYRotationMatrix(Vector3D vector) {
        float[][] matrix = new float[][]{
                {(float) Math.cos(vector.y()), 0, (float) Math.sin(vector.y()), 0},
                {0, 1f, 0, 0},
                {(float) (-1 * Math.sin(vector.y())), 0, (float) Math.cos(vector.y()), 0},
                {0, 0, 0, 1f}
        };
        return new Matrix4D(matrix);
    }

    public static Matrix4D getZRotationMatrix(Vector3D vector) {
        float[][] matrix = new float[][]{
                {(float) Math.cos(vector.z()), (float) (-1 * Math.sin(vector.z())), 0, 0},
                {(float) Math.sin(vector.z()), (float) Math.cos(vector.z()), 0, 0},
                {0, 0, 1f, 0},
                {0, 0, 0, 1f}
        };
        return new Matrix4D(matrix);
    }

    public static Matrix4D getTranslationMatrix(Vector3D translation) {
        return new Matrix4D(new float[][]{
                {1, 0, 0, translation.x()},
                {0, 1, 0, translation.y()},
                {0, 0, 1, translation.z()},
                {0, 0, 0, 1}
        });
    }

    public static Matrix4D getScaleMatrix(Vector3D scale) {
        return new Matrix4D(new float[][]{
                {scale.x(), 0, 0, 0},
                {0, scale.y(), 0, 0},
                {0, 0, scale.z(), 0},
                {0, 0, 0, 1}
        });
    }
}
