package com.morka.cga.viewer.utils;

import com.morka.cga.viewer.model.Vector3D;

public final class PbrUtils {

    private PbrUtils() {
        throw new IllegalAccessError();
    }

    /**
     * Calculates ggx distribution.
     *
     * @param N         normal vector
     * @param H         halfway vector
     * @param roughness roughness
     * @return ggx distribution
     */
    public static float distributionGGX(Vector3D N, Vector3D H, float roughness) {
        float a = roughness * roughness;
        float a2 = a * a;
        float nDotH = Math.max(N.dot(H), 0.0f);
        float nDotH2 = nDotH * nDotH;

        float denominator = (nDotH2 * (a2 - 1.0f) + 1.0f);
        denominator = (float) Math.PI * denominator * denominator;

        return a2 / denominator;
    }

    /**
     * Calculates geometry Schlick-GGX.
     *
     * @param nDotV     dot product of normal and view vectors
     * @param roughness roughness coefficient
     * @return geometry Schlick-GGX
     */
    public static float geometrySchlickGGX(float nDotV, float roughness) {
        float r = (roughness + 1.0f);
        float k = (r * r) / 8.0f;

        float denominator = nDotV * (1.0f - k) + k;

        return nDotV / denominator;
    }

    /**
     * Calculates geometry smith.
     *
     * @param N         normal vector
     * @param V         view vector
     * @param L         light vector
     * @param roughness roughness coefficient
     * @return geometry smith
     */
    public static float geometrySmith(Vector3D N, Vector3D V, Vector3D L, float roughness) {
        float nDotV = Math.max(N.dot(V), 0.0f);
        float nDotL = Math.max(N.dot(L), 0.0f);
        float ggx2 = geometrySchlickGGX(nDotV, roughness);
        float ggx1 = geometrySchlickGGX(nDotL, roughness);

        return ggx1 * ggx2;
    }

    public static Vector3D fresnelSchlick(float cosTheta, Vector3D f0) {
        float clampedOneMinusCos = Math.max(Math.min(1.0f - cosTheta, 1.0f), 0.0f);
        float pow = (float) Math.pow(clampedOneMinusCos, 5.0);
        return f0.add(f0.subtractFrom(1.0f).mul(pow));
    }
}
