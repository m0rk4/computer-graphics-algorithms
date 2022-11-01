package com.morka.cga.viewer.controller;

import com.morka.cga.parser.model.ObjGroup;
import com.morka.cga.parser.service.ObjFileParser;
import com.morka.cga.parser.service.ObjFileParserBuilder;
import com.morka.cga.viewer.buffer.WritableImageView;
import com.morka.cga.viewer.model.Matrix4D;
import com.morka.cga.viewer.model.Vector3D;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import static com.morka.cga.viewer.utils.MatrixUtils.*;
import static java.util.Objects.nonNull;
import static javafx.beans.binding.Bindings.createObjectBinding;

public class MainController {

    private final ExecutorService executorService;
    private final ObjFileParser parser = ObjFileParserBuilder.build();

    @FXML
    private BorderPane pane;
    private static final Group GROUP = new Group();
    private static final ObservableList<Node> NODES = GROUP.getChildren();


    private static final int W = 1280;
    private static final int H = 690;
    private static final int ARGB_BLACK = 255 << 24;
    private static final int BUFFER_SIZE = 3;
    private static final int[] BACKGROUND_COLOR_ARRAY = new int[W * H];
    private final BlockingQueue<WritableImageView> fullBuffers = new ArrayBlockingQueue<>(BUFFER_SIZE);
    private final BlockingQueue<WritableImageView> emptyBuffers = new ArrayBlockingQueue<>(BUFFER_SIZE);
    private WritableImageView currentBuffer;

    private static final SimpleObjectProperty<ObjGroup> OBJECT_BINDING = new SimpleObjectProperty<>();
    IntegerProperty xTranslationProperty = new SimpleIntegerProperty(0);
    IntegerProperty yTranslationProperty = new SimpleIntegerProperty(0);
    IntegerProperty zTranslationProperty = new SimpleIntegerProperty(0);

    FloatProperty xRotationProperty = new SimpleFloatProperty(0);
    FloatProperty yRotationProperty = new SimpleFloatProperty(0);
    FloatProperty zRotationProperty = new SimpleFloatProperty(0);

    FloatProperty scaleProperty = new SimpleFloatProperty(1);

    FloatProperty xEyeProperty = new SimpleFloatProperty(0);
    FloatProperty yEyeProperty = new SimpleFloatProperty(0);
    FloatProperty zEyeProperty = new SimpleFloatProperty(0);

    FloatProperty radiusProperty = new SimpleFloatProperty(100);

    ObjectBinding<Vector3D> translationBinding = createObjectBinding(
            () -> new Vector3D(xTranslationProperty.get(), yTranslationProperty.get(), zTranslationProperty.get()),
            xTranslationProperty, yTranslationProperty, zTranslationProperty
    );

    ObjectBinding<Vector3D> scaleBinding = createObjectBinding(
            () -> new Vector3D(scaleProperty.get(), scaleProperty.get(), scaleProperty.get()),
            scaleProperty
    );

    ObjectBinding<Vector3D> rotationBinding = createObjectBinding(
            () -> new Vector3D(xRotationProperty.get(), yRotationProperty.get(), zRotationProperty.get()),
            xRotationProperty, yRotationProperty, zRotationProperty
    );

    ObjectBinding<Matrix4D> modelMatrix = createObjectBinding(
            () -> getModelMatrix(translationBinding.get(), scaleBinding.get(), rotationBinding.get()),
            translationBinding, scaleBinding, rotationBinding
    );

    ObjectBinding<Vector3D> eyeBinding = createObjectBinding(
            () -> getEyeVector(xEyeProperty.get(), yEyeProperty.get(), radiusProperty.get()),
            xEyeProperty, yEyeProperty, radiusProperty
    );

    ObjectBinding<Matrix4D> viewMatrix = createObjectBinding(
            () -> getViewMatrix(eyeBinding.get()),
            eyeBinding
    );

    private static final Matrix4D PROJECTION_MATRIX = buildProjectionMatrix(W, H, 60f, 0.1f, 100.f);
    private static final Matrix4D VIEWPORT_MATRIX = buildViewportMatrix(W, H);

    private boolean mouseDragging = false;

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

