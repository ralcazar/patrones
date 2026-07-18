package com.ejemplo.app.carga.mocks;

import com.ejemplo.app.business.ordermanager.dominio.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.MotivoFallo;
import com.ejemplo.app.carga.ContextoPod;

/**
 * Comportamiento común de todos los mocks de puertos externos (composición,
 * no herencia: cada mock lo invoca al principio de cada método del puerto):
 * duerme una latencia uniforme min-max y, con probabilidad {@code tasa-fallo}
 * (global del escenario o el override {@code por-puerto} de ese puerto en
 * concreto), lanza {@link ExcepcionServicioExterno} — el mismo tipo que la
 * javadoc de cada puerto real documenta ("el adaptador lanza
 * ExcepcionServicioExterno"), más específico y más fiel al contrato real que
 * una {@code RuntimeException} genérica, y sigue siendo una
 * {@code RuntimeException} tal como pide el plan de pruebas de carga.
 */
public final class SimuladorRestMock {

    private SimuladorRestMock() {
    }

    public static void simular(ContextoPod contexto, String nombrePuerto) {
        var rest = contexto.escenario().rest();
        var random = contexto.random();

        var rango = rest.latenciaMsPara(nombrePuerto);
        long espera = rango.min() >= rango.max()
                ? rango.min()
                : rango.min() + (long) (random.nextDouble() * (rango.max() - rango.min()));
        try {
            Thread.sleep(espera);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExcepcionServicioExterno(
                    MotivoFallo.errorTecnico("Mock de " + nombrePuerto + " interrumpido durmiendo la latencia"), e);
        }

        double tasaFallo = rest.tasaFalloPara(nombrePuerto);
        if (random.nextDouble() < tasaFallo) {
            throw new ExcepcionServicioExterno(
                    MotivoFallo.errorTecnico("Fallo simulado por el mock de " + nombrePuerto), null);
        }
    }
}
