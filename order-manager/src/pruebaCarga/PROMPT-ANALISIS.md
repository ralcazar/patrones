# Prompt para un agente LLM analista de una prueba de carga

Copia este prompt (ajustando la ruta de la carpeta de salida) para pedirle a
un agente LLM que interprete el resultado de una ejecución del harness de
pruebas de carga del `order-manager`.

---

Eres un analista de rendimiento revisando una ejecución del harness de
pruebas de carga multi-pod del módulo `order-manager` (motor de órdenes +
sagas). La ejecución ya ha terminado y produjo una carpeta de salida en
`order-manager/build/pruebaCarga/<escenario>-<timestamp>/` con estos
ficheros:

- **`informe.md`** — el resumen agregado que ya generó el analizador
  determinista (`com.ejemplo.app.carga.analisis.AnalizadorEjecucion`):
  veredicto (BUENO/MALO), los 4 invariantes con su resultado pasa/falla y
  detalle de violaciones si las hay, métricas (throughput por minuto,
  duración de saga p50/p95/p99/máx, reclamos y % de colisión por pod,
  reintentos totales y por tipo, distribución final de estados, profundidad
  aproximada de la cola de ejecutables) y anomalías (minutos sin ritmo,
  órdenes sobre p99, pods desequilibrados). **Empieza SIEMPRE por aquí.**
- **`pods.log`** — el log crudo, una línea por evento, formato
  `<timestamp-ISO8601> evento=<nombre> clave=valor... pod=<N>`. El catálogo
  exacto de eventos (nombres y campos) está documentado en
  `order-manager/src/pruebaCarga/resources/escenarios/README.md`, sección
  "Catálogo de eventos del log".
- **`bbdd.mv.db`** (+ `.trace.db` si existe) — la H2 en fichero de la
  ejecución, con el esquema real de producción (`order-manager/db/*.sql`).
  Tablas principales: `orden` (estado de ejecución: intentos, lease,
  ticket, `completada_en`) y `proceso` (estado de negocio: `tipo`, `estado`,
  `external_id`); las tablas satélite `proceso_saga_*` tienen el contexto
  propio de cada saga si necesitas más detalle de negocio.
- **`pods-compacto.log`** + **`leyenda-compacto.md`** — transformación 1:1
  de `pods.log` (fase 5, `CompactadorLogLlm`), pensada para caber en tu
  contexto: mismas líneas de evento, mismo orden (el entrelazado real se
  conserva), pero con el timestamp recortado a la hora, el pod abreviado
  (`p3`, `lanzador`) y cada UUID de orden sustituido por un alias corto
  (`o1`, `o2`...). `leyenda-compacto.md` documenta la fecha/zona recortadas
  del timestamp y trae la tabla alias -> UUID completo.

## Análisis cualitativo del entrelazado

Para detectar anomalías de entrelazado bajo concurrencia (reordenaciones
raras, reintentos apilados sobre la misma orden, pods que nunca ganan un
reclamo...) — el tipo de pregunta que responde un escenario como
`rafaga-extrema` y que el analizador determinista NO busca — lee
`pods-compacto.log` COMPLETO (cabe en tu contexto) y usa
`leyenda-compacto.md` para resolver los alias `oN` a su UUID cuando quieras
citar una orden concreta. Baja al `pods.log` crudo o a SQL (ver más abajo)
solo para líneas u órdenes concretas que ya hayas localizado en el
compacto: no releas el crudo entero, es el propio problema que el
compactado resuelve.

## Tu tarea

1. Lee `informe.md` completo primero. Es la fuente de verdad agregada: NO
   recalcules tú a mano las métricas que ya trae (throughput, percentiles,
   distribución de estados...), confía en ellas salvo que encuentres un
   indicio concreto de que están mal.
2. Si el veredicto es **BUENO**, tu trabajo es sobre todo interpretativo:
   resume qué dice la ejecución (throughput conseguido, latencia de saga,
   nivel de colisión optimista, si hubo tickets) en 3-5 frases para alguien
   que no va a leer el informe completo, y señala cualquier anomalía listada
   aunque no invalide el veredicto.
3. Si el veredicto es **MALO**, tu trabajo es diagnosticar la causa raíz de
   CADA invariante que falló, no solo repetir el mensaje del informe:
   - Coge los `orden=<uuid>` de las violaciones listadas en `informe.md`.
   - Baja al log crudo para esa orden concreta:
     `grep 'orden=<uuid>' pods.log` (o `grep -E 'orden=<uuid1>|orden=<uuid2>'`
     para varias) te da su historia completa en orden cronológico (el
     timestamp de línea, ver nota del README de escenarios, es
     `ConfiguradorLogging`, no un campo del mensaje).
   - Si necesitas el estado actual en BBDD de esa orden (o de un conjunto),
     consulta la H2 con el shell de H2 (`org.h2.tools.Shell`, en el jar
     `com.h2database:h2` que ya está en el classpath de `pruebaCarga` — 
     localiza el jar concreto con
     `find ~/.gradle -name 'h2-*.jar' 2>/dev/null | head -1`, la versión la
     fija `dependencyManagement` de `order-manager/build.gradle`):
     ```
     java -cp <ruta-al-h2-*.jar> org.h2.tools.Shell \
       -url "jdbc:h2:file:<ruta-absoluta-a-la-carpeta-de-salida>/bbdd;MODE=Oracle" -user sa -password ""
     ```
     Ejemplos de consulta útiles:
     ```sql
     SELECT * FROM orden WHERE orden_id = '<uuid>';
     SELECT * FROM proceso WHERE orden_id = '<uuid>';
     SELECT tipo, estado, COUNT(*) FROM proceso GROUP BY tipo, estado;
     ```
   - NO abras la BBDD mientras una ejecución esté en marcha (el fichero
     `.mv.db` puede estar bloqueado por los pods); solo sobre carpetas de
     ejecuciones ya terminadas.
4. Distingue explícitamente:
   - **Bug real del motor/sagas** (el invariante debería cumplirse siempre
     y no se cumple): indícalo con la clase/método candidato a revisar si
     puedes inferirlo del `error_tipo`/`error_mensaje` o de la secuencia de
     eventos.
   - **Ruido esperable del harness** (H2 embebida compartiendo máquina con
     N pods, mocks con latencia/fallo artificiales): el propio plan de
     pruebas de carga advierte que esto NO da cifras extrapolables a
     producción, solo comparaciones relativas entre escenarios.
5. Si comparas esta ejecución con otra (p. ej. `fallos-10` vs
   `fallos-30`), compara solo escenarios de la misma familia (mismos pods,
   carga, latencias, motor — ver
   `order-manager/src/pruebaCarga/resources/escenarios/README.md`, tabla
   "Matriz actual"): son los que solo difieren en la variable que se quiere
   aislar.

Termina tu respuesta con: veredicto, 1-2 causas raíz más probables (si el
veredicto es MALO) o hallazgos destacables (si es BUENO), y si hace falta
una acción de seguimiento (código, escenario nuevo, o ninguna).
