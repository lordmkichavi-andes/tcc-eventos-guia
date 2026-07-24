package com.tcc.eventos.publisher;

import com.tcc.eventos.model.EventoGuia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitEventoGuiaPublisher implements EventoGuiaPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventoGuiaPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public RabbitEventoGuiaPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${app.rabbit.exchange}") String exchange,
            @Value("${app.rabbit.routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Override
    public void publicar(EventoGuia evento) {
        rabbitTemplate.convertAndSend(exchange, routingKey, evento);
        log.info("Evento publicado: guia={}, estado={}, eventoId={}",
                evento.numeroGuia(), evento.estado(), evento.eventoId());
    }
}
