package com.tcc.eventos.service;

import com.tcc.eventos.dto.EventoGuiaRequest;
import com.tcc.eventos.dto.EventoGuiaResponse;
import com.tcc.eventos.model.EventoGuia;
import com.tcc.eventos.publisher.EventoGuiaPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventoGuiaServiceTest {

    @Mock
    private EventoGuiaPublisher publisher;

    @InjectMocks
    private EventoGuiaService service;

    @Test
    void debeConstruirEventoYDelegarPublicacion() {
        EventoGuiaRequest request = new EventoGuiaRequest(
                "GUIA-999",
                "ENTREGADA",
                Instant.parse("2026-07-23T18:00:00Z"),
                "TMS",
                Map.of("receptor", "Juan Pérez")
        );

        EventoGuiaResponse response = service.registrarEvento(request, "clave-idem");

        assertThat(response.numeroGuia()).isEqualTo("GUIA-999");
        assertThat(response.estado()).isEqualTo("ENTREGADA");
        assertThat(response.eventoId()).isNotNull();

        ArgumentCaptor<EventoGuia> captor = ArgumentCaptor.forClass(EventoGuia.class);
        verify(publisher).publicar(captor.capture());

        EventoGuia publicado = captor.getValue();
        assertThat(publicado.idempotencyKey()).isEqualTo("clave-idem");
        assertThat(publicado.origen()).isEqualTo("TMS");
        assertThat(publicado.metadata()).containsEntry("receptor", "Juan Pérez");
    }
}
