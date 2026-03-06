# Etapa 1: Baixar o Maven e compilar o código
FROM maven:3.8.5-openjdk-17 AS build
COPY . .
RUN mvn clean package -DskipTests

# Etapa 2: Pegar o arquivo compilado e rodar num Java mais leve
FROM openjdk:17.0.1-jdk-slim
COPY --from=build /target/horus-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]