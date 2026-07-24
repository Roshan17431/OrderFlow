FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN unset MAVEN_CONFIG \
    && ./mvnw -B -ntp -pl api-gateway -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S orderflow \
    && adduser -S -G orderflow -h /app orderflow
WORKDIR /app
COPY --from=build --chown=orderflow:orderflow /workspace/api-gateway/target/app.jar /app/app.jar
USER orderflow
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=10 \
  CMD curl --fail --silent http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
