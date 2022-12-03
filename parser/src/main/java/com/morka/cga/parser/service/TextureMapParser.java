package com.morka.cga.parser.service;

import com.morka.cga.parser.model.TextureMap;

import java.io.File;

public interface TextureMapParser {

    TextureMap parse(File file);
}
