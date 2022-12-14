package com.morka.cga.parser.service.impl;

import com.morka.cga.parser.exception.ObjParserException;
import com.morka.cga.parser.model.Face;
import com.morka.cga.parser.model.FaceElement;
import com.morka.cga.parser.model.Line;
import com.morka.cga.parser.model.ObjGroup;
import com.morka.cga.parser.model.Vertex;
import com.morka.cga.parser.model.VertexNormal;
import com.morka.cga.parser.model.VertexTexture;
import com.morka.cga.parser.service.ObjFileParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.regex.Pattern;

public final class ObjFileParserImpl implements ObjFileParser {

    private static final String SPACES_REGEX = "\\s+";

    private static final String VERTEX_PREFIX = "v ";

    private static final String VERTEX_TEXTURE_PREFIX = "vt ";

    private static final String VERTEX_NORMAL_PREFIX = "vn ";

    private static final String FACE_PREFIX = "f ";

    private static final Pattern FACE_ELEMENT_VERTEX_PATTERN = Pattern.compile("^[1-9][0-9+]*$");

    private static final Pattern FACE_ELEMENT_VERTEX_TEXTURE_PATTERN =
            Pattern.compile("^[1-9][0-9+]*/[1-9][0-9+]*$");

    private static final Pattern FACE_ELEMENT_VERTEX_NORMAL_PATTERN =
            Pattern.compile("^[1-9][0-9+]*//[1-9][0-9+]*$");

    private static final Pattern FACE_ELEMENT_VERTEX_TEXTURE_NORMAL_PATTERN =
            Pattern.compile("^[1-9][0-9+]*/[1-9][0-9+]*/[1-9][0-9+]*$");

    private static Vertex parseVertex(String line) {
        final var coords = line
                .substring(VERTEX_PREFIX.length())
                .trim()
                .split(SPACES_REGEX);

        assert coords.length >= 3 : "Vertex didn't follow the pattern: v x y z [w]\nFound: %s".formatted(line);

        final var builder = Vertex.builder()
                .x(Float.parseFloat(coords[0]))
                .y(Float.parseFloat(coords[1]))
                .z(Float.parseFloat(coords[2]));
        if (coords.length > 3)
            builder.w(Float.parseFloat(coords[3]));

        return builder.build();
    }

    private static Face parseFaceFromVertexesList(Map<Integer, Vertex> vertexMap, String[] faceElements) {
        final var elements = new FaceElement[faceElements.length];
        for (var i = 0; i < faceElements.length; i++) {
            final var relativeIndex = Integer.parseInt(faceElements[i]);
            final var index = getFaceElementIndex(vertexMap.size(), relativeIndex);
            final var vertex = vertexMap.get(index);
            final var faceElement = FaceElement.builder().id(index).vertex(vertex).build();
            elements[i] = faceElement;
        }
        return new Face(elements);
    }

    private static Face parseFaceFromVertexAndTexturesList(Map<Integer, Vertex> vertexMap,
                                                           Map<Integer, VertexTexture> vertexTextureMap,
                                                           String[] faceElementsStringified) {
        final var faceElements = new FaceElement[faceElementsStringified.length];
        for (var i = 0; i < faceElementsStringified.length; i++) {
            final var items = faceElementsStringified[i].split("/");
            final var vertexNumber = getFaceElementIndex(vertexMap.size(), Integer.parseInt(items[0]));
            final var vertexTextureNumber = getFaceElementIndex(vertexTextureMap.size(), Integer.parseInt(items[1]));
            final var element = FaceElement.builder()
                    .id(vertexNumber)
                    .vertex(vertexMap.get(vertexNumber))
                    .vertexTexture(vertexTextureMap.get(vertexTextureNumber))
                    .build();
            faceElements[i] = element;
        }
        return new Face(faceElements);
    }

    private static int getFaceElementIndex(int size, int index) {
        return index < 0 ? size + index + 1 : index;
    }

    @Override
    public ObjGroup parse(File file, DoubleConsumer progressConsumer) throws ObjParserException {
        try {
            var faces = new ArrayList<Face>();
            var vertexMap = new HashMap<Integer, Vertex>();
            var vertexTextureMap = new HashMap<Integer, VertexTexture>();
            var vertexNormalMap = new HashMap<Integer, VertexNormal>();
            var vertexFacesMap = new HashMap<FaceElement, List<Face>>();
            var lines = new ArrayList<Line>();
            var fileLines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            var fileLinesCount = fileLines.size();
            var parsedLines = 0;
            for (var line : fileLines) {
                if (line.startsWith(VERTEX_PREFIX))
                    vertexMap.put(vertexMap.size() + 1, parseVertex(line));

                if (line.startsWith(VERTEX_TEXTURE_PREFIX))
                    vertexTextureMap.put(vertexTextureMap.size() + 1, parseVertexTexture(line));

                if (line.startsWith(VERTEX_NORMAL_PREFIX))
                    vertexNormalMap.put(vertexNormalMap.size() + 1, parseVertexNormal(line));

                if (line.startsWith(FACE_PREFIX)) {
                    var face = parseFace(vertexMap, vertexTextureMap, vertexNormalMap, line);
                    triangulateFaceIfNeeded(faces, face);
                    var elements = face.faceElements();
                    for (var element : elements) {
                        vertexFacesMap.computeIfAbsent(element, k -> new ArrayList<>());
                        vertexFacesMap.get(element).add(face);
                    }

                    var vertexesCount = elements.length;
                    for (var i = 0; i < vertexesCount; i++) {
                        var from = elements[i].getVertex();
                        var to = elements[(i + 1) % vertexesCount].getVertex();
                        lines.add(new Line(from, to));
                    }
                }

                parsedLines++;
                var progress = (double) parsedLines / fileLinesCount;
                progressConsumer.accept(progress);
            }

            return new ObjGroup(faces, lines, vertexFacesMap);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ObjParserException(e.getMessage(), e);
        }
    }

