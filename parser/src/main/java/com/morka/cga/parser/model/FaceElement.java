package com.morka.cga.parser.model;

import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@Data
@Builder
public class FaceElement {
    private final int id;

    private final Vertex vertex;

    @Nullable
    @Builder.Default
    private final VertexTexture vertexTexture = null;

    @Nullable
    @Builder.Default
    private final VertexNormal vertexNormal = null;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FaceElement that = (FaceElement) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
