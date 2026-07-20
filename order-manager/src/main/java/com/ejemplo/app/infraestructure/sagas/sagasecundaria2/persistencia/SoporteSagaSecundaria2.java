package com.ejemplo.app.infraestructure.sagas.sagasecundaria2.persistencia;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.Proceso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.EstadoSagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.DescriptorSoporteOrden;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso;

/** {@link MapeadorProceso} y {@link DescriptorSoporteOrden} de la saga secundaria 2. */
@Component
public class SoporteSagaSecundaria2 implements MapeadorProceso, DescriptorSoporteOrden {

    private final ProcesoSagaSecundaria2JpaRepository repo;

    public SoporteSagaSecundaria2(ProcesoSagaSecundaria2JpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public TipoOrden tipo() {
        return SagaSecundaria2.TIPO;
    }

    @Override
    public String pasoPendiente(String estado) {
        return "INICIAL".equals(estado) || "ESPERANDO_RESPUESTA".equals(estado) ? "SOLICITUD" : null;
    }

    @Override
    public boolean datosManualesObligatorios(String estado) {
        return false;
    }

    @Override
    public boolean cancelable(String estado) {
        return false;
    }

    @Override
    public String estado(Proceso<?> proceso) {
        return ((SagaSecundaria2) proceso).estado().name();
    }

    @Override
    public void guardarContexto(Proceso<?> proceso) {
        var s = (SagaSecundaria2) proceso;
        repo.save(new ProcesoSagaSecundaria2Entity(s.id().valor(), s.refPaso5().valor(),
                s.refRespuesta() == null ? null : s.refRespuesta().valor()));
    }

    @Override
    public Proceso<?> rearmar(OrdenId id, ExternalId externalId, String estado, List<AuditoriaIntervencion> auditoria) {
        var entity = repo.findById(id.valor())
                .orElseThrow(() -> new IllegalArgumentException("No existe el contexto de la saga secundaria 2 " + id.valor()));
        return SagaSecundaria2.rehidratar(id, externalId, new RefPaso5(entity.getRefPaso5()),
                entity.getRefRespuesta() == null ? null : new RefRespuesta(entity.getRefRespuesta()),
                EstadoSagaSecundaria2.valueOf(estado), auditoria);
    }

    @Override
    public void borrarContexto(List<UUID> ordenIds) {
        repo.borrarPorIds(ordenIds);
    }
}