    public MainController(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @FXML
    protected void initialize() {
        pane.setCenter(GROUP);
        for (int i = 0; i < BUFFER_SIZE; i++)
            emptyBuffers.add(new WritableImageView(W, H));

        final var rotationStep = (float) Math.PI / 32.0f;
        final var translationStep = 1;
        final var scaleStep = 0.05f;

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

        OBJECT_BINDING.addListener((__, ___, obj) -> {
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
            double dy = e.getDeltaY();
            if (Double.compare(dy, 0.0) == 0)
                return;
            radiusProperty.set((float) (radiusProperty.get() - dy / 20));
        });
    }

    public void onUpdate() throws InterruptedException {
        if (fullBuffers.isEmpty())
            return;

        var buffer = fullBuffers.take();

        addUiNode(buffer);

        if (currentBuffer != null) {
            removeUiNode(currentBuffer);
            emptyBuffers.add(currentBuffer);
        }

        buffer.updateBuffer();

        currentBuffer = buffer;
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
        zEyeProperty.set(0);
    }

    void addUiNode(Node node) {
        NODES.add(node);
    }

    void removeUiNode(Node node) {
        NODES.remove(node);
    }

    private void repaint() {
        draw(OBJECT_BINDING.get());
    }

    private void listenFor(KeyCode key, Runnable item) {
        KEYS.get(key).addListener((__, ___, val) -> {
            if (val)
                item.run();
        });
    }

    private void listenFor(KeyCode firstKey, KeyCode secondKey, Runnable item) {
        final var first = KEYS.get(firstKey);
        final var second = KEYS.get(secondKey);
        final var and = first.and(second);
        first.addListener((__, ___, ____) -> and.get());
        and.addListener((__, ___, val) -> {
            if (val)
                item.run();
        });
    }

    public void onKeyPressed(KeyEvent e) {
        final var code = e.getCode();
        if (KEYS.containsKey(code))
            KEYS.get(code).set(true);
    }

    public void onKeyReleased(KeyEvent e) {
        final var code = e.getCode();
        if (KEYS.containsKey(code))
            KEYS.get(code).set(false);
    }

    float lastPositionX;
    float lastPositionY;
    float sensibility = 0.01f;
    float lastTheta = 0;
    float lastPhi = 0;


    public void onMouseDragged(MouseEvent e) {
        if (!mouseDragging)
            return;

        xEyeProperty.set((float) e.getX());
        yEyeProperty.set((float) e.getY());
    }

    private Vector3D getEyeVector(float posX, float posY, float radius) {
        final var dx = posX - lastPositionX;
        final var dy = posY - lastPositionY;

        lastPositionX = posX;
        lastPositionY = posY;

        var theta = lastTheta + sensibility * dy;
        if (theta < Math.PI / -2)
            theta = (float) Math.PI / -2;
        else if (theta > Math.PI / 2)
            theta = (float) Math.PI / 2;

        final var phi = (lastPhi + sensibility * dx * -1) % (Math.PI * 2);

        lastTheta = theta;
        lastPhi = (float) phi;

        final var eyeX = (float) (radius * Math.cos(theta) * Math.cos(phi));
        float eyeY = (float) (radius * Math.sin(theta));
        float eyeZ = (float) (radius * Math.cos(theta) * Math.sin(phi));
        return new Vector3D(eyeX, eyeY, eyeZ);
    }


    private void draw(ObjGroup group) {
        executorService.submit(() -> {
            try {
                final var buffer = emptyBuffers.take();
                buffer.setPixels(BACKGROUND_COLOR_ARRAY);
                group.lines().parallelStream().forEach(line -> {
                    final var mvp = PROJECTION_MATRIX.multiply(viewMatrix.get()).multiply(modelMatrix.get());
                    final var fromTransformedNormalized = mvp.multiplyWithWNormalization(line.from());
                    final var toTransformedNormalized = mvp.multiplyWithWNormalization(line.to());
                    final var from = VIEWPORT_MATRIX.multiply(fromTransformedNormalized);
                    final var to = VIEWPORT_MATRIX.multiply(toTransformedNormalized);
                    drawLine(buffer,
                            Math.round(from.getX()),
                            Math.round(from.getY()),
                            Math.round(to.getX()),
                            Math.round(to.getY()),
                            ARGB_BLACK);
                });
                fullBuffers.add(buffer);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
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

    @FXML
    void onFileOpen() {
        final var fileChooser = new FileChooser();
        final var filter = new FileChooser.ExtensionFilter("Wavefont OBJ (*.obj)", "*.obj");
        fileChooser.getExtensionFilters().add(filter);
        final var file = fileChooser.showOpenDialog(null);
        if (nonNull(file))
            OBJECT_BINDING.set(parser.parse(file));
    }
}
