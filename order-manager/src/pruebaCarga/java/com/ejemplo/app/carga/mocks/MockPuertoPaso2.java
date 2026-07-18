package com.ejemplo.app.carga.mocks;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso2;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso2;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.carga.ContextoPod;

/** Mock del servicio externo del PASO2 (ver {@code plan-pruebas-carga.md}, fase 2). */
@Component
public class MockPuertoPaso2 implements PuertoPaso2 {

    private static final String NOMBRE_PUERTO = "PuertoPaso2";

    private final ContextoPod contexto;

    public MockPuertoPaso2(ContextoPod contexto) {
        this.contexto = contexto;
    }

    @Override
    public ResultadoPasoPrincipal.ResultadoPaso2 ejecutar(ComandoPasoPrincipal.EjecutarPaso2 cmd, DatosNegocio datos,
            List<DocumentoNegocio> documentos) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        return new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("mock-paso2-" + Long.toHexString(contexto.random().nextLong())));
    }

    @Override
    public void compensar(ComandoPasoPrincipal.CompensarPaso2 cmd) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
    }
}
