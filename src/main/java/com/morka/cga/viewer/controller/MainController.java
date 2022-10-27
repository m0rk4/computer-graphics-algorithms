package com.morka.cga.viewer.controller;

import com.morka.cga.parser.model.Face;
import com.morka.cga.parser.model.ObjGroup;
import com.morka.cga.parser.service.ObjFileParser;
import com.morka.cga.parser.service.ObjFileParserBuilder;
import com.morka.cga.viewer.buffer.WritableImageView;
import com.morka.cga.viewer.model.Matrix4D;
import com.morka.cga.viewer.model.Vector3D;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static java.util.Objects.nonNull;
import static javafx.beans.binding.Bindings.createObjectBinding;

public class MainController {

    private final ExecutorService threadPool;

    @FXML
    private BorderPane pane;

    private final Group group = new Group();

    private final ObjFileParser parser = ObjFileParserBuilder.build();

    private static final int W = 1280;
    private static final int H = 690;

    private WritableImageView currentBuffer;
    private WritableImageView secondBuffer;
    private static final int[] BACKGROUND_COLOR_ARRAY = new int[W * H];

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

    ObjectBinding<Matrix4D> modelMatrixBinding = createObjectBinding(
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

    private static final Matrix4D VIEWPORT_X_PROJECTION;

    static {
        VIEWPORT_X_PROJECTION = getViewportMatrix()
                .multiply(buildProjectionMatrix(W, H, 60.0f, 0.1f, 1000.f));
    }

    public MainController(ExecutorService executorService) {
        threadPool = executorService;
    }

    @FXML
    protected void initialize() {
        final WritableImageView node = new WritableImageView(W, H);
        pane.setCenter(group);
        addUiNode(node);
        currentBuffer = node;
        secondBuffer = new WritableImageView(W, H);

        modelMatrixBinding.addListener((__, ___, ____) -> repaint());
        viewMatrix.addListener((__, ___, ____) -> repaint());

        final var rotationStep = (float) Math.PI / 32.0f;
        final var translationStep = 10;
        final var scaleStep = 0.05f;

        // scale
        listenFor(KeyCode.P, () -> scaleProperty.set(scaleProperty.get() + scaleStep));
        listenFor(KeyCode.M, () -> scaleProperty.set(scaleProperty.get() - scaleStep));

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

        OBJECT_BINDING.addListener((__, ___, obj) -> {
            resetStates();
            if (nonNull(obj))
                repaint();
        });
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
        group.getChildren().add(node);
    }

    void removeUiNode(Node node) {
        group.getChildren().remove(node);
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
        final double posX = e.getX();
        final double posY = e.getX();
        float dx = (float) posX - mouseX;
        float dy = (float) posY - mouseY;
        degX += -dx / 5.f;
        degY += -dy / 8.f;
        mouseX = (float) posX;
        mouseY = (float) posY;
        xEyeProperty.set(degX);
        yEyeProperty.set(degY);
    }

    @SneakyThrows
    private void draw(ObjGroup group) {
        System.out.println("--\nDraw Started");
//        currentBuffer.setPixels(BACKGROUND_COLOR_ARRAY);

        final Face[] faces = group.faces();
        final int length = faces.length;
        final int seven = length / 7;
        final int last = length % 7;

        CompletableFuture[] tasks = new CompletableFuture[8];

        // using second clear buffer
        for (int i = 0; i < 7; i++)
            tasks[i] = CompletableFuture.runAsync(getTask(secondBuffer, faces, i * seven, (i + 1) * seven), threadPool);
        tasks[7] = CompletableFuture.runAsync(getTask(secondBuffer, faces, 7 * seven, 7 * seven + last), threadPool);

        CompletableFuture.allOf(tasks).join();
        // buffer is ready
        secondBuffer.updateBuffer();
        // show on ui
        addUiNode(secondBuffer);
        removeUiNode(currentBuffer);

        // clear old one
        currentBuffer.setPixels(BACKGROUND_COLOR_ARRAY);

        //swap bufs
        WritableImageView temp = currentBuffer;
        currentBuffer = secondBuffer;
        secondBuffer = temp;
//        currentBuffer.updateBuffer();

//        getTask(faces, 0, faces.length).run();
//        currentBuffer.updateBuffer();
        System.out.println("FINIshed");
    }

    Runnable getTask(WritableImageView buffer, Face[] faces, int start, int end) {
        return () -> {
            for (int j = start; j < end; j++) {
                final var face = faces[j];
                final var faceElements = face.faceElements();
                for (var i = 0; i < faceElements.length - 1; i++) {
                    final var matrix = VIEWPORT_X_PROJECTION
                            .multiply(viewMatrix.get())
                            .multiply(modelMatrixBinding.get());
                    final var from = matrix.multiply(faceElements[i].getVertex());
                    final var to = matrix.multiply(faceElements[i + 1].getVertex());
                    drawLine(buffer,
                            Math.round(from.getX() + W / 2.f),
                            Math.round(from.getY() + H / 2.f),
                            Math.round(to.getX() + W / 2.f),
                            Math.round(to.getY() + H / 2.f),
                            255 << 24
                    );
                }
            }
        };
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
        final var radius = 5.f;
        final Vector3D vector3D = eyeBinding.get();
        final var degX = vector3D.x();
        final var degY = vector3D.y();

        final var pos = new Vector3D((float) Math.cos(degX / 100.f) * radius, 0, (float) Math.sin(degX / 100.f) * radius);
        final var center = new Vector3D(0, 0, 0);
        var up = new Vector3D(0, -1, 0);

        final var viewray = center.subtract(pos).normalize();
        final var right = viewray.cross(up).normalize();
        up = right.cross(viewray);

        return new Matrix4D(new float[][]{
                {right.x(), up.x(), viewray.x(), 0},
                {right.y(), up.y(), viewray.y(), 0},
                {right.z(), up.z(), viewray.z(), 0},
                {-right.dot(pos), -up.dot(pos), viewray.dot(pos), 1}
        });
    }

    public static Matrix4D buildProjectionMatrix(float W, float H, float deg, float near, float far) {
        final var aspect = W / H;
        final var fov = (float) Math.toRadians(deg);
        final var tanHalfFov = (float) Math.tan(fov / 2.f);
        return new Matrix4D(new float[][]{
                {1.f / (aspect * tanHalfFov), 0f, 0f, 0f},
                {0f, 1.f / tanHalfFov, 0f, 0f},
                {0f, 0f, (far + near) / (near - far), -1f},
                {0f, 0f, (2f * far) * near / (near - far), 0f}
        });
    }

    private static Matrix4D getViewportMatrix() {
//        return new Matrix4D(new float[][]{
//                {W / 2.0f, 0, 0, W / 2.0f + W / 2.0f},
//                {0, H / 2.0f, 0, H / 2.0f + H / 2.0f},
//                {0, 0, 255 / 2.f, 255 / 2.f},
//                {0, 0, 0, 1}
//        });
        // TODO: replace
        return new Matrix4D(new float[][]{
                {1.f, 0.f, 0.f, 0.f},
                {0.f, 1.f, 0.f, 0.f},
                {0.f, 0.f, 1.f, 0.f},
                {0.f, 0.f, 0.f, 1.f},
        });
    }


    private void drawLine(WritableImageView imageView, int x1, int y1, int x2, int y2, int color) {
        var dx = Math.abs(x2 - x1);
        var sx = x1 < x2 ? 1 : -1;
        var dy = -Math.abs(y2 - y1);
        var sy = y1 < y2 ? 1 : -1;
        var err = dx + dy;
        while (true) {
            drawPixel(imageView, x1, y1, color);
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

    private void drawPixel(WritableImageView imageView, int x, int y, int argbColor) {
        if (x >= 0 && x < W && y >= 0 && y < H)
            imageView.setArgb(x, y, argbColor);
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
