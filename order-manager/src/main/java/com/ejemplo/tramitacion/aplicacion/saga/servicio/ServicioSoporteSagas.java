package com.ejemplo.tramitacion.aplicacion.saga.servicio;

import java.util.List;
import java.util.Map;

import com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada.CasoUsoConsultarSagasSoporte;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada.CasoUsoIntervenirSaga;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoConsultaSagasSoporte;
import com.ejemplo.tramitacion.dominio.saga.general.DatoNegocio1Id;
import com.ejemplo.tramitacion.dominio.saga.general.Paso;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;
import com.ejemplo.tramitacion.dominio.saga.general.TipoSaga;
import com.ejemplo.tramitacion.dominio.saga.general.UsuarioSoporte;

/**
 * Fachada para la pantalla de soporte: enruta cada intervención al servicio
 * de la saga correcta y delega las consultas en el modelo de lectura.
 */
public class ServicioSoporteSagas implements CasoUsoIntervenirSaga, CasoUsoConsultarSagasSoporte {

    private final ServicioSagaPrincipal principal;
    private final ServicioSagasSucesoras sucesoras;
    private final PuertoConsultaSagasSoporte consultas;

    public ServicioSoporteSagas(ServicioSagaPrincipal principal, ServicioSagasSucesoras sucesoras,
                                PuertoConsultaSagasSoporte consultas) {
        this.principal = principal;
        this.sucesoras = sucesoras;
        this.consultas = consultas;
    }

    // --- intervenciones ---

    @Override
    public void cancelarPrincipal(SagaId id, UsuarioSoporte quien, String motivo) {
        // El agregado valida el punto de no retorno; las excepciones
        // (PuntoNoRetornoSuperadoException, etc.) suben al adaptador REST tal cual.
        principal.cancelar(id, quien, motivo);
    }

    @Override
    public void reintentarPaso(TipoSaga tipo, SagaId id, Paso paso, UsuarioSoporte quien) {
        switch (tipo) {
            case PRINCIPAL -> principal.reanudarPaso(id, paso, quien);
            case ASINCRONA, SECUENCIAL, SIMPLE -> sucesoras.reanudarPaso(id, paso, quien);
        }
    }

    @Override
    public void marcarPasoOk(TipoSaga tipo, SagaId id, Paso paso, UsuarioSoporte quien,
                             String justificacion, Map<String, String> datosManuales) {
        var datos = FabricaResultadoManual.paraPaso(paso, datosManuales);
        switch (tipo) {
            case PRINCIPAL -> principal.marcarPasoOk(id, paso, quien, justificacion, datos);
            case ASINCRONA, SECUENCIAL, SIMPLE -> sucesoras.marcarPasoOk(id, paso, quien, justificacion, datos);
        }
    }

    // --- consultas (modelo de lectura) ---

    @Override
    public List<SagaResumen> sagasBloqueadas() {
        return consultas.sagasBloqueadas();
    }

    @Override
    public List<SagaResumen> sagasEnEjecucion() {
        return consultas.sagasEnEjecucion();
    }

    @Override
    public List<SagaResumen> buscar(FiltroSagas filtro) {
        return consultas.buscar(filtro);
    }

    @Override
    public VistaDatoNegocio1 vistaDatoNegocio1(DatoNegocio1Id datoNegocio1Id) {
        return consultas.vistaDatoNegocio1(datoNegocio1Id);
    }

    @Override
    public SagaDetalle detalle(TipoSaga tipo, SagaId id) {
        return consultas.detalle(tipo, id);
    }
}
