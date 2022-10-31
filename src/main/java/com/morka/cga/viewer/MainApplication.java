package com.morka.cga.viewer;

import com.morka.cga.viewer.controller.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainApplication extends Application {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(Runtime.getRuntime().availableProcessors()),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    private static final int THREAD_POOL_TERMINATION_TIME_IN_SECONDS = 60;

    private static final String FXML_VIEW_NAME = "main-view.fxml";
    private static final String APP_TITLE = ".OBJ Viewer";

    @Override
    public void init() {
        Runtime.getRuntime().addShutdownHook(new Thread(MainApplication::destroyThreadPool));
    }

    @Override
    public void start(Stage stage) throws IOException {
        final var fxmlLoader = new FXMLLoader(MainApplication.class.getResource(FXML_VIEW_NAME));
        final var controller = new MainController(THREAD_POOL);
        fxmlLoader.setControllerFactory(__ -> controller);
        final var scene = new Scene(fxmlLoader.load(), 1280, 720);
        scene.setOnKeyPressed(controller::onKeyPressed);
        scene.setOnKeyReleased(controller::onKeyReleased);
        stage.setTitle(APP_TITLE);
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        destroyThreadPool();
    }

    private static void destroyThreadPool() {
        THREAD_POOL.shutdown();
        try {
            if (!THREAD_POOL.awaitTermination(THREAD_POOL_TERMINATION_TIME_IN_SECONDS, TimeUnit.SECONDS))
                THREAD_POOL.shutdownNow();
        } catch (InterruptedException e) {
            THREAD_POOL.shutdownNow();
        }
    }
}
