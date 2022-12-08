# computer-graphics-algorithms
Wavefont OBJ. viewer application with rasterization and shading utilizing only CPU.

## Supports following light models:
* Flat (Lambertian)
* Phong
* PBR 

Customization of Phong and PBR is supported in real-time.
Also it is possible to force the use of computed normals (instead of ones which reside in .OBJ).

## Supports following texture maps:
* diffuse (base color / albedo)
* normal
* emission
* mrao (metallic, roughness, ambient occlusion) for PBR

## Pay attention that these maps have higher priority than any other configurable parameters.

Java 17 is required to run the application. 
