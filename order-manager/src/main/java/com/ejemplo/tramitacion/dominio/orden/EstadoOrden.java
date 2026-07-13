package com.ejemplo.tramitacion.dominio.orden;

/**
 * Estados de una orden. Los nombres coinciden EXACTAMENTE con los literales
 * usados en las consultas nativas de RepositorioOrdenes ('PENDIENTE', etc.),
 * porque el estado se persiste como String (@Enumerated(STRING)).
 */
public enum EstadoOrden {
    PENDIENTE,   // pendiente de procesar
    EN_PROCESO,  // reclamada por un trabajador (con lease vivo mientras reclamada_en es reciente)
    COMPLETADA,  // procesada con éxito
    FALLIDA      // procesada con error
}
