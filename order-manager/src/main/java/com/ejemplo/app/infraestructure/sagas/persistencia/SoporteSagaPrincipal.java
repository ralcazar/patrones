package com.ejemplo.app.infraestructure.sagas.persistencia;

import static com.ejemplo.app.infraestructure.sagas.persistencia.AyudanteContexto.ponerSiNoNulo;
import static com.ejemplo.app.infraestructure.sagas.persistencia.AyudanteContexto.refONull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.dominio.comun.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.Saga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ContextoTramitacion;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.EstadoSagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso2;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso4;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso6;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso8;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.DescriptorSoporteOrden;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso;

/** {@link MapeadorProceso} y {@link DescriptorSoporteOrden} de la saga principal. */
@Component
public class SoporteSagaPrincipal implements MapeadorProceso, DescriptorSoporteOrden {

    @Override
    public TipoSaga tipo() {
        return TipoSaga.PRINCIPAL;
    }

    @Override
    public String pasoPendiente(String estado) {
        return switch (estado) {
            case "INICIAL" -> "PASO1";
            case "PASO1_HECHO" -> "PASO2";
            case "PASO2_HECHO" -> "PASO3";
            case "PASO3_HECHO" -> "PASO4";
            case "PASO4_HECHO" -> "PASO5";
            case "PASO5_HECHO" -> "PASO6";
            case "PASO6_HECHO" -> "PASO7";
            case "PASO7_HECHO" -> "PASO8";
            default -> null;
        };
    }

    @Override
    public boolean datosManualesObligatorios(String estado) {
        return switch (estado) {
            case "INICIAL", "PASO1_HECHO", "PASO3_HECHO", "PASO4_HECHO", "PASO6_HECHO" -> true;
            default -> false;
        };
    }

    @Override
    public boolean cancelable(String estado) {
        return switch (estado) {
            case "INICIAL", "PASO1_HECHO", "PASO2_HECHO", "PASO3_HECHO",
                    "PASO4_HECHO", "PASO5_HECHO", "PASO6_HECHO" -> true;
            default -> false;
        };
    }

    @Override
    public ProcesoPersistible desarmar(Saga<?> saga) {
        var s = (SagaPrincipal) saga;
        return new ProcesoPersistible(s.estado().name(), contextoDePrincipal(s.contexto()));
    }

    @Override
    public Saga<?> rearmar(SagaId id, ExternalId externalId, String estado, Map<String, String> contexto,
            List<AuditoriaIntervencion> auditoria) {
        return SagaPrincipal.rehidratar(id, externalId, contextoTramitacionDesde(contexto),
                EstadoSagaPrincipal.valueOf(estado), auditoria);
    }

    private static Map<String, String> contextoDePrincipal(ContextoTramitacion ctx) {
        var m = new LinkedHashMap<String, String>();
        m.put("datoNegocio3Valor1", ctx.datoNegocio3().valor1());
        m.put("datoNegocio3Valor2", ctx.datoNegocio3().valor2());
        m.put("datoNegocio2Valor1", ctx.datoNegocio2().valor1());
        m.put("datoNegocio2Valor2", ctx.datoNegocio2().valor2());
        ponerSiNoNulo(m, "refPaso1", ctx.refPaso1() == null ? null : ctx.refPaso1().valor());
        ponerSiNoNulo(m, "refPaso2", ctx.refPaso2() == null ? null : ctx.refPaso2().valor());
        ponerSiNoNulo(m, "refPaso3", ctx.refPaso3() == null ? null : ctx.refPaso3().valor());
        ponerSiNoNulo(m, "refPaso4", ctx.refPaso4() == null ? null : ctx.refPaso4().valor());
        ponerSiNoNulo(m, "refPaso5", ctx.refPaso5() == null ? null : ctx.refPaso5().valor());
        ponerSiNoNulo(m, "refPaso6", ctx.refPaso6() == null ? null : ctx.refPaso6().valor());
        ponerSiNoNulo(m, "refPaso7", ctx.refPaso7() == null ? null : ctx.refPaso7().valor());
        ponerSiNoNulo(m, "refPaso8", ctx.refPaso8() == null ? null : ctx.refPaso8().valor());
        return m;
    }

    private static ContextoTramitacion contextoTramitacionDesde(Map<String, String> ctx) {
        return ContextoTramitacion.rehidratar(
                new DatoNegocio3(ctx.get("datoNegocio3Valor1"), ctx.get("datoNegocio3Valor2")),
                new DatoNegocio2(ctx.get("datoNegocio2Valor1"), ctx.get("datoNegocio2Valor2")),
                refONull(ctx, "refPaso1", RefPaso1::new), refONull(ctx, "refPaso2", RefPaso2::new),
                refONull(ctx, "refPaso3", RefPaso3::new), refONull(ctx, "refPaso4", RefPaso4::new),
                refONull(ctx, "refPaso5", RefPaso5::new), refONull(ctx, "refPaso6", RefPaso6::new),
                refONull(ctx, "refPaso7", RefPaso7::new), refONull(ctx, "refPaso8", RefPaso8::new));
    }
}
