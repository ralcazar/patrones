package com.ejemplo.app.business.ordermanager.dominio.comun;

public enum EstadoSaga {
    INICIADA,
    EN_CURSO,
    /**
     * Fallo NO reintentable (p. ej. datos imparseables): sin reintento
     * automático, la saga queda parada con el paso culpable en
     * BLOQUEADO_SOPORTE y el flag de ticket en PENDIENTE. Los fallos
     * reintentables NUNCA llegan aquí: siguen reintentando indefinidamente.
     * Soporte la devuelve a EN_CURSO al reanudar o marcar-OK el paso.
     */
    FALLIDA,
    COMPLETADA,
    CANCELADA
}
