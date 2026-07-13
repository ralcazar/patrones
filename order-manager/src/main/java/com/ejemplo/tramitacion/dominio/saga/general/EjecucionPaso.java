package com.ejemplo.tramitacion.dominio.saga.general;

/**
 * Estado de ejecución de un paso. Las transiciones las gobiernan los agregados
 * (métodos package-private); el exterior solo lee.
 */
public class EjecucionPaso {

    private final Paso paso;
    private EstadoPaso estado;
    private int intentos;
    private MotivoFallo ultimoFallo;

    private EjecucionPaso(Paso paso, EstadoPaso estado, int intentos, MotivoFallo ultimoFallo) {
        this.paso = paso;
        this.estado = estado;
        this.intentos = intentos;
        this.ultimoFallo = ultimoFallo;
    }

    public static EjecucionPaso nuevo(Paso paso) {
        return new EjecucionPaso(paso, EstadoPaso.PENDIENTE, 0, null);
    }

    /** Para el adaptador de persistencia. */
    public static EjecucionPaso rehidratar(Paso paso, EstadoPaso estado, int intentos, MotivoFallo ultimoFallo) {
        return new EjecucionPaso(paso, estado, intentos, ultimoFallo);
    }

    void solicitar()        { estado = EstadoPaso.SOLICITADO; }
    void completar()        { estado = EstadoPaso.COMPLETADO; }
    void completarManual()  { estado = EstadoPaso.COMPLETADO_MANUAL; }
    void esperarReintento() { estado = EstadoPaso.ESPERANDO_REINTENTO; }
    void bloquear()         { estado = EstadoPaso.BLOQUEADO_SOPORTE; }
    void cancelar()         { estado = EstadoPaso.CANCELADO; }
    void compensado()       { estado = EstadoPaso.COMPENSADO; }
    void resetearIntentos() { intentos = 0; ultimoFallo = null; }

    void registrarFallo(MotivoFallo motivo) {
        intentos++;
        ultimoFallo = motivo;
    }

    public Paso paso() { return paso; }
    public EstadoPaso estado() { return estado; }
    public int intentos() { return intentos; }
    public MotivoFallo ultimoFallo() { return ultimoFallo; }
}
