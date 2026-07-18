package com.ejemplo.app.carga.mocks;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoConciliacionSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.carga.ContextoPod;

/**
 * Mock de la conciliación REST de la saga secundaria 2. Además de la
 * latencia/fallo común (que puede lanzar {@code ExcepcionServicioExterno}
 * como cualquier otro mock REST), cuando SÍ responde tiene que decidir cuál
 * de los 3 resultados posibles ({@code Resultado} es una interfaz sealed)
 * devuelve. Criterio elegido (arbitrario pero documentado, ver
 * {@code plan-pruebas-carga.md} fase 2): 50% {@code SinResultado} (lo más
 * realista — la conciliación se suele llamar antes de que el resultado esté
 * listo), 30% {@code Disponible}, 20% {@code FalloRegistrado}.
 */
@Component
public class MockPuertoConciliacionSecundaria2 implements PuertoConciliacionSecundaria2 {

    private static final String NOMBRE_PUERTO = "PuertoConciliacionSecundaria2";

    private final ContextoPod contexto;

    public MockPuertoConciliacionSecundaria2(ContextoPod contexto) {
        this.contexto = contexto;
    }

    @Override
    public Resultado consultar(OrdenId sagaId, ExternalId externalId) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        double dado = contexto.random().nextDouble();
        if (dado < 0.50) {
            return new Resultado.SinResultado();
        } else if (dado < 0.80) {
            return new Resultado.Disponible(new RefRespuesta("mock-conciliacion-" + Long.toHexString(contexto.random().nextLong())));
        } else {
            return new Resultado.FalloRegistrado(MotivoFallo.errorNegocio("Fallo de negocio simulado por la conciliación"));
        }
    }
}
