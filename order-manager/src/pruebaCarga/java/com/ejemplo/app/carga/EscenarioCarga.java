package com.ejemplo.app.carga;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.yaml.snakeyaml.Yaml;

/**
 * Escenario de prueba de carga, mapeado 1:1 del esquema documentado en
 * {@code src/pruebaCarga/resources/escenarios/README.md} (fuente de verdad:
 * el lanzador no acepta ningún parámetro suelto, solo {@code -Pescenario}).
 *
 * <p>La carga es estricta a propósito: cualquier clave desconocida en el yml
 * (típicamente un typo) aborta con un mensaje claro en vez de ignorarse en
 * silencio. Se implementa cargando primero a {@code Map<String,Object>} con
 * SnakeYAML y validando las claves conocidas nivel a nivel antes de construir
 * los records; es más código que un mapeo directo, pero permite el mensaje de
 * error exacto (clave y nivel del yml) que un {@code Constructor} de
 * SnakeYAML no da con la misma claridad.
 */
public record EscenarioCarga(
        String nombre,
        String descripcion,
        long semilla,
        Duration duracion,
        Duration drenajeMaximo,
        int pods,
        Inyeccion inyeccion,
        Rest rest,
        Kafka kafka,
        Motor motor) {

    public record Inyeccion(int tramitaciones, int ritmoPorSegundo) {}

    public record RangoMs(long min, long max) {}

    /** Override de latencia/fallo para un puerto concreto (ambos campos opcionales: null = usa el valor general). */
    public record RestPuerto(RangoMs latenciaMs, Double tasaFallo) {}

    public record Rest(RangoMs latenciaMs, double tasaFallo, Map<String, RestPuerto> porPuerto) {

        /** Latencia efectiva para el puerto dado: su override si lo tiene, si no la general. */
        public RangoMs latenciaMsPara(String puerto) {
            var override = porPuerto.get(puerto);
            return (override != null && override.latenciaMs() != null) ? override.latenciaMs() : latenciaMs;
        }

        /** Tasa de fallo efectiva para el puerto dado: su override si lo tiene, si no la general. */
        public double tasaFalloPara(String puerto) {
            var override = porPuerto.get(puerto);
            return (override != null && override.tasaFallo() != null) ? override.tasaFallo() : tasaFallo;
        }
    }

    public record Kafka(RangoMs retrasoMs, double tasaPerdida) {}

    public record Planificador(long intervaloMs, int trabajadores, int lote) {}

    /**
     * Overrides de los cron de producción (ver application.yml:
     * ordermanager.tickets.cron / ordermanager.limpieza.cron /
     * sagas.purga-datos-negocio.cron), añadidos en la fase 2 del plan de
     * pruebas de carga porque una ejecución corta no dispara los cron reales
     * (cada 3h / cada noche). Los tres campos son opcionales: null = se deja
     * el valor por defecto de application.yml para ese pod.
     */
    public record Cron(String tickets, String limpieza, String purga) {

        static final Cron VACIO = new Cron(null, null, null);
    }

    public record Motor(Planificador planificador, Duration lease, Cron cron) {}

    // --- claves conocidas por nivel del yml (para el rechazo de claves desconocidas) ---

    private static final Set<String> CLAVES_RAIZ = Set.of(
            "nombre", "descripcion", "semilla", "duracion", "drenaje-maximo", "pods", "inyeccion", "rest", "kafka",
            "motor");
    private static final Set<String> CLAVES_INYECCION = Set.of("tramitaciones", "ritmo-por-segundo");
    private static final Set<String> CLAVES_RANGO_MS = Set.of("min", "max");
    private static final Set<String> CLAVES_REST = Set.of("latencia-ms", "tasa-fallo", "por-puerto");
    private static final Set<String> CLAVES_REST_PUERTO = Set.of("latencia-ms", "tasa-fallo");
    private static final Set<String> CLAVES_KAFKA = Set.of("retraso-ms", "tasa-perdida");
    private static final Set<String> CLAVES_MOTOR = Set.of("planificador", "lease", "cron");
    private static final Set<String> CLAVES_PLANIFICADOR = Set.of("intervalo-ms", "trabajadores", "lote");
    private static final Set<String> CLAVES_CRON = Set.of("tickets", "limpieza", "purga");

    /**
     * Carga el escenario {@code <nombreEscenario>.yml} del classpath (ver
     * {@code src/pruebaCarga/resources/escenarios/}). El nombre debe coincidir
     * con el campo {@code nombre} del propio fichero (invariante documentado
     * en el README de escenarios).
     */
    public static EscenarioCarga cargar(String nombreEscenario) {
        String recurso = "escenarios/" + nombreEscenario + ".yml";
        try (InputStream in = EscenarioCarga.class.getClassLoader().getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalArgumentException("No se encontró el escenario en el classpath: " + recurso);
            }
            Object raiz = new Yaml().load(in);
            var escenario = desdeMapa(aMapa(raiz, "raíz"));
            if (!escenario.nombre().equals(nombreEscenario)) {
                throw new IllegalArgumentException(
                        "El campo 'nombre' (" + escenario.nombre() + ") del fichero " + recurso
                                + " no coincide con el nombre de fichero pedido (" + nombreEscenario + ")");
            }
            return escenario;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + recurso, e);
        }
    }

    private static EscenarioCarga desdeMapa(Map<String, Object> raiz) {
        validarClaves(raiz, CLAVES_RAIZ, "raíz");
        String nombre = requerido(raiz, "nombre", String.class, "raíz");
        String descripcion = requerido(raiz, "descripcion", String.class, "raíz");
        long semilla = requeridoNumero(raiz, "semilla", "raíz").longValue();
        Duration duracion = Duration.parse(requerido(raiz, "duracion", String.class, "raíz"));
        Duration drenajeMaximo = Duration.parse(requerido(raiz, "drenaje-maximo", String.class, "raíz"));
        int pods = requeridoNumero(raiz, "pods", "raíz").intValue();

        var inyeccion = leerInyeccion(requeridoMapa(raiz, "inyeccion", "raíz"));
        var rest = leerRest(requeridoMapa(raiz, "rest", "raíz"));
        var kafka = leerKafka(requeridoMapa(raiz, "kafka", "raíz"));
        var motor = leerMotor(requeridoMapa(raiz, "motor", "raíz"));

        return new EscenarioCarga(nombre, descripcion, semilla, duracion, drenajeMaximo, pods, inyeccion, rest, kafka,
                motor);
    }

    private static Inyeccion leerInyeccion(Map<String, Object> mapa) {
        validarClaves(mapa, CLAVES_INYECCION, "inyeccion");
        int tramitaciones = requeridoNumero(mapa, "tramitaciones", "inyeccion").intValue();
        int ritmo = requeridoNumero(mapa, "ritmo-por-segundo", "inyeccion").intValue();
        return new Inyeccion(tramitaciones, ritmo);
    }

    private static RangoMs leerRango(Map<String, Object> mapa, String nivel) {
        validarClaves(mapa, CLAVES_RANGO_MS, nivel);
        long min = requeridoNumero(mapa, "min", nivel).longValue();
        long max = requeridoNumero(mapa, "max", nivel).longValue();
        return new RangoMs(min, max);
    }

    private static Rest leerRest(Map<String, Object> mapa) {
        validarClaves(mapa, CLAVES_REST, "rest");
        var latenciaMs = leerRango(requeridoMapa(mapa, "latencia-ms", "rest"), "rest.latencia-ms");
        double tasaFallo = requeridoNumero(mapa, "tasa-fallo", "rest").doubleValue();
        Map<String, RestPuerto> porPuerto = new LinkedHashMap<>();
        var porPuertoMapa = opcionalMapa(mapa, "por-puerto");
        if (porPuertoMapa != null) {
            for (var entrada : porPuertoMapa.entrySet()) {
                String puerto = entrada.getKey();
                var overrideMapa = aMapa(entrada.getValue(), "rest.por-puerto." + puerto);
                validarClaves(overrideMapa, CLAVES_REST_PUERTO, "rest.por-puerto." + puerto);
                RangoMs latenciaOverride = null;
                var latenciaOverrideMapa = opcionalMapa(overrideMapa, "latencia-ms");
                if (latenciaOverrideMapa != null) {
                    latenciaOverride = leerRango(latenciaOverrideMapa, "rest.por-puerto." + puerto + ".latencia-ms");
                }
                Double tasaFalloOverride = opcionalNumero(overrideMapa, "tasa-fallo", "rest.por-puerto." + puerto);
                porPuerto.put(puerto, new RestPuerto(latenciaOverride, tasaFalloOverride));
            }
        }
        return new Rest(latenciaMs, tasaFallo, porPuerto);
    }

    private static Kafka leerKafka(Map<String, Object> mapa) {
        validarClaves(mapa, CLAVES_KAFKA, "kafka");
        var retrasoMs = leerRango(requeridoMapa(mapa, "retraso-ms", "kafka"), "kafka.retraso-ms");
        double tasaPerdida = requeridoNumero(mapa, "tasa-perdida", "kafka").doubleValue();
        return new Kafka(retrasoMs, tasaPerdida);
    }

    private static Motor leerMotor(Map<String, Object> mapa) {
        validarClaves(mapa, CLAVES_MOTOR, "motor");
        var planificadorMapa = requeridoMapa(mapa, "planificador", "motor");
        validarClaves(planificadorMapa, CLAVES_PLANIFICADOR, "motor.planificador");
        var planificador = new Planificador(
                requeridoNumero(planificadorMapa, "intervalo-ms", "motor.planificador").longValue(),
                requeridoNumero(planificadorMapa, "trabajadores", "motor.planificador").intValue(),
                requeridoNumero(planificadorMapa, "lote", "motor.planificador").intValue());
        Duration lease = null;
        String leaseStr = opcional(mapa, "lease", String.class, "motor");
        if (leaseStr != null) {
            lease = Duration.parse(leaseStr);
        }
        Cron cron = Cron.VACIO;
        var cronMapa = opcionalMapa(mapa, "cron");
        if (cronMapa != null) {
            validarClaves(cronMapa, CLAVES_CRON, "motor.cron");
            cron = new Cron(
                    opcional(cronMapa, "tickets", String.class, "motor.cron"),
                    opcional(cronMapa, "limpieza", String.class, "motor.cron"),
                    opcional(cronMapa, "purga", String.class, "motor.cron"));
        }
        return new Motor(planificador, lease, cron);
    }

    // --- helpers de lectura/validación ---

    @SuppressWarnings("unchecked")
    private static Map<String, Object> aMapa(Object valor, String nivel) {
        if (!(valor instanceof Map)) {
            throw new IllegalArgumentException("Se esperaba un objeto en '" + nivel + "', se encontró: " + valor);
        }
        return (Map<String, Object>) valor;
    }

    private static void validarClaves(Map<String, Object> mapa, Set<String> permitidas, String nivel) {
        var desconocidas = new TreeSet<>(mapa.keySet());
        desconocidas.removeAll(permitidas);
        if (!desconocidas.isEmpty()) {
            throw new IllegalArgumentException(
                    "Clave(s) desconocida(s) en '" + nivel + "': " + desconocidas + " (permitidas: " + permitidas
                            + ")");
        }
    }

    private static Object requeridoCrudo(Map<String, Object> mapa, String clave, String nivel) {
        if (!mapa.containsKey(clave) || mapa.get(clave) == null) {
            throw new IllegalArgumentException("Falta la clave obligatoria '" + clave + "' en '" + nivel + "'");
        }
        return mapa.get(clave);
    }

    private static <T> T requerido(Map<String, Object> mapa, String clave, Class<T> tipo, String nivel) {
        Object valor = requeridoCrudo(mapa, clave, nivel);
        if (!tipo.isInstance(valor)) {
            throw new IllegalArgumentException(
                    "La clave '" + clave + "' en '" + nivel + "' debe ser " + tipo.getSimpleName() + ", es: " + valor);
        }
        return tipo.cast(valor);
    }

    private static Number requeridoNumero(Map<String, Object> mapa, String clave, String nivel) {
        Object valor = requeridoCrudo(mapa, clave, nivel);
        if (!(valor instanceof Number numero)) {
            throw new IllegalArgumentException("La clave '" + clave + "' en '" + nivel + "' debe ser numérica, es: " + valor);
        }
        return numero;
    }

    private static <T> T opcional(Map<String, Object> mapa, String clave, Class<T> tipo, String nivel) {
        Object valor = mapa.get(clave);
        if (valor == null) {
            return null;
        }
        if (!tipo.isInstance(valor)) {
            throw new IllegalArgumentException(
                    "La clave '" + clave + "' en '" + nivel + "' debe ser " + tipo.getSimpleName() + ", es: " + valor);
        }
        return tipo.cast(valor);
    }

    private static Double opcionalNumero(Map<String, Object> mapa, String clave, String nivel) {
        Object valor = mapa.get(clave);
        if (valor == null) {
            return null;
        }
        if (!(valor instanceof Number numero)) {
            throw new IllegalArgumentException("La clave '" + clave + "' en '" + nivel + "' debe ser numérica, es: " + valor);
        }
        return numero.doubleValue();
    }

    private static Map<String, Object> requeridoMapa(Map<String, Object> mapa, String clave, String nivel) {
        return aMapa(requeridoCrudo(mapa, clave, nivel), nivel + "." + clave);
    }

    private static Map<String, Object> opcionalMapa(Map<String, Object> mapa, String clave) {
        Object valor = mapa.get(clave);
        return valor == null ? null : aMapa(valor, clave);
    }
}
