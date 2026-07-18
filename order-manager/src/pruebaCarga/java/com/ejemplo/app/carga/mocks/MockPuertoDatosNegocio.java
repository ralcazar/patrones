package com.ejemplo.app.carga.mocks;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoDatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio1;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;
import com.ejemplo.app.carga.ContextoPod;

/**
 * Mock del servicio externo "DatosDeNegocio" ({@link PuertoDatosNegocio}):
 * en producción NO existe todavía ningún adaptador real (ver la javadoc del
 * puerto), así que el mock, además de simular latencia/fallo, tiene que
 * FABRICAR él mismo una respuesta sintética válida (datos escalares +
 * documentos) con el {@link java.util.Random} del pod.
 */
@Component
public class MockPuertoDatosNegocio implements PuertoDatosNegocio {

    private static final String NOMBRE_PUERTO = "PuertoDatosNegocio";
    private static final LocalDate EPOCA = LocalDate.of(2000, 1, 1);

    private final ContextoPod contexto;

    public MockPuertoDatosNegocio(ContextoPod contexto) {
        this.contexto = contexto;
    }

    @Override
    public RespuestaDatosNegocio obtener(ExternalId externalId) {
        SimuladorRestMock.simular(contexto, NOMBRE_PUERTO);
        var random = contexto.random();

        var datoNegocio1 = new DatoNegocio1(random.nextInt(1_000_000));
        var datoNegocio2 = new DatoNegocio2(EPOCA.plusDays(random.nextInt(365 * 20)));
        var datoNegocio3 = new DatoNegocio3("dato-negocio-3-" + Long.toHexString(random.nextLong()));

        int numDocumentos = 1 + random.nextInt(2); // 1 ó 2 documentos sintéticos
        var documentos = new java.util.ArrayList<DocumentoNegocio>(numDocumentos);
        for (int i = 0; i < numDocumentos; i++) {
            byte[] contenido = new byte[16 + random.nextInt(64)];
            random.nextBytes(contenido);
            documentos.add(new DocumentoNegocio("documento-sintetico-" + i + ".bin", "application/octet-stream", contenido));
        }

        return new RespuestaDatosNegocio(datoNegocio1, datoNegocio2, datoNegocio3, List.copyOf(documentos));
    }
}
