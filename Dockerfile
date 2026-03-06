# ==========================================
# ETAPA 1: Construção (Build)
# ==========================================
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# ==========================================
# ETAPA 2: Execução (Run)
# ==========================================
FROM openjdk:17.0.1-jdk-slim
WORKDIR /app

# ATENÇÃO PARA ESTA LINHA ABAIXO:
COPY --from=build /app/target/horus-api.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]