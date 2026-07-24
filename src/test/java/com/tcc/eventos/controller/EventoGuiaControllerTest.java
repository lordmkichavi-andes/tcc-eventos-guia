package com.tcc.eventos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcc.eventos.dto.EventoGuiaRequest;
import com.tcc.eventos.publisher.EventoGuiaPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EventoGuiaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventoGuiaPublisher publisher;

    @Test
    void debeAceptarEventoValidoYPublicarlo() throws Exception {
        EventoGuiaRequest request = new EventoGuiaRequest(
                "GUIA-123456",
                "EN_TRANSITO",
                Instant.parse("2026-07-23T15:30:00Z"),
                "TMS",
                Map.of("ciudad", "Bogotá")
        );

        mockMvc.perform(post("/api/v1/eventos/guia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-001")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.numeroGuia").value("GUIA-123456"))
                .andExpect(jsonPath("$.estado").value("EN_TRANSITO"))
                .andExpect(jsonPath("$.mensaje").value("Evento publicado correctamente"))
                .andExpect(jsonPath("$.eventoId").exists());

        verify(publisher).publicar(any());
    }

    @Test
    void debeRechazarEventoInvalido() throws Exception {
        String jsonInvalido = """
                {
                  "numeroGuia": "",
                  "estado": "EN_TRANSITO",
                  "fechaEvento": "2026-07-23T15:30:00Z",
                  "origen": "TMS"
                }
                """;

        mockMvc.perform(post("/api/v1/eventos/guia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Solicitud inválida"));
    }
}
