# Etapa 1: compilar con Maven
FROM maven:3.9-eclipse-temurin-21 AS compilacion
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# Etapa 2: imagen ligera solo con el JAR
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=compilacion /app/target/servidor-*.jar servidor.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "servidor.jar"]
