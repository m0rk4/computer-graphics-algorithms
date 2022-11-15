package com.morka.cga.parser.service;

import com.morka.cga.parser.exception.ObjParserException;
import com.morka.cga.parser.service.impl.ObjFileParserImpl;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjFileParserImplTest {

    private final ObjFileParser parser = new ObjFileParserImpl();

    @Test
    public void test() throws URISyntaxException, ObjParserException {
        final var resource = Thread.currentThread().getContextClassLoader().getResource("cube.obj");
        assert resource != null;

        final var group = parser.parse(new File(resource.toURI()), v -> {
        });

        assertEquals(12, group.faceList().size());
    }
}
