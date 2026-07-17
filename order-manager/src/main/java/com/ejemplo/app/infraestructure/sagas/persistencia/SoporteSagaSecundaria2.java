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
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.EstadoSagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.DescriptorSoporteOrden;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso;

/** {@link MapeadorProceso} y {@link DescriptorSoporteOrden} de la saga secundaria 2. */
@Component
public class SoporteSagaSecundaria2 implements MapeadorProceso, DescriptorSoporteOrden {

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
    public ProcesoPersistible desarmar(Proceso<?> saga) {
        var s = (SagaSecundaria2) saga;
        var m = new LinkedHashMap<String, String>();
        m.put("refPaso5", s.refPaso5().valor());
        ponerSiNoNulo(m, "refRespuesta", s.refRespuesta() == null ? null : s.refRespuesta().valor());
        return new ProcesoPersistible(s.estado().name(), m);
    }

    @Override
    public Proceso<?> rearmar(OrdenId id, ExternalId externalId, String estado, Map<String, String> contexto,
            List<AuditoriaIntervencion> auditoria) {
        return SagaSecundaria2.rehidratar(id, externalId, new RefPaso5(contexto.get("refPaso5")),
                refONull(contexto, "refRespuesta", RefRespuesta::new),
                EstadoSagaSecundaria2.valueOf(estado), auditoria);
    }
}
