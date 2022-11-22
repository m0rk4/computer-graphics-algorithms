package com.morka.cga.parser.service;

import com.morka.cga.parser.exception.ObjParserException;
import com.morka.cga.parser.model.ObjGroup;
import com.morka.cga.parser.service.impl.ObjFileParserImpl;

import java.io.File;
import java.util.function.DoubleConsumer;

public sealed interface ObjFileParser permits ObjFileParserImpl {

    /**
     * Parses obj file.
     *
     * @param file             obj file
     * @param progressConsumer progress consumer (being called every processed line)
     * @return obj group containing lines, vertexes, vertex to faces
     * @throws ObjParserException if IO exception occurs
     */
    ObjGroup parse(File file, DoubleConsumer progressConsumer) throws ObjParserException;
}
