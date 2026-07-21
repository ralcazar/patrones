package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoIncidencias;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoPurgarAdjuntos;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;

/**
 * Purga de adjuntos (criterio por tramitación): para cada grupo de las 4
 * sagas que comparten {@code external_id} y están todas terminadas antes del
 * corte recibido, anula el contenido de los documentos de su
 * {@code datos_negocio} SIN borrar filas (ver
 * {@link RepositorioDatosNegocio#purgarAdjuntos}). El corte (retención) lo
 * calcula el planificador de infraestructura -- este servicio no conoce
 * "hoy" ni cuántos días de retención hay, solo el {@link Instant} recibido.
 * Idempotente: la selección
 * ({@link RepositorioDatosNegocio#idsPorExternalIdsSinPurgar}) ya excluye lo
 * purgado en una pasada anterior, así que repetir el batch completo no
 * reprocesa nada.
 *
 * Reintento operativo (ver {@link ReintentoOperativo}): {@link #purgarAdjuntos}
 * NO es transaccional; reintenta la ejecución completa hasta agotar
 * reintentos y, si los agota, abre una incidencia en vez de propagar el
 * fallo. Cada intento pasa por {@code self} (el proxy transaccional de
 * Spring, inyectado por ConfiguracionSagas) para que {@link #ejecutar} no
 * pierda su {@code @Transactional} por auto-invocación.
 */
@Service
public class ServicioPurgarAdjuntos implements CasoUsoPurgarAdjuntos {

    private static final String TAREA = "purga-adjuntos";

    private final RepositorioOrden motor;
    private final RepositorioDatosNegocio repoDatos;
    private final PuertoIncidencias incidencias;
    private ServicioPurgarAdjuntos self;

    public ServicioPurgarAdjuntos(RepositorioOrden motor, RepositorioDatosNegocio repoDatos,
            PuertoIncidencias incidencias) {
        this.motor = motor;
        this.repoDatos = repoDatos;
        this.incidencias = incidencias;
        this.self = this; // valor por defecto (tests unitarios); ConfiguracionSagas lo sustituye por el proxy
    }

    /** Referencia al proxy transaccional de Spring de este mismo bean (ver ConfiguracionSagas). */
    public void establecerSelf(ServicioPurgarAdjuntos self) {
        this.self = self;
    }

    @Override
    public long purgarAdjuntos(Instant corte) {
        // via proxy -> @Transactional; si se agotan los reintentos, 0L (la última tx falló y deshizo)
        return ReintentoOperativo.ejecutar(TAREA, incidencias, () -> self.ejecutar(corte), 0L);
    }

    @Transactional
    public long ejecutar(Instant corte) {
        var externalIds = motor.externalIdsFinalizadosAntesDe(corte);
        if (externalIds.isEmpty()) {
            return 0L;
        }
        var idsSinPurgar = repoDatos.idsPorExternalIdsSinPurgar(externalIds);
        idsSinPurgar.forEach(repoDatos::purgarAdjuntos);
        return idsSinPurgar.size();
    }
}
