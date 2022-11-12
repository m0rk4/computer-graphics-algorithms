package com.morka.cga.viewer.controller;

import com.morka.cga.parser.exception.ObjParserException;
import com.morka.cga.parser.model.ObjGroup;
import com.morka.cga.parser.model.Vertex;
import com.morka.cga.parser.service.ObjFileParser;
import com.morka.cga.parser.service.ObjFileParserBuilder;
import com.morka.cga.viewer.buffer.WritableImageView;
import com.morka.cga.viewer.model.Matrix4D;
import com.morka.cga.viewer.model.Vector3D;
import com.morka.cga.viewer.model.Vector4D;
import javafx.application.Platform;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.DoubleConsumer;

import static com.morka.cga.viewer.utils.MatrixUtils.buildProjectionMatrix;
import static com.morka.cga.viewer.utils.MatrixUtils.buildViewportMatrix;
import static com.morka.cga.viewer.utils.MatrixUtils.getModelMatrix;
import static com.morka.cga.viewer.utils.MatrixUtils.getViewMatrix;
import static java.util.Objects.nonNull;
import static javafx.beans.binding.Bindings.createObjectBinding;

public class MainController {
    private static final Group FRAME_GROUP = new Group();
    private static final ObservableList<Node> FRAME_NODES = FRAME_GROUP.getChildren();
    private static final int W = 1280;
    private static final int H = 690;
    private static final int ARGB_BLACK = 255 << 24 | 255 << 16 | 128 << 8 | 128;
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
    private final FloatProperty radiusProperty = new SimpleFloatProperty(50);
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
            resetStates();
            if (nonNull(obj))
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
            if (Double.compare(dy, 0.0) == 0)
                return;
            radiusProperty.set((float) (radiusProperty.get() - dy / 20));
        });
    }

    private void prepareBuffers() {
        Arrays.fill(BACKGROUND_COLOR_ARRAY, ARGB_BLACK);
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
    }

    void addUiNode(Node node) {
        FRAME_NODES.add(node);
    }

    void removeUiNode(Node node) {
        FRAME_NODES.remove(node);
    }

    private void repaint() {
        draw(CURRENT_OBJ.get());
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

                    var firstOriginal = vector4D(elements[0].getVertex());
                    var secondOriginal = vector4D(elements[1].getVertex());
                    var thirdOriginal = vector4D(elements[2].getVertex());

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

                    var worldNormal = thirdWorld.subtract(firstWorld)
                            .cross(thirdWorld.subtract(secondWorld))
                            .normalize();
                    var eye = eyeBinding.get().subtract(firstWorld).normalize();
                    if (worldNormal.dot(eye) <= 0)
                        return;

                    var factor = (worldNormal.dot(light) * 255);
                    if (factor <= 0)
                        factor = 0;
                    var intensity = (int) factor;
                    var color = 255 << 24 | intensity << 16 | intensity << 8 | intensity;

                    drawTriangleBoxing(
                            frameBuffer,
                            zBuffer,
                            new Vector3D(firstViewport.x(), firstViewport.y(), firstMv.z()),
                            new Vector3D(secondViewport.x(), secondViewport.y(), secondMv.z()),
                            new Vector3D(thirdViewport.x(), thirdViewport.y(), thirdMv.z()),
                            color
                    );
                });
                fullBuffers.add(buffers);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Vector4D vector4D(Vertex vertex) {
        return new Vector4D(vertex.getX(), vertex.getY(), vertex.getZ(), vertex.getW());
    }

    private void drawTriangleBoxing(WritableImageView buffer,
                                    float[] zBuffer,
                                    Vector3D t0,
                                    Vector3D t1,
                                    Vector3D t2,
                                    int color) {
        var bboxminX = W - 1f;
        var bboxminY = H - 1f;
        var bboxmaxX = 0f;
        var bboxmaxY = 0f;
        var clampX = W - 1f;
        var clampY = H - 1f;

        // 0
        bboxminX = Math.max(0, Math.min(bboxminX, t0.x()));
        bboxminY = Math.max(0, Math.min(bboxminY, t0.y()));

        bboxmaxX = Math.min(clampX, Math.max(bboxmaxX, t0.x()));
        bboxmaxY = Math.min(clampY, Math.max(bboxmaxY, t0.y()));

        // 1
        bboxminX = Math.max(0, Math.min(bboxminX, t1.x()));
        bboxminY = Math.max(0, Math.min(bboxminY, t1.y()));

        bboxmaxX = Math.min(clampX, Math.max(bboxmaxX, t1.x()));
        bboxmaxY = Math.min(clampY, Math.max(bboxmaxY, t1.y()));

        // 2
        bboxminX = Math.max(0, Math.min(bboxminX, t2.x()));
        bboxminY = Math.max(0, Math.min(bboxminY, t2.y()));

        bboxmaxX = Math.min(clampX, Math.max(bboxmaxX, t2.x()));
        bboxmaxY = Math.min(clampY, Math.max(bboxmaxY, t2.y()));

        for (var x = (int) bboxminX; x <= (int) bboxmaxX; x++) {
            for (var y = (int) bboxminY; y <= (int) bboxmaxY; y++) {
                var barycentric = barycentric(t0, t1, t2, x, y);
                if (barycentric.x() < 0 || barycentric.y() < 0 || barycentric.z() < 0)
                    continue;

                var depth = t0.z() * barycentric.x() + t1.z() * barycentric.y() + t2.z() * barycentric.z();
                var idx = x + y * W;
                if (zBuffer[idx] < depth) {
                    drawPixel(buffer, x, y, color);
                    zBuffer[idx] = depth;
                }
            }
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
