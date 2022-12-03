package com.morka.cga.viewer.model;

public record Vector2D(float u, float v) {

    public float length() {
        return (float) Math.sqrt(u * u + v * v);
    }

    public Vector2D normalize() {
        var length = length();
        return new Vector2D(u / length, v / length);
    }

    public Vector2D subtract(Vector2D other) {
        return new Vector2D(
                u - other.u,
                v - other.v
        );
    }

    public Vector2D add(Vector2D other) {
        return new Vector2D(
                u + other.u,
                v + other.v
        );
    }

    public Vector2D divide(float divisor) {
        return new Vector2D(
                u / divisor,
                v / divisor
        );
    }

    public Vector2D mul(float mul) {
        return new Vector2D(
                u * mul,
                v * mul
        );
    }
}
