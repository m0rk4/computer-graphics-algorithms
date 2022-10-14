package com.morka.cga.parser.service;

import com.morka.cga.parser.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;

public final class ObjFileParserImpl implements ObjFileParser {

    private static final Logger LOGGER = Logger.getLogger(ObjFileParserImpl.class.getName());

    private static final String SPACES_REGEX = "\\s+";

    private static final String VERTEX_PREFIX = "v ";

    private static final String VERTEX_TEXTURE_PREFIX = "vt ";

    private static final String VERTEX_NORMAL_PREFIX = "vn ";

    private static final String FACE_PREFIX = "f ";

    private static final Pattern FACE_ELEMENT_VERTEX_PATTERN = Pattern.compile("^[1-9][0-9+]*$");

    private static final Pattern FACE_ELEMENT_VERTEX_TEXTURE_PATTERN = Pattern.compile("^[1-9][0-9+]*/[1-9][0-9+]*$");

    private static final Pattern FACE_ELEMENT_VERTEX_NORMAL_PATTERN = Pattern.compile("^[1-9][0-9+]*//[1-9][0-9+]*$");

    private static final Pattern FACE_ELEMENT_VERTEX_TEXTURE_NORMAL_PATTERN = Pattern.compile("^[1-9][0-9+]*/[1-9][0-9+]*/[1-9][0-9+]*$");

