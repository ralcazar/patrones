package com.ejemplo.app.infraestructure.ordermanager.saga;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;

/**
 * Implementación de UnidadDeTrabajo con TransactionTemplate. Los servicios de
 * saga abren aquí la transacción; ServicioOrdenes.encolar (REQUIRED) se une a
 * ella, garantizando el commit atómico de estado + tareas.
 */
@Component
public class UnidadDeTrabajoSpring implements UnidadDeTrabajo {

    private final TransactionTemplate template;

    public UnidadDeTrabajoSpring(TransactionTemplate template) {
        this.template = template;
    }

    @Override
    public <T> T enTransaccion(Supplier<T> accion) {
        return template.execute(status -> accion.get());
    }
}
