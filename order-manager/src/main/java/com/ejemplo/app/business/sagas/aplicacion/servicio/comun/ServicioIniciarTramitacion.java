package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoBusquedaTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoDatosNegocio;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.ExternalIdDuplicadoException;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;

/**
 * Iniciar una tramitación: pide los datos de negocio al servicio externo
 * (fuera de transacción) y, con la respuesta, crea los agregados (datos de
 * negocio + orden con la saga principal) en una transacción y responde al
 * instante con el sagaId. Al nacer con {@code proximoReintentoEn = ahora}, el
 * planificador la recoge como candidata inmediata en su siguiente pasada y
 * arranca la ejecución.
 *
 * Idempotencia: si ya existe una orden principal para el externalId (porque
 * el cliente reintentó el POST, o dos peticiones llegaron en carrera), se
 * devuelve la existente en vez de duplicar la tramitación. La carrera de dos
 * POST simultáneos para el mismo externalId la resuelve el índice único de
 * {@code datos_negocio.external_id}: el segundo en comitear falla (el
 * adaptador de persistencia traduce el conflicto a
 * {@link ExternalIdDuplicadoException}, ver
 * {@link com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio#crear}),
 * y se relee al ganador.
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
    private final PuertoBusquedaTramitacion busqueda;
    private ServicioIniciarTramitacion self;

    public ServicioIniciarTramitacion(RepositorioOrden repo, RepositorioDatosNegocio repoDatos,
            PuertoDatosNegocio puertoDatosNegocio, PuertoBusquedaTramitacion busqueda) {
        this.repo = repo;
        this.repoDatos = repoDatos;
        this.puertoDatosNegocio = puertoDatosNegocio;
        this.busqueda = busqueda;
        this.self = this; // valor por defecto (tests unitarios); ConfiguracionSagas lo sustituye por el proxy
    }

    /** Referencia al proxy transaccional de Spring de este mismo bean (ver ConfiguracionSagas). */
    public void establecerSelf(ServicioIniciarTramitacion self) {
        this.self = self;
    }

    @Override
    public OrdenId iniciar(ComandoIniciarTramitacion cmd) {
        var existente = busqueda.ordenPrincipalDe(cmd.externalId());
        if (existente.isPresent()) {
            return existente.get();
        }
        var respuesta = puertoDatosNegocio.obtener(cmd.externalId()); // REST fuera de tx
        try {
            return self.crearAgregados(cmd.externalId(), respuesta); // via proxy -> @Transactional
        } catch (ExternalIdDuplicadoException e) {
            // Carrera de dos POST simultáneos: el índice único de datos_negocio.external_id
            // hizo fallar a este; la otra petición ya comiteó. Responder idempotente.
            return busqueda.ordenPrincipalDe(cmd.externalId()).orElseThrow(() -> e);
        }
    }

    @Transactional
    public OrdenId crearAgregados(ExternalId externalId, PuertoDatosNegocio.RespuestaDatosNegocio respuesta) {
        var datosNegocioId = DatosNegocioId.nuevo();
        var datosNegocio = DatosNegocio.crear(datosNegocioId, externalId,
                respuesta.datoNegocio1(), respuesta.datoNegocio2(), respuesta.datoNegocio3());
        repoDatos.crear(datosNegocio, respuesta.documentos());
        var sagaId = OrdenId.nuevo();
        var saga = SagaPrincipal.crear(sagaId, externalId, datosNegocioId);
        var prioridad = respuesta.datoNegocio3().prioridad();
        repo.crear(OrdenRoot.nueva(saga, prioridad, Instant.now()));
        return sagaId;
    }
}
