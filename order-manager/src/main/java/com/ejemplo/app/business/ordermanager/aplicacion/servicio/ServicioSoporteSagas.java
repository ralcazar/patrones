package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.util.List;
import java.util.Map;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoIntervenirSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaSagasSoporte;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.PasoSagaPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.PasoSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.PasoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.PasoSagaSecundaria3;

/**
 * Fachada para la pantalla de soporte: resuelve el paso (que llega por nombre
 * desde el REST del backoffice) al enum de la saga indicada y enruta cada
 * intervención al orquestador correcto; las consultas van al modelo de lectura.
 */
@Service
public class ServicioSoporteSagas implements CasoUsoIntervenirSaga, CasoUsoConsultarSagasSoporte {

    private final ServicioSagaPrincipal principal;
    private final ServicioSagaSecundaria1 secundaria1;
    private final ServicioSagaSecundaria2 secundaria2;
    private final ServicioSagaSecundaria3 secundaria3;
    private final PuertoConsultaSagasSoporte consultas;

    public ServicioSoporteSagas(ServicioSagaPrincipal principal, ServicioSagaSecundaria1 secundaria1,
                                ServicioSagaSecundaria2 secundaria2, ServicioSagaSecundaria3 secundaria3,
                                PuertoConsultaSagasSoporte consultas) {
        this.principal = principal;
        this.secundaria1 = secundaria1;
        this.secundaria2 = secundaria2;
        this.secundaria3 = secundaria3;
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
    public void reintentarPaso(TipoSaga tipo, SagaId id, String nombrePaso, UsuarioSoporte quien) {
        switch (tipo) {
            case PRINCIPAL -> principal.reanudarPaso(id, PasoSagaPrincipal.valueOf(nombrePaso), quien);
            case SECUNDARIA1 -> secundaria1.reanudarPaso(id, PasoSagaSecundaria1.valueOf(nombrePaso), quien);
            case SECUNDARIA2 -> secundaria2.reanudarPaso(id, PasoSagaSecundaria2.valueOf(nombrePaso), quien);
            case SECUNDARIA3 -> secundaria3.reanudarPaso(id, PasoSagaSecundaria3.valueOf(nombrePaso), quien);
        }
    }

    @Override
    public void marcarPasoOk(TipoSaga tipo, SagaId id, String nombrePaso, UsuarioSoporte quien,
                             String justificacion, Map<String, String> datosManuales) {
        switch (tipo) {
            case PRINCIPAL -> {
                var paso = PasoSagaPrincipal.valueOf(nombrePaso);
                principal.marcarPasoOk(id, paso, quien, justificacion,
                        FabricaResultadoManual.paraPaso(paso, datosManuales));
            }
            case SECUNDARIA1 -> {
                var paso = PasoSagaSecundaria1.valueOf(nombrePaso);
                secundaria1.marcarPasoOk(id, paso, quien, justificacion,
                        FabricaResultadoManual.paraPaso(paso, datosManuales));
            }
            case SECUNDARIA2 -> {
                var paso = PasoSagaSecundaria2.valueOf(nombrePaso);
                secundaria2.marcarPasoOk(id, paso, quien, justificacion,
                        FabricaResultadoManual.paraPaso(paso, datosManuales));
            }
            case SECUNDARIA3 -> {
                var paso = PasoSagaSecundaria3.valueOf(nombrePaso);
                secundaria3.marcarPasoOk(id, paso, quien, justificacion,
                        FabricaResultadoManual.paraPaso(paso, datosManuales));
            }
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
    public VistaTramitacion vistaTramitacion(ExternalId externalId) {
        return consultas.vistaTramitacion(externalId);
    }

    @Override
    public SagaDetalle detalle(TipoSaga tipo, SagaId id) {
        return consultas.detalle(tipo, id);
    }
}
