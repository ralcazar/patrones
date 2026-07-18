package com.ejemplo.app.infraestructure.sagas.rest;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ejemplo.app.business.ordermanager.dominio.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion;

/**
 * {@link ControladorTramitaciones} aislado (solo la capa web, sin el resto
 * del contexto Spring): {@link CasoUsoIniciarTramitacion} se dobla con
 * {@code @MockBean} (aún no deprecado en Spring Boot 3.3.5, ver
 * spring-boot-starter-test), que es la única dependencia del controlador
 * (regla de arquitectura entrada -> aplicación: nunca un puerto de salida).
 */
@WebMvcTest(ControladorTramitaciones.class)
class ControladorTramitacionesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CasoUsoIniciarTramitacion casoUso;

    @Test
    void iniciar_flujoFeliz_devuelve202AcceptedConElOrdenIdEnElBody() throws Exception {
        var ordenId = OrdenId.nuevo();
        when(casoUso.iniciar(any())).thenReturn(ordenId);
        var externalId = UUID.randomUUID();

        mockMvc.perform(post("/tramitaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"" + externalId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.ordenId", is(ordenId.valor().toString())));
    }

    @Test
    void iniciar_conFalloDelServicioExternoDeDatosDeNegocioDevuelve502BadGatewaySinNadaPersistido() throws Exception {
        when(casoUso.iniciar(any())).thenThrow(new ExcepcionServicioExterno(MotivoFallo.timeout(), null));
        var externalId = UUID.randomUUID();

        mockMvc.perform(post("/tramitaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"" + externalId + "\"}"))
                .andExpect(status().isBadGateway());
    }
}
