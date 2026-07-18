package com.ejemplo.app.carga.mocks;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso1;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.carga.ContextoPod;

/** Mock del servicio externo del PASO1 (ver {@code plan-pruebas-carga.md}, fase 2). */
@Component
public class MockPuertoPaso1 implements PuertoPaso1 {

    private static final String NOMBRE_PUERTO = "PuertoPaso1";

    private final ContextoPod contexto;

    public MockPuertoPaso1(ContextoPod contexto) {
        this.contexto = contexto;
    }

    @Override
    public ResultadoPasoPrincipal.ResultadoPaso1 ejecutar(ComandoPasoPrincipal.EjecutarPaso1 cmd, DatosNegocio datos) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        return new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("mock-paso1-" + Long.toHexString(contexto.random().nextLong())));
    }

    @Override
    public void compensar(ComandoPasoPrincipal.CompensarPaso1 cmd) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
    }
}
