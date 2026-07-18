package com.ejemplo.app.infraestructure.sagas.persistencia;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.Proceso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.EstadoSagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefConfirmacion;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefInicio;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.DescriptorSoporteOrden;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso;

/** {@link MapeadorProceso} y {@link DescriptorSoporteOrden} de la saga secundaria 1. */
@Component
public class SoporteSagaSecundaria1 implements MapeadorProceso, DescriptorSoporteOrden {

    private final ProcesoSagaSecundaria1JpaRepository repo;

    public SoporteSagaSecundaria1(ProcesoSagaSecundaria1JpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public TipoOrden tipo() {
        return SagaSecundaria1.TIPO;
    }

    @Override
    public String pasoPendiente(String estado) {
        return switch (estado) {
            case "INICIAL" -> "INICIO";
            case "INICIO_HECHO" -> "CONFIRMACION";
            default -> null;
        };
    }

    @Override
    public boolean datosManualesObligatorios(String estado) {
        return "INICIAL".equals(estado);
    }

    @Override
    public boolean cancelable(String estado) {
        return false;
    }

    @Override
    public String estado(Proceso<?> proceso) {
        return ((SagaSecundaria1) proceso).estado().name();
    }

    @Override
    public void guardarContexto(Proceso<?> proceso) {
        var s = (SagaSecundaria1) proceso;
        repo.save(new ProcesoSagaSecundaria1Entity(s.id().valor(), s.refPaso1().valor(),
                s.refInicio() == null ? null : s.refInicio().valor(),
                s.refConfirmacion() == null ? null : s.refConfirmacion().valor()));
    }

    @Override
    public Proceso<?> rearmar(OrdenId id, ExternalId externalId, String estado, List<AuditoriaIntervencion> auditoria) {
        var entity = repo.findById(id.valor())
                .orElseThrow(() -> new IllegalArgumentException("No existe el contexto de la saga secundaria 1 " + id.valor()));
        return SagaSecundaria1.rehidratar(id, externalId, new RefPaso1(entity.getRefPaso1()),
                entity.getRefInicio() == null ? null : new RefInicio(entity.getRefInicio()),
                entity.getRefConfirmacion() == null ? null : new RefConfirmacion(entity.getRefConfirmacion()),
                EstadoSagaSecundaria1.valueOf(estado), auditoria);
    }

    @Override
    public void borrarContexto(List<UUID> ordenIds) {
        repo.borrarPorIds(ordenIds);
    }
}
