package com.morka.cga.viewer.controller;

import com.morka.cga.parser.model.ObjGroup;
import com.morka.cga.parser.service.ObjFileParser;
import com.morka.cga.parser.service.ObjFileParserBuilder;
import com.morka.cga.viewer.buffer.WritableImageView;
import com.morka.cga.viewer.model.Matrix4D;
import com.morka.cga.viewer.model.Vector3D;
import javafx.animation.AnimationTimer;
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

import static java.util.Objects.nonNull;
import static javafx.beans.binding.Bindings.createObjectBinding;

public class MainController {

    private final ExecutorService executorService;

    @FXML
    private BorderPane pane;

    private static final Group GROUP = new Group();
    private static final ObservableList<Node> NODES = GROUP.getChildren();

    private final ObjFileParser parser = ObjFileParserBuilder.build();

    private static final int W = 1280;
    private static final int H = 690;
    private static final int ARGB_BLACK = 255 << 24;
    private static final int BUFFER_SIZE = 3;
    private static final int[] BACKGROUND_COLOR_ARRAY = new int[W * H];
    private final BlockingQueue<WritableImageView> fullBuffers = new ArrayBlockingQueue<>(BUFFER_SIZE);
    private final BlockingQueue<WritableImageView> emptyBuffers = new ArrayBlockingQueue<>(BUFFER_SIZE);
    private WritableImageView currentBuffer;
    private long lastFrameTimestampInNanoseconds;


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
            this::getModelMatrix,
            translationBinding, scaleBinding, rotationBinding
    );

    ObjectBinding<Vector3D> eyeBinding = createObjectBinding(
            () -> new Vector3D(xEyeProperty.get(), yEyeProperty.get(), zEyeProperty.get()),
            xEyeProperty, yEyeProperty, zEyeProperty
    );

    ObjectBinding<Matrix4D> viewMatrix = createObjectBinding(
            this::getViewMatrix,
            eyeBinding
    );

    private static final Matrix4D PROJECTION = buildProjectionMatrix(W, H, 55.f, 0.1f, 100.f);
    private static final Matrix4D VIEWPORT = buildViewportMatrix(W, H);

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

        put(KeyCode.E, new SimpleBooleanProperty(false));
        put(KeyCode.R, new SimpleBooleanProperty(false));
        put(KeyCode.T, new SimpleBooleanProperty(false));
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

        // scale
        listenFor(KeyCode.P, () -> scaleProperty.set(scaleProperty.get() + scaleStep));
        listenFor(KeyCode.M, () -> scaleProperty.set(Math.max(0.05f, scaleProperty.get() - scaleStep)));

        listenFor(KeyCode.E, KeyCode.UP, () -> xEyeProperty.set(xEyeProperty.get() + 5));
        listenFor(KeyCode.R, KeyCode.UP, () -> yEyeProperty.set(yEyeProperty.get() + 5));
        listenFor(KeyCode.T, KeyCode.UP, () -> zEyeProperty.set(zEyeProperty.get() + 5));
        listenFor(KeyCode.E, KeyCode.DOWN, () -> xEyeProperty.set(xEyeProperty.get() - 5));
        listenFor(KeyCode.R, KeyCode.DOWN, () -> yEyeProperty.set(yEyeProperty.get() - 5));
        listenFor(KeyCode.T, KeyCode.DOWN, () -> zEyeProperty.set(zEyeProperty.get() - 5));

        // rotation
        listenFor(KeyCode.X, KeyCode.RIGHT, () -> xRotationProperty.set(xRotationProperty.get() + rotationStep));
        listenFor(KeyCode.X, KeyCode.LEFT, () -> xRotationProperty.set(xRotationProperty.get() - rotationStep));
        listenFor(KeyCode.Y, KeyCode.RIGHT, () -> yRotationProperty.set(yRotationProperty.get() + rotationStep));
        listenFor(KeyCode.Y, KeyCode.LEFT, () -> yRotationProperty.set(yRotationProperty.get() - rotationStep));
        listenFor(KeyCode.Z, KeyCode.RIGHT, () -> zRotationProperty.set(zRotationProperty.get() + rotationStep));
        listenFor(KeyCode.Z, KeyCode.LEFT, () -> zRotationProperty.set(zRotationProperty.get() - rotationStep));

        // translate
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
            mouseX = (float) e.getX();
            mouseY = (float) e.getY();
            mouseDragging = true;
        });
        pane.setOnMouseDragged(this::onMouseDragged);
        pane.setOnMouseReleased(__ -> mouseDragging = false);

        final var animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                try {
                    if (now - lastFrameTimestampInNanoseconds < 16666666)
                        return;

                    if (fullBuffers.isEmpty())
                        return;

//                     skip when less than ~~16ms ~~60 fps

                    lastFrameTimestampInNanoseconds = now;

                    var buffer = fullBuffers.take();

                    addUiNode(buffer);

                    if (currentBuffer != null) {
                        removeUiNode(currentBuffer);
                        emptyBuffers.add(currentBuffer);
                    }

                    buffer.updateBuffer();

                    currentBuffer = buffer;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        animationTimer.start();
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
        if (KEYS.containsKey(code)) {
            KEYS.get(code).set(true);
        }
    }

    public void onKeyReleased(KeyEvent e) {
        final var code = e.getCode();
        if (KEYS.containsKey(code)) {
            KEYS.get(code).set(false);
        }
    }

    float mouseX;
    float mouseY;
    float degX = 0;
    float degY = 0;

    public void onMouseDragged(MouseEvent e) {
        if (mouseDragging) {
            final double posX = e.getX();
            final double posY = e.getX();
            float dx = (float) posX - mouseX;
            float dy = (float) posY - mouseY;
            degX += -dx / 5.f;
            degY += -dy / 8.f;
            xEyeProperty.set(degX);
            yEyeProperty.set(degY);
        }
    }

    long last;

    private void draw(ObjGroup group) {
        if (System.nanoTime() - last < 16666666) return;
        last = System.nanoTime();

        executorService.submit(() -> {
            try {
                final var buffer = emptyBuffers.take();
                buffer.setPixels(BACKGROUND_COLOR_ARRAY);
                long st = System.nanoTime();
                group.lines().parallelStream().forEach(line -> {
                    final var mvp = PROJECTION.multiply(viewMatrix.get()).multiply(modelMatrix.get());
                    final var fromNormalized = mvp.multiplyWithWNormalization(line.from());
                    final var toNormalized = mvp.multiplyWithWNormalization(line.to());
                    final var fromFinal = VIEWPORT.multiply(fromNormalized);
                    final var toFinal = VIEWPORT.multiply(toNormalized);
                    drawLine(buffer,
                            Math.round(fromFinal.getX()),
                            Math.round(fromFinal.getY()),
                            Math.round(toFinal.getX()),
                            Math.round(toFinal.getY()),
                            ARGB_BLACK
                    );
                });
                System.out.println((System.nanoTime() - st) / 1000000.f);
                fullBuffers.add(buffer);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Matrix4D getModelMatrix() {
        final var rotation = rotationBinding.get();
        final var translationMatrix = getTranslationMatrix(translationBinding.get());
        final var scaleMatrix = getScaleMatrix(scaleBinding.get());
        final var xRotationMatrix = getXRotationMatrix(rotation);
        final var yRotationMatrix = getYRotationMatrix(rotation);
        final var zRotationMatrix = getZRotationMatrix(rotation);
        return translationMatrix.multiply(xRotationMatrix).multiply(yRotationMatrix).multiply(zRotationMatrix).multiply(scaleMatrix);
    }

    public Matrix4D getXRotationMatrix(Vector3D vector) {
        float[][] matrix = new float[][]{
                {1f, 0, 0, 0},
                {0, (float) Math.cos(vector.x()), (float) (-1 * Math.sin(vector.x())), 0},
                {0, (float) Math.sin(vector.x()), (float) Math.cos(vector.x()), 0},
                {0, 0, 0, 1f}
        };
        return new Matrix4D(matrix);
    }

    public Matrix4D getYRotationMatrix(Vector3D vector) {
        float[][] matrix = new float[][]{
                {(float) Math.cos(vector.y()), 0, (float) Math.sin(vector.y()), 0},
                {0, 1f, 0, 0},
                {(float) (-1 * Math.sin(vector.y())), 0, (float) Math.cos(vector.y()), 0},
                {0, 0, 0, 1f}
        };
        return new Matrix4D(matrix);
    }

    public Matrix4D getZRotationMatrix(Vector3D vector) {
        float[][] matrix = new float[][]{
                {(float) Math.cos(vector.z()), (float) (-1 * Math.sin(vector.z())), 0, 0},
                {(float) Math.sin(vector.z()), (float) Math.cos(vector.z()), 0, 0},
                {0, 0, 1f, 0},
                {0, 0, 0, 1f}
        };
        return new Matrix4D(matrix);
    }

    private Matrix4D getTranslationMatrix(Vector3D translation) {
        return new Matrix4D(new float[][]{
                {1, 0, 0, translation.x()},
                {0, 1, 0, translation.y()},
                {0, 0, 1, translation.z()},
                {0, 0, 0, 1}
        });
    }

    private Matrix4D getScaleMatrix(Vector3D scale) {
        return new Matrix4D(new float[][]{
                {scale.x(), 0, 0, 0},
                {0, scale.y(), 0, 0},
                {0, 0, scale.z(), 0},
                {0, 0, 0, 1}
        });
    }

    private Matrix4D getViewMatrix() {
        final var radius = 100.f;
        final var vector3D = eyeBinding.get();
        final var eye = new Vector3D((float) Math.cos(degX / 100.f) * radius, 0, (float) Math.sin(degX / 100.f) * radius);
        final var target = new Vector3D(0, 0, 0);
        final var up = new Vector3D(0, -1, 0);

        final var zAxis = eye.subtract(target).normalize();
        final var xAxis = up.cross(zAxis).normalize();
        final var yAxis = xAxis.cross(zAxis);

        return new Matrix4D(new float[][]{
                {xAxis.x(), xAxis.y(), xAxis.z(), -xAxis.dot(eye)},
                {yAxis.x(), yAxis.y(), yAxis.z(), -yAxis.dot(eye)},
                {zAxis.x(), zAxis.y(), zAxis.z(), -zAxis.dot(eye)},
                {0.f, 0.f, 0.f, 1.f}
        });
    }

    public static Matrix4D buildProjectionMatrix(float W, float H, float deg, float near, float far) {
        final var aspect = W / H;
        final var fov = (float) Math.toRadians(deg);
        final var invTanHalfFov = (float) (1.f / Math.tan(fov / 2.f));
        final var invRange = 1.f / (near - far);
        return new Matrix4D(new float[][]{
                {invTanHalfFov / aspect, 0.f, 0.f, 0.f},
                {0.f, invTanHalfFov, 0.f, 0.f},
                {0.f, 0.f, far * invRange, far * near * invRange},
                {0.f, 0.f, -1.f, 0.f}
        });
    }

    private static Matrix4D buildViewportMatrix(float width, float height) {
        return new Matrix4D(new float[][]{
                {width / 2.f, 0.f, 0.f, width / 2.f},
                {0.f, -height / 2.f, 0.f, height / 2.f},
                {0.f, 0.f, 1.f, 0.f},
                {0.f, 0.f, 0.f, 1.f}
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
            if (x1 == x2 && y1 == y2) {
                return;
            }
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
