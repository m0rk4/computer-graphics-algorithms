module com.morka.cga.viewer {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.morka.cga.parser;
    requires static lombok;

    exports com.morka.cga.viewer;
    exports com.morka.cga.viewer.controller;
    opens com.morka.cga.viewer.controller to javafx.fxml;
}
