package com.tcc.eventos.service;

import com.tcc.eventos.dto.EventoGuiaRequest;
import com.tcc.eventos.dto.EventoGuiaResponse;
import com.tcc.eventos.model.EventoGuia;
import com.tcc.eventos.publisher.EventoGuiaPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class EventoGuiaService {

    private final EventoGuiaPublisher publisher;

    public EventoGuiaService(EventoGuiaPublisher publisher) {
        this.publisher = publisher;
    }

    public EventoGuiaResponse registrarEvento(EventoGuiaRequest request, String idempotencyKey) {
        UUID eventoId = UUID.randomUUID();
        Instant ahora = Instant.now();

        EventoGuia evento = new EventoGuia(
                eventoId,
                request.numeroGuia(),
                request.estado(),
                request.fechaEvento(),
                request.origen(),
                idempotencyKey,
                request.metadata(),
                ahora
        );

        publisher.publicar(evento);

        return new EventoGuiaResponse(
                eventoId,
                request.numeroGuia(),
                request.estado(),
                ahora,
                "Evento publicado correctamente"
        );
    }
}
