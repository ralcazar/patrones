package com.ejemplo.app.business.ordermanager.dominio.sagaprincipal;

import org.jmolecules.ddd.annotation.Entity;

import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso1;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso5;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso7;

/** Datos que la saga principal acumula paso a paso. */
@Entity
public class ContextoTramitacion {

    private final DatoNegocio3 datoNegocio3;
    private final DatoNegocio2 datoNegocio2;

    private RefPaso1 refPaso1;   // lo consume PASO2, PASO4 y la saga SECUNDARIA1
    private RefPaso2 refPaso2;   // lo consume PASO3 y PASO4
    private RefPaso3 refPaso3;
    private RefPaso4 refPaso4;   // lo consume PASO5
    private RefPaso5 refPaso5;   // lo consume PASO6, PASO7 y la saga SECUNDARIA2
    private RefPaso6 refPaso6;
    private RefPaso7 refPaso7;   // lo consume PASO8 y la saga SECUNDARIA3
    private RefPaso8 refPaso8;

    private ContextoTramitacion(DatoNegocio3 datoNegocio3, DatoNegocio2 datoNegocio2) {
        this.datoNegocio3 = datoNegocio3;
        this.datoNegocio2 = datoNegocio2;
    }

    public static ContextoTramitacion inicial(DatoNegocio3 datos, DatoNegocio2 datoNegocio2) {
        return new ContextoTramitacion(datos, datoNegocio2);
    }

    /** Para el adaptador de persistencia. */
    public static ContextoTramitacion rehidratar(DatoNegocio3 datos, DatoNegocio2 datoNegocio2,
            RefPaso1 refPaso1, RefPaso2 refPaso2, RefPaso3 refPaso3, RefPaso4 refPaso4,
            RefPaso5 refPaso5, RefPaso6 refPaso6, RefPaso7 refPaso7, RefPaso8 refPaso8) {
        var ctx = new ContextoTramitacion(datos, datoNegocio2);
        ctx.refPaso1 = refPaso1;
        ctx.refPaso2 = refPaso2;
        ctx.refPaso3 = refPaso3;
        ctx.refPaso4 = refPaso4;
        ctx.refPaso5 = refPaso5;
        ctx.refPaso6 = refPaso6;
        ctx.refPaso7 = refPaso7;
        ctx.refPaso8 = refPaso8;
        return ctx;
    }

    void aplicar(ResultadoPasoPrincipal r) {
        switch (r) {
            case ResultadoPasoPrincipal.ResultadoPaso1(var ref) -> refPaso1 = ref;
            case ResultadoPasoPrincipal.ResultadoPaso2(var ref) -> refPaso2 = ref;
            case ResultadoPasoPrincipal.ResultadoPaso3(var ref) -> refPaso3 = ref;
            case ResultadoPasoPrincipal.ResultadoPaso4(var ref) -> refPaso4 = ref;
            case ResultadoPasoPrincipal.ResultadoPaso5(var ref) -> refPaso5 = ref;
            case ResultadoPasoPrincipal.ResultadoPaso6(var ref) -> refPaso6 = ref;
            case ResultadoPasoPrincipal.ResultadoPaso7(var ref) -> refPaso7 = ref;
            case ResultadoPasoPrincipal.ResultadoPaso8(var ref) -> refPaso8 = ref;
        }
    }

    public DatoNegocio3 datoNegocio3() { return datoNegocio3; }
    public DatoNegocio2 datoNegocio2() { return datoNegocio2; }
    public RefPaso1 refPaso1() { return refPaso1; }
    public RefPaso2 refPaso2() { return refPaso2; }
    public RefPaso3 refPaso3() { return refPaso3; }
    public RefPaso4 refPaso4() { return refPaso4; }
    public RefPaso5 refPaso5() { return refPaso5; }
    public RefPaso6 refPaso6() { return refPaso6; }
    public RefPaso7 refPaso7() { return refPaso7; }
    public RefPaso8 refPaso8() { return refPaso8; }
}
