# STAGE::01 Build & Cache
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy the dependency files first !important for caching dependencies
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Make the mvnw executable (important in Linux runners)
RUN chmod +x mvnw

# Download the dependancies of the cached layers
RUN ./mvnw dependency:go-offline -B

# Copy the source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests


# STAGE::02 Runtime
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar rapid-engine.jar

RUN useradd -r -u 1001 appusr && chown appusr:appusr rapid-engine.jar

ENV JAVA_OPTS=""

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar rapid-engine.jar"]
