package com.ejemplo.app.business.ordermanager.dominio;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Peso de planificación de una orden: a mayor {@code peso}, antes la coge el
 * planificador (ver
 * {@link com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden}
 * #buscarEjecutables). Es un VALOR NEUTRO: el motor ordena por él pero no
 * sabe qué lo determina (lo fija quien crea la orden, p. ej. una saga a
 * partir de su dato de negocio). {@link #normal()} (peso 0) es el valor por
 * defecto de las órdenes sin prioridad especial.
 */
@ValueObject
public record Prioridad(int peso) {
    private static final Prioridad NORMAL = new Prioridad(0);
    public static Prioridad normal() { return NORMAL; }
}
