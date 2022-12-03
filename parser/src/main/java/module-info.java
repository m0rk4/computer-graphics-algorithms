import com.morka.cga.parser.service.ObjFileParser;
import com.morka.cga.parser.service.TextureMapParser;
import com.morka.cga.parser.service.impl.ObjFileParserImpl;
import com.morka.cga.parser.service.impl.TextureMapParserImpl;

module com.morka.cga.parser {
    requires static lombok;
    requires java.logging;
    requires java.desktop;
    requires org.jetbrains.annotations;

    exports com.morka.cga.parser.model;
    exports com.morka.cga.parser.service;
    exports com.morka.cga.parser.exception;

    provides ObjFileParser with ObjFileParserImpl;
    provides TextureMapParser with TextureMapParserImpl;
}