    @Override
    public ObjGroup parse(File file) {
        try {
            List<Face> faces = new ArrayList<>();
            Map<Integer, Vertex> vertexMap = new HashMap<>();
            Map<Integer, VertexTexture> vertexTextureMap = new HashMap<>();
            Map<Integer, VertexNormal> vertexNormalMap = new HashMap<>();
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.startsWith(VERTEX_PREFIX))
                    vertexMap.put(vertexMap.size() + 1, parseVertex(line));

                if (line.startsWith(VERTEX_TEXTURE_PREFIX))
                    vertexTextureMap.put(vertexTextureMap.size() + 1, parseVertexTexture(line));

                if (line.startsWith(VERTEX_NORMAL_PREFIX))
                    vertexNormalMap.put(vertexNormalMap.size() + 1, parseVertexNormal(line));

                if (line.startsWith(FACE_PREFIX))
                    faces.add(parseFace(vertexMap, vertexTextureMap, vertexNormalMap, line));
            }
            return new ObjGroup(faces);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, () -> "Failed to load file %s.".formatted(file));
            return new ObjGroup(emptyList());
        }
    }

    private static Vertex parseVertex(String line) {
        final String[] coords = line
                .substring(VERTEX_PREFIX.length())
                .trim()
                .split(SPACES_REGEX);

        assert coords.length >= 3 : "Vertex didn't follow the pattern: v x y z [w]\nFound: %s".formatted(line);

        Vertex.VertexBuilder builder = Vertex.builder()
                .x(Double.parseDouble(coords[0]))
                .y(Double.parseDouble(coords[1]))
                .z(Double.parseDouble(coords[2]));
        if (coords.length > 3)
            builder.w(Double.parseDouble(coords[3]));

        return builder.build();
    }

    private VertexTexture parseVertexTexture(String line) {
        final String[] coords = line
                .substring(VERTEX_TEXTURE_PREFIX.length())
                .trim()
                .split(SPACES_REGEX);

        assert coords.length >= 1 : "Vertex texture didn't follow the pattern: vt u [v] [w]\nFound: %s".formatted(line);

        final VertexTexture.VertexTextureBuilder builder = VertexTexture.builder().u(Double.parseDouble(coords[0]));
        if (coords.length > 1)
            builder.v(Double.parseDouble(coords[1]));
        if (coords.length > 2)
            builder.w(Double.parseDouble(coords[2]));

        return builder.build();
    }

    private VertexNormal parseVertexNormal(String line) {
        final String[] coords = line
                .substring(VERTEX_NORMAL_PREFIX.length())
                .trim()
                .split(SPACES_REGEX);

        assert coords.length == 3 : "Vertex texture didn't follow the pattern: vn x y z\nFound: %s".formatted(line);

        return VertexNormal.builder()
                .x(Double.parseDouble(coords[0]))
                .y(Double.parseDouble(coords[1]))
                .z(Double.parseDouble(coords[2]))
                .build();
    }

    private Face parseFace(Map<Integer, Vertex> vertexMap,
                           Map<Integer, VertexTexture> vertexTextureMap,
                           Map<Integer, VertexNormal> vertexNormalMap,
                           String line) {
        final String[] faceElementsStringified = line.substring(FACE_PREFIX.length())
                .trim()
                .split(SPACES_REGEX);

        assert faceElementsStringified.length >= 3
                : "There are should be at least 3 face elements.\nFound: %s".formatted(line);

        if (FACE_ELEMENT_VERTEX_PATTERN.matcher(faceElementsStringified[0]).matches())
            return parseFaceFromVertexesList(vertexMap, faceElementsStringified);

        if (FACE_ELEMENT_VERTEX_TEXTURE_PATTERN.matcher(faceElementsStringified[0]).matches())
            return parseFaceFromVertexAndTexturesList(vertexMap, vertexTextureMap, faceElementsStringified);

        if (FACE_ELEMENT_VERTEX_NORMAL_PATTERN.matcher(faceElementsStringified[0]).matches())
            return parseFaceFromVertexAndNormalsList(vertexMap, vertexNormalMap, faceElementsStringified);

        if (FACE_ELEMENT_VERTEX_TEXTURE_NORMAL_PATTERN.matcher(faceElementsStringified[0]).matches())
            return parseFaceFromVertexAndTextureAndNormalsList(vertexMap, vertexTextureMap, vertexNormalMap, faceElementsStringified);

        return new Face(emptyList());
    }

    private static Face parseFaceFromVertexesList(Map<Integer, Vertex> vertexMap, String[] faceElements) {
        final List<FaceElement> elements = Arrays.stream(faceElements)
                .mapToInt(Integer::parseInt)
                .map(vertexNumber -> getFaceElementIndex(vertexMap.size(), vertexNumber))
                .mapToObj(vertexMap::get)
                .map(FaceElement.builder()::vertex)
                .map(FaceElement.FaceElementBuilder::build)
                .toList();
        return new Face(elements);
    }

    private static Face parseFaceFromVertexAndTexturesList(Map<Integer, Vertex> vertexMap,
                                                           Map<Integer, VertexTexture> vertexTextureMap,
                                                           String[] faceElementsStringified) {
        List<FaceElement> faceElements = new ArrayList<>(faceElementsStringified.length);
        for (String faceElement : faceElementsStringified) {
            final String[] items = faceElement.split("/");
            int vertexNumber = getFaceElementIndex(vertexMap.size(), Integer.parseInt(items[0]));
            int vertexTextureNumber = getFaceElementIndex(vertexTextureMap.size(), Integer.parseInt(items[1]));
            final FaceElement element = FaceElement.builder()
                    .vertex(vertexMap.get(vertexNumber))
                    .vertexTexture(vertexTextureMap.get(vertexTextureNumber))
                    .build();
            faceElements.add(element);
        }
        return new Face(faceElements);
    }

    private Face parseFaceFromVertexAndNormalsList(Map<Integer, Vertex> vertexMap,
                                                   Map<Integer, VertexNormal> vertexNormalMap,
                                                   String[] faceElementsStringified) {
        List<FaceElement> faceElements = new ArrayList<>(faceElementsStringified.length);
        for (String faceElement : faceElementsStringified) {
            final String[] items = faceElement.split("//");
            int vertexNumber = getFaceElementIndex(vertexMap.size(), Integer.parseInt(items[0]));
            int vertexNormalNumber = getFaceElementIndex(vertexNormalMap.size(), Integer.parseInt(items[1]));
            final FaceElement element = FaceElement.builder()
                    .vertex(vertexMap.get(vertexNumber))
                    .vertexNormal(vertexNormalMap.get(vertexNormalNumber))
                    .build();
            faceElements.add(element);
        }
        return new Face(faceElements);
    }

    private Face parseFaceFromVertexAndTextureAndNormalsList(Map<Integer, Vertex> vertexMap,
                                                             Map<Integer, VertexTexture> vertexTextureMap,
                                                             Map<Integer, VertexNormal> vertexNormalMap,
                                                             String[] faceElementsStringified) {
        List<FaceElement> faceElements = new ArrayList<>(faceElementsStringified.length);
        for (String faceElement : faceElementsStringified) {
            final String[] items = faceElement.split("/");
            int vertexNumber = getFaceElementIndex(vertexMap.size(), Integer.parseInt(items[0]));
            int vertexTextureNumber = getFaceElementIndex(vertexTextureMap.size(), Integer.parseInt(items[1]));
            int vertexNormalNumber = getFaceElementIndex(vertexNormalMap.size(), Integer.parseInt(items[2]));
            final FaceElement element = FaceElement.builder()
                    .vertex(vertexMap.get(vertexNumber))
                    .vertexTexture(vertexTextureMap.get(vertexTextureNumber))
                    .vertexNormal(vertexNormalMap.get(vertexNormalNumber))
                    .build();
            faceElements.add(element);
        }
        return new Face(faceElements);
    }

    private static int getFaceElementIndex(int size, int index) {
        return index < 0 ? size + index + 1 : index;
    }
}
