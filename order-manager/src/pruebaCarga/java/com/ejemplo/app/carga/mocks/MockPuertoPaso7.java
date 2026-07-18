package com.ejemplo.app.carga.mocks;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso7;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.carga.ContextoPod;

/** Mock del servicio externo del PASO7 (ver {@code plan-pruebas-carga.md}, fase 2). */
@Component
public class MockPuertoPaso7 implements PuertoPaso7 {

    private static final String NOMBRE_PUERTO = "PuertoPaso7";

    private final ContextoPod contexto;

    public MockPuertoPaso7(ContextoPod contexto) {
        this.contexto = contexto;
    }

    @Override
    public ResultadoPasoPrincipal.ResultadoPaso7 ejecutar(ComandoPasoPrincipal.EjecutarPaso7 cmd, DatosNegocio datos) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        return new ResultadoPasoPrincipal.ResultadoPaso7(new RefPaso7("mock-paso7-" + Long.toHexString(contexto.random().nextLong())));
    }
}
