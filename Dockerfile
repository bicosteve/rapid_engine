# Multi-stage build:
#   STAGE 1 (builder) compiles & packages the Spring Boot fat jar.
#   STAGE 2 (runtime) ships only the JRE + jar on a slim, non-root image.

# STAGE 01 :: Build & dependency cache
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline -B

COPY src ./src

RUN ./mvnw clean package -DskipTests -B


# STAGE 02 :: Runtime (slim, non-root, production hardened)
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN useradd -r -u 1001 appusr \
    && chown -R appusr:appusr /app


# Copy only the built artifact from the builder stage.
COPY --from=builder /app/target/*.jar rapid-engine.jar

# Production runtime defaults.
# - Activate the prod-friendly settings via the SPRING_PROFILES_ACTIVE env var.
# - Container-aware JVM memory settings for the EC2 instance.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0" \
    SPRING_PROFILES_ACTIVE=prod \
    APP_PORT=5002

# The app listens on APP_PORT (default 5002).
EXPOSE 5002

USER appusr

# APP_PORT controls the listening port, with 5002 as a hard fallback.
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -Dserver.port=${APP_PORT:-5002} -jar rapid-engine.jar"]


