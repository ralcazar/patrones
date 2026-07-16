package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoContinuarSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.PoliticaReintentos;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Bucle de ejecución de una saga: reclama el token bajo optimistic lock y
 * encadena pasos síncronos hasta que el servicio de la saga aparca, termina o falla.
 * Carga el agregado una única vez por paso, antes del REST (ver
 * {@link ServicioSaga}), y reutiliza esa misma instancia si el paso falla
 * y hay que programar un reintento: así el guardado del reintento conserva
 * la protección por version frente a un takeover concurrente (otro pod que
 * reclamó tras vencer el lease).
 *
 * El reclamo del token y el guardado del reintento son transacciones propias
 * ({@code @Transactional}); entre medias corre el REST del paso (a través de
 * {@code servicioSaga.ejecutarPaso}, otro bean). Como este servicio es un
 * POJO creado por {@code @Bean}, esas dos transacciones se invocan a través
 * de {@code self} (el propio proxy, inyectado por ConfiguracionAplicacion)
 * para que la anotación no se ignore por auto-invocación.
 */
@Service
public class ServicioContinuarSaga implements CasoUsoContinuarSaga {

    private final Map<TipoSaga, ServicioSaga> serviciosSaga;
    private final RepositorioOrden repo;
    private final PoliticaReintentos politica;
    private final Duration lease;
    private final int lote;
    private ServicioContinuarSaga self;

    public ServicioContinuarSaga(Map<TipoSaga, ServicioSaga> serviciosSaga, RepositorioOrden repo,
            PoliticaReintentos politica, Duration lease, int lote) {
        this.serviciosSaga = serviciosSaga;
        this.repo = repo;
        this.politica = politica;
        this.lease = lease;
        this.lote = lote;
        this.self = this; // valor por defecto (tests unitarios); ConfiguracionAplicacion lo sustituye por el proxy
    }

    /** Referencia al proxy transaccional de Spring de este mismo bean (ver ConfiguracionAplicacion). */
    public void establecerSelf(ServicioContinuarSaga self) {
        this.self = self;
    }

    /** Reclama el token y, si gana, encadena los pasos; devuelve si llegó a reclamar. */
    private boolean reclamarYEjecutar(SagaId id, TipoSaga tipo) {
        // Reclamo con optimistic lock: si otro pod ya la tomó (o venció el
        // conflicto contra el guardado), nos retiramos en silencio.
        boolean reclamada;
        try {
            reclamada = self.reclamarToken(id);
        } catch (ConcurrenciaOptimistaException e) {
            reclamada = false;
        }
        if (!reclamada) {
            return false;
        }

        var servicioSaga = serviciosSaga.get(tipo);
        while (true) {
            var orden = repo.cargar(id); // una única carga por paso, antes del REST
            SenalPaso senal;
            try {
                senal = servicioSaga.ejecutarPaso(orden);
            } catch (ConcurrenciaOptimistaException e) {
                return true; // otro pod/actor tocó el agregado entre medias
            } catch (RuntimeException e) {
                // Reintento sobre la MISMA orden cargada arriba (fix del takeover
                // seguro): si otro actor escribió entre medias, este guardado falla
                // por version igual que el de cualquier otro paso.
                self.programarReintento(orden);
                return true;
            }
            switch (senal) {
                case SenalPaso.HayMasTrabajo() -> { /* el servicio de la saga ya reseteó intentos y renovó el lease */ }
                case SenalPaso.Aparcar(var ventana) -> { return true; } // ya persistido por el servicio de la saga
                case SenalPaso.Finalizada(var resultado) -> { return true; } // ya persistido por el servicio de la saga
            }
        }
    }

    @Transactional
    public boolean reclamarToken(SagaId id) {
        var orden = repo.cargar(id);
        var ahora = Instant.now();
        if (!orden.estaViva() || orden.tieneTokenVigente(ahora)) {
            return false;
        }
        orden.asignarToken(UUID.randomUUID(), lease, ahora);
        repo.guardar(orden);
        return true;
    }

    @Transactional
    public void programarReintento(OrdenRoot orden) {
        orden.programarReintento(politica, Instant.now());
        repo.guardar(orden);
    }

    @Override
    public boolean continuarSiguiente() {
        for (var candidata : repo.buscarEjecutables(Instant.now(), lote)) {
            if (reclamarYEjecutar(candidata.sagaId(), candidata.tipo())) {
                return true;   // procesada una; el worker volverá a llamar
            }                  // otro worker/pod la ganó: probamos la siguiente del lote
        }
        return false;          // lote agotado sin ganar ninguna: no hay trabajo
    }

    @Override
    public boolean hayTrabajoPendiente() {
        return repo.hayEjecutables(Instant.now());
    }
}
