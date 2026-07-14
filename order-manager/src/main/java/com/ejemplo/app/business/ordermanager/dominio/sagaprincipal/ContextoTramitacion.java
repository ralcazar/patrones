package com.ejemplo.app.business.ordermanager.dominio.sagaprincipal;

import org.jmolecules.ddd.annotation.ValueObject;

import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso1;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso5;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso7;

/**
 * Datos que la saga principal acumula paso a paso. Value object inmutable:
 * {@link #aplicar} no muta, devuelve un contexto nuevo con la ref del paso
 * completado. Las refs aún no producidas son null.
 */
@ValueObject
public record ContextoTramitacion(
        DatoNegocio3 datoNegocio3,
        DatoNegocio2 datoNegocio2,
        RefPaso1 refPaso1,   // lo consume PASO2, PASO4 y la saga SECUNDARIA1
        RefPaso2 refPaso2,   // lo consume PASO3 y PASO4
        RefPaso3 refPaso3,
        RefPaso4 refPaso4,   // lo consume PASO5
        RefPaso5 refPaso5,   // lo consume PASO6, PASO7 y la saga SECUNDARIA2
        RefPaso6 refPaso6,
        RefPaso7 refPaso7,   // lo consume PASO8 y la saga SECUNDARIA3
        RefPaso8 refPaso8) {

    public static ContextoTramitacion inicial(DatoNegocio3 datos, DatoNegocio2 datoNegocio2) {
        return new ContextoTramitacion(datos, datoNegocio2,
                null, null, null, null, null, null, null, null);
    }

    /** Para el adaptador de persistencia. */
    public static ContextoTramitacion rehidratar(DatoNegocio3 datos, DatoNegocio2 datoNegocio2,
            RefPaso1 refPaso1, RefPaso2 refPaso2, RefPaso3 refPaso3, RefPaso4 refPaso4,
            RefPaso5 refPaso5, RefPaso6 refPaso6, RefPaso7 refPaso7, RefPaso8 refPaso8) {
        return new ContextoTramitacion(datos, datoNegocio2,
                refPaso1, refPaso2, refPaso3, refPaso4, refPaso5, refPaso6, refPaso7, refPaso8);
    }

    ContextoTramitacion aplicar(ResultadoPasoPrincipal r) {
        return switch (r) {
            case ResultadoPasoPrincipal.ResultadoPaso1(var ref) -> new ContextoTramitacion(
                    datoNegocio3, datoNegocio2, ref, refPaso2, refPaso3, refPaso4, refPaso5, refPaso6, refPaso7, refPaso8);
            case ResultadoPasoPrincipal.ResultadoPaso2(var ref) -> new ContextoTramitacion(
                    datoNegocio3, datoNegocio2, refPaso1, ref, refPaso3, refPaso4, refPaso5, refPaso6, refPaso7, refPaso8);
            case ResultadoPasoPrincipal.ResultadoPaso3(var ref) -> new ContextoTramitacion(
                    datoNegocio3, datoNegocio2, refPaso1, refPaso2, ref, refPaso4, refPaso5, refPaso6, refPaso7, refPaso8);
            case ResultadoPasoPrincipal.ResultadoPaso4(var ref) -> new ContextoTramitacion(
                    datoNegocio3, datoNegocio2, refPaso1, refPaso2, refPaso3, ref, refPaso5, refPaso6, refPaso7, refPaso8);
            case ResultadoPasoPrincipal.ResultadoPaso5(var ref) -> new ContextoTramitacion(
                    datoNegocio3, datoNegocio2, refPaso1, refPaso2, refPaso3, refPaso4, ref, refPaso6, refPaso7, refPaso8);
            case ResultadoPasoPrincipal.ResultadoPaso6(var ref) -> new ContextoTramitacion(
                    datoNegocio3, datoNegocio2, refPaso1, refPaso2, refPaso3, refPaso4, refPaso5, ref, refPaso7, refPaso8);
            case ResultadoPasoPrincipal.ResultadoPaso7(var ref) -> new ContextoTramitacion(
                    datoNegocio3, datoNegocio2, refPaso1, refPaso2, refPaso3, refPaso4, refPaso5, refPaso6, ref, refPaso8);
            case ResultadoPasoPrincipal.ResultadoPaso8(var ref) -> new ContextoTramitacion(
                    datoNegocio3, datoNegocio2, refPaso1, refPaso2, refPaso3, refPaso4, refPaso5, refPaso6, refPaso7, ref);
        };
    }
}
