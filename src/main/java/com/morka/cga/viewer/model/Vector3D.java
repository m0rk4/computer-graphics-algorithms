package com.morka.cga.viewer.model;

public record Vector3D(float x, float y, float z) {

    public Vector3D(double x, double y, double z) {
        this((float) x, (float) y, (float) z);
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public Vector3D(float a) {
        this(a, a, a);
    }

    public static Vector3D from(float a) {
        return new Vector3D(a, a, a);
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

    /**
     * Method for `num - vec3` operation.
     *
     * @param num num to subtract from
     * @return num - vec3
     */
    public Vector3D subtractFrom(float num) {
        return new Vector3D(
                num - x,
                num - y,
                num - z
        );
    }

    public Vector3D add(Vector3D other) {
        return new Vector3D(
                x + other.x,
                y + other.y,
                z + other.z
        );
    }

    public Vector3D divide(float divisor) {
        return new Vector3D(
                x / divisor,
                y / divisor,
                z / divisor
        );
    }

    public Vector3D divide(double divisor) {
        float div = (float) divisor;
        return new Vector3D(
                x / div,
                y / div,
                z / div
        );
    }

    public Vector3D divide(Vector3D divisor) {
        return new Vector3D(
                x / divisor.x(),
                y / divisor.y(),
                z / divisor.z()
        );
    }

    public Vector3D pow(Vector3D pow) {
        return new Vector3D(
                (float) Math.pow(x, pow.x()),
                (float) Math.pow(y, pow.y()),
                (float) Math.pow(z, pow.z())
        );
    }

    public Vector3D pow(float pow) {
        return new Vector3D(
                (float) Math.pow(x, pow),
                (float) Math.pow(y, pow),
                (float) Math.pow(z, pow)
        );
    }

    public Vector3D mul(float mul) {
        return new Vector3D(
                x * mul,
                y * mul,
                z * mul
        );
    }

    public Vector3D mul(Vector3D other) {
        return new Vector3D(
                x * other.x(),
                y * other.y(),
                z * other.z()
        );
    }
}
