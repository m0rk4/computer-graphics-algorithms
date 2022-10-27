import com.morka.cga.parser.service.ObjFileParser;
import com.morka.cga.parser.service.impl.ObjFileParserImpl;

module com.morka.cga.parser {
    requires static lombok;
    requires java.logging;
    requires org.jetbrains.annotations;

    exports com.morka.cga.parser.model;
    exports com.morka.cga.parser.service;

    provides ObjFileParser with ObjFileParserImpl;
}
