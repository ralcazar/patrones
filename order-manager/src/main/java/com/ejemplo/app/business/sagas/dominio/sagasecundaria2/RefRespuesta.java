package com.ejemplo.app.business.sagas.dominio.sagasecundaria2;

import org.jmolecules.ddd.annotation.ValueObject;

/** Referencia que produce la SOLICITUD (llega en el evento Kafka de respuesta). */
@ValueObject
public record RefRespuesta(String valor) {}
