package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoIncidencias;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoPurgarAdjuntos;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;

/**
 * Purga de adjuntos (corte 30 días, criterio por tramitación): para cada
 * grupo de las 4 sagas que comparten {@code external_id} y están todas
 * terminadas hace más del corte, anula el contenido de los documentos de su
 * {@code datos_negocio} SIN borrar filas (ver
 * {@link RepositorioDatosNegocio#purgarAdjuntos}). Idempotente: la selección
 * ({@link RepositorioDatosNegocio#idsPorExternalIdsSinPurgar}) ya excluye lo
 * purgado en una pasada anterior, así que repetir el batch completo no
 * reprocesa nada.
 *
 * Reintento operativo (ver {@link ReintentoOperativo}): {@link #purgarAdjuntos()}
 * NO es transaccional; reintenta la ejecución completa hasta agotar
 * reintentos y, si los agota, abre una incidencia en vez de propagar el
 * fallo. Cada intento pasa por {@code self} (el proxy transaccional de
 * Spring, inyectado por ConfiguracionSagas) para que {@link #ejecutar} no
 * pierda su {@code @Transactional} por auto-invocación.
 */
@Service
public class ServicioPurgarAdjuntos implements CasoUsoPurgarAdjuntos {

    private static final String TAREA = "purga-adjuntos";
    private static final Duration RETENCION = Duration.ofDays(30);

    private final RepositorioOrden motor;
    private final RepositorioDatosNegocio repoDatos;
    private final PuertoIncidencias incidencias;
    private final Clock reloj;
    private ServicioPurgarAdjuntos self;

    public ServicioPurgarAdjuntos(RepositorioOrden motor, RepositorioDatosNegocio repoDatos,
            PuertoIncidencias incidencias, Clock reloj) {
        this.motor = motor;
        this.repoDatos = repoDatos;
        this.incidencias = incidencias;
        this.reloj = reloj;
        this.self = this; // valor por defecto (tests unitarios); ConfiguracionSagas lo sustituye por el proxy
    }

    /** Referencia al proxy transaccional de Spring de este mismo bean (ver ConfiguracionSagas). */
    public void establecerSelf(ServicioPurgarAdjuntos self) {
        this.self = self;
    }

    @Override
    public void purgarAdjuntos() {
        ReintentoOperativo.ejecutar(TAREA, incidencias, () -> self.ejecutar()); // via proxy -> @Transactional
    }

    @Transactional
    public void ejecutar() {
        var corte = Instant.now(reloj).minus(RETENCION);
        var externalIds = motor.externalIdsFinalizadosAntesDe(corte);
        if (externalIds.isEmpty()) {
            return;
        }
        var idsSinPurgar = repoDatos.idsPorExternalIdsSinPurgar(externalIds);
        idsSinPurgar.forEach(repoDatos::purgarAdjuntos);
    }
}
