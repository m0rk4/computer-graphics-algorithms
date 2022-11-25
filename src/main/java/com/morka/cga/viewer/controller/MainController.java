package com.morka.cga.viewer.controller;

import com.morka.cga.parser.exception.ObjParserException;
import com.morka.cga.parser.model.FaceElement;
import com.morka.cga.parser.model.ObjGroup;
import com.morka.cga.parser.model.Vertex;
import com.morka.cga.parser.service.ObjFileParser;
import com.morka.cga.parser.service.ObjFileParserBuilder;
import com.morka.cga.viewer.buffer.WritableImageView;
import com.morka.cga.viewer.model.Matrix4D;
import com.morka.cga.viewer.model.Vector3D;
import com.morka.cga.viewer.model.Vector4D;
import com.morka.cga.viewer.utils.ColorUtils;
import com.morka.cga.viewer.utils.GeomUtils;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.morka.cga.viewer.utils.GeomUtils.vector4D;
import static com.morka.cga.viewer.utils.MatrixUtils.buildProjectionMatrix;
import static com.morka.cga.viewer.utils.MatrixUtils.buildViewportMatrix;
import static com.morka.cga.viewer.utils.MatrixUtils.getModelMatrix;
import static com.morka.cga.viewer.utils.MatrixUtils.getViewMatrix;
import static java.util.Objects.nonNull;
import static javafx.beans.binding.Bindings.createObjectBinding;

public class MainController {
    private static final Group FRAME_GROUP = new Group();
    private static final ObservableList<Node> FRAME_NODES = FRAME_GROUP.getChildren();
    private static final int W = 1160;
    private static final int H = 680;
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
    private final ConcurrentHashMap<Vertex, Vector3D> vertexWorldNormalMap = new ConcurrentHashMap<>();
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
    @FXML
    private Slider ambientCoef;
    @FXML
    private ColorPicker ambientColorPicker;
    @FXML
    private Slider diffuseCoef;
    @FXML
    private ColorPicker diffuseColorPicker;
    @FXML
    private Slider specularCoef;
    @FXML
    private ColorPicker specularColorPicker;
    @FXML
    private CheckBox normalCalculationCheckbox;

    private Map<FaceElement, Vector3D> vertexNormalMap;
    private FrameAndZBuffers currentBuffer;
    private boolean mouseDragging = false;
    private long lastProgressUpdateTimestamp = System.nanoTime();

    public MainController(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @FXML
    void initialize() {
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
        CURRENT_OBJ.addListener((__, ___, obj) -> onObjChanged(obj, normalCalculationCheckbox.isSelected(), true));
        pane.setOnMousePressed(this::onMousePressed);
        pane.setOnMouseDragged(this::onMouseDragged);
        pane.setOnMouseReleased(e -> mouseDragging = false);
        pane.setOnScroll(this::onScroll);
        backgroundColorPicker.valueProperty().addListener((__, ___, color) -> onBackgroundColorChanged(color));
        ambientColorPicker.valueProperty().addListener((__, ___, ____) -> repaint());
        ambientCoef.valueProperty().addListener((__, ___, ____) -> repaint());
        diffuseColorPicker.valueProperty().addListener((__, ___, ____) -> repaint());
        diffuseCoef.valueProperty().addListener((__, ___, ____) -> repaint());
        specularColorPicker.valueProperty().addListener((__, ___, ____) -> repaint());
        specularCoef.valueProperty().addListener((__, ___, ____) -> repaint());
        normalCalculationCheckbox.selectedProperty().addListener((__, ___, selected) -> onObjChanged(CURRENT_OBJ.get(), selected, false));
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

    private void prepareBuffers() {
        Arrays.fill(BACKGROUND_COLOR_ARRAY, ColorUtils.toArgb(backgroundColorPicker.getValue()));
        Arrays.fill(Z_BUFFER_INIT_ARRAY, Float.NEGATIVE_INFINITY);
        for (var i = 0; i < BUFFER_SIZE; i++) {
            var buffer = new WritableImageView(W, H);
            var zBuffer = new float[W * H];
            emptyBuffers.add(new FrameAndZBuffers(buffer, zBuffer));
        }
    }

    private void onMousePressed(MouseEvent e) {
        lastPositionX = (float) e.getX();
        lastPositionY = (float) e.getY();
        mouseDragging = true;
    }

    private void onScroll(ScrollEvent e) {
        var dy = e.getDeltaY();
        if (Double.compare(dy, 0.0) != 0)
            radiusProperty.set((float) (radiusProperty.get() - dy / 20));
    }

    private void onBackgroundColorChanged(Color color) {
        Arrays.fill(BACKGROUND_COLOR_ARRAY, ColorUtils.toArgb(color));
        repaint();
    }

    private void onObjChanged(ObjGroup obj, boolean forceNormalCalculation, boolean forceReset) {
        vertexNormalMap = obj.vertexToFaces().entrySet().parallelStream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> GeomUtils.getNormalForVertex(e.getKey(), e.getValue(), forceNormalCalculation)
        ));
        vertexWorldNormalMap.clear();
        if (forceReset)
            resetStates();
        repaint();
    }

