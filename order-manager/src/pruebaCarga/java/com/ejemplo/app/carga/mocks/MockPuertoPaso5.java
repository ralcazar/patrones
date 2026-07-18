package com.ejemplo.app.carga.mocks;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso5;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.carga.ContextoPod;

/** Mock del servicio externo del PASO5 (ver {@code plan-pruebas-carga.md}, fase 2). */
@Component
public class MockPuertoPaso5 implements PuertoPaso5 {

    private static final String NOMBRE_PUERTO = "PuertoPaso5";

    private final ContextoPod contexto;

    public MockPuertoPaso5(ContextoPod contexto) {
        this.contexto = contexto;
    }

    @Override
    public ResultadoPasoPrincipal.ResultadoPaso5 ejecutar(ComandoPasoPrincipal.EjecutarPaso5 cmd) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        return new ResultadoPasoPrincipal.ResultadoPaso5(new RefPaso5("mock-paso5-" + Long.toHexString(contexto.random().nextLong())));
    }
}
