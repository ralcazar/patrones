package com.ejemplo.app.infraestructure.sagas.persistencia;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.Proceso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.EstadoSagaSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.RefEjecucion;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.DescriptorSoporteOrden;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso;

/** {@link MapeadorProceso} y {@link DescriptorSoporteOrden} de la saga secundaria 3. */
@Component
public class SoporteSagaSecundaria3 implements MapeadorProceso, DescriptorSoporteOrden {

    private final ProcesoSagaSecundaria3JpaRepository repo;

    public SoporteSagaSecundaria3(ProcesoSagaSecundaria3JpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public TipoOrden tipo() {
        return SagaSecundaria3.TIPO;
    }

    @Override
    public String pasoPendiente(String estado) {
        return "INICIAL".equals(estado) ? "EJECUCION" : null;
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
        return ((SagaSecundaria3) proceso).estado().name();
    }

    @Override
    public void guardarContexto(Proceso<?> proceso) {
        var s = (SagaSecundaria3) proceso;
        repo.save(new ProcesoSagaSecundaria3Entity(s.id().valor(), s.refPaso7().valor(),
                s.refEjecucion() == null ? null : s.refEjecucion().valor()));
    }

    @Override
    public Proceso<?> rearmar(OrdenId id, ExternalId externalId, String estado, List<AuditoriaIntervencion> auditoria) {
        var entity = repo.findById(id.valor())
                .orElseThrow(() -> new IllegalArgumentException("No existe el contexto de la saga secundaria 3 " + id.valor()));
        return SagaSecundaria3.rehidratar(id, externalId, new RefPaso7(entity.getRefPaso7()),
                entity.getRefEjecucion() == null ? null : new RefEjecucion(entity.getRefEjecucion()),
                EstadoSagaSecundaria3.valueOf(estado), auditoria);
    }

    @Override
    public void borrarContexto(List<UUID> ordenIds) {
        repo.borrarPorIds(ordenIds);
    }
}
