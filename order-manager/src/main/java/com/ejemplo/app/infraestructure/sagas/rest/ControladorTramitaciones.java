package com.ejemplo.app.infraestructure.sagas.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ejemplo.app.business.ordermanager.dominio.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion.ComandoIniciarTramitacion;

/**
 * Adaptador de entrada REST de {@code POST /tramitaciones}: SOLO invoca el
 * caso de uso de aplicación, nunca un puerto o adaptador de salida
 * directamente (regla de arquitectura entrada -> aplicación -> salida del
 * CLAUDE.md). Si el servicio externo de datos de negocio falla
 * ({@link ExcepcionServicioExterno}), no queda nada persistido: la excepción
 * salta antes de crear los agregados (ver
 * {@link com.ejemplo.app.business.sagas.aplicacion.servicio.comun.ServicioIniciarTramitacion}),
 * así que basta con traducirla a 502 sin ninguna compensación.
 */
@RestController
public class ControladorTramitaciones {

    private final CasoUsoIniciarTramitacion casoUso;

    public ControladorTramitaciones(CasoUsoIniciarTramitacion casoUso) {
        this.casoUso = casoUso;
    }

    @PostMapping("/tramitaciones")
    public ResponseEntity<RespuestaTramitacion> iniciar(@RequestBody PeticionTramitacion peticion) {
        var ordenId = casoUso.iniciar(new ComandoIniciarTramitacion(ExternalId.de(peticion.externalId())));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RespuestaTramitacion(ordenId.valor().toString()));
    }

    /** El REST externo de datos de negocio ha fallado (timeout, error técnico...): 502, nada persistido. */
    @ExceptionHandler(ExcepcionServicioExterno.class)
    public ResponseEntity<Void> alFallarElServicioExterno(ExcepcionServicioExterno e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }

    public record PeticionTramitacion(String externalId) {}

    public record RespuestaTramitacion(String ordenId) {}
}
