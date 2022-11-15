package com.morka.cga.viewer.controller;

import com.morka.cga.parser.exception.ObjParserException;
import com.morka.cga.parser.model.ObjGroup;
import com.morka.cga.parser.model.Vertex;
import com.morka.cga.parser.service.ObjFileParser;
import com.morka.cga.parser.service.ObjFileParserBuilder;
import com.morka.cga.viewer.buffer.WritableImageView;
import com.morka.cga.viewer.model.Matrix4D;
import com.morka.cga.viewer.model.Vector3D;
import com.morka.cga.viewer.utils.ColorUtils;
import com.morka.cga.viewer.utils.GeomUtils;
import javafx.application.Platform;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;

import static com.morka.cga.viewer.utils.GeomUtils.vector4D;
import static com.morka.cga.viewer.utils.MatrixUtils.*;
import static java.util.Objects.nonNull;
import static javafx.beans.binding.Bindings.createObjectBinding;

public class MainController {
    private static final Group FRAME_GROUP = new Group();
    private static final ObservableList<Node> FRAME_NODES = FRAME_GROUP.getChildren();
    private static final int W = 1280;
    private static final int H = 690;
    private static final int BUFFER_SIZE = 3;
    private static final int[] BACKGROUND_COLOR_ARRAY = new int[W * H];
    private static final float[] Z_BUFFER_INIT_ARRAY = new float[W * H];
    private static final SimpleObjectProperty<ObjGroup> CURRENT_OBJ = new SimpleObjectProperty<>();
    private static final Matrix4D PROJECTION_MATRIX = buildProjectionMatrix(W, H, 45, 0.1f, 100);
    private static final Matrix4D VIEWPORT_MATRIX = buildViewportMatrix(W, H);
    private static final Map<KeyCode, BooleanProperty> KEYS = new HashMap<>() {{
        put(KeyCode.X, new SimpleBooleanProperty(false));
        put(KeyCode.Y, new SimpleBooleanProperty(false));
        put(KeyCode.Z, new SimpleBooleanProperty(false));
        put(KeyCode.UP, new SimpleBooleanProperty(false));
        put(KeyCode.DOWN, new SimpleBooleanProperty(false));
        put(KeyCode.P, new SimpleBooleanProperty(false));
        put(KeyCode.M, new SimpleBooleanProperty(false));
        put(KeyCode.LEFT, new SimpleBooleanProperty(false));
        put(KeyCode.RIGHT, new SimpleBooleanProperty(false));
    }};
    private static final float CAMERA_SENSITIVITY = 0.01f;
    private final ExecutorService executorService;
    private final ObjFileParser parser = ObjFileParserBuilder.build();
    private final BlockingQueue<FrameAndZBuffers> fullBuffers = new ArrayBlockingQueue<>(BUFFER_SIZE);
    private final BlockingQueue<FrameAndZBuffers> emptyBuffers = new ArrayBlockingQueue<>(BUFFER_SIZE);
    private final IntegerProperty xTranslationProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty yTranslationProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty zTranslationProperty = new SimpleIntegerProperty(0);
    private final FloatProperty xRotationProperty = new SimpleFloatProperty(0);
    private final FloatProperty yRotationProperty = new SimpleFloatProperty(0);
    private final FloatProperty zRotationProperty = new SimpleFloatProperty(0);
    private final FloatProperty scaleProperty = new SimpleFloatProperty(1);
    private final FloatProperty xEyeProperty = new SimpleFloatProperty(0);
    private final FloatProperty yEyeProperty = new SimpleFloatProperty(0);
    private final FloatProperty radiusProperty = new SimpleFloatProperty(100);
    private final ObjectBinding<Vector3D> translationBinding = createObjectBinding(
            () -> new Vector3D(xTranslationProperty.get(), yTranslationProperty.get(), zTranslationProperty.get()),
            xTranslationProperty, yTranslationProperty, zTranslationProperty
    );
    private final ObjectBinding<Vector3D> scaleBinding = createObjectBinding(
            () -> new Vector3D(scaleProperty.get(), scaleProperty.get(), scaleProperty.get()),
            scaleProperty
    );
    private final ObjectBinding<Vector3D> rotationBinding = createObjectBinding(
            () -> new Vector3D(xRotationProperty.get(), yRotationProperty.get(), zRotationProperty.get()),
            xRotationProperty, yRotationProperty, zRotationProperty
    );
    private final ObjectBinding<Matrix4D> modelMatrix = createObjectBinding(
            () -> getModelMatrix(translationBinding.get(), scaleBinding.get(), rotationBinding.get()),
            translationBinding, scaleBinding, rotationBinding
    );
    private final Vector3D light = new Vector3D(0, 0, 1);
    private float lastPositionX;
    private float lastPositionY;
    private double lastTheta = 0;
    private double lastPhi = 0;
    private final ObjectBinding<Vector3D> eyeBinding = createObjectBinding(
            () -> getEyeVector(xEyeProperty.get(), yEyeProperty.get(), radiusProperty.get()),
            xEyeProperty, yEyeProperty, radiusProperty
    );
    private final ObjectBinding<Matrix4D> viewMatrix = createObjectBinding(
            () -> getViewMatrix(eyeBinding.get()),
            eyeBinding
    );

