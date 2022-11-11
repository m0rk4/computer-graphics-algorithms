package com.morka.cga.viewer.utils;

import com.morka.cga.viewer.model.Matrix4D;
import com.morka.cga.viewer.model.Vector3D;

public final class MatrixUtils {

    private MatrixUtils() {
        throw new IllegalAccessError();
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

    public static Matrix4D getModelMatrix(Vector3D trans, Vector3D scale, Vector3D rotation) {
        final var translationMatrix = getTranslationMatrix(trans);
        final var scaleMatrix = getScaleMatrix(scale);
        final var xRotationMatrix = getXRotationMatrix(rotation);
        final var yRotationMatrix = getYRotationMatrix(rotation);
        final var zRotationMatrix = getZRotationMatrix(rotation);
        return translationMatrix.multiply(xRotationMatrix).multiply(yRotationMatrix).multiply(zRotationMatrix).multiply(scaleMatrix);
    }

    public static Matrix4D getViewMatrix(Vector3D eye) {
        final var target = new Vector3D(0, 0, 0);
        final var up = new Vector3D(0, -1, 0);

        final var zAxis = eye.subtract(target).normalize();
        final var xAxis = up.cross(zAxis).normalize();
        final var yAxis = xAxis.cross(zAxis);

        return new Matrix4D(new float[][]{
                {xAxis.x(), xAxis.y(), xAxis.z(), -xAxis.dot(eye)},
                {yAxis.x(), yAxis.y(), yAxis.z(), -yAxis.dot(eye)},
                {zAxis.x(), zAxis.y(), zAxis.z(), -zAxis.dot(eye)},
                {0.f, 0.f, 0.f, 1.f}
        });
    }
}
