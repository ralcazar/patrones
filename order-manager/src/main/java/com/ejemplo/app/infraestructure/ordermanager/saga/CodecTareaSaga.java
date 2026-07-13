package com.ejemplo.app.infraestructure.ordermanager.saga;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.PasoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.RefRespuesta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Serializa TareaSaga a JSON con campo discriminador "tipo". Mapeo manual y
 * explícito (sin @JsonTypeInfo): el contenido persiste en BBDD y puede ser leído
 * por versiones futuras del código; conviene que el formato sea estable y obvio.
 * Los pasos se serializan por NOMBRE y se reconstruyen con PasoSaga.de(tipo, nombre).
 */
@Component
public class CodecTareaSaga {

    private final ObjectMapper mapper = new ObjectMapper();

    public String tipoDe(TareaSaga tarea) {
        return switch (tarea) {
            case TareaSaga.IniciarTramitacion t           -> "INICIAR";
            case TareaSaga.ArrancarSaga t                 -> "ARRANCAR_SAGA";
            case TareaSaga.Reintentar t                   -> "REINTENTAR";
            case TareaSaga.TimeoutSagaSecundaria2 t       -> "TIMEOUT_SECUNDARIA2";
            case TareaSaga.ResultadoSagaSecundaria2Ok t   -> "RESULTADO_SECUNDARIA2_OK";
            case TareaSaga.ResultadoSagaSecundaria2Error t -> "RESULTADO_SECUNDARIA2_ERROR";
        };
    }

    public String codificar(TareaSaga tarea) {
        try {
            ObjectNode n = mapper.createObjectNode();
            n.put("tipo", tipoDe(tarea));
            n.put("sagaId", tarea.sagaId().valor().toString());
            switch (tarea) {
                case TareaSaga.IniciarTramitacion t -> {
                    n.put("externalId", t.externalId().valor().toString());
                    n.put("datoNegocio3Valor1", t.datos().valor1());
                    n.put("datoNegocio3Valor2", t.datos().valor2());
                    n.put("datoNegocio2Valor1", t.datoNegocio2().valor1());
                    n.put("datoNegocio2Valor2", t.datoNegocio2().valor2());
                }
                case TareaSaga.ArrancarSaga t -> n.put("tipoSaga", t.tipo().name());
                case TareaSaga.Reintentar t -> {
                    n.put("tipoSaga", t.tipo().name());
                    n.put("paso", t.paso().name());
                    n.put("intentoNum", t.intentoNum());
                }
                case TareaSaga.TimeoutSagaSecundaria2 t -> { /* solo sagaId */ }
                case TareaSaga.ResultadoSagaSecundaria2Ok t -> {
                    n.put("ref", t.ref().valor());
                    n.put("mensajeId", t.mensajeId());
                }
                case TareaSaga.ResultadoSagaSecundaria2Error t -> {
                    n.put("codigo", t.codigo());
                    n.put("detalle", t.detalle());
                    n.put("reintentable", t.reintentable());
                    n.put("mensajeId", t.mensajeId());
                }
            }
            return mapper.writeValueAsString(n);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar la tarea " + tarea, e);
        }
    }

    public TareaSaga decodificar(String contenido) {
        try {
            JsonNode n = mapper.readTree(contenido);
            var sagaId = SagaId.de(n.get("sagaId").asText());
            return switch (n.get("tipo").asText()) {
                case "INICIAR" -> new TareaSaga.IniciarTramitacion(sagaId,
                        ExternalId.de(n.get("externalId").asText()),
                        new DatoNegocio3(n.get("datoNegocio3Valor1").asText(), n.get("datoNegocio3Valor2").asText()),
                        new DatoNegocio2(n.get("datoNegocio2Valor1").asText(), n.get("datoNegocio2Valor2").asText()));
                case "ARRANCAR_SAGA" -> new TareaSaga.ArrancarSaga(sagaId,
                        TipoSaga.valueOf(n.get("tipoSaga").asText()));
                case "REINTENTAR" -> {
                    var tipoSaga = TipoSaga.valueOf(n.get("tipoSaga").asText());
                    yield new TareaSaga.Reintentar(tipoSaga, sagaId,
                            PasoSaga.de(tipoSaga, n.get("paso").asText()), n.get("intentoNum").asInt());
                }
                case "TIMEOUT_SECUNDARIA2" -> new TareaSaga.TimeoutSagaSecundaria2(sagaId);
                case "RESULTADO_SECUNDARIA2_OK" -> new TareaSaga.ResultadoSagaSecundaria2Ok(sagaId,
                        new RefRespuesta(n.get("ref").asText()), n.get("mensajeId").asText());
                case "RESULTADO_SECUNDARIA2_ERROR" -> new TareaSaga.ResultadoSagaSecundaria2Error(sagaId,
                        n.get("codigo").asText(), n.get("detalle").asText(),
                        n.get("reintentable").asBoolean(), n.get("mensajeId").asText());
                default -> throw new IllegalArgumentException("Tipo de tarea desconocido: " + n.get("tipo"));
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Contenido de tarea ilegible: " + contenido, e);
        }
    }
}
