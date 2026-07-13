package com.ejemplo.app.business.ordermanager.dominio.comun;

import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.PasoSagaPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.PasoSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.PasoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.PasoSagaSecundaria3;

/**
 * Marcadora que implementan los 4 enums de paso (uno por saga). Permite que
 * las APIs transversales (tareas, tickets, soporte) manejen "un paso" sin
 * conocer la saga concreta; cada agregado trabaja siempre con SU enum.
 */
public interface PasoSaga {

    /** La implementan los enums gratis. */
    String name();

    /** Frontera de deserialización: reconstruye el paso desde su nombre persistido. */
    static PasoSaga de(TipoSaga tipo, String nombre) {
        return switch (tipo) {
            case PRINCIPAL   -> PasoSagaPrincipal.valueOf(nombre);
            case SECUNDARIA1 -> PasoSagaSecundaria1.valueOf(nombre);
            case SECUNDARIA2 -> PasoSagaSecundaria2.valueOf(nombre);
            case SECUNDARIA3 -> PasoSagaSecundaria3.valueOf(nombre);
        };
    }
}
