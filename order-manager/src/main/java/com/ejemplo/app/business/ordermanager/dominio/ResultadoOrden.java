package com.ejemplo.app.business.ordermanager.dominio;

/**
 * Cómo terminó la ejecución de una orden. Es el campo {@code resultado} de la
 * {@link OrdenRoot}: {@code null} mientras la orden sigue viva.
 *
 * No pertenece al estado de NEGOCIO (que vive en la FSM de cada Proceso), sino
 * al estado OPERATIVO de la ejecución. Al terminar, la OrdenRoot no se elimina:
 * se conserva con este resultado hasta que la limpieza la purga.
 */
public enum ResultadoOrden {

    /** El proceso recorrió su FSM de negocio hasta el final con éxito. */
    FINALIZADA_OK,

    /** El proceso se canceló y se compensaron sus pasos (rama de compensación). */
    FINALIZADA_COMPENSADA
}
