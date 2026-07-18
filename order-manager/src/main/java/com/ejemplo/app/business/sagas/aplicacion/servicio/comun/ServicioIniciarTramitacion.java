package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoDatosNegocio;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;

/**
 * Iniciar una tramitación: pide los datos de negocio al servicio externo
 * (fuera de transacción) y, con la respuesta, crea los agregados (datos de
 * negocio + orden con la saga principal) en una transacción y responde al
 * instante con el sagaId. Al nacer con {@code proximoReintentoEn = ahora}, el
 * planificador la recoge como candidata inmediata en su siguiente pasada y
 * arranca la ejecución.
 *
 * El REST ocurre fuera de transacción; solo crear y guardar los agregados va
 * en {@code @Transactional}. Como este servicio es un POJO creado por
 * {@code @Bean}, una llamada interna (this.crearAgregados(...)) NO pasaría
 * por el proxy transaccional de Spring, así que se invoca a través de
 * {@code self}: la referencia al propio proxy, inyectada por
 * ConfiguracionSagas (ver {@link #establecerSelf}).
 */
@Service
public class ServicioIniciarTramitacion implements CasoUsoIniciarTramitacion {

    private final RepositorioOrden repo;
    private final RepositorioDatosNegocio repoDatos;
    private final PuertoDatosNegocio puertoDatosNegocio;
    private ServicioIniciarTramitacion self;

    public ServicioIniciarTramitacion(RepositorioOrden repo, RepositorioDatosNegocio repoDatos,
            PuertoDatosNegocio puertoDatosNegocio) {
        this.repo = repo;
        this.repoDatos = repoDatos;
        this.puertoDatosNegocio = puertoDatosNegocio;
        this.self = this; // valor por defecto (tests unitarios); ConfiguracionSagas lo sustituye por el proxy
    }

    /** Referencia al proxy transaccional de Spring de este mismo bean (ver ConfiguracionSagas). */
    public void establecerSelf(ServicioIniciarTramitacion self) {
        this.self = self;
    }

    @Override
    public OrdenId iniciar(ComandoIniciarTramitacion cmd) {
        var respuesta = puertoDatosNegocio.obtener(cmd.externalId()); // REST fuera de tx
        return self.crearAgregados(cmd.externalId(), respuesta); // via proxy -> @Transactional
    }

    @Transactional
    public OrdenId crearAgregados(ExternalId externalId, PuertoDatosNegocio.RespuestaDatosNegocio respuesta) {
        var datosNegocioId = DatosNegocioId.nuevo();
        var datosNegocio = DatosNegocio.crear(datosNegocioId, externalId,
                respuesta.datoNegocio1(), respuesta.datoNegocio2(), respuesta.datoNegocio3());
        repoDatos.crear(datosNegocio, respuesta.documentos());
        var sagaId = OrdenId.nuevo();
        var saga = SagaPrincipal.crear(sagaId, externalId, datosNegocioId);
        repo.crear(OrdenRoot.nueva(saga, Instant.now()));
        return sagaId;
    }
}
