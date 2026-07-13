package com.ejemplo.app.business.ordermanager.dominio.comun;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Contexto recortado con el que arranca cada saga secundaria: exactamente los
 * datos que necesita y nada más. La saga arrancada nunca vuelve a mirar a la
 * que la originó; su única correlación con la tramitación es el externalId.
 *
 * RefPaso1/RefPaso5/RefPaso7 viven en este paquete común porque los produce
 * la principal y los consumen las secundarias (shared kernel mínimo).
 */
public sealed interface ContextoArranque {

    ExternalId externalId();

    @ValueObject
    record ArranqueSecundaria1(ExternalId externalId, RefPaso1 refPaso1) implements ContextoArranque {}

    @ValueObject
    record ArranqueSecundaria2(ExternalId externalId, RefPaso5 refPaso5) implements ContextoArranque {}

    @ValueObject
    record ArranqueSecundaria3(ExternalId externalId, RefPaso7 refPaso7) implements ContextoArranque {}
}
