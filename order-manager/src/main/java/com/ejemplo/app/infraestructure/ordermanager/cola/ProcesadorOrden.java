package com.ejemplo.app.infraestructure.ordermanager.cola;

/**
 * Lógica de negocio de una orden: el punto donde el gestor entrega el trabajo
 * a quien sepa procesarlo (en esta aplicación, el ProcesadorOrdenSaga).
 * El gestor solo conoce este contrato; no sabe nada de sagas.
 *
 * CONTRATO IMPORTANTE: procesar(...) DEBE ser idempotente. Con el patrón lease,
 * si el procesamiento supera el timeout otro trabajador puede reclamar la misma
 * orden y procesarla de nuevo. La idempotencia es lo que hace ese reproceso
 * inofensivo.
 *
 * Se ejecuta FUERA de cualquier transacción de ServicioOrdenes: no asumas una
 * transacción activa; gestiona la tuya propia si la necesitas.
 */
public interface ProcesadorOrden {

    void procesar(Orden orden);
}
