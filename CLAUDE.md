# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Spring Boot 3.3 service for TCC technical test: REST API that receives guide status events and publishes them to RabbitMQ.

## Build system

Maven, Java 17.

Common commands:
```
docker compose up -d          # start RabbitMQ locally
mvn spring-boot:run           # run the API
mvn test                      # run tests
mvn package                   # build jar
```

## Architecture

- `controller/` — REST endpoints
- `service/` — business logic
- `publisher/` — queue publishing (interface + RabbitMQ impl)
- `model/` — domain event
- `dto/` — API request/response
- `config/` — RabbitMQ exchange setup

See `README.md` and `ARQUITECTURA.md` for full documentation.
