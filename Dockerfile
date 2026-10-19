# Estágio de build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Dependências primeiro: esta camada só é refeita quando o pom.xml muda, não a cada
# alteração de código.
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x ./mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q clean package -DskipTests

# Estágio de execução
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuário não-root
RUN addgroup -S spring && adduser -S spring -G spring

# O jar executável é o único *.jar de target/ (o repackage deixa o original como .jar.original)
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar
USER spring:spring

EXPOSE 8081

# Heap proporcional ao limite de memória do container, em vez do padrão de 1/4.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health/liveness | grep -q UP || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
