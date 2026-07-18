package com.ejemplo.app.business.ordermanager.dominio;

import java.util.UUID;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Identificador externo de la tramitación. Siempre existe, es único por
 * tramitación y es la ÚNICA correlación entre la orden principal y sus tres
 * secundarias: no hay FK ni relación de objetos entre ellas.
 */
@ValueObject
public record ExternalId(UUID valor) {
    public static ExternalId de(String valor) { return new ExternalId(UUID.fromString(valor)); }
}
