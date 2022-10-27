package com.morka.cga.parser.service;

import com.morka.cga.parser.service.impl.ObjFileParserImpl;

public final class ObjFileParserBuilder {

    private ObjFileParserBuilder() {
        throw new AssertionError();
    }

    public static ObjFileParser build() {
        return new ObjFileParserImpl();
    }
}
