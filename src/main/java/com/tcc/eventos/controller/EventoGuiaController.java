package com.tcc.eventos.controller;

import com.tcc.eventos.dto.EventoGuiaRequest;
import com.tcc.eventos.dto.EventoGuiaResponse;
import com.tcc.eventos.service.EventoGuiaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/eventos")
public class EventoGuiaController {

    private final EventoGuiaService service;

    public EventoGuiaController(EventoGuiaService service) {
        this.service = service;
    }

    @PostMapping("/guia")
    public ResponseEntity<EventoGuiaResponse> registrarEvento(
            @Valid @RequestBody EventoGuiaRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        EventoGuiaResponse response = service.registrarEvento(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
