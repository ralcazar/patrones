package com.ejemplo.tramitacion.infraestructura.saga;

import org.springframework.stereotype.Component;

import com.ejemplo.tramitacion.aplicacion.saga.tarea.TareaSaga;
import com.ejemplo.tramitacion.dominio.saga.paso1.DatoNegocio3;
import com.ejemplo.tramitacion.dominio.saga.paso7.DatoNegocio2;
import com.ejemplo.tramitacion.dominio.saga.general.DatoNegocio1Id;
import com.ejemplo.tramitacion.dominio.saga.general.Paso;
import com.ejemplo.tramitacion.dominio.saga.asincrono.RefAsincrono;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;
import com.ejemplo.tramitacion.dominio.saga.general.TipoSaga;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Serializa TareaSaga a JSON con campo discriminador "tipo". Mapeo manual y
 * explícito (sin @JsonTypeInfo): el contenido persiste en BBDD y puede ser leído
 * por versiones futuras del código; conviene que el formato sea estable y obvio.
 */
@Component
public class CodecTareaSaga {

    private final ObjectMapper mapper = new ObjectMapper();

    public String tipoDe(TareaSaga tarea) {
        return switch (tarea) {
            case TareaSaga.IniciarTramitacion t      -> "INICIAR";
            case TareaSaga.ArrancarSaga t            -> "ARRANCAR_SAGA";
            case TareaSaga.Reintentar t              -> "REINTENTAR";
            case TareaSaga.TimeoutAsincrono t        -> "TIMEOUT_ASINCRONO";
            case TareaSaga.ResultadoAsincronoOk t    -> "RESULTADO_ASINCRONO_OK";
            case TareaSaga.ResultadoAsincronoError t -> "RESULTADO_ASINCRONO_ERROR";
        };
    }

    public String codificar(TareaSaga tarea) {
        try {
            ObjectNode n = mapper.createObjectNode();
            n.put("tipo", tipoDe(tarea));
            n.put("sagaId", tarea.sagaId().valor().toString());
            switch (tarea) {
                case TareaSaga.IniciarTramitacion t -> {
                    n.put("datoNegocio1Id", t.datoNegocio1Id().valor().toString());
                    n.put("datoNegocio3Valor1", t.datos().valor1());
                    n.put("datoNegocio3Valor2", t.datos().valor2());
                    n.put("datoNegocio2Valor1", t.datoNegocio2().valor1());
                    n.put("datoNegocio2Valor2", t.datoNegocio2().valor2());
                }
                case TareaSaga.ArrancarSaga t -> { /* solo sagaId */ }
                case TareaSaga.Reintentar t -> {
                    n.put("tipoSaga", t.tipo().name());
                    n.put("paso", t.paso().name());
                    n.put("intentoNum", t.intentoNum());
                }
                case TareaSaga.TimeoutAsincrono t -> { /* solo sagaId */ }
                case TareaSaga.ResultadoAsincronoOk t -> {
                    n.put("ref", t.ref().valor());
                    n.put("mensajeId", t.mensajeId());
                }
                case TareaSaga.ResultadoAsincronoError t -> {
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
                        DatoNegocio1Id.de(n.get("datoNegocio1Id").asText()),
                        new DatoNegocio3(n.get("datoNegocio3Valor1").asText(), n.get("datoNegocio3Valor2").asText()),
                        new DatoNegocio2(n.get("datoNegocio2Valor1").asText(), n.get("datoNegocio2Valor2").asText()));
                case "ARRANCAR_SAGA" -> new TareaSaga.ArrancarSaga(sagaId);
                case "REINTENTAR" -> new TareaSaga.Reintentar(
                        TipoSaga.valueOf(n.get("tipoSaga").asText()), sagaId,
                        Paso.valueOf(n.get("paso").asText()), n.get("intentoNum").asInt());
                case "TIMEOUT_ASINCRONO" -> new TareaSaga.TimeoutAsincrono(sagaId);
                case "RESULTADO_ASINCRONO_OK" -> new TareaSaga.ResultadoAsincronoOk(sagaId,
                        new RefAsincrono(n.get("ref").asText()), n.get("mensajeId").asText());
                case "RESULTADO_ASINCRONO_ERROR" -> new TareaSaga.ResultadoAsincronoError(sagaId,
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
