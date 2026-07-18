package com.ejemplo.app.carga.mocks;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.carga.ContextoPod;

/** Mock del servicio externo del PASO3 (ver {@code plan-pruebas-carga.md}, fase 2). */
@Component
public class MockPuertoPaso3 implements PuertoPaso3 {

    private static final String NOMBRE_PUERTO = "PuertoPaso3";

    private final ContextoPod contexto;

    public MockPuertoPaso3(ContextoPod contexto) {
        this.contexto = contexto;
    }

    @Override
    public ResultadoPasoPrincipal.ResultadoPaso3 ejecutar(ComandoPasoPrincipal.EjecutarPaso3 cmd) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        return new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("mock-paso3-" + Long.toHexString(contexto.random().nextLong())));
    }
}
