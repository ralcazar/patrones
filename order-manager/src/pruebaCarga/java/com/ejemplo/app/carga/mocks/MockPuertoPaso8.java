package com.ejemplo.app.carga.mocks;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso8;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso8;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.carga.ContextoPod;

/** Mock del servicio externo del PASO8 (ver {@code plan-pruebas-carga.md}, fase 2). */
@Component
public class MockPuertoPaso8 implements PuertoPaso8 {

    private static final String NOMBRE_PUERTO = "PuertoPaso8";

    private final ContextoPod contexto;

    public MockPuertoPaso8(ContextoPod contexto) {
        this.contexto = contexto;
    }

    @Override
    public ResultadoPasoPrincipal.ResultadoPaso8 ejecutar(ComandoPasoPrincipal.EjecutarPaso8 cmd) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        return new ResultadoPasoPrincipal.ResultadoPaso8(new RefPaso8("mock-paso8-" + Long.toHexString(contexto.random().nextLong())));
    }
}
