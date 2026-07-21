package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoIncidencias;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoPurgarCompletadas;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;

/**
 * Purga de tramitaciones completadas (criterio por tramitación): a
 * diferencia de la purga de adjuntos (que solo anula el contenido), esta
 * BORRA el agregado completo -- {@code datos_negocio} + documentos y las 4
 * órdenes de la tramitación (satélites + auditoría) -- para cada grupo
 * terminado antes del corte recibido. El corte (retención) lo calcula el
 * planificador de infraestructura -- este servicio no conoce "hoy" ni
 * cuántos días de retención hay, solo el {@link Instant} recibido (mismo
 * contrato que {@code ServicioLimpiezaDatos.purgarAnterioresA}).
 *
 * Orden de borrado (respeta las FK reales, sin {@code ON DELETE CASCADE} --
 * ver CLAUDE.md): la satélite {@code proceso_saga_principal} (borrada por
 * {@link RepositorioOrden#purgarPorExternalIds}) tiene FK a
 * {@code datos_negocio}, así que las órdenes (con sus satélites) se borran
 * PRIMERO y {@code datos_negocio} (con sus documentos) DESPUÉS: si se hiciera
 * al revés, el borrado de datos_negocio violaría esa FK mientras la satélite
 * todavía lo referencia.
 *
 * Idempotente: borrar una orden o un datos_negocio ya borrado es un no-op
 * (ver {@link RepositorioOrden#purgarPorExternalIds} y
 * {@link RepositorioDatosNegocio#buscarPorExternalId}), así que reintentar el
 * batch completo es seguro.
 *
 * Reintento operativo (ver {@link ReintentoOperativo}): {@link #purgarCompletadas}
 * NO es transaccional; reintenta la ejecución completa hasta agotar
 * reintentos y, si los agota, abre una incidencia en vez de propagar el
 * fallo. Cada intento pasa por {@code self} (el proxy transaccional de
 * Spring, inyectado por ConfiguracionSagas) para que {@link #ejecutar} no
 * pierda su {@code @Transactional} por auto-invocación.
 */
@Service
public class ServicioPurgarCompletadas implements CasoUsoPurgarCompletadas {

    private static final String TAREA = "purga-completadas";

    private final RepositorioOrden motor;
    private final RepositorioDatosNegocio repoDatos;
    private final PuertoIncidencias incidencias;
    private ServicioPurgarCompletadas self;

    public ServicioPurgarCompletadas(RepositorioOrden motor, RepositorioDatosNegocio repoDatos,
            PuertoIncidencias incidencias) {
        this.motor = motor;
        this.repoDatos = repoDatos;
        this.incidencias = incidencias;
        this.self = this; // valor por defecto (tests unitarios); ConfiguracionSagas lo sustituye por el proxy
    }

    /** Referencia al proxy transaccional de Spring de este mismo bean (ver ConfiguracionSagas). */
    public void establecerSelf(ServicioPurgarCompletadas self) {
        this.self = self;
    }

    @Override
    public long purgarCompletadas(Instant corte) {
        // via proxy -> @Transactional; si se agotan los reintentos, 0L (la última tx falló y deshizo)
        return ReintentoOperativo.ejecutar(TAREA, incidencias, () -> self.ejecutar(corte), 0L);
    }

    @Transactional
    public long ejecutar(Instant corte) {
        var externalIds = motor.externalIdsFinalizadosAntesDe(corte);
        if (externalIds.isEmpty()) {
            return 0L;
        }
        // Las órdenes (con sus satélites, incluida la que referencia datos_negocio) se
        // borran ANTES que datos_negocio: ver la javadoc de la clase.
        motor.purgarPorExternalIds(externalIds);
        var tramitacionesPurgadas = 0L;
        for (var externalId : externalIds) {
            var datos = repoDatos.buscarPorExternalId(externalId);
            if (datos.isPresent()) {
                repoDatos.borrar(datos.get().id());
                tramitacionesPurgadas++;
            }
        }
        return tramitacionesPurgadas;
    }
}
