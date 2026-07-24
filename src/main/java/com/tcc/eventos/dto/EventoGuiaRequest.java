package com.tcc.eventos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record EventoGuiaRequest(
        @NotBlank String numeroGuia,
        @NotBlank String estado,
        @NotNull Instant fechaEvento,
        @NotBlank String origen,
        Map<String, String> metadata
) {
    public EventoGuiaRequest {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