    private void triangulateFaceIfNeeded(List<Face> faces, Face face) {
        var faceElements = face.faceElements();
        var length = faceElements.length;
        if (length < 4) {
            faces.add(face);
            return;
        }

        for (var i = 1; i < length - 1; i++)
            faces.add(new Face(new FaceElement[]{
                    faceElements[0],
                    faceElements[i],
                    faceElements[i + 1]
            }));
    }

    private VertexTexture parseVertexTexture(String line) {
        final var coords = line
                .substring(VERTEX_TEXTURE_PREFIX.length())
                .trim()
                .split(SPACES_REGEX);

        assert coords.length >= 1 : "Vertex texture didn't follow the pattern: vt u [v] [w]\nFound: %s".formatted(line);

        final var builder = VertexTexture.builder().u(Float.parseFloat(coords[0]));
        if (coords.length > 1)
            builder.v(Float.parseFloat(coords[1]));
        if (coords.length > 2)
            builder.w(Float.parseFloat(coords[2]));

        return builder.build();
    }

    private VertexNormal parseVertexNormal(String line) {
        final var coords = line
                .substring(VERTEX_NORMAL_PREFIX.length())
                .trim()
                .split(SPACES_REGEX);

        assert coords.length == 3 : "Vertex texture didn't follow the pattern: vn x y z\nFound: %s".formatted(line);

        return VertexNormal.builder()
                .x(Float.parseFloat(coords[0]))
                .y(Float.parseFloat(coords[1]))
                .z(Float.parseFloat(coords[2]))
                .build();
    }

    private Face parseFace(Map<Integer, Vertex> vertexMap,
                           Map<Integer, VertexTexture> vertexTextureMap,
                           Map<Integer, VertexNormal> vertexNormalMap,
                           String line) {
        final var faceElementsStringified = line.substring(FACE_PREFIX.length())
                .trim()
                .split(SPACES_REGEX);

        assert faceElementsStringified.length >= 3
                : "There are should be at least 3 faces elements.\nFound: %s".formatted(line);

        if (FACE_ELEMENT_VERTEX_PATTERN.matcher(faceElementsStringified[0]).matches())
            return parseFaceFromVertexesList(vertexMap, faceElementsStringified);

        if (FACE_ELEMENT_VERTEX_TEXTURE_PATTERN.matcher(faceElementsStringified[0]).matches())
            return parseFaceFromVertexAndTexturesList(vertexMap, vertexTextureMap, faceElementsStringified);

        if (FACE_ELEMENT_VERTEX_NORMAL_PATTERN.matcher(faceElementsStringified[0]).matches())
            return parseFaceFromVertexAndNormalsList(vertexMap, vertexNormalMap, faceElementsStringified);

        if (FACE_ELEMENT_VERTEX_TEXTURE_NORMAL_PATTERN.matcher(faceElementsStringified[0]).matches())
            return parseFaceFromVertexAndTextureAndNormalsList(vertexMap, vertexTextureMap, vertexNormalMap, faceElementsStringified);

        return new Face(new FaceElement[0]);
    }

    private Face parseFaceFromVertexAndNormalsList(Map<Integer, Vertex> vertexMap,
                                                   Map<Integer, VertexNormal> vertexNormalMap,
                                                   String[] faceElementsStringified) {
        final var faceElements = new FaceElement[faceElementsStringified.length];
        for (var i = 0; i < faceElementsStringified.length; i++) {
            final var items = faceElementsStringified[i].split("//");
            final var vertexNumber = getFaceElementIndex(vertexMap.size(), Integer.parseInt(items[0]));
            final var vertexNormalNumber = getFaceElementIndex(vertexNormalMap.size(), Integer.parseInt(items[1]));
            final var element = FaceElement.builder()
                    .id(vertexNumber)
                    .vertex(vertexMap.get(vertexNumber))
                    .vertexNormal(vertexNormalMap.get(vertexNormalNumber))
                    .build();
            faceElements[i] = element;
        }
        return new Face(faceElements);
    }

    private Face parseFaceFromVertexAndTextureAndNormalsList(Map<Integer, Vertex> vertexMap,
                                                             Map<Integer, VertexTexture> vertexTextureMap,
                                                             Map<Integer, VertexNormal> vertexNormalMap,
                                                             String[] faceElementsStringified) {
        final var faceElements = new FaceElement[faceElementsStringified.length];
        for (var i = 0; i < faceElementsStringified.length; i++) {
            final var items = faceElementsStringified[i].split("/");
            final var vertexNumber = getFaceElementIndex(vertexMap.size(), Integer.parseInt(items[0]));
            final var vertexTextureNumber = getFaceElementIndex(vertexTextureMap.size(), Integer.parseInt(items[1]));
            final var vertexNormalNumber = getFaceElementIndex(vertexNormalMap.size(), Integer.parseInt(items[2]));
            final FaceElement element = FaceElement.builder()
                    .id(vertexNumber)
                    .vertex(vertexMap.get(vertexNumber))
                    .vertexTexture(vertexTextureMap.get(vertexTextureNumber))
                    .vertexNormal(vertexNormalMap.get(vertexNormalNumber))
                    .build();
            faceElements[i] = element;
        }
        return new Face(faceElements);
    }
}
