package com.morka.cga.parser.model;

import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
@Builder
public class FaceElement {
    private final Vertex vertex;

    @Nullable
    @Builder.Default
    private final VertexTexture vertexTexture = null;

    @Nullable
    @Builder.Default
    private final VertexNormal vertexNormal = null;
}
