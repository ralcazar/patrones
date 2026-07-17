package com.ejemplo.app.infraestructure.sagas.persistencia;

import static com.ejemplo.app.infraestructure.sagas.persistencia.AyudanteContexto.ponerSiNoNulo;
import static com.ejemplo.app.infraestructure.sagas.persistencia.AyudanteContexto.refONull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public ProcesoPersistible desarmar(Proceso<?> saga) {
        var s = (SagaSecundaria3) saga;
        var m = new LinkedHashMap<String, String>();
        m.put("refPaso7", s.refPaso7().valor());
        ponerSiNoNulo(m, "refEjecucion", s.refEjecucion() == null ? null : s.refEjecucion().valor());
        return new ProcesoPersistible(s.estado().name(), m);
    }

    @Override
    public Proceso<?> rearmar(OrdenId id, ExternalId externalId, String estado, Map<String, String> contexto,
            List<AuditoriaIntervencion> auditoria) {
        return SagaSecundaria3.rehidratar(id, externalId, new RefPaso7(contexto.get("refPaso7")),
                refONull(contexto, "refEjecucion", RefEjecucion::new),
                EstadoSagaSecundaria3.valueOf(estado), auditoria);
    }
}
