package com.morka.cga.parser.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Vertex {
    private final double x;
    private final double y;
    private final double z;
    @Builder.Default
    private final double w = 1.0;
}
