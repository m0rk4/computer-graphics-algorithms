package com.morka.cga.viewer.model;

public record Vector4D(float x, float y, float z, float w) {

    public Vector4D divide(float divisor) {
        return new Vector4D(
                x / divisor,
                y / divisor,
                z / divisor,
                w / divisor
        );
    }

    public Vector3D to3D() {
        return new Vector3D(
                x,
                y,
                z
        );
    }
}
