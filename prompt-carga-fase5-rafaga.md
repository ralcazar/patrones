# Prompt: escenario `rafaga-extrema` + compactador de log para agente LLM

Eres un agente implementador trabajando en el repositorio `patrones`
(directorio de trabajo: la raíz del repo). **Lee entero `CLAUDE.md` antes de
empezar** y respétalo; en particular: nomenclatura en español, cobertura
100% intocable, nada de Docker/brokers, y `./gradlew check` verde antes de
commitear. Esta tarea NO toca código de producción (`src/main`) ni
diagramas: todo vive en `order-manager/src/pruebaCarga/`.

## Contexto (ya construido; no rehacer nada)

El repo tiene un harness de pruebas de carga completo (commits "Carga fase
0–4"). Piezas relevantes:

- Escenarios yml en `order-manager/src/pruebaCarga/resources/escenarios/`
  (su `README.md` documenta el esquema, la matriz de escenarios y el
  catálogo exacto de eventos del log — es el contrato del analizador).
- Lanzador `LanzadorPruebaCarga` (`carga/`): `./gradlew pruebaCarga
  -Pescenario=<nombre>` arranca N contextos Spring ("pods") contra una H2
  en fichero, inyecta tramitaciones, drena y llama al analizador.
- Analizador determinista en `carga/analisis/` (`AnalizadorEjecucion`):
  lee `pods.log`, consulta la H2 y escribe `informe.md` con veredicto,
  invariantes y métricas. Salidas de cada ejecución en
  `order-manager/build/pruebaCarga/<escenario>-<timestamp>/`.
- `order-manager/src/pruebaCarga/PROMPT-ANALISIS.md`: prompt plantilla
  para un agente LLM analista.

Dato medido que motiva esta tarea: el escenario `humo` (100 tramitaciones)
produce un `pods.log` de ~2.600 líneas / 388 KB ≈ ~120K tokens. Un agente
LLM en plan Pro no puede leer eso entero; el objetivo es un escenario de
ráfaga corta cuyo log, compactado, quepa en ~25–40K tokens.

## Objetivo

Dos entregables, en un único commit:

1. **Escenario `rafaga-extrema`**: ráfaga de pocos segundos con máxima
   concurrencia (muchos pods, muchos hilos, lote pequeño) y poco volumen
   de órdenes, pensado para que un agente LLM experto lea el log completo
   y detecte anomalías cualitativas de entrelazado que el analizador
   determinista no busca.
2. **Compactador de log**: tras cada ejecución (de cualquier escenario),
   generar `pods-compacto.log` + `leyenda-compacto.md`, una transformación
   determinista 1:1 del log de eventos que reduce ~3–4× los tokens.

## Tarea 1 — Escenario `rafaga-extrema.yml`

Crear `order-manager/src/pruebaCarga/resources/escenarios/rafaga-extrema.yml`
siguiendo el esquema del README (cabecera de comentario explicando la
intención, como hacen los demás escenarios). Valores de partida:

```yaml
nombre: rafaga-extrema
descripcion: Ráfaga corta de máxima concurrencia (12 pods x 8 hilos, lote mínimo) con log pequeño, pensada para análisis cualitativo por un agente LLM.

semilla: 42
duracion: PT20S
drenaje-maximo: PT2M

pods: 12

inyeccion:
  tramitaciones: 30
  ritmo-por-segundo: 6

rest:
  latencia-ms: { min: 10, max: 50 }
  tasa-fallo: 0.05        # algún fallo para que haya carreras de reintento

kafka:
  retraso-ms: { min: 100, max: 500 }
  tasa-perdida: 0.0

motor:
  planificador:
    intervalo-ms: 250
    lote: 4               # lote << hilos totales (12x8): máxima pelea por
    trabajadores: 8       # las mismas órdenes -> colisiones optimistas
  lease: PT10M
```

Notas:

- Antes de dar los valores por buenos, comprueba en `EscenarioCarga` que la
  validación acepta duraciones en segundos (`PT20S`) y estos rangos; si algo
  no encaja, manda el código: ajusta el yml, no el validador (salvo bug
  evidente, que documentarías en el commit).
- Añade la fila a la matriz del README de escenarios. Pregunta que
  responde: "¿Aparecen anomalías de entrelazado bajo concurrencia máxima?
  (log pequeño para análisis por agente LLM)". Deja claro (en el comentario
  del yml) que NO mide throughput ni nada estadístico: demasiado pocas
  órdenes; su valor es cualitativo.

## Tarea 2 — Compactador de log

Nueva clase en `order-manager/src/pruebaCarga/java/com/ejemplo/app/carga/analisis/`
(nombre en español, p. ej. `CompactadorLogLlm`), invocada al final de cada
ejecución en el mismo punto donde ya se invoca `AnalizadorEjecucion`
(mira cómo lo llama el lanzador y engánchate igual; también debe poder
ejecutarse a mano sobre una carpeta de salida existente, como el
analizador). Escribe en la carpeta de salida:

