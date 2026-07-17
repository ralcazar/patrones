package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/**
 * SPI de lectura por tipo de orden: {@link AdaptadorConsultaOrdenesSoporte}
 * indexa las implementaciones por {@link #tipo()} y delega en ellas cómo se
 * deriva el paso pendiente, si requiere datos manuales y si es cancelable a
 * partir del estado de la FSM de negocio (una tabla por tipo, sin cargar
 * agregados).
 */
public interface DescriptorSoporteOrden {

    TipoOrden tipo();

    /** El paso pendiente (null si la orden ya no avanza: terminada o en compensación). */
    String pasoPendiente(String estado);

    boolean datosManualesObligatorios(String estado);

    boolean cancelable(String estado);
}
