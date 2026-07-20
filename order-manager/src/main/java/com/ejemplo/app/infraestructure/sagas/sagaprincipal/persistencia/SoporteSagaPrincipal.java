package com.ejemplo.app.infraestructure.sagas.sagaprincipal.persistencia;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.Proceso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ContextoTramitacion;
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

    private final ProcesoSagaPrincipalJpaRepository repo;

    public SoporteSagaPrincipal(ProcesoSagaPrincipalJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public TipoOrden tipo() {
        return SagaPrincipal.TIPO;
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
    public String estado(Proceso<?> proceso) {
        return ((SagaPrincipal) proceso).estado().name();
    }

    @Override
    public void guardarContexto(Proceso<?> proceso) {
        var s = (SagaPrincipal) proceso;
        var ctx = s.contexto();
        repo.save(new ProcesoSagaPrincipalEntity(s.id().valor(), ctx.datosNegocioId().valor(),
                ctx.refPaso1() == null ? null : ctx.refPaso1().valor(),
                ctx.refPaso2() == null ? null : ctx.refPaso2().valor(),
                ctx.refPaso3() == null ? null : ctx.refPaso3().valor(),
                ctx.refPaso4() == null ? null : ctx.refPaso4().valor(),
                ctx.refPaso5() == null ? null : ctx.refPaso5().valor(),
                ctx.refPaso6() == null ? null : ctx.refPaso6().valor(),
                ctx.refPaso7() == null ? null : ctx.refPaso7().valor(),
                ctx.refPaso8() == null ? null : ctx.refPaso8().valor()));
    }

    @Override
    public Proceso<?> rearmar(OrdenId id, ExternalId externalId, String estado, List<AuditoriaIntervencion> auditoria) {
        var entity = repo.findById(id.valor())
                .orElseThrow(() -> new IllegalArgumentException("No existe el contexto de la saga principal " + id.valor()));
        var ctx = ContextoTramitacion.rehidratar(new DatosNegocioId(entity.getDatosnegocioId()),
                entity.getRefPaso1() == null ? null : new RefPaso1(entity.getRefPaso1()),
                entity.getRefPaso2() == null ? null : new RefPaso2(entity.getRefPaso2()),
                entity.getRefPaso3() == null ? null : new RefPaso3(entity.getRefPaso3()),
                entity.getRefPaso4() == null ? null : new RefPaso4(entity.getRefPaso4()),
                entity.getRefPaso5() == null ? null : new RefPaso5(entity.getRefPaso5()),
                entity.getRefPaso6() == null ? null : new RefPaso6(entity.getRefPaso6()),
                entity.getRefPaso7() == null ? null : new RefPaso7(entity.getRefPaso7()),
                entity.getRefPaso8() == null ? null : new RefPaso8(entity.getRefPaso8()));
        return SagaPrincipal.rehidratar(id, externalId, ctx, EstadoSagaPrincipal.valueOf(estado), auditoria);
    }

    @Override
    public void borrarContexto(List<UUID> ordenIds) {
        repo.borrarPorIds(ordenIds);
    }
}
