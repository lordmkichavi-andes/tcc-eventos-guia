# Etapa 1: compila el jar con Maven (no requiere Java ni Maven en el host)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Descargar dependencias primero para aprovechar la cache de capas
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# Etapa 2: imagen final liviana, solo JRE
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
