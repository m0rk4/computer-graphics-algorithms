package com.morka.cga.parser.model;

import lombok.Data;

import java.util.List;

@Data
public class Face {
    private final List<FaceElement> faceElement;
}
