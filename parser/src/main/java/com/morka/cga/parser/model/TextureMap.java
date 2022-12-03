package com.morka.cga.parser.model;

public record TextureMap(int w, int h, int[][] pixels) {
    public int at(int x, int y) {
        return pixels[y][x];
    }
}
