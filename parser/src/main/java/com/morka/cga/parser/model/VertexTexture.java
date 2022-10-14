package com.morka.cga.parser.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VertexTexture {
    private final double u;
    @Builder.Default
    private final double v = 0.0;
    @Builder.Default
    private final double w = 0.0;
}
