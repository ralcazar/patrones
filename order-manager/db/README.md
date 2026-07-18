# Esquema de order-manager

DDL manual de Oracle: un `.sql` por objeto de base de datos (tabla, índices y
FKs de esa tabla van en el fichero de su propia tabla). No hay ninguna
herramienta de migración (Flyway) ni `ddl-auto` de JPA que ejecute esto
automáticamente: lo aplica un DBA a mano.

## Orden de aplicación

Las FKs obligan este orden:

1. `proceso.sql` — tabla `proceso` (sin dependencias).
2. `proceso_auditoria.sql` — tabla `proceso_auditoria`, FK a `proceso`.
3. `orden.sql` — tabla `orden`, FK a `proceso`.
4. `datos_negocio.sql` — tabla `datos_negocio` (sin dependencias).
5. `datos_negocio_documento.sql` — tabla `datos_negocio_documento`, FK a `datos_negocio`.
6. `proceso_saga_principal.sql` — tabla satélite del contexto de la saga
   principal, FK a `proceso` Y a `datos_negocio`.
7. `proceso_saga_secundaria1.sql` — tabla satélite del contexto de la saga
   secundaria 1, FK a `proceso`.
8. `proceso_saga_secundaria2.sql` — tabla satélite del contexto de la saga
   secundaria 2, FK a `proceso`.
9. `proceso_saga_secundaria3.sql` — tabla satélite del contexto de la saga
   secundaria 3, FK a `proceso`.

Ninguna tabla de este esquema usa `ON DELETE CASCADE` (prohibido, ver
CLAUDE.md): los borrados de filas hijas los hace explícitos, en la misma
transacción y en el orden correcto (hijas antes que padre), el adaptador de
persistencia (`AdaptadorRepositorioOrden.purgarFinalizadasAntesDe`).
