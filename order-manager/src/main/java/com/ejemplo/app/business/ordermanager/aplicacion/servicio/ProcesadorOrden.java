package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/**
 * Ejecuta UN paso de un tipo de orden concreto: recibe el agregado ya cargado
 * por el llamante (una única carga por paso, antes del REST; ver
 * {@link ServicioContinuarOrden}), hace el REST del paso fuera de transacción
 * (puede lanzar; el llamante lo convierte en reintento sobre esa misma
 * instancia) y, en UNA sola transacción, aplica el resultado a la orden y la
 * parte operativa de la señal devuelta (reset de intentos + renovación de
 * lease, o aparcar, o finalizar), y guarda esa misma instancia una única vez.
 * Cuando la señal es {@link SenalPaso.HayMasTrabajo}, debe llevar la instancia
 * tal como quedó persistida (con su version real, la que devuelve
 * {@code RepositorioOrden.guardar}), para que el llamante encadene el
 * siguiente paso sin tener que recargar de BD. Una implementación por
 * TipoOrden.
 */
public interface ProcesadorOrden {

    TipoOrden tipo();

    SenalPaso ejecutarPaso(OrdenRoot orden);
}
