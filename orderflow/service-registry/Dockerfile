FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN unset MAVEN_CONFIG \
    && ./mvnw -B -ntp -pl service-registry -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S orderflow \
    && adduser -S -G orderflow -h /app orderflow
WORKDIR /app
COPY --from=build --chown=orderflow:orderflow /workspace/service-registry/target/app.jar /app/app.jar
USER orderflow
EXPOSE 8761
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=10 \
  CMD curl --fail --silent http://localhost:8761/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
