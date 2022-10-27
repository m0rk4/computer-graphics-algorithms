package com.morka.cga.parser.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VertexNormal {
    private final float x;
    private final float y;
    private final float z;
}
