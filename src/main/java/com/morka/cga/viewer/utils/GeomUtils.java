package com.morka.cga.viewer.utils;

import com.morka.cga.parser.model.Face;
import com.morka.cga.parser.model.FaceElement;
import com.morka.cga.parser.model.Vertex;
import com.morka.cga.parser.model.VertexNormal;
import com.morka.cga.parser.model.VertexTexture;
import com.morka.cga.viewer.model.Vector2D;
import com.morka.cga.viewer.model.Vector3D;
import com.morka.cga.viewer.model.Vector4D;

import java.util.List;

public final class GeomUtils {

    private GeomUtils() {
        throw new AssertionError();
    }

    public static Vector3D getNormal(Face face) {
        var elements = face.faceElements();
        var v1 = vector3D(elements[0].getVertex());
        var v2 = vector3D(elements[1].getVertex());
        var v3 = vector3D(elements[2].getVertex());
        return v2.subtract(v1).cross(v3.subtract(v1)).normalize();
    }

    public static Vector3D getNormalForVertex(FaceElement element, List<Face> faces, boolean forceCalculation) {
        var shouldCalculateNormal = forceCalculation || element.getVertexNormal() == null;
        if (shouldCalculateNormal) {
            return faces.stream()
                    .map(GeomUtils::getNormal)
                    .reduce(Vector3D::add)
                    .map(normal -> normal.divide(faces.size()))
                    .orElseThrow(() ->
                            new IllegalStateException("No faces for vertex with id=%s".formatted(element.getId())));
        }
        return vector3D(element.getVertexNormal());
    }

    public static Vector3D mix(Vector3D i0, Vector3D i1, float t) {
        return i1.mul(t).add(i0.mul(1 - t));
    }

    public static Vector3D vector3D(Vertex vertex) {
        return new Vector3D(vertex.getX(), vertex.getY(), vertex.getZ());
    }

    public static Vector3D vector3D(VertexNormal vertex) {
        return new Vector3D(vertex.getX(), vertex.getY(), vertex.getZ());
    }

    public static Vector4D vector4D(Vertex vertex) {
        return new Vector4D(vertex.getX(), vertex.getY(), vertex.getZ(), vertex.getW());
    }

    public static Vector2D vector2D(VertexTexture texture) {
        return new Vector2D(texture.getU(), texture.getV());
    }
}
