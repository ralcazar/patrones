package com.ejemplo.app.carga;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion;
import com.ejemplo.app.carga.analisis.AnalizadorEjecucion;
import com.ejemplo.app.carga.esquema.InicializadorEsquemaH2;
import com.ejemplo.app.carga.logging.ConfiguradorLogging;

/**
 * Punto de entrada del harness de pruebas de carga multi-pod (ver
 * {@code plan-pruebas-carga.md} en la raíz del repo, fase 2).
 *
 * <p>Secuencia completa de una ejecución:
 * <ol>
 *   <li>Carga y valida el escenario ({@link EscenarioCarga}).</li>
 *   <li>Crea {@code build/pruebaCarga/<nombre>-<timestamp>/} y configura el
 *       log (consola + {@code pods.log}, ver {@link ConfiguradorLogging}).</li>
 *   <li>Desactiva el {@code LoggingSystem} de Spring Boot (los N
 *       {@code SpringApplication} de los pods, en la misma JVM, no deben
 *       reinicializar/pisar la configuración de logging del paso anterior).</li>
 *   <li>Inicializa el esquema H2 ejecutando {@code order-manager/db/*.sql}
 *       (ver {@link InicializadorEsquemaH2}) ANTES de arrancar ningún pod —
 *       desviación documentada respecto al plan (que sugería hacerlo "desde
 *       el contexto del pod 0"): al no depender de Spring, no hace falta
 *       ninguna coordinación entre pods para evitar la carrera.</li>
 *   <li>Arranca los N pods (perfil {@code carga}, sin servidor web, todos
 *       contra la misma H2 en fichero).</li>
 *   <li>Inyecta tramitaciones desde el pod 0 al ritmo configurado
 *       ({@link InyectorTramitaciones}) hasta agotar el total o la
 *       duración.</li>
 *   <li>Espera el drenaje (que no queden órdenes vivas) hasta
 *       {@code drenaje-maximo}.</li>
 *   <li>Cierra los contextos ordenadamente, invoca el analizador determinista
 *       de la fase 3 ({@link #invocarAnalizadorSiExiste}) y termina con su
 *       veredicto como exit code (0 BUENO, 1 MALO).</li>
 * </ol>
 */
public final class LanzadorPruebaCarga {

    private static final Logger log = LoggerFactory.getLogger(LanzadorPruebaCarga.class);
    private static final DateTimeFormatter FORMATO_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(ZoneOffset.UTC);
    private static final long INTERVALO_SONDEO_DRENAJE_MS = 2000;

