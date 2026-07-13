package com.ejemplo.app.business.ordermanager.dominio.sagaprincipal;

import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso1;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso5;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso7;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoPaso;

/**
 * Lo que cada paso de la saga principal produce al completarse. Sealed: el
 * switch de aplicación al contexto es exhaustivo y el compilador avisa si
 * aparece un paso nuevo.
 */
public sealed interface ResultadoPasoPrincipal extends ResultadoPaso {

    record ResultadoPaso1(RefPaso1 ref) implements ResultadoPasoPrincipal {}
    record ResultadoPaso2(RefPaso2 ref) implements ResultadoPasoPrincipal {}
    record ResultadoPaso3(RefPaso3 ref) implements ResultadoPasoPrincipal {}
    record ResultadoPaso4(RefPaso4 ref) implements ResultadoPasoPrincipal {}
    record ResultadoPaso5(RefPaso5 ref) implements ResultadoPasoPrincipal {}
    record ResultadoPaso6(RefPaso6 ref) implements ResultadoPasoPrincipal {}
    record ResultadoPaso7(RefPaso7 ref) implements ResultadoPasoPrincipal {}
    record ResultadoPaso8(RefPaso8 ref) implements ResultadoPasoPrincipal {}
}
