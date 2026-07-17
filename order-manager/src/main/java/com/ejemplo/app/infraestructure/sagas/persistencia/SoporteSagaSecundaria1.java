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
    public ProcesoPersistible desarmar(Proceso<?> saga) {
        var s = (SagaSecundaria1) saga;
        var m = new LinkedHashMap<String, String>();
        m.put("refPaso1", s.refPaso1().valor());
        ponerSiNoNulo(m, "refInicio", s.refInicio() == null ? null : s.refInicio().valor());
        ponerSiNoNulo(m, "refConfirmacion", s.refConfirmacion() == null ? null : s.refConfirmacion().valor());
        return new ProcesoPersistible(s.estado().name(), m);
    }

    @Override
    public Proceso<?> rearmar(OrdenId id, ExternalId externalId, String estado, Map<String, String> contexto,
            List<AuditoriaIntervencion> auditoria) {
        return SagaSecundaria1.rehidratar(id, externalId, new RefPaso1(contexto.get("refPaso1")),
                refONull(contexto, "refInicio", RefInicio::new),
                refONull(contexto, "refConfirmacion", RefConfirmacion::new),
                EstadoSagaSecundaria1.valueOf(estado), auditoria);
    }
}