    private LanzadorPruebaCarga() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Se esperaba exactamente un argumento (nombre del escenario), recibidos: " + args.length);
        }
        var escenario = EscenarioCarga.cargar(args[0]);

        Path directorioProyecto = Path.of(System.getProperty("pruebaCarga.directorioProyecto", System.getProperty("user.dir")));
        Path carpetaSalida = directorioProyecto.resolve("build/pruebaCarga/" + escenario.nombre() + "-"
                + FORMATO_TIMESTAMP.format(Instant.now()));
        crearDirectorio(carpetaSalida);

        ConfiguradorLogging.configurar(carpetaSalida.resolve("pods.log"));
        // Los pods, en la misma JVM, comparten el LoggerContext estático de
        // Logback: si Spring Boot reinicializase el logging al arrancar cada
        // SpringApplication, se perdería la configuración anterior. Se
        // desactiva su LoggingSystem para que la configuración manual de
        // arriba sobreviva a los N arranques.
        System.setProperty("org.springframework.boot.logging.LoggingSystem", "none");

        log.info("evento=prueba_carga_iniciada escenario={} pods={} carpeta={} pod=lanzador",
                escenario.nombre(), escenario.pods(), carpetaSalida);

        // DB_CLOSE_DELAY=-1: sin esto, H2 en fichero cierra la BBDD entera en
        // cuanto el recuento de conexiones abiertas de TODO el proceso cae a
        // cero (no solo las de un pod), lo que con N pods x su propio pool
        // Hikari puede pasar en cualquier instante bajo contención y tumbar a
        // los demás pods a mitad de ejecución (visto en un run de
        // humo-contencion: JDBCConnectionException en cascada en los 8 pods).
        // Con -1 la BBDD sobrevive mientras quede al menos un pod vivo.
        String urlJdbc = "jdbc:h2:file:" + carpetaSalida.resolve("bbdd") + ";MODE=Oracle;DB_CLOSE_DELAY=-1";
        InicializadorEsquemaH2.inicializar(urlJdbc, directorioProyecto.resolve("db"));

        var contextos = new ConfigurableApplicationContext[escenario.pods()];
        try {
            for (int i = 0; i < escenario.pods(); i++) {
                contextos[i] = arrancarPod(escenario, i, urlJdbc);
            }
        } catch (RuntimeException e) {
            log.error("evento=arranque_fallido pod=lanzador error={}", e.getMessage(), e);
            for (var contextoArrancado : contextos) {
                if (contextoArrancado != null && contextoArrancado.isActive()) {
                    contextoArrancado.close();
                }
            }
            System.err.println("[pruebaCarga] Fallo arrancando los pods: " + e.getMessage());
            System.exit(1);
            return; // inalcanzable: deja explícito que no se sigue tras el exit
        }

        int inyectadas = ejecutarInyeccionYDrenaje(escenario, contextos[0], urlJdbc);

        log.info("evento=prueba_carga_cerrando_contextos pod=lanzador");
        for (int i = contextos.length - 1; i >= 0; i--) {
            contextos[i].close();
        }

        int codigoSalida = invocarAnalizadorSiExiste(carpetaSalida, escenario);
        log.info("evento=prueba_carga_finalizada escenario={} inyectadas={} pod=lanzador", escenario.nombre(), inyectadas);
        System.exit(codigoSalida);
    }

    private static ConfigurableApplicationContext arrancarPod(EscenarioCarga escenario, int indicePod, String urlJdbc) {
        var contextoPod = ContextoPod.de(escenario, indicePod);
        var motor = escenario.motor();

        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("spring.application.name", "pod-" + indicePod);
        propiedades.put("spring.main.web-application-type", "none");
        propiedades.put("spring.jmx.enabled", "false");
        propiedades.put("spring.datasource.url", urlJdbc);
        propiedades.put("spring.datasource.driver-class-name", "org.h2.Driver");
        propiedades.put("spring.datasource.username", "sa");
        propiedades.put("spring.datasource.password", "");
        propiedades.put("spring.jpa.hibernate.ddl-auto", "none");
        propiedades.put("ordermanager.pod", String.valueOf(indicePod));
        propiedades.put("ordermanager.planificador.intervalo-ms", motor.planificador().intervaloMs());
        propiedades.put("ordermanager.planificador.trabajadores", motor.planificador().trabajadores());
        propiedades.put("ordermanager.planificador.lote", motor.planificador().lote());
        if (motor.lease() != null) {
            propiedades.put("ordermanager.lease", motor.lease().toString());
        }
        if (motor.cron().tickets() != null) {
            propiedades.put("ordermanager.tickets.cron", motor.cron().tickets());
        }
        if (motor.cron().limpieza() != null) {
            propiedades.put("ordermanager.limpieza.cron", motor.cron().limpieza());
        }
        if (motor.cron().purga() != null) {
            propiedades.put("sagas.purga-datos-negocio.cron", motor.cron().purga());
        }

        // NOTA (corregido tras la primera prueba de humo, que arrancó Tomcat
        // en todos los pods): SpringApplicationBuilder.properties(Map) las
        // registra como "defaultProperties" (la prioridad MÁS BAJA de
        // todas), así que NO pueden pisar spring.datasource.url de
        // application.yml (el de producción, Oracle). Para que de verdad
        // ganen, se añaden a mano como el PRIMER PropertySource del entorno,
        // en el mismo ApplicationContextInitializer que registra
        // ContextoPod. PERO eso vale solo para propiedades que se leen
        // durante la autoconfiguración normal (datasource, JPA, etc.):
        // "spring.main.web-application-type" NO es una de ellas. Spring Boot
        // decide el tipo de ApplicationContext (con o sin servidor web) y lo
        // CREA antes de aplicar los ApplicationContextInitializer (que
        // corren ya sobre un contexto que, si spring-boot-starter-web está
        // en el classpath como aquí, ya nació de tipo SERVLET) — por eso el
        // primer intento montó Tomcat en todos los pods pese a la property.
        // La única forma fiable de forzarlo es en el propio
        // SpringApplication/Builder ANTES de run(), con .web(...).
        ApplicationContextInitializer<ConfigurableApplicationContext> inicializadorPod = ctx -> {
            ctx.getEnvironment().getPropertySources()
                    .addFirst(new org.springframework.core.env.MapPropertySource("pruebaCarga-pod-" + indicePod, propiedades));
            if (!(ctx instanceof GenericApplicationContext contextoGenerico)) {
                throw new IllegalStateException("Se esperaba GenericApplicationContext, era: " + ctx.getClass());
            }
            contextoGenerico.registerBean(ContextoPod.class, () -> contextoPod);
        };

        log.info("evento=pod_arrancando pod={}", indicePod);
        ConfigurableApplicationContext contexto;
        try {
            contexto = new SpringApplicationBuilder(AplicacionPruebaCarga.class)
                    .web(WebApplicationType.NONE)
                    .profiles("carga")
                    .initializers(inicializadorPod)
                    .run();
        } catch (RuntimeException e) {
            throw new IllegalStateException("El pod " + indicePod + " no pudo arrancar: " + e.getMessage(), e);
        }
        log.info("evento=pod_arrancado pod={}", indicePod);
        return contexto;
    }

    private static int ejecutarInyeccionYDrenaje(EscenarioCarga escenario, ConfigurableApplicationContext contextoPod0,
            String urlJdbc) {
        var contextoPod0Datos = contextoPod0.getBean(ContextoPod.class);
        var casoUso = contextoPod0.getBean(CasoUsoIniciarTramitacion.class);

        ScheduledExecutorService schedulerInyector = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var hilo = new Thread(runnable, "inyector-tramitaciones");
            hilo.setDaemon(true);
            return hilo;
        });
        var inyector = new InyectorTramitaciones(casoUso, contextoPod0Datos, schedulerInyector);
        try {
            inyector.ejecutarHastaAgotar();
        } finally {
            schedulerInyector.shutdownNow();
        }

        esperarDrenaje(urlJdbc, escenario.drenajeMaximo().toMillis());
        return inyector.inyectadas();
    }

    private static void esperarDrenaje(String urlJdbc, long drenajeMaximoMs) {
        long limite = System.currentTimeMillis() + drenajeMaximoMs;
        long vivas = contarOrdenesVivas(urlJdbc);
        while (vivas > 0 && System.currentTimeMillis() < limite) {
            log.info("evento=drenaje_en_progreso ordenes_vivas={} pod=lanzador", vivas);
            try {
                Thread.sleep(INTERVALO_SONDEO_DRENAJE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            vivas = contarOrdenesVivas(urlJdbc);
        }
        if (vivas > 0) {
            log.warn("evento=drenaje_incompleto ordenes_vivas={} pod=lanzador", vivas);
        } else {
            log.info("evento=drenaje_completo pod=lanzador");
        }
    }

    private static long contarOrdenesVivas(String urlJdbc) {
        try (Connection conexion = DriverManager.getConnection(urlJdbc, "sa", "");
                Statement sentencia = conexion.createStatement();
                ResultSet filas = sentencia.executeQuery("SELECT COUNT(*) FROM orden WHERE completada_en IS NULL")) {
            filas.next();
            return filas.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar las órdenes vivas en " + urlJdbc, e);
        }
    }

    /**
     * Invoca el analizador determinista de la fase 3 sobre {@code carpetaSalida}
     * ({@link AnalizadorEjecucion}, genera {@code informe.md} a partir de
     * {@code pods.log} + la H2) y devuelve su veredicto (0 invariantes OK, 1
     * violadas) como código de salida de este lanzador. Se le pasa el lease
     * REAL usado por los pods de esta ejecución (el del escenario si lo
     * sobreescribe, si no el mismo por defecto de {@code application.yml}
     * que {@link AnalizadorEjecucion#LEASE_POR_DEFECTO} documenta): la
     * carpeta de salida no conserva el .yml del escenario, así que el
     * invariante de solapes de ejecución necesita este dato para distinguir
     * un takeover legítimo (lease vencido) de un solape real.
     */
    private static int invocarAnalizadorSiExiste(Path carpetaSalida, EscenarioCarga escenario) {
        var lease = escenario.motor().lease() != null ? escenario.motor().lease() : AnalizadorEjecucion.LEASE_POR_DEFECTO;
        log.info("evento=analizador_iniciado carpeta={} lease={} pod=lanzador", carpetaSalida, lease);
        return AnalizadorEjecucion.analizar(carpetaSalida, lease);
    }

    private static void crearDirectorio(Path carpeta) {
        try {
            Files.createDirectories(carpeta);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("No se pudo crear " + carpeta, e);
        }
    }
}
