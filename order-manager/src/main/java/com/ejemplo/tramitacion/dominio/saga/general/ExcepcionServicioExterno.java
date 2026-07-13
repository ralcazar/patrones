package com.ejemplo.tramitacion.dominio.saga.general;

/** La lanzan los adaptadores de los puertos de salida ante fallos del servicio remoto. */
public class ExcepcionServicioExterno extends RuntimeException {
    private final transient MotivoFallo motivo;

    public ExcepcionServicioExterno(MotivoFallo motivo, Throwable causa) {
        super(motivo.detalle(), causa);
        this.motivo = motivo;
    }

    public MotivoFallo motivo() { return motivo; }
}
