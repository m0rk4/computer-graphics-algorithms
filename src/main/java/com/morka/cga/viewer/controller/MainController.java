package com.morka.cga.viewer.controller;

import com.morka.cga.parser.exception.ObjParserException;
import com.morka.cga.parser.model.FaceElement;
import com.morka.cga.parser.model.ObjGroup;
import com.morka.cga.parser.model.TextureMap;
import com.morka.cga.parser.model.Vertex;
import com.morka.cga.parser.service.ObjFileParser;
import com.morka.cga.parser.service.ObjFileParserBuilder;
import com.morka.cga.parser.service.TextureMapParser;
import com.morka.cga.viewer.buffer.WritableImageView;
import com.morka.cga.viewer.model.Matrix4D;
import com.morka.cga.viewer.model.Vector2D;
import com.morka.cga.viewer.model.Vector3D;
import com.morka.cga.viewer.model.Vector4D;
import com.morka.cga.viewer.utils.ColorUtils;
import com.morka.cga.viewer.utils.GeomUtils;
import com.morka.cga.viewer.utils.PbrUtils;
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
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
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

import static com.morka.cga.viewer.utils.GeomUtils.mix;
import static com.morka.cga.viewer.utils.GeomUtils.vector2D;
import static com.morka.cga.viewer.utils.GeomUtils.vector4D;
import static com.morka.cga.viewer.utils.MatrixUtils.buildProjectionMatrix;
import static com.morka.cga.viewer.utils.MatrixUtils.buildViewportMatrix;
import static com.morka.cga.viewer.utils.MatrixUtils.getModelMatrix;
import static com.morka.cga.viewer.utils.MatrixUtils.getViewMatrix;
import static java.lang.Math.max;
import static java.lang.Math.min;
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
    private final ObjFileParser parser = ObjFileParserBuilder.buildObjParser();
    private final TextureMapParser textureParser = ObjFileParserBuilder.buildTextureParser();
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
    private final Vector3D light = new Vector3D(0, 0, -50);
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
    private ColorPicker iAPicker;

    @FXML
    private ColorPicker iDPicker;

    @FXML
    private ColorPicker iSPicker;

    @FXML
    private ColorPicker kAPicker;

    @FXML
    private ColorPicker kDPicker;

    @FXML
    private ColorPicker kSPicker;

    @FXML
    private Slider specularPower;

    @FXML
    private CheckBox normalCalculationCheckbox;

    @FXML
    private ColorPicker pbrAlbedoPicker;

    @FXML
    private Slider roughnessSlider;

    @FXML
    private Slider metallicSlider;

    @FXML
    private Slider aoSlider;

    @FXML
    private ToggleGroup shaderToggle;


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
        listenFor(KeyCode.M, () -> scaleProperty.set(max(0.05f, scaleProperty.get() - scaleStep)));
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
        iAPicker.valueProperty().addListener((__, ___, ____) -> repaint());
        kAPicker.valueProperty().addListener((__, ___, ____) -> repaint());
        iDPicker.valueProperty().addListener((__, ___, ____) -> repaint());
        kDPicker.valueProperty().addListener((__, ___, ____) -> repaint());
        iSPicker.valueProperty().addListener((__, ___, ____) -> repaint());
        kSPicker.valueProperty().addListener((__, ___, ____) -> repaint());
        specularPower.valueProperty().addListener((__, ___, ____) -> repaint());
        pbrAlbedoPicker.valueProperty().addListener((__, ___, ____) -> repaint());
        metallicSlider.valueProperty().addListener((__, ___, ____) -> repaint());
        roughnessSlider.valueProperty().addListener((__, ___, ____) -> repaint());
        aoSlider.valueProperty().addListener((__, ___, ____) -> repaint());
        shaderToggle.selectedToggleProperty().addListener((__, ___, toggle) -> {
            var radio = (RadioButton) toggle;
            isPbr = "PBR".equalsIgnoreCase(radio.getText());
            isPhong = "Phong".equalsIgnoreCase(radio.getText());
            isFlat = "Flat".equalsIgnoreCase(radio.getText());
            repaint();
        });
        normalCalculationCheckbox.selectedProperty().addListener((__, ___, selected) -> onObjChanged(CURRENT_OBJ.get(), selected, false));
    }

    boolean isPbr;
    boolean isPhong = true;

    boolean isFlat;

    TextureMap diffuseMap;
    TextureMap normalMap;
    TextureMap emissionMap;
    TextureMap mraoMap;

    @FXML
    void onMRAOLoad() {
        mraoMap = loadTextureFile();
    }

    @FXML
    void onEmissionLoad() {
        emissionMap = loadTextureFile();
    }

    @FXML
    void onNormalLoad() {
        normalMap = loadTextureFile();
    }

    @FXML
    void onDiffuseLoad() {
        diffuseMap = loadTextureFile();
    }

    private TextureMap loadTextureFile() {
        pane.requestFocus();
        var fileChooser = new FileChooser();
        var file = fileChooser.showOpenDialog(null);
        if (nonNull(file))
            return textureParser.parse(file);
        return null;
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
        if (obj == null)
            return;

        vertexNormalMap = obj.vertexToFaces().entrySet().parallelStream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> GeomUtils.getNormalForVertex(e.getKey(), e.getValue(), forceNormalCalculation)
        ));
        vertexWorldNormalMap.clear();
        if (forceReset)
            resetStates();
        repaint();
    }

    private long lastDragEventTimestamp;

    private void onMouseDragged(MouseEvent e) {
        var now = System.nanoTime();
        var dragThrottleTime = 16666666;
        if (now - lastDragEventTimestamp <= dragThrottleTime)
            return;

        lastDragEventTimestamp = now;
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

                    // TODO: interpolate
                    // TODO: tone mapping
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
                            new VertexTextureNormal(
                                    new Vector3D((int) firstViewport.x(), (int) firstViewport.y(), firstMv.z()),
                                    vector2D(elements[0].getVertexTexture()),
                                    n0
                            ),
                            new VertexTextureNormal(
                                    new Vector3D((int) secondViewport.x(), (int) secondViewport.y(), secondMv.z()),
                                    vector2D(elements[1].getVertexTexture()),
                                    n1
                            ),
                            new VertexTextureNormal(
                                    new Vector3D((int) thirdViewport.x(), (int) thirdViewport.y(), thirdMv.z()),
                                    vector2D(elements[2].getVertexTexture()),
                                    n2
                            ),
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

    private record VertexTextureNormal(Vector3D vertex, Vector2D texture, Vector3D normal) {
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
                              VertexTextureNormal t0,
                              VertexTextureNormal t1,
                              VertexTextureNormal t2,
                              Vector3D light,
                              Vector3D camera,
                              Function<Vector3D, Vector3D> toWorld) {
        if (t0.vertex().y() > t1.vertex.y()) {
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

        var t2y = min(max(0, (int) t2.vertex().y()), H - 1);
        var t1y = min(max(0, (int) t1.vertex().y()), H - 1);
        var t0y = min(max(0, (int) t0.vertex().y()), H - 1);

        var t2x = min(max(0, (int) t2.vertex().x()), W - 1);
        var t1x = min(max(0, (int) t1.vertex().x()), W - 1);
        var t0x = min(max(0, (int) t0.vertex().x()), W - 1);

        var degenerateTriangle = t0y == t1y && t0y == t2y;
        if (degenerateTriangle)
            return;

        var totalHeight = t2y - t0y;
        var d12y = t1.vertex().y() - t2.vertex().y();
        var d12x = t1.vertex().x() - t2.vertex().x();
        var d20y = t2.vertex().y() - t0.vertex().y();
        var d01y = t0.vertex().y() - t1.vertex().y();
        var d01x = t0.vertex().x() - t1.vertex().x();
        var d20x = t2.vertex().x() - t0.vertex().x();
        var triangleArea = -d20y * d12x + d12y * d20x;

        var t10 = t1.vertex().subtract(t0.vertex());
        var t20 = t2.vertex().subtract(t0.vertex());
        var isFirstVertexLeft = t10.x() * t20.y() - t10.y() * t20.x() <= 0;

        var iA = ColorUtils.toVector(iAPicker.getValue());
        var iD = ColorUtils.toVector(iDPicker.getValue());
        var iS = ColorUtils.toVector(iSPicker.getValue());
        var specularAlpha = (float) specularPower.getValue();

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

            var left = isFirstVertexLeft ? t1 : (isSecondHalf ? t0 : t2);
            var right = !isFirstVertexLeft ? t1 : (isSecondHalf ? t0 : t2);
            var angle = isSecondHalf ? t2 : t0;

            var leftWeight = (y - left.vertex().y()) / (angle.vertex().y() - left.vertex().y());
            var rightWeight = (y - right.vertex().y()) / (angle.vertex().y() - right.vertex().y());
            var nA = mix(left.normal(), angle.normal(), leftWeight);
            var nB = mix(right.normal(), angle.normal(), rightWeight);

            var leftW = new Vector3D(1f / left.vertex().z(), left.texture().u() / left.vertex().z(), left.texture().v() / left.vertex().z());
            var angleW = new Vector3D(1f / angle.vertex().z(), angle.texture().u() / angle.vertex().z(), angle.texture().v() / angle.vertex().z());
            var rightW = new Vector3D(1f / right.vertex().z(), right.texture().u() / right.vertex().z(), right.texture().v() / right.vertex().z());
            var tA = mix(leftW, angleW, leftWeight);
            var tB = mix(rightW, angleW, rightWeight);

            var u = ((y - t2.vertex().y()) * d12x + d12y * (t2.vertex().x() - Ax)) / triangleArea;
            var v = ((y - t0.vertex().y()) * d20x + d20y * (t0.vertex().x() - Ax)) / triangleArea;
            var w = ((y - t1.vertex().y()) * d01x + d01y * (t1.vertex().x() - Ax)) / triangleArea;
            var dU = -d12y / triangleArea;
            var dV = -d20y / triangleArea;
            var dW = -d01y / triangleArea;

            var dN = nB.subtract(nA).divide(Bx - Ax);
            var dT = tB.subtract(tA).divide(Bx - Ax);
            var normal = nA;
            var texture = tA;

            for (var x = Ax; x <= Bx; x++) {
                if (x != Ax) {
                    u += dU;
                    v += dV;
                    w += dW;
                }
                var isPixelOutsideOfTriangle = u < 0 || u > 1 || v < 0 || v > 1 || w < 0 || w > 1;
                if (isPixelOutsideOfTriangle)
                    continue;

                if (x != Ax) {
                    normal = normal.add(dN);
                    texture = texture.add(dT);
                }

                var z = t0.vertex().z() * u + t1.vertex().z() * v + t2.vertex().z() * w;
                var pixelWorld = toWorld.apply(new Vector3D(x, y, z));
                var textureCorrected = new Vector2D(texture.y() / texture.x(), texture.z() / texture.x());

                var N = normalMap == null
                        ? normal.normalize()
                        : ColorUtils.toVector(getTextureArgb(textureCorrected, normalMap))
                        .mul(2)
                        .subtract(new Vector3D(1, 1, 1));
                var L = light.subtract(pixelWorld).normalize();
                var V = camera.subtract(pixelWorld).normalize();
                var H = V.add(L).normalize();

                Vector3D color = null;
                if (isPbr) {
                    var mrao = mraoMap != null
                            ? ColorUtils.toVector(getTextureArgb(textureCorrected, mraoMap))
                            : new Vector3D(metallicSlider.getValue(), roughnessSlider.getValue(), aoSlider.getValue());

                    var metallic = mrao.x();
                    var roughness = mrao.y();
                    var ao = mrao.z();
                    var albedo = diffuseMap != null
                            ? ColorUtils.toVector(getTextureArgb(textureCorrected, diffuseMap)).pow(2.2f)
                            : ColorUtils.toVector(pbrAlbedoPicker.getValue());

                    //TODO: light color?
                    Vector3D lightColor = new Vector3D(1f, 1f, 1f);

                    // iterate by all light sources
                    var distance = light.subtract(pixelWorld).length();
                    var radiance = lightColor.divide(distance * distance);

                    var f0 = mix(new Vector3D(0.04f), albedo, metallic);
                    var f = PbrUtils.fresnelSchlick(max(H.dot(V), 0.0f), f0);
                    var kD = Vector3D.from(1).subtract(f).mul(1.0f - metallic);

                    var D = PbrUtils.distributionGGX(N, H, roughness);
                    var G = PbrUtils.geometrySmith(N, V, L, roughness);
                    var numerator = f.mul(D * G);
                    var denominator = 4.0f * max(N.dot(V), 0.0f) * max(N.dot(L), 0.0f) + 0.0001f;
                    var BRDF = numerator.divide(denominator);

                    var nDotL = max(N.dot(L), 0.0f);
                    var lambert = albedo.divide(Math.PI);
                    var lO = (kD.mul(lambert).add(BRDF)).mul(radiance).mul(nDotL);
                    // end light process

                    var ambient = Vector3D.from(0.03f).mul(albedo).mul(ao);
                    color = ambient.add(lO);
                    color = color
                            .divide(color.add(Vector3D.from(1)))
                            .pow(1.0f / 2.2f);
                } else if (isPhong) {
                    // START: Phong mixed with diffuse/emission maps.
                    Vector3D kA;
                    Vector3D kD;
                    if (diffuseMap != null) {
                        var argb = getTextureArgb(textureCorrected, diffuseMap);
                        var aD = ColorUtils.toVector4(argb);
                        kA = new Vector3D(aD.x(), aD.x(), aD.x());
                        kD = new Vector3D(aD.y(), aD.z(), aD.w());
                    } else {
                        kA = ColorUtils.toVector(kAPicker.getValue());
                        kD = ColorUtils.toVector(kDPicker.getValue());
                    }
                    var kS = emissionMap == null
                            ? ColorUtils.toVector(kSPicker.getValue())
                            : ColorUtils.toVector(getTextureArgb(textureCorrected, emissionMap));
                    var reflect = L.subtract(N.mul(2 * L.dot(N)));

                    var ambient = kA
                            .mul(iA);
                    var diffuse = kD
                            .mul(max(-N.dot(L), 0f))
                            .mul(iD);
                    var specular = kS
                            .mul((float) Math.pow(max(reflect.dot(V), 0f), specularAlpha))
                            .mul(iS);
                    color = ambient.add(diffuse).add(specular);
                    // END: Phong mixed with diffuse/emission maps.
                }

                var idx = x + y * W;
                if (zBuffer[idx] < z) {
                    drawPixel(buffer, x, y, ColorUtils.toArgbWithClamp(color));
                    zBuffer[idx] = z;
                }
            }
        }
    }

    private static int getTextureArgb(Vector2D texel, TextureMap map) {
        var textureX = min(max((int) (texel.u() * map.w()) - 1, 0), map.w() - 1);
        var textureY = min(max((int) ((1 - texel.v()) * map.h()) - 1, 0), map.h() - 1);
        return map.at(textureX, textureY);
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
