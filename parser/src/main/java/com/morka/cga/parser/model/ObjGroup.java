package com.morka.cga.parser.model;

import java.util.List;
import java.util.Map;

public record ObjGroup(List<Face> faces,
                       List<Line> lines,
                       Map<FaceElement, List<Face>> vertexToFaces) {
}
