package com.ejemplo.app.carga.logging;

import java.nio.file.Path;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;

/**
 * Configura Logback A MANO (consola + fichero {@code pods.log}), una línea
 * por evento con timestamp ISO-8601 delante del texto ya estructurado que
 * escriben {@code AdaptadorObservadorLog}/{@code AdaptadorTicketsLog}/
 * {@code ControladorTramitaciones}/{@code ConsumidorRespuestaSecundaria2}
 * (formato {@code evento=<nombre> ... pod=<valor>}, ver catálogo en
 * {@code src/pruebaCarga/resources/escenarios/README.md}).
 *
 * <p>El timestamp NO es una clave {@code clave=valor} más (esos mensajes ya
 * están fijados en producción y esta fase no la toca): es un prefijo de línea
 * que añade Logback, pensado para que el analizador de la fase 3 pueda
 * calcular throughput por minuto sin depender de que cada evento lleve su
 * propio campo de tiempo.
 *
 * <p>Se invoca UNA vez desde {@code LanzadorPruebaCarga}, antes de arrancar
 * ningún pod, y se combina con desactivar el {@code LoggingSystem} de Spring
 * Boot ({@code -Dorg.springframework.boot.logging.LoggingSystem=none}): como
 * los N {@code SpringApplication} comparten JVM (y por tanto el mismo
 * {@link LoggerContext} estático de Logback), si no se desactiva, cada pod
 * reinicializaría el logging al arrancar y se perdería esta configuración.
 */
public final class ConfiguradorLogging {

    private static final String PATRON = "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %msg%n";

    private ConfiguradorLogging() {
    }

    public static void configurar(Path ficheroLog) {
        var contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger raiz = contexto.getLogger(Logger.ROOT_LOGGER_NAME);
        raiz.detachAndStopAllAppenders();
        raiz.setLevel(Level.INFO);

        raiz.addAppender(consola(contexto));
        raiz.addAppender(fichero(contexto, ficheroLog));
    }

    private static ConsoleAppender<ILoggingEvent> consola(LoggerContext contexto) {
        var encoder = encoder(contexto);
        var appender = new ConsoleAppender<ILoggingEvent>();
        appender.setContext(contexto);
        appender.setName("consola-carga");
        appender.setEncoder(encoder);
        appender.start();
        return appender;
    }

    private static FileAppender<ILoggingEvent> fichero(LoggerContext contexto, Path ficheroLog) {
        var encoder = encoder(contexto);
        var appender = new FileAppender<ILoggingEvent>();
        appender.setContext(contexto);
        appender.setName("fichero-carga");
        appender.setFile(ficheroLog.toString());
        appender.setAppend(true);
        appender.setEncoder(encoder);
        appender.start();
        return appender;
    }

    private static PatternLayoutEncoder encoder(LoggerContext contexto) {
        var encoder = new PatternLayoutEncoder();
        encoder.setContext(contexto);
        encoder.setPattern(PATRON);
        encoder.start();
        return encoder;
    }
}
