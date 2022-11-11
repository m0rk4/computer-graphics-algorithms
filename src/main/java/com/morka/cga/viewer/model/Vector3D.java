package com.morka.cga.viewer.model;

public record Vector3D(float x, float y, float z) {

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public Vector3D normalize() {
        var length = length();
        return new Vector3D(x / length, y / length, z / length);
    }

    public float dot(Vector3D other) {
        return x * other.x() + y * other.y() + z * other.z();
    }

    public Vector3D cross(Vector3D other) {
        return new Vector3D(
                y * other.z() - other.y() * z,
                z * other.x() - other.z() * x,
                x * other.y() - other.x() * y
        );
    }

    public Vector3D subtract(Vector3D other) {
        return new Vector3D(
                x - other.x,
                y - other.y,
                z - other.z
        );
    }
}
