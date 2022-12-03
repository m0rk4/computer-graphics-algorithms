package com.morka.cga.parser.service.impl;

import com.morka.cga.parser.model.TextureMap;
import com.morka.cga.parser.service.TextureMapParser;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class TextureMapParserImpl implements TextureMapParser {

    public TextureMap parse(File file) {
        try {
            var bufferedImage = ImageIO.read(file);
            var w = bufferedImage.getWidth();
            var h = bufferedImage.getHeight();
            int[][] pixels = new int[h][w];
            for (var i = 0; i < h; i++) {
                for (var j = 0; j < w; j++) {
                    pixels[i][j] = bufferedImage.getRGB(j, i);
                }
            }
            System.out.println("Image load finished: " + file.getName());
            return new TextureMap(w, h, pixels);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
