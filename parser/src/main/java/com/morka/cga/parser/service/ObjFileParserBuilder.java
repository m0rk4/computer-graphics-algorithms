package com.morka.cga.parser.service;

import com.morka.cga.parser.service.impl.ObjFileParserImpl;
import com.morka.cga.parser.service.impl.TextureMapParserImpl;

public final class ObjFileParserBuilder {

    private ObjFileParserBuilder() {
        throw new AssertionError();
    }

    public static ObjFileParser buildObjParser() {
        return new ObjFileParserImpl();
    }

    public static TextureMapParser buildTextureParser() {
        return new TextureMapParserImpl();
    }
}
