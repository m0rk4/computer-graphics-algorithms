package com.morka.cga.parser.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VertexTexture {
    private final float u;
    @Builder.Default
    private final float v = 0.0f;
    @Builder.Default
    private final float w = 0.0f;
}
