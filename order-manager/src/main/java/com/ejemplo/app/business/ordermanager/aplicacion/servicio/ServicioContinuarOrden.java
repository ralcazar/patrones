package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoContinuarOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.PoliticaReintentos;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/**
 * Bucle de ejecución de una orden: reclama el token bajo optimistic lock y
 * encadena pasos síncronos hasta que el procesador de la orden aparca, termina o falla.
 * Nunca recarga de BD dentro del bucle: {@code reclamarToken} usa la instancia
 * que {@code RepositorioOrden.guardar} devuelve ya persistida (con su version
 * real) para el primer paso, y cada señal {@code HayMasTrabajo} que devuelve
 * el procesador de la orden (ver {@link ProcesadorOrden}) lleva, por el mismo
 * motivo, la instancia actualizada para el siguiente. La misma instancia usada en
 * cada paso se reutiliza si el paso falla y hay que programar un reintento: así
 * el guardado del reintento conserva la protección por version frente a un
 * takeover concurrente (otro pod que reclamó tras vencer el lease).
 *
 * El reclamo del token y el guardado del reintento son transacciones propias
 * ({@code @Transactional}); entre medias corre el REST del paso (a través de
 * {@code procesador.ejecutarPaso}, otro bean). Como este servicio es un
 * POJO creado por {@code @Bean}, esas dos transacciones se invocan a través
 * de {@code self} (el propio proxy, inyectado por ConfiguracionOrderManager)
 * para que la anotación no se ignore por auto-invocación.
 */
@Service
public class ServicioContinuarOrden implements CasoUsoContinuarOrden {

    private final Map<TipoOrden, ProcesadorOrden> procesadores;
    private final RepositorioOrden repo;
    private final PoliticaReintentos politica;
    private final Duration lease;
    private final int lote;
    private ServicioContinuarOrden self;

    public ServicioContinuarOrden(Map<TipoOrden, ProcesadorOrden> procesadores, RepositorioOrden repo,
            PoliticaReintentos politica, Duration lease, int lote) {
        this.procesadores = procesadores;
        this.repo = repo;
        this.politica = politica;
        this.lease = lease;
        this.lote = lote;
        this.self = this; // valor por defecto (tests unitarios); ConfiguracionOrderManager lo sustituye por el proxy
    }

    /** Referencia al proxy transaccional de Spring de este mismo bean (ver ConfiguracionOrderManager). */
    public void establecerSelf(ServicioContinuarOrden self) {
        this.self = self;
    }

    /** Reclama el token y, si gana, encadena los pasos; devuelve si llegó a reclamar. */
    private boolean reclamarYEjecutar(OrdenId id, TipoOrden tipo) {
        // Reclamo con optimistic lock: si otro pod ya la tomó (o venció el
        // conflicto contra el guardado), nos retiramos en silencio.
        Optional<OrdenRoot> reclamada;
        try {
            reclamada = self.reclamarToken(id);
        } catch (ConcurrenciaOptimistaException e) {
            reclamada = Optional.empty();
        }
        if (reclamada.isEmpty()) {
            return false;
        }

        var procesador = procesadores.get(tipo);
        if (procesador == null) {
            throw new IllegalStateException("No hay ProcesadorOrden registrado para el tipo " + tipo);
        }

        // Para el primer paso reutilizamos la instancia que reclamarToken acaba
        // de cargar y guardar, ya persistida (con su version real): no hace
        // falta una recarga redundante justo después de reclamar.
        var orden = reclamada.get();
        var hayMasPasos = true;
        while (hayMasPasos) {
            SenalPaso senal;
            try {
                senal = procesador.ejecutarPaso(orden);
            } catch (ConcurrenciaOptimistaException e) {
                return true; // otro pod/actor tocó el agregado entre medias
            } catch (RuntimeException e) {
                // Reintento sobre la MISMA orden usada arriba (fix del takeover
                // seguro): si otro actor escribió entre medias, este guardado falla
                // por version igual que el de cualquier otro paso.
                self.programarReintento(orden);
                return true;
            }
            switch (senal) {
                // El procesador ya guardó esta instancia (reseteó intentos y renovó
                // el lease) y la señal trae la persistida: no hace falta recargar.
                case SenalPaso.HayMasTrabajo(var ordenActualizada) -> orden = ordenActualizada;
                case SenalPaso.Aparcar(var ventana) -> hayMasPasos = false; // ya persistido por el procesador
                case SenalPaso.Finalizada() -> hayMasPasos = false; // ya persistido por el procesador
            }
        }
        return true;
    }

    @Transactional
    public Optional<OrdenRoot> reclamarToken(OrdenId id) {
        var orden = repo.cargar(id);
        var ahora = Instant.now();
        if (!orden.estaViva() || orden.tieneTokenVigente(ahora)) {
            return Optional.empty();
        }
        orden.asignarToken(UUID.randomUUID(), lease, ahora);
        return Optional.of(repo.guardar(orden));
    }

    @Transactional
    public void programarReintento(OrdenRoot orden) {
        orden.programarReintento(politica, Instant.now());
        repo.guardar(orden);
    }

    @Override
    public boolean continuarSiguiente() {
        for (var candidata : repo.buscarEjecutables(Instant.now(), lote)) {
            if (reclamarYEjecutar(candidata.ordenId(), candidata.tipo())) {
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
