package com.patrones.sagamanager.domain.model;

import java.util.Objects;

/**
 * Clave natural/de negocio de una saga: la app de origen y la referencia externa que esa app
 * usa para correlacionar. id_externo es único por app, por eso la identidad es el par y no solo
 * el ExternalRef.
 */
public record ExternalIdentity(AppId appId, ExternalRef externalRef) {

	public ExternalIdentity {
		Objects.requireNonNull(appId, "appId must not be null");
		Objects.requireNonNull(externalRef, "externalRef must not be null");
	}

	public static ExternalIdentity of(AppId appId, ExternalRef externalRef) {
		return new ExternalIdentity(appId, externalRef);
	}
}
