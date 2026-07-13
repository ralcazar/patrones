package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;

public interface PuertoSecuencial {
    ResultadoPaso.ResultadoSecuencial1 iniciar(ComandoPaso.EjecutarSecuencial1 cmd);
    ResultadoPaso.ResultadoSecuencial2 confirmar(ComandoPaso.EjecutarSecuencial2 cmd);
}