    private void onMouseDragged(MouseEvent e) {
        if (!mouseDragging)
            return;
        xEyeProperty.set((float) e.getX());
        yEyeProperty.set((float) e.getY());
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

    private void addUiNode(Node node) {
        FRAME_NODES.add(node);
    }

    private void removeUiNode(Node node) {
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
                var zBuffer = buffers.zBuffer();
                frameBuffer.setPixels(BACKGROUND_COLOR_ARRAY);
                System.arraycopy(Z_BUFFER_INIT_ARRAY, 0, zBuffer, 0, zBuffer.length);

                var worldMatrix = modelMatrix.get();
                var viewMatrix = this.viewMatrix.get();
                var invViewport = VIEWPORT_MATRIX.invert();
                var invProj = PROJECTION_MATRIX.invert();
                var invView = viewMatrix.invert();
                var mvp = PROJECTION_MATRIX.multiply(viewMatrix).multiply(worldMatrix);
                group.faces().parallelStream().forEach(face -> {
                    var elements = face.faceElements();

                    var firstVertex = elements[0].getVertex();
                    var secondVertex = elements[1].getVertex();
                    var thirdVertex = elements[2].getVertex();

                    var n0 = computeWorldNormalIfAbsent(elements[0], worldMatrix);
                    var n1 = computeWorldNormalIfAbsent(elements[1], worldMatrix);
                    var n2 = computeWorldNormalIfAbsent(elements[2], worldMatrix);

                    var firstOriginal = vector4D(firstVertex);
                    var secondOriginal = vector4D(secondVertex);
                    var thirdOriginal = vector4D(thirdVertex);

                    var firstMvp = mvp.multiply(firstOriginal);
                    var secondMvp = mvp.multiply(secondOriginal);
                    var thirdMvp = mvp.multiply(thirdOriginal);

                    var firstNdc = firstMvp.divide(firstMvp.w());
                    var secondNdc = secondMvp.divide(secondMvp.w());
                    var thirdNdc = thirdMvp.divide(thirdMvp.w());

                    var firstViewport = VIEWPORT_MATRIX.multiply(firstNdc).to3D();
                    var secondViewport = VIEWPORT_MATRIX.multiply(secondNdc).to3D();
                    var thirdViewport = VIEWPORT_MATRIX.multiply(thirdNdc).to3D();

                    var viewportToWorldConverter = (Function<Vector3D, Vector3D>) viewport -> {
                        var ndc = invViewport.multiply(new Vector4D(viewport.x(), viewport.y(), viewport.z(), 1f));
                        var homView = invProj.multiply(ndc);
                        var view = new Vector4D(homView.x() / homView.w(), homView.y() / homView.w(),
                                homView.z() / homView.w(), 1);
                        return invView.multiply(view).to3D();
                    };

                    var firstWorld = worldMatrix.multiply(firstOriginal).to3D();
                    var secondWorld = worldMatrix.multiply(secondOriginal).to3D();
                    var thirdWorld = worldMatrix.multiply(thirdOriginal).to3D();

                    var firstMv = viewMatrix.multiply(worldMatrix).multiply(firstOriginal).to3D();
                    var secondMv = viewMatrix.multiply(worldMatrix).multiply(secondOriginal).to3D();
                    var thirdMv = viewMatrix.multiply(worldMatrix).multiply(thirdOriginal).to3D();

                    // backface culling
                    var eye = eyeBinding.get();
                    var faceNormal = firstWorld
                            .subtract(secondWorld)
                            .cross(firstWorld.subtract(thirdWorld))
                            .normalize();
                    var eyeBackFaceCulling = eye.subtract(firstWorld).normalize();
                    if (faceNormal.dot(eyeBackFaceCulling) <= 0)
                        return;

                    // TODO: move flat lighting logic to different place
                    var factor = (faceNormal.dot(light) * 255);
                    if (factor <= 0)
                        factor = 0;
                    var intensity = (int) factor;
                    var flatColor = 255 << 24 | intensity << 16 | intensity << 8 | intensity;

                    drawTriangle(
                            frameBuffer,
                            zBuffer,
                            new Vector3D((int) firstViewport.x(), (int) firstViewport.y(), firstMv.z()),
                            new Vector3D((int) secondViewport.x(), (int) secondViewport.y(), secondMv.z()),
                            new Vector3D((int) thirdViewport.x(), (int) thirdViewport.y(), thirdMv.z()),
                            n0,
                            n1,
                            n2,
                            light,
                            eye,
                            viewportToWorldConverter
                    );
                });
                fullBuffers.add(buffers);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Vector3D computeWorldNormalIfAbsent(FaceElement vertex, Matrix4D worldMatrix) {
        var info = vertexWorldNormalMap.get(vertex.getVertex());
        if (info != null)
            return info;
        info = worldMatrix.multiply(vertexNormalMap.get(vertex));
        var putByOtherThreadJustNow = vertexWorldNormalMap.putIfAbsent(vertex.getVertex(), info);
        if (putByOtherThreadJustNow != null)
            info = putByOtherThreadJustNow;
        return info;
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
                              Function<Vector3D, Vector3D> toWorld) {
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
        var d12y = t1.y() - t2.y();
        var d12x = t1.x() - t2.x();
        var d20y = t2.y() - t0.y();
        var d01y = t0.y() - t1.y();
        var d01x = t0.x() - t1.x();
        var d20x = t2.x() - t0.x();
        var triangleArea = -d20y * d12x + d12y * d20x;

        var t10 = t1.subtract(t0);
        var t20 = t2.subtract(t0);
        var isFirstVertexLeft = t10.x() * t20.y() - t10.y() * t20.x() <= 0;

        // todo: Can be moved even higher
        var ambient = ColorUtils.toVector(ambientColorPicker.getValue(), ambientCoef.getValue());
        var diffuseAlbedo = ColorUtils.toVector(diffuseColorPicker.getValue(), diffuseCoef.getValue());
        var specularAlbedo = ColorUtils.toVector(specularColorPicker.getValue(), specularCoef.getValue());
        var specularPower = 128;

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
            var y = t0y + i;

            // TODO: calculate incrementally + generalize
            Vector3D nA;
            Vector3D nB;
            if (isSecondHalf) {
                var leftN = isFirstVertexLeft ? n1 : n0;
                var rightN = isFirstVertexLeft ? n0 : n1;
                var left = isFirstVertexLeft ? t1 : t0;
                var right = isFirstVertexLeft ? t0 : t1;
                var angle = t2;
                var angleN = n2;
                nA = angleN.mul(y - left.y()).add(leftN.mul(angle.y() - y)).divide(angle.y() - left.y());
                nB = angleN.mul(y - right.y()).add(rightN.mul(angle.y() - y)).divide(angle.y() - right.y());
            } else {
                var leftN = isFirstVertexLeft ? n1 : n2;
                var rightN = isFirstVertexLeft ? n2 : n1;
                var left = isFirstVertexLeft ? t1 : t2;
                var right = isFirstVertexLeft ? t2 : t1;
                var angle = t0;
                var angleN = n0;
                nA = angleN.mul(left.y() - y).add(leftN.mul(y - angle.y())).divide(left.y() - angle.y());
                nB = angleN.mul(right.y() - y).add(rightN.mul(y - angle.y())).divide(right.y() - angle.y());
            }

            // barycentric incremental calculation
            var u = ((y - t2.y()) * d12x + d12y * (t2.x() - Ax)) / triangleArea;
            var v = ((y - t0.y()) * d20x + d20y * (t0.x() - Ax)) / triangleArea;
            var w = ((y - t1.y()) * d01x + d01y * (t1.x() - Ax)) / triangleArea;
            var dU = -d12y / triangleArea;
            var dV = -d20y / triangleArea;
            var dW = -d01y / triangleArea;

            // normal incremental calculation
            var dN = nB.subtract(nA).divide(Bx - Ax);
            var normal = nA;

            for (var x = Ax; x <= Bx; x++) {
                if (x != Ax) {
                    u += dU;
                    v += dV;
                    w += dW;
                }
                var isPixelOutsideOfTriangle = u < 0 || u > 1 || v < 0 || v > 1 || w < 0 || w > 1;
                if (isPixelOutsideOfTriangle)
                    continue;

                if (x != Ax)
                    normal = normal.add(dN);

                var z = t0.z() * u + t1.z() * v + t2.z() * w;
                var pixelWorld = toWorld.apply(new Vector3D(x, y, z));

                var normalNormalized = normal.normalize();
                var lightNormalized = light.subtract(pixelWorld).normalize();
                var eyeNormalized = eye.subtract(pixelWorld).normalize();
                var reflect = normalNormalized
                        .mul(2 * lightNormalized.dot(normalNormalized))
                        .subtract(lightNormalized);

                var diffuse = diffuseAlbedo.mul(Math.max(-normalNormalized.dot(lightNormalized), 0));
                var specular = specularAlbedo.mul((float) Math.pow(Math.max(0, -reflect.dot(eyeNormalized)), specularPower));
                var color = ambient.add(diffuse).add(specular);
                var argb = ColorUtils.toArgb(color);

                var idx = x + y * W;
                if (zBuffer[idx] < z) {
                    drawPixel(buffer, x, y, argb);
                    zBuffer[idx] = z;
                }
            }
        }
    }

    private void drawLine(WritableImageView buffer, int x1, int y1, int x2, int y2, int color) {
        var dx = Math.abs(x2 - x1);
        var sx = x1 < x2 ? 1 : -1;
        var dy = -Math.abs(y2 - y1);
        var sy = y1 < y2 ? 1 : -1;
        var err = dx + dy;
        while (true) {
            drawPixel(buffer, x1, y1, color);
            if (x1 == x2 && y1 == y2)
                return;
            var err2 = err * 2;
            if (err2 > dy) {
                err += dy;
                x1 += sx;
            }
            if (err2 <= dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    private void drawPixel(WritableImageView buffer, int x, int y, int argbColor) {
        if (x >= 0 && x < W && y >= 0 && y < H)
            buffer.setArgb(x, y, argbColor);
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
