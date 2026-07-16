package com.ejemplo.app.infraestructure.ordermanager.saga;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * (De)serializa el {@code contexto} de una saga a JSON plano. Todos los
 * campos propios de cada tipo de saga son, en el dominio, wrappers de un
 * único String, así que un {@code Map<String,String>} basta para las 4 formas.
 */
final class ContextoCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> TIPO_MAPA = new TypeReference<>() {};

    private ContextoCodec() {}

    static String escribir(Map<String, String> valores) {
        try {
            return MAPPER.writeValueAsString(valores);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el contexto de la saga", e);
        }
    }

    static Map<String, String> leer(String json) {
        try {
            return json == null ? Map.of() : MAPPER.readValue(json, TIPO_MAPA);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo deserializar el contexto de la saga", e);
        }
    }
}
