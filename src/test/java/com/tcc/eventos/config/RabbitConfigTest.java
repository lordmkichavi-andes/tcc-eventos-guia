package com.tcc.eventos.config;

import com.tcc.eventos.model.EventoGuia;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitConfigTest {

    /**
     * Regresión: el RabbitTemplate autoconfigurado usa SimpleMessageConverter por
     * defecto, que solo acepta String/byte[]/Serializable y falla con records de
     * dominio como EventoGuia. Este test falla si el bean jsonMessageConverter()
     * se elimina de RabbitConfig.
     */
    @Test
    void debeSerializarYDeserializarEventoGuiaComoJson() {
        MessageConverter converter = new RabbitConfig().jsonMessageConverter();

        EventoGuia evento = new EventoGuia(
                UUID.randomUUID(),
                "GUIA-123456",
                "EN_TRANSITO",
                Instant.parse("2026-07-23T15:30:00Z"),
                "TMS",
                "idem-001",
                Map.of("ciudad", "Bogotá"),
                Instant.parse("2026-07-23T15:30:01Z"));

        Message message = converter.toMessage(evento, new MessageProperties());
        Object recuperado = converter.fromMessage(message);

        assertThat(recuperado).isEqualTo(evento);
    }
}