---
name: puml-to-png
description: Convierte diagramas PlantUML (.puml) a imágenes PNG usando plantuml.jar local. Usar cuando el usuario pida convertir, renderizar, generar o exportar diagramas .puml/PlantUML a PNG (o cuando pida regenerar las imágenes de los diagramas del proyecto).
---

# Convertir diagramas PlantUML a PNG

## Requisitos

- Java (verificar con `java -version`; ya hay Java 21 en `/usr/bin/java`).
- `plantuml.jar` en `~/.claude/tools/plantuml.jar`. Si no existe, descargarlo:

```bash
mkdir -p ~/.claude/tools
curl -sL -o ~/.claude/tools/plantuml.jar \
  https://github.com/plantuml/plantuml/releases/latest/download/plantuml.jar
```

## Conversión

Convertir todos los `.puml` de un directorio (los PNG se generan junto a los fuentes, con el mismo nombre):

```bash
java -jar ~/.claude/tools/plantuml.jar -tpng -charset UTF-8 /ruta/al/directorio/*.puml
```

Convertir un archivo concreto:

```bash
java -jar ~/.claude/tools/plantuml.jar -tpng -charset UTF-8 /ruta/al/archivo.puml
```

Opciones útiles:

- `-o /ruta/salida` — escribir los PNG en otro directorio.
- `-tsvg` — generar SVG en lugar de PNG.
- `-checkonly` — validar sintaxis sin generar imágenes.
- `-Playout=smetana` — usar el motor interno de layout si el diagrama requiere Graphviz (`dot`) y no está instalado. Los diagramas de secuencia no necesitan Graphviz; los de clases/componentes/estados sí (o smetana).

## Notas

- Usar siempre `-charset UTF-8`: los diagramas del proyecto contienen tildes y eñes.
- Si un diagrama falla, PlantUML genera igualmente un PNG con el mensaje de error dibujado; revisar la salida de consola y el código de salida para detectarlo.
- En este proyecto los diagramas viven en `order-manager/docs/*.puml`.

## Verificación

Tras convertir, comprobar que existe un `.png` por cada `.puml`:

```bash
ls /ruta/al/directorio/*.png
```
