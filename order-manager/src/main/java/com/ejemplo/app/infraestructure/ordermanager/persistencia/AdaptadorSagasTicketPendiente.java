package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagasTicketPendiente;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoOrden;

/** Query directa {@code intentos >= 8 AND ticket_abierto_en IS NULL AND resultado IS NULL}, sin cargar agregados. */
@Component
public class AdaptadorSagasTicketPendiente implements PuertoSagasTicketPendiente {

    private final OrdenJpaRepository ordenes;

    public AdaptadorSagasTicketPendiente(OrdenJpaRepository ordenes) {
        this.ordenes = ordenes;
    }

    @Override
    public List<SagaTicketPendiente> buscar() {
        return ordenes.buscarTicketsPendientes().stream()
                .map(f -> new SagaTicketPendiente(new TipoOrden(f.getTipo()), SagaId.de(f.getSagaId()),
                        ExternalId.de(f.getExternalId()), f.getIntentos()))
                .toList();
    }
}
