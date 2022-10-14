package com.morka.cga.parser.service;

import com.morka.cga.parser.model.ObjGroup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

public class ObjFileParserImplTest {

    private final ObjFileParser parser = new ObjFileParserImpl();

    @Test
    public void test() throws URISyntaxException {
        final URL resource = Thread.currentThread().getContextClassLoader().getResource("cube.obj");
        assert resource != null;

        final ObjGroup group = parser.parse(new File(resource.toURI()));

        Assertions.assertEquals(12, group.getFaces().size());
    }
}
