package com.ejemplo.app.business.sagas.dominio.datosnegocio;

import org.jmolecules.ddd.annotation.ValueObject;

import com.ejemplo.app.business.ordermanager.dominio.Prioridad;

/** Uno de los tres escalares de negocio obtenidos al iniciar la tramitación (ver {@link DatosNegocio}). */
@ValueObject
public record DatoNegocio3(String valor) {

    /**
     * Prioridad de planificación derivada del origen: ORIGEN2 &gt; ORIGEN1 &gt;
     * ORIGEN3; cualquier otro valor cae a {@link Prioridad#normal()} (la más baja).
     */
    public Prioridad prioridad() {
        return Origen.de(valor).prioridad();
    }

    private enum Origen {
        ORIGEN2(30), ORIGEN1(20), ORIGEN3(10), OTRO(0);

        private final int peso;

        Origen(int peso) { this.peso = peso; }

        Prioridad prioridad() { return new Prioridad(peso); }

        static Origen de(String valor) {
            for (var origen : values()) {
                if (origen != OTRO && origen.name().equals(valor)) {
                    return origen;
                }
            }
            return OTRO;
        }
    }
}
