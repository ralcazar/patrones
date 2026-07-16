# Esquema de order-manager

DDL manual de Oracle: un `.sql` por objeto de base de datos (tabla, índices y
FKs de esa tabla van en el fichero de su propia tabla). No hay ninguna
herramienta de migración (Flyway) ni `ddl-auto` de JPA que ejecute esto
automáticamente: lo aplica un DBA a mano.

## Orden de aplicación

Las FKs obligan este orden:

1. `saga.sql` — tabla `saga` (sin dependencias).
2. `saga_auditoria.sql` — tabla `saga_auditoria`, FK a `saga`.
3. `orden.sql` — tabla `orden`, FK a `saga`.
