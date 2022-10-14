package com.morka.cga.parser.service;

import com.morka.cga.parser.model.ObjGroup;

import java.io.File;

public sealed interface ObjFileParser permits ObjFileParserImpl {
    ObjGroup parse(File file);
}
