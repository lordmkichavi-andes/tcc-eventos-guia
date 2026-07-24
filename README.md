# Servicio de Eventos de Guía — Prueba Técnica TCC

API REST que recibe eventos de estado de guías y los publica en RabbitMQ para procesamiento asíncrono.

## Requisitos

- Java 17+
- Maven 3.8+
- Docker (opcional, para RabbitMQ local)

## Ejecución rápida

```bash
# 1. Levantar RabbitMQ
docker compose up -d

# 2. Compilar y ejecutar
mvn spring-boot:run

# 3. Enviar un evento de prueba
curl -X POST http://localhost:8080/api/v1/eventos/guia \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: idem-001" \
  -d '{
    "numeroGuia": "GUIA-123456",
    "estado": "EN_TRANSITO",
    "fechaEvento": "2026-07-23T15:30:00Z",
    "origen": "TMS",
    "metadata": { "ciudad": "Bogotá" }
  }'
```

Respuesta esperada (`202 Accepted`):

```json
{
  "eventoId": "uuid-generado",
  "numeroGuia": "GUIA-123456",
  "estado": "EN_TRANSITO",
  "publicadoEn": "2026-07-23T15:30:01Z",
  "mensaje": "Evento publicado correctamente"
}
```

Consola de RabbitMQ: http://localhost:15672 (guest / guest)

## Pruebas

```bash
mvn test
```

## Estructura del proyecto

```
src/main/java/com/tcc/eventos/
├── controller/     # REST API
├── service/        # Lógica de negocio
├── publisher/      # Publicación a cola (interfaz + RabbitMQ)
├── model/          # Evento de dominio
├── dto/            # Request/Response
└── config/         # Configuración RabbitMQ
```

## Decisiones técnicas

| Decisión | Ventaja | Desventaja |
|----------|---------|------------|
| **RabbitMQ** como cola | Simple, maduro, confirma entrega, equipo pequeño lo opera fácil | Menor throughput que Kafka en picos extremos |
| **HTTP 202 Accepted** | Responde rápido sin bloquear al cliente | El cliente debe consultar otro canal para confirmación final |
| **Interfaz `EventoGuiaPublisher`** | Permite cambiar broker (Kafka, SQS) sin tocar el servicio | Una capa extra (aceptable para evolución) |
| **Header `X-Idempotency-Key`** | Base para deduplicación en consumidores | Idempotencia completa requiere store (Redis/DB) en producción |

## Arquitectura de alto nivel (contexto TCC)

Ver [ARQUITECTURA.md](ARQUITECTURA.md) para el diseño completo de la solución de alta concurrencia.

```
[TMS / Sistemas origen] --HTTP--> [API Eventos Guía] --publish--> [RabbitMQ/Kafka]
                                                                        |
                                        [Consumidor Estado] <-----------+
                                        [Consumidor Notificaciones]
                                        [Actualización BD / Cache]
```

## Variables de entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `RABBITMQ_HOST` | localhost | Host de RabbitMQ |
| `RABBITMQ_PORT` | 5672 | Puerto AMQP |
| `RABBITMQ_USER` | guest | Usuario |
| `RABBITMQ_PASSWORD` | guest | Contraseña |

## Endpoint

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/v1/eventos/guia` | Registra y publica un evento de estado |
| GET | `/actuator/health` | Health check |
