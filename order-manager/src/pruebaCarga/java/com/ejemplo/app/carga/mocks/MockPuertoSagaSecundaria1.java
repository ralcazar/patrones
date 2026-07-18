package com.ejemplo.app.carga.mocks;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ComandoPasoSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefConfirmacion;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefInicio;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ResultadoPasoSecundaria1;
import com.ejemplo.app.carga.ContextoPod;

/** Mock del servicio externo de la saga secundaria 1 (dos métodos: INICIO y CONFIRMACION). */
@Component
public class MockPuertoSagaSecundaria1 implements PuertoSagaSecundaria1 {

    private static final String NOMBRE_PUERTO = "PuertoSagaSecundaria1";

    private final ContextoPod contexto;

    public MockPuertoSagaSecundaria1(ContextoPod contexto) {
        this.contexto = contexto;
    }

    @Override
    public ResultadoPasoSecundaria1.Iniciada iniciar(ComandoPasoSecundaria1.Iniciar cmd) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        return new ResultadoPasoSecundaria1.Iniciada(new RefInicio("mock-inicio-" + Long.toHexString(contexto.random().nextLong())));
    }

    @Override
    public ResultadoPasoSecundaria1.Confirmada confirmar(ComandoPasoSecundaria1.Confirmar cmd) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        return new ResultadoPasoSecundaria1.Confirmada(
                new RefConfirmacion("mock-confirmacion-" + Long.toHexString(contexto.random().nextLong())));
    }
}
