package com.ejemplo.app.carga.mocks;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso4;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso4;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.carga.ContextoPod;

/** Mock del servicio externo del PASO4 (ver {@code plan-pruebas-carga.md}, fase 2). */
@Component
public class MockPuertoPaso4 implements PuertoPaso4 {

    private static final String NOMBRE_PUERTO = "PuertoPaso4";

    private final ContextoPod contexto;

    public MockPuertoPaso4(ContextoPod contexto) {
        this.contexto = contexto;
    }

    @Override
    public ResultadoPasoPrincipal.ResultadoPaso4 ejecutar(ComandoPasoPrincipal.EjecutarPaso4 cmd) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        return new ResultadoPasoPrincipal.ResultadoPaso4(new RefPaso4("mock-paso4-" + Long.toHexString(contexto.random().nextLong())));
    }
}
