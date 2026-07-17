package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoOrdenesTicketPendiente;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/** Query directa {@code intentos >= 8 AND ticket_abierto_en IS NULL AND resultado IS NULL}, sin cargar agregados. */
@Component
public class AdaptadorOrdenesTicketPendiente implements PuertoOrdenesTicketPendiente {

    private final OrdenJpaRepository ordenes;

    public AdaptadorOrdenesTicketPendiente(OrdenJpaRepository ordenes) {
        this.ordenes = ordenes;
    }

    @Override
    public List<OrdenTicketPendiente> buscar() {
        return ordenes.buscarTicketsPendientes().stream()
                .map(f -> new OrdenTicketPendiente(new TipoOrden(f.getTipo()), OrdenId.de(f.getOrdenId()),
                        ExternalId.de(f.getExternalId()), f.getIntentos()))
                .toList();
    }
}
