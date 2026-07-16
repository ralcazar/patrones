package com.ejemplo.app.business.ordermanager.dominio.comun;

/**
 * Cómo terminó la ejecución de una saga. Es el campo {@code resultado} de la
 * {@link OrdenRoot}: {@code null} mientras la saga sigue viva.
 *
 * No pertenece al estado de NEGOCIO (que vive en la FSM de cada Saga), sino
 * al estado OPERATIVO de la ejecución. Al terminar, la OrdenRoot no se elimina:
 * se conserva con este resultado hasta que la limpieza la purga.
 */
public enum ResultadoOrden {

    /** La saga recorrió su FSM de negocio hasta el final con éxito. */
    FINALIZADA_OK,

    /** La saga se canceló y se compensaron sus pasos (rama de compensación). */
    FINALIZADA_COMPENSADA
}