    @FXML
    private BorderPane pane;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private ColorPicker backgroundColorPicker;

    private Map<Vertex, Vector3D> vertexNormalMap;
    private final ConcurrentHashMap<Vertex, Vector3D> vertexWorldNormalMap = new ConcurrentHashMap<>();

    private FrameAndZBuffers currentBuffer;
    private boolean mouseDragging = false;
    private long lastProgressUpdateTimestamp = System.nanoTime();

    public MainController(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @FXML
    protected void initialize() {
        prepareBuffers();
        var rotationStep = (float) Math.PI / 32.0f;
        var translationStep = 1;
        var scaleStep = 1f;
        listenFor(KeyCode.P, () -> scaleProperty.set(scaleProperty.get() + scaleStep));
        listenFor(KeyCode.M, () -> scaleProperty.set(Math.max(0.05f, scaleProperty.get() - scaleStep)));
        listenFor(KeyCode.X, KeyCode.RIGHT, () -> xRotationProperty.set(xRotationProperty.get() + rotationStep));
        listenFor(KeyCode.X, KeyCode.LEFT, () -> xRotationProperty.set(xRotationProperty.get() - rotationStep));
        listenFor(KeyCode.Y, KeyCode.RIGHT, () -> yRotationProperty.set(yRotationProperty.get() + rotationStep));
        listenFor(KeyCode.Y, KeyCode.LEFT, () -> yRotationProperty.set(yRotationProperty.get() - rotationStep));
        listenFor(KeyCode.Z, KeyCode.RIGHT, () -> zRotationProperty.set(zRotationProperty.get() + rotationStep));
        listenFor(KeyCode.Z, KeyCode.LEFT, () -> zRotationProperty.set(zRotationProperty.get() - rotationStep));
        listenFor(KeyCode.X, KeyCode.UP, () -> xTranslationProperty.set(xTranslationProperty.get() + translationStep));
        listenFor(KeyCode.X, KeyCode.DOWN, () -> xTranslationProperty.set(xTranslationProperty.get() - translationStep));
        listenFor(KeyCode.Y, KeyCode.UP, () -> yTranslationProperty.set(yTranslationProperty.get() + translationStep));
        listenFor(KeyCode.Y, KeyCode.DOWN, () -> yTranslationProperty.set(yTranslationProperty.get() - translationStep));
        listenFor(KeyCode.Z, KeyCode.UP, () -> zTranslationProperty.set(zTranslationProperty.get() + translationStep));
        listenFor(KeyCode.Z, KeyCode.DOWN, () -> zTranslationProperty.set(zTranslationProperty.get() - translationStep));
        modelMatrix.addListener((__, ___, ____) -> repaint());
        viewMatrix.addListener((__, ___, ____) -> repaint());
        CURRENT_OBJ.addListener((__, ___, obj) -> {
            vertexNormalMap = obj.vertexToFaces().entrySet().parallelStream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue().stream()
                                    .map(GeomUtils::getNormal)
                                    .reduce(Vector3D::add)
                                    .map(normal -> normal.divide(e.getValue().size()))
                                    .get()
                    ));
            vertexWorldNormalMap.clear();
            resetStates();
            repaint();
        });
        pane.setOnMousePressed(e -> {
            lastPositionX = (float) e.getX();
            lastPositionY = (float) e.getY();
            mouseDragging = true;
        });
        pane.setOnMouseDragged(this::onMouseDragged);
        pane.setOnMouseReleased(__ -> mouseDragging = false);
        pane.setOnScroll(e -> {
            var dy = e.getDeltaY();
            if (Double.compare(dy, 0.0) != 0)
                radiusProperty.set((float) (radiusProperty.get() - dy / 20));
        });
        backgroundColorPicker.valueProperty().addListener((__, ___, color) -> {
            Arrays.fill(BACKGROUND_COLOR_ARRAY, ColorUtils.toArgb(color));
            repaint();
        });
    }

    private void prepareBuffers() {
        Arrays.fill(BACKGROUND_COLOR_ARRAY, ColorUtils.toArgb(backgroundColorPicker.getValue()));
        Arrays.fill(Z_BUFFER_INIT_ARRAY, Float.NEGATIVE_INFINITY);
        for (var i = 0; i < BUFFER_SIZE; i++) {
            var buffer = new WritableImageView(W, H);
            var zBuffer = new float[W * H];
            emptyBuffers.add(new FrameAndZBuffers(buffer, zBuffer));
        }
    }

    public void onUpdate() throws InterruptedException {
        if (fullBuffers.isEmpty())
            return;

        var buffers = fullBuffers.take();
        var frameBuffer = buffers.frameBuffer();

        addUiNode(frameBuffer);

        if (currentBuffer != null) {
            removeUiNode(currentBuffer.frameBuffer());
            emptyBuffers.add(currentBuffer);
        }

        frameBuffer.updateBuffer();
        currentBuffer = buffers;
    }

    private void resetStates() {
        xTranslationProperty.set(0);
        yTranslationProperty.set(0);
        zTranslationProperty.set(0);
        xRotationProperty.set(0);
        yRotationProperty.set(0);
        zRotationProperty.set(0);
        scaleProperty.set(1);
        xEyeProperty.set(0);
        yEyeProperty.set(0);
        radiusProperty.set(100);
    }

    void addUiNode(Node node) {
        FRAME_NODES.add(node);
    }

    void removeUiNode(Node node) {
        FRAME_NODES.remove(node);
    }

    private void repaint() {
        var obj = CURRENT_OBJ.get();
        if (obj != null)
            draw(obj);
    }

    private void listenFor(KeyCode key, Runnable item) {
        KEYS.get(key).addListener((__, ___, val) -> {
            if (val)
                item.run();
        });
    }

    private void listenFor(KeyCode firstKey, KeyCode secondKey, Runnable item) {
        var first = KEYS.get(firstKey);
        var second = KEYS.get(secondKey);
        var and = first.and(second);
        first.addListener((__, ___, ____) -> and.get());
        and.addListener((__, ___, val) -> {
            if (val)
                item.run();
        });
    }

    public void onKeyPressed(KeyEvent e) {
        var code = e.getCode();
        if (KEYS.containsKey(code))
            KEYS.get(code).set(true);
    }

    public void onKeyReleased(KeyEvent e) {
        var code = e.getCode();
        if (KEYS.containsKey(code))
            KEYS.get(code).set(false);
    }

    public void onMouseDragged(MouseEvent e) {
        if (!mouseDragging)
            return;
        xEyeProperty.set((float) e.getX());
        yEyeProperty.set((float) e.getY());
    }

    private Vector3D getEyeVector(float posX, float posY, float radius) {
        var dx = lastPositionX - posX;
        var dy = posY - lastPositionY;

        lastPositionX = posX;
        lastPositionY = posY;

        var theta = lastTheta + CAMERA_SENSITIVITY * dy;
        lastTheta = theta;

        if (theta < Math.PI / -2)
            theta = Math.PI / -2;
        else if (theta > Math.PI / 2)
            theta = Math.PI / 2;

        var phi = (lastPhi + CAMERA_SENSITIVITY * dx) % (Math.PI * 2);
        lastPhi = phi;

        var eyeX = (float) (radius * Math.cos(theta) * Math.cos(phi));
        var eyeY = (float) (radius * Math.sin(theta));
        var eyeZ = (float) (radius * Math.cos(theta) * Math.sin(phi));
        return new Vector3D(eyeX, eyeY, eyeZ);
    }

    private void draw(ObjGroup group) {
        executorService.submit(() -> {
            try {
                var buffers = emptyBuffers.take();
                var frameBuffer = buffers.frameBuffer();
                frameBuffer.setPixels(BACKGROUND_COLOR_ARRAY);
                var zBuffer = buffers.zBuffer();
                System.arraycopy(Z_BUFFER_INIT_ARRAY, 0, zBuffer, 0, zBuffer.length);

                var worldMatrix = this.modelMatrix.get();
                var viewMatrix = this.viewMatrix.get();
                var mvp = PROJECTION_MATRIX.multiply(viewMatrix).multiply(worldMatrix);
                group.faceList().parallelStream().forEach(face -> {
                    var elements = face.faceElements();

                    var firstVertex = elements[0].getVertex();
                    var secondVertex = elements[1].getVertex();
                    var thirdVertex = elements[2].getVertex();

                    var n0 = getFromCache(firstVertex, worldMatrix);
                    var n1 = getFromCache(secondVertex, worldMatrix);
                    var n2 = getFromCache(thirdVertex, worldMatrix);

                    var firstOriginal = vector4D(firstVertex);
                    var secondOriginal = vector4D(secondVertex);
                    var thirdOriginal = vector4D(thirdVertex);

                    var firstMvp = mvp.multiply(firstOriginal);
                    var secondMvp = mvp.multiply(secondOriginal);
                    var thirdMvp = mvp.multiply(thirdOriginal);

                    var firstMvpNormalized = firstMvp.divide(firstMvp.w());
                    var secondMvpNormalized = secondMvp.divide(secondMvp.w());
                    var thirdMvpNormalized = thirdMvp.divide(thirdMvp.w());

                    var firstViewport = VIEWPORT_MATRIX.multiply(firstMvpNormalized).to3D();
                    var secondViewport = VIEWPORT_MATRIX.multiply(secondMvpNormalized).to3D();
                    var thirdViewport = VIEWPORT_MATRIX.multiply(thirdMvpNormalized).to3D();

                    var firstWorld = worldMatrix.multiply(firstOriginal).to3D();
                    var secondWorld = worldMatrix.multiply(secondOriginal).to3D();
                    var thirdWorld = worldMatrix.multiply(thirdOriginal).to3D();

                    var firstMv = viewMatrix.multiply(worldMatrix).multiply(firstOriginal).to3D();
                    var secondMv = viewMatrix.multiply(worldMatrix).multiply(secondOriginal).to3D();
                    var thirdMv = viewMatrix.multiply(worldMatrix).multiply(thirdOriginal).to3D();

                    // backface culling
                    var faceNormal = firstWorld.subtract(secondWorld).cross(firstWorld.subtract(thirdWorld)).normalize();
                    var eye = eyeBinding.get().subtract(firstWorld).normalize();
                    if (faceNormal.dot(eye) <= 0)
                        return;

                    var factor = (faceNormal.dot(light) * 255);
                    if (factor <= 0)
                        factor = 0;
                    var intensity = (int) factor;
                    var flatColor = 255 << 24 | intensity << 16 | intensity << 8 | intensity;

//                    drawTriangle(
//                            frameBuffer,
//                            zBuffer,
//                            new Vector3D((int) firstViewport.x(), (int) firstViewport.y(), firstMv.z()),
//                            new Vector3D((int) secondViewport.x(), (int) secondViewport.y(), secondMv.z()),
//                            new Vector3D((int) thirdViewport.x(), (int) thirdViewport.y(), thirdMv.z()),
//                            n0,
//                            n1,
//                            n2,
//                            light,
//                            eye,
//                            flatColor
//                    );
                    drawTriangleBoxing(
                            frameBuffer,
                            zBuffer,
                            new VertexNormal(new Vector3D(firstViewport.x(), firstViewport.y(), firstMv.z()), n0),
                            new VertexNormal(new Vector3D(secondViewport.x(), secondViewport.y(), secondMv.z()), n1),
                            new VertexNormal(new Vector3D(thirdViewport.x(), thirdViewport.y(), thirdMv.z()), n2),
                            light,
                            eye
                    );
                });
                fullBuffers.add(buffers);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Vector3D getFromCache(Vertex vertex, Matrix4D worldMatrix) {
        var info = vertexWorldNormalMap.get(vertex);
        if (info != null)
            return info;
        info = worldMatrix.multiply(vertexNormalMap.get(vertex)).normalize();
        var putByOtherThreadJustNow = vertexWorldNormalMap.putIfAbsent(vertex, info);
        if (putByOtherThreadJustNow != null)
            info = putByOtherThreadJustNow;
        return info;
    }

    private record VertexNormal(Vector3D vertex, Vector3D normal) {
    }

    private void drawTriangleBoxing(WritableImageView buffer,
                                    float[] zBuffer,
                                    VertexNormal t0,
                                    VertexNormal t1,
                                    VertexNormal t2,
                                    Vector3D light,
                                    Vector3D eye) {
        if (t0.vertex().y() > t1.vertex().y()) {
            var temp = t0;
            t0 = t1;
            t1 = temp;
        }

        if (t0.vertex().y() > t2.vertex().y()) {
            var temp = t0;
            t0 = t2;
            t2 = temp;
        }

        if (t1.vertex().y() > t2.vertex().y()) {
            var temp = t1;
            t1 = t2;
            t2 = temp;
        }

        var topY = t2;
        var midY = t1;
        var lowY = t0;

        if (t0.vertex().x() > t1.vertex().x()) {
            var temp = t0;
            t0 = t1;
            t1 = temp;
        }

        if (t0.vertex().x() > t2.vertex().x()) {
            var temp = t0;
            t0 = t2;
            t2 = temp;
        }

        if (t1.vertex().x() > t2.vertex().x()) {
            var temp = t1;
            t1 = t2;
            t2 = temp;
        }

        var topX = t2;
        var midX = t1;
        var lowX = t0;

        var minX = (int) Math.max(0, lowX.vertex().x());
        var maxX = (int) Math.min(W - 1, topX.vertex().x());
        var minY = (int) Math.max(0, lowY.vertex().y());
        var maxY = (int) Math.min(H - 1, topY.vertex().y());

        for (var x = minX; x <= maxX; x++) {
            for (var y = minY; y <= maxY; y++) {
                var barycentric = barycentric(t0.vertex(), t1.vertex(), t2.vertex(), x, y);
                if (barycentric.x() < 0 || barycentric.y() < 0 || barycentric.z() < 0)
                    continue;

                var normal = getNormalInPixelInner(topY, midY, lowY, topX, midX, lowX, x, y).normalize();
                var reflect = normal.mul(2 * light.dot(normal)).subtract(light);

                var diffuseAlbedo = new Vector3D(0.5f, 0.2f, 0.7f);
                var specularAlbedo = new Vector3D(0.7f, 0.7f, 0.7f);
                var specularPower = 64f;

                var ambient = new Vector3D(0.1f, 0.1f, 0.1f);
                var diffuse = diffuseAlbedo.mul(Math.max(normal.dot(light), 0));
                var specular = specularAlbedo.mul((float) Math.pow(Math.max(0, reflect.dot(eye)), specularPower));

                var color = ambient.add(diffuse).add(specular);
                var colorFx = new Color(Math.min(1.0, color.x()), Math.min(1.0, color.y()), Math.min(color.z(), 1.0), 1);
                var argb = ColorUtils.toArgb(colorFx);

                var idx = x + y * W;
                var depth = t0.vertex().z() * barycentric.x() + t1.vertex().z() * barycentric.y() + t2.vertex().z() * barycentric.z();
                if (zBuffer[idx] < depth) {
                    drawPixel(buffer, x, y, argb);
                    zBuffer[idx] = depth;
                }
            }
        }
    }

    private static Vector3D getNormalInPixelInner(VertexNormal topY,
                                                  VertexNormal midY,
                                                  VertexNormal lowY,
                                                  VertexNormal topX,
                                                  VertexNormal midX,
                                                  VertexNormal lowX,
                                                  int x, int y) {
        var totalHeight = topY.vertex().y() - lowY.vertex().y();
        var i = y - lowY.vertex().y();
        var mY = (int) midY.vertex().y();
        if (y < mY) {
            var vn1 = lowY;
            var vn2 = lowY == lowX ? midX : lowX;
            var vn3 = lowY == topX ? midX : topX;
            var nA = vn1.normal().mul(vn2.vertex().y() - y)
                    .add(vn2.normal().mul(y - vn1.vertex().y()))
                    .divide(vn2.vertex().y() - vn1.vertex().y());
            var nB = vn1.normal().mul(vn3.vertex().y() - y)
                    .add(vn3.normal().mul(y - vn1.vertex().y()))
                    .divide(vn3.vertex().y() - vn1.vertex().y());

            var segmentHeight = midY.vertex().y() - lowY.vertex().y();
            var alpha = i / totalHeight;
            var beta = i / segmentHeight; // be careful: with above conditions no division by zero here
            var A = lowY.vertex().add(topY.vertex().subtract(lowY.vertex()).mul(alpha));
            var B = lowY.vertex().add(midY.vertex().subtract(lowY.vertex()).mul(beta));

            if (A.x() > B.x()) {
                var temp = A;
                A = B;
                B = temp;
            }

            return nA.mul(B.x() - x).add(nB.mul(x - A.x())).divide(B.x() - A.x());
        } else {
            var vn1 = topY;
            var vn2 = topY == lowX ? midX : lowX;
            var vn3 = topY == topX ? midX : topX;
            var nA = vn1.normal().mul(y - vn2.vertex().y())
                    .add(vn2.normal().mul(vn1.vertex().y() - y))
                    .divide(vn1.vertex().y() - vn2.vertex().y());
            var nB = vn1.normal().mul(y - vn3.vertex().y())
                    .add(vn3.normal().mul(vn1.vertex().y() - y))
                    .divide(vn1.vertex().y() - vn3.vertex().y());

            var segmentHeight = topY.vertex().y() - midY.vertex().y();
            var alpha = i / totalHeight;
            var beta = (i - (midY.vertex().y() - lowY.vertex().y())) / segmentHeight; // be careful: with above conditions no division by zero here
            var A = lowY.vertex().add(topY.vertex().subtract(lowY.vertex()).mul(alpha));
            var B = midY.vertex().add(topY.vertex().subtract(midY.vertex()).mul(beta));

            if (A.x() > B.x()) {
                var temp = A;
                A = B;
                B = temp;
            }

            return nA.mul(B.x() - x).add(nB.mul(x - A.x())).divide(B.x() - A.x());
        }
    }

    private void drawTriangle(WritableImageView buffer,
                              float[] zBuffer,
                              Vector3D t0,
                              Vector3D t1,
                              Vector3D t2,
                              Vector3D n0,
                              Vector3D n1,
                              Vector3D n2,
                              Vector3D light,
                              Vector3D eye,
                              int testColor) {
        if (t0.y() > t1.y()) {
            var temp = t0;
            t0 = t1;
            t1 = temp;
            temp = n0;
            n0 = n1;
            n1 = temp;
        }

        if (t0.y() > t2.y()) {
            var temp = t0;
            t0 = t2;
            t2 = temp;
            temp = n0;
            n0 = n2;
            n2 = temp;
        }

        if (t1.y() > t2.y()) {
            var temp = t1;
            t1 = t2;
            t2 = temp;
            temp = n1;
            n1 = n2;
            n2 = temp;
        }

        var t2y = Math.min(Math.max(0, (int) t2.y()), H - 1);
        var t1y = Math.min(Math.max(0, (int) t1.y()), H - 1);
        var t0y = Math.min(Math.max(0, (int) t0.y()), H - 1);

        var t2x = Math.min(Math.max(0, (int) t2.x()), W - 1);
        var t1x = Math.min(Math.max(0, (int) t1.x()), W - 1);
        var t0x = Math.min(Math.max(0, (int) t0.x()), W - 1);
        var degenerateTriangle = t0y == t1y && t0y == t2y;
        if (degenerateTriangle)
            return;

        var totalHeight = t2y - t0y;
        for (var i = 0; i < totalHeight; i++) {
            var isSecondHalf = i >= t1y - t0y;
            var segmentHeight = isSecondHalf ? t2y - t1y : t1y - t0y;
            var alpha = (float) i / totalHeight;
            var beta = (float) (i - (isSecondHalf ? t1y - t0y : 0)) / segmentHeight;
            var Ax = (int) (t0x + (t2x - t0x) * alpha);
            var Bx = (int) (isSecondHalf ? (t1x + (t2x - t1x) * beta) : (t0x + (t1x - t0x) * beta));

            if (Ax > Bx) {
                var temp = Ax;
                Ax = Bx;
                Bx = temp;
            }

            for (var x = Ax; x <= Bx; x++) {
                var y = t0y + i;
                var barycentric = barycentric(t0, t1, t2, x, y);
                var depth = t0.z() * barycentric.x() + t1.z() * barycentric.y() + t2.z() * barycentric.z();

                var normal = getNormal(
                        isSecondHalf,
                        Ax,
                        Bx,
                        new VertexNormal(new Vector3D(t0x, t0y, t0.z()), n0),
                        new VertexNormal(new Vector3D(t1x, t1y, t1.z()), n1),
                        new VertexNormal(new Vector3D(t2x, t2y, t2.z()), n2),
                        x, y
                );
                var reflect = normal.mul(2 * light.dot(normal)).subtract(light);

                var diffuseAlbedo = new Vector3D(0.5f, 0.2f, 0.7f);
                var specularAlbedo = new Vector3D(0.7f, 0.7f, 0.7f);
                var specularPower = 64f;

                var ambient = new Vector3D(0.1f, 0.1f, 0.1f);
                var diffuse = diffuseAlbedo.mul(Math.max(normal.dot(light), 0));
                var specular = specularAlbedo.mul((float) Math.pow(Math.max(0, reflect.dot(eye)), specularPower));

                var color = ambient.add(diffuse).add(specular);
                var colorFx = new Color(Math.min(1.0, color.x()), Math.min(1.0, color.y()), Math.min(color.z(), 1.0), 1);
                var argb = ColorUtils.toArgb(colorFx);

                var idx = x + y * W;
                if (zBuffer[idx] < depth) {
                    drawPixel(buffer, x, y, argb);
                    zBuffer[idx] = depth;
                }
            }
        }
    }

    private static Vector3D getNormal(boolean isSecondHalf,
                                      int Ax,
                                      int Bx,
                                      VertexNormal v0,
                                      VertexNormal v1,
                                      VertexNormal v2,
                                      int x, int y) {
        if (isSecondHalf) {
            var vn1 = v2;
            var firstLeft = v1.vertex().x() < v0.vertex().x();
            var vn2 = firstLeft ? v1 : v0;
            var vn3 = firstLeft ? v0 : v1;

            var vn1Y = (int) vn1.vertex().y();
            var vn2Y = (int) vn2.vertex().y();
            var vn3Y = (int) vn3.vertex().y();

            var nA = vn1.normal()
                    .mul(y - vn2Y)
                    .add(vn2.normal().mul(vn1Y - y))
                    .divide(vn1Y - vn2Y);

            var nB = vn1.normal().mul(y - vn3Y)
                    .add(vn3.normal().mul(vn1Y - y))
                    .divide(vn1Y - vn3Y);

            return nA.mul(Bx - x).add(nB.mul(x - Ax)).divide(Bx - Ax);
        } else {
            var vn1 = v0;
            var firstLeft = v1.vertex().x() < v2.vertex().x();
            var vn2 = firstLeft ? v1 : v2;
            var vn3 = firstLeft ? v2 : v1;

            var vn1Y = (int) vn1.vertex().y();
            var vn2Y = (int) vn2.vertex().y();
            var vn3Y = (int) vn3.vertex().y();

            var nA = vn1.normal()
                    .mul(vn2Y - y)
                    .add(vn2.normal().mul(y - vn1Y))
                    .divide(vn2Y - vn1Y);
            var nB = vn1.normal()
                    .mul(vn3Y - y)
                    .add(vn3.normal().mul(y - vn1Y))
                    .divide(vn3Y - vn1Y);
            return nA.mul(Bx - x).add(nB.mul(x - Ax)).divide(Bx - Ax);
        }
    }

    private Vector3D barycentric(Vector3D A, Vector3D B, Vector3D C, int x, int y) {
        var first = new Vector3D(C.x() - A.x(), B.x() - A.x(), A.x() - x);
        var second = new Vector3D(C.y() - A.y(), B.y() - A.y(), A.y() - y);
        var u = first.cross(second);
        if (Math.abs(u.z()) > 1e-2)
            return new Vector3D(1.f - (u.x() + u.y()) / u.z(), u.y() / u.z(), u.x() / u.z());
        return new Vector3D(-1, 1, 1);
    }

    private void drawPixel(WritableImageView buffer, int x, int y, int argbColor) {
        if (x >= 0 && x < W && y >= 0 && y < H)
            buffer.setArgb(x, y, argbColor);
    }

    @FXML
    void onFileOpen() {
        pane.requestFocus();
        var fileChooser = new FileChooser();
        var filter = new FileChooser.ExtensionFilter("Wavefont OBJ (*.obj)", "*.obj");
        fileChooser.getExtensionFilters().add(filter);
        var file = fileChooser.showOpenDialog(null);
        if (nonNull(file)) {
            pane.setCenter(progressIndicator);
            CompletableFuture.supplyAsync(() -> parseObjAndUpdateProgress(file)).thenAccept(objOpt ->
                    objOpt.ifPresent(obj -> Platform.runLater(() -> {
                        progressIndicator.setProgress(0);
                        pane.setCenter(FRAME_GROUP);
                        CURRENT_OBJ.set(obj);
                    })));
        }
    }

    private Optional<ObjGroup> parseObjAndUpdateProgress(File file) {
        var progressConsumer = (DoubleConsumer) progress -> {
            // throttle ui events and make them ~60 fps (16.(6) ms)
            var throttleTime = 17000000;
            var now = System.nanoTime();
            if (now - lastProgressUpdateTimestamp > throttleTime) {
                lastProgressUpdateTimestamp = now;
                Platform.runLater(() -> progressIndicator.setProgress(progress));
            }
        };
        try {
            return Optional.of(parser.parse(file, progressConsumer));
        } catch (ObjParserException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private record FrameAndZBuffers(WritableImageView frameBuffer, float[] zBuffer) {
    }
}
