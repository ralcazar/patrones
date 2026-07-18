package com.ejemplo.app.carga.mocks;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso6;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso6;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.carga.ContextoPod;

/** Mock del servicio externo del PASO6 (ver {@code plan-pruebas-carga.md}, fase 2). */
@Component
public class MockPuertoPaso6 implements PuertoPaso6 {

    private static final String NOMBRE_PUERTO = "PuertoPaso6";

    private final ContextoPod contexto;

    public MockPuertoPaso6(ContextoPod contexto) {
        this.contexto = contexto;
    }

    @Override
    public ResultadoPasoPrincipal.ResultadoPaso6 ejecutar(ComandoPasoPrincipal.EjecutarPaso6 cmd) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        return new ResultadoPasoPrincipal.ResultadoPaso6(new RefPaso6("mock-paso6-" + Long.toHexString(contexto.random().nextLong())));
    }
}
