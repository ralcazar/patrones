package com.ejemplo.app.business.ordermanager.dominio.sagaprincipal;

import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso1;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso5;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso7;

/** Lo que la saga principal ordena ejecutar. La capa de aplicación lo traduce a llamadas a puertos. */
public sealed interface ComandoPasoPrincipal extends ComandoPaso {

    record EjecutarPaso1(ExternalId externalId, DatoNegocio3 datos) implements ComandoPasoPrincipal {}
    record EjecutarPaso2(RefPaso1 refPaso1) implements ComandoPasoPrincipal {}
    record EjecutarPaso3(ExternalId externalId, RefPaso2 refPaso2) implements ComandoPasoPrincipal {}
    record EjecutarPaso4(RefPaso1 refPaso1, RefPaso2 refPaso2) implements ComandoPasoPrincipal {}
    record EjecutarPaso5(RefPaso4 refPaso4) implements ComandoPasoPrincipal {}
    record EjecutarPaso6(RefPaso5 refPaso5) implements ComandoPasoPrincipal {}
    record EjecutarPaso7(RefPaso5 refPaso5, DatoNegocio2 datoNegocio2) implements ComandoPasoPrincipal {}
    record EjecutarPaso8(ExternalId externalId, RefPaso7 refPaso7) implements ComandoPasoPrincipal {}

    // --- compensaciones (solo antes del punto de no retorno) ---
    record CompensarPaso1(RefPaso1 refPaso1) implements ComandoPasoPrincipal {}
    record CompensarPaso2(RefPaso2 refPaso2) implements ComandoPasoPrincipal {}
}
