package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * SPI de lectura por tipo de saga: {@link AdaptadorConsultaSagasSoporte}
 * indexa las implementaciones por {@link #tipo()} y delega en ellas cómo se
 * deriva el paso pendiente, si requiere datos manuales y si es cancelable a
 * partir del estado de la FSM de negocio (una tabla por tipo, sin cargar
 * agregados).
 */
public interface DescriptorSoporteOrden {

    TipoSaga tipo();

    /** El paso pendiente (null si la saga ya no avanza: terminada o en compensación). */
    String pasoPendiente(String estado);

    boolean datosManualesObligatorios(String estado);

    boolean cancelable(String estado);
}
