package com.ejemplo.app.business.ordermanager.dominio.sagaprincipal;

/**
 * FSM de negocio de la saga principal. El orden de declaración de los estados
 * {@code PASOn_HECHO} es el orden del flujo normal; los de compensación
 * quedan al final porque solo se alcanzan desde una cancelación.
 */
public enum EstadoSagaPrincipal {
    INICIAL,
    PASO1_HECHO,
    PASO2_HECHO,
    PASO3_HECHO,
    PASO4_HECHO,
    PASO5_HECHO,
    PASO6_HECHO,
    PASO7_HECHO,
    TERMINADA,
    COMPENSAR_PASO2,
    COMPENSAR_PASO1,
    CANCELADA
}
