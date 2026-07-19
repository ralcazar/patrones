# Prompt para el agente coordinador (sesión nueva, modelo Sonnet)

Copiar-pegar tal cual:

---

Trabajas en el repo `patrones` (raíz del repo; el módulo relevante es
`order-manager`). Eres el COORDINADOR de un refactor por fases descrito en
`plan-fusion-vo-orden-proceso.md` (raíz del repo): fusión de las tablas
`orden`+`proceso`, re-check de ejecutabilidad en el reclamo, VO-ización de
`Proceso`, reglas jMolecules en ArchUnit y un invariante nuevo del
analizador de carga — con un test que primero reproduce en rojo el bug de
lectura mixta que motiva todo. Lee ENTERO el plan y `CLAUDE.md` antes de
empezar. El plan es la fuente de verdad; si una fase descubre algo que lo
contradice, decides tú (anotándolo), no el subagente.

Modo de trabajo:

1. Lanza UN subagente por fase, en orden 1→7 (la fase 4 son DOS subagentes
   secuenciales: 4a dominio, 4b llamantes; entre 4a y 4b el proyecto no
   compila y es esperado). Nunca dos fases de código en paralelo: tocan los
   mismos ficheros. Pásale a cada subagente el texto íntegro de su fase +
   las "Reglas transversales" del plan, y dile que NO comitee.
2. A cada subagente dale un objetivo verificable y un límite de tiempo
   razonable; mientras corre, comprueba su progreso por los efectos en
   disco (`git status`, ficheros nuevos) en lugar de esperar pasivamente.
   Si un subagente se atasca o se desvía del plan, córtalo y relanza con
   instrucciones corregidas.
3. Verificaciones de puerta (las haces TÚ, no el subagente):
   - Fase 1: el test nuevo debe FALLAR y por el motivo correcto (2
     invocaciones de `solicitar` en vez de 1). No se comitea aún.
   - Fase 2: el test de la fase 1 pasa a verde SIN tocarlo, y
     `./gradlew check` verde (cobertura 100% instrucción y rama; jamás se
     baja el umbral). Commit único con test + fusión.
   - Fase 3: `check` verde; el catálogo de eventos del harness recoge el
     motivo nuevo `NO_EJECUTABLE`. Commit.
   - Fase 4: tras 4b, `check` verde. UN único commit para toda la fase.
   - Fases 5 y 6: `check` verde. Un commit por fase.
   - Fase 7: `./gradlew clean check` + una ejecución de
     `./gradlew pruebaCarga -Pescenario=rafaga-extrema`: el `informe.md`
     debe dar veredicto BUENO con los 5 invariantes en PASA, y en
     `pods-compacto.log` ninguna SECUNDARIA2 puede tener dos
     `respuesta_secundaria2_registrada` sin un `exito=false` previo.
4. Commits: mensaje en español describiendo la fase; los diagramas `.puml`
   modificados van con su PNG regenerado (skill `puml-to-png`) y
   `docs/README.md` al día EN EL MISMO commit. Push solo al final de todo
   (config `http.postBuffer` amplia: los PNG pesan).
5. Todo el código, comentarios y nombres nuevos en español, siguiendo el
   estilo del proyecto. La capa `business/**` solo Java + jMolecules (lo
   vigila `ReglasArquitecturaTest`).

Termina con un resumen: qué hizo cada fase, estado rojo→verde del test de
la fase 1, resultado del `check` final y de la pasada de `pruebaCarga`
(5 invariantes), y la lista de commits creados.
