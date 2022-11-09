package com.morka.cga.viewer.model;

public record Vector2D(float x, float y) {

    public Vector2D subtract(Vector2D other) {
        return new Vector2D(
                x - other.x,
                y - other.y
        );
    }

    public Vector2D add(Vector2D other) {
        return new Vector2D(
                x + other.x,
                y + other.y
        );
    }

    public Vector2D mul(float mul) {
        return new Vector2D(
                x * mul,
                y * mul
        );
    }
}
