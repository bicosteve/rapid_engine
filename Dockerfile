# Production Dockerfile for rapid-engine
#
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
    && mkdir -p /app/logs \
    && chown -R appusr:appusr /app

# Copy only the built artifact from the builder stage.
COPY --from=builder /app/target/*.jar rapid-engine.jar

# Production runtime defaults.
#   - Activate the prod-friendly settings via the SPRING_PROFILES_ACTIVE env var.
#   - Container-aware JVM memory settings for Render's constrained instances.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0" \
    SPRING_PROFILES_ACTIVE=prod \
    APP_PORT=5002

# Render injects the listening port via $PORT; expose the documented default.
EXPOSE 5002

USER appusr

# Port resolution order (with a guaranteed final default so it is never empty):
#   1. $PORT     -> injected by Render at runtime
#   2. $APP_PORT -> the ENV default above (5002), useful for local runs
#   3. 5002      -> hard fallback if both are somehow unset
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -Dserver.port=${PORT:-${APP_PORT:-5002}} -jar rapid-engine.jar"]

