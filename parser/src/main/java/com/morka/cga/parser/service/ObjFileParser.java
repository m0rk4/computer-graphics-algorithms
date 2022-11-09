package com.morka.cga.parser.service;

import com.morka.cga.parser.exception.ObjParserException;
import com.morka.cga.parser.model.ObjGroup;
import com.morka.cga.parser.service.impl.ObjFileParserImpl;

import java.io.File;

public sealed interface ObjFileParser permits ObjFileParserImpl {
    ObjGroup parse(File file) throws ObjParserException;
}
