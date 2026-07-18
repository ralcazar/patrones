package com.ejemplo.app.testsoporte;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Adjunta un {@code ListAppender} de Logback al logger de una clase para
 * capturar sus líneas formateadas en tests unitarios (Logback no es Spring:
 * permitido en {@code src/test} sin violar la regla de pureza de negocio, ya
 * que solo se usa desde tests de adaptadores de infraestructura).
 */
public final class CapturaLog implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public CapturaLog(Class<?> clase) {
        this.logger = (Logger) LoggerFactory.getLogger(clase);
        appender.start();
        logger.addAppender(appender);
    }

    /** Mensajes ya interpolados (placeholders {} sustituidos), en orden de emisión. */
    public List<String> mensajes() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
    }
}