### `pods-compacto.log`

Transformación línea a línea de `pods.log`:

- **Se descartan** las líneas que no son eventos (arranque de Spring,
  Hikari, Hibernate…): solo pasan las líneas que contienen `evento=`.
- **Timestamp**: de `2026-07-19T08:22:31.123+02:00` se conserva solo
  `08:22:31.123` (la fecha y la zona son constantes en toda la ejecución;
  se documentan una vez en la leyenda).
- **Pod**: `pod=3` → `p3` (y `pod=lanzador` → `lanzador`).
- **Orden**: cada UUID de `orden=` se sustituye por un alias corto `o1`,
  `o2`… asignado por orden de primera aparición en el log.
- **Formato de línea**: `<hora> <pod> <evento> [<alias-orden> <tipo>]
  <resto>` donde `<resto>` son los demás campos `clave=valor` tal cual,
  con una excepción: en `error_tipo` se conserva solo el nombre simple de
  la clase, no el FQCN.
  - Ejemplo: `2026-07-19T08:22:31.123+02:00 evento=paso_completado
    orden=6f9a… tipo=PRINCIPAL duracion_ms=123 pod=3` →
    `08:22:31.123 p3 paso_completado o17 PRINCIPAL duracion_ms=123`.
- **Nada de agregación ni filtrado de eventos**: es 1:1 con las líneas de
  evento del crudo, mismo orden, para que el entrelazado se conserve
  intacto. El catálogo de eventos del README es el contrato de qué campos
  existen; no inventes campos ni renombres eventos.

### `leyenda-compacto.md`

- Fecha y zona horaria de la ejecución (lo recortado del timestamp).
- El formato de línea compacta y sus reglas (un párrafo).
- Tabla alias → UUID completo, para poder volver al `pods.log` crudo o a
  la H2 con el identificador real.

### Documentación

- README de escenarios, sección "Salidas de una ejecución": añadir los dos
  ficheros nuevos.
- `PROMPT-ANALISIS.md`: sección nueva indicando al agente analista que para
  análisis cualitativo lea `pods-compacto.log` completo (cabe en contexto),
  use `leyenda-compacto.md` para resolver alias, y baje al `pods.log` crudo
  o a SQL solo para líneas/órdenes concretas ya localizadas.

## Restricciones

- Cero cambios bajo `order-manager/src/main` y `order-manager/src/test`,
  `src/integrationTest`, `order-manager/docs/` (pruebaCarga no es PROD:
  sin diagramas). Si crees necesitar tocar algo de eso, PARA y repórtalo.
- El código de `src/pruebaCarga` no computa cobertura: no necesita tests,
  pero sí la validación de punta a punta de abajo.
- Java y nombres en español, estilo del código vecino de `carga/`.

## Verificación (todo antes del commit)

1. `./gradlew check` verde (no debe verse afectado; si falla, arregla sin
   bajar umbrales ni tocar reglas ArchUnit).
2. `./gradlew pruebaCarga -Pescenario=rafaga-extrema` termina con exit 0.
   Ten paciencia: la ráfaga dura segundos pero arrancar 12 contextos
   Spring puede llevar 1–2 minutos; presupuesta ~10 min de timeout.
3. En la carpeta de salida:
   - `informe.md` con invariantes OK y `colision_optimista` > 0 (si sale
     0, la contención no se está dando: baja `lote` a 2 o `intervalo-ms`
     a 100 y repite; anota el ajuste en el yml).
   - `pods-compacto.log` con **≤ 2.000 líneas y ≤ 120 KB** (`wc -l -c`).
     Si se pasa, reduce `tramitaciones` y/o `duracion` hasta cumplirlo:
     el presupuesto de tokens del agente lector manda sobre el volumen.
   - Mismo número de líneas de evento en crudo y compacto
     (`grep -c 'evento=' pods.log` == `wc -l < pods-compacto.log`).
   - Los alias de la leyenda casan con los UUID del crudo (muestreo: elige
     2–3 alias y compruébalos a mano).
4. Ejecuta también `./gradlew pruebaCarga -Pescenario=humo` para confirmar
   que el compactador funciona en un escenario preexistente sin romper nada.

## Commit

Un único commit con SOLO los ficheros de esta tarea (no incluyas
`plan-pruebas-carga.md` ni este prompt), mensaje:
`Carga fase 5: escenario rafaga-extrema + compactador de log para agente LLM`.
No hagas push.

Al terminar, informa: valores finales del yml (si ajustaste alguno y por
qué), tamaño en líneas/KB del compacto en la ejecución de verificación, y
cuántas `colision_optimista` registró el informe.
