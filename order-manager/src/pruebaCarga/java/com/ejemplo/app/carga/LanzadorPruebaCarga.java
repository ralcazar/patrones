package com.ejemplo.app.carga;

/**
 * Punto de entrada del harness de pruebas de carga multi-pod (ver
 * {@code plan-pruebas-carga.md} en la raíz del repo).
 *
 * <p>Esta es una clase ESQUELETO de la fase 1 del plan: solo existe para que
 * el source set {@code pruebaCarga} y la task de Gradle {@code pruebaCarga}
 * compilen y se puedan invocar de punta a punta. La fase 2 la sustituirá por
 * el lanzador real (arranque de N contextos Spring "pod", inyección de
 * tramitaciones, mocks de los puertos externos, etc.).
 *
 * <p>La task de Gradle ya valida antes de invocar este {@code main} que
 * {@code -Pescenario} se ha indicado y que el fichero
 * {@code src/pruebaCarga/resources/escenarios/<nombre>.yml} existe, así que
 * aquí simplemente se confía en recibir el nombre del escenario como único
 * argumento.
 */
public final class LanzadorPruebaCarga {

    private LanzadorPruebaCarga() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Se esperaba exactamente un argumento (nombre del escenario), recibidos: " + args.length);
        }
        String nombreEscenario = args[0];
        System.out.println("[pruebaCarga] escenario recibido: " + nombreEscenario
                + " (lanzador esqueleto de la fase 1; la fase 2 implementa la ejecución real)");
    }
}
