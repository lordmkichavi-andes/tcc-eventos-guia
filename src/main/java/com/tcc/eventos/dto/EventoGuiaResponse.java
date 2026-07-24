package com.tcc.eventos.dto;

import java.time.Instant;
import java.util.UUID;

public record EventoGuiaResponse(
        UUID eventoId,
        String numeroGuia,
        String estado,
        Instant publicadoEn,
        String mensaje
) {
}
