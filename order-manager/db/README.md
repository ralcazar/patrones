# Esquema de order-manager

DDL manual de Oracle: un `.sql` por objeto de base de datos (tabla, índices y
FKs de esa tabla van en el fichero de su propia tabla). No hay ninguna
herramienta de migración (Flyway) ni `ddl-auto` de JPA que ejecute esto
automáticamente: lo aplica un DBA a mano.

## Orden de aplicación

Las FKs obligan este orden:

1. `orden.sql` — tabla `orden`: raíz del agregado, fusión de lo que antes eran
   dos filas (orden + proceso) en una sola fila atómica (negocio: `tipo`,
   `external_id`, `estado`; ejecución: intentos, lease, ticket, `version`).
   Sin dependencias.
2. `proceso_auditoria.sql` — tabla `proceso_auditoria`, FK a `orden` (nombre
   histórico, ver comentario en el propio `.sql`).
3. `datos_negocio.sql` — tabla `datos_negocio` (sin dependencias).
4. `datos_negocio_documento.sql` — tabla `datos_negocio_documento`, FK a `datos_negocio`.
5. `proceso_saga_principal.sql` — tabla satélite del contexto de la saga
   principal, FK a `orden` Y a `datos_negocio`.
6. `proceso_saga_secundaria1.sql` — tabla satélite del contexto de la saga
   secundaria 1, FK a `orden`.
7. `proceso_saga_secundaria2.sql` — tabla satélite del contexto de la saga
   secundaria 2, FK a `orden`.
8. `proceso_saga_secundaria3.sql` — tabla satélite del contexto de la saga
   secundaria 3, FK a `orden`.

Ninguna tabla de este esquema usa `ON DELETE CASCADE` (prohibido, ver
CLAUDE.md): los borrados de filas hijas los hace explícitos, en la misma
transacción y en el orden correcto (hijas antes que padre), el adaptador de
persistencia (`AdaptadorRepositorioOrden.purgarFinalizadasAntesDe`).
