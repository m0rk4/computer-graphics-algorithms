package com.morka.cga.parser.model;

import java.util.List;
import java.util.Map;

public record ObjGroup(List<Face> faceList, List<Line> lines, Map<Vertex, List<Face>> vertexToFaces) {
}
