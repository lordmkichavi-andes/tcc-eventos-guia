package com.tcc.eventos.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventoGuia(
        UUID eventoId,
        String numeroGuia,
        String estado,
        Instant fechaEvento,
        String origen,
        String idempotencyKey,
        Map<String, String> metadata,
        Instant recibidoEn
) {
}
