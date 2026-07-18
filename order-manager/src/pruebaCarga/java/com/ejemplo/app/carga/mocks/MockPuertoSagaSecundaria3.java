package com.ejemplo.app.carga.mocks;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.ComandoPasoSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.RefEjecucion;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.ResultadoPasoSecundaria3;
import com.ejemplo.app.carga.ContextoPod;

/** Mock del servicio externo de la saga secundaria 3 (una llamada REST síncrona). */
@Component
public class MockPuertoSagaSecundaria3 implements PuertoSagaSecundaria3 {

    private static final String NOMBRE_PUERTO = "PuertoSagaSecundaria3";

    private final ContextoPod contexto;

    public MockPuertoSagaSecundaria3(ContextoPod contexto) {
        this.contexto = contexto;
    }

    @Override
    public ResultadoPasoSecundaria3.Ejecutada ejecutar(ComandoPasoSecundaria3.Ejecutar cmd) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        return new ResultadoPasoSecundaria3.Ejecutada(
                new RefEjecucion("mock-ejecucion-" + Long.toHexString(contexto.random().nextLong())));
    }
}
