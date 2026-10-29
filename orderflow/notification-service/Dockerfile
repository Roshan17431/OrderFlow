FROM --platform=$BUILDPLATFORM amazoncorretto:21.0.13-alpine3.24@sha256:d942d2e3e0f480c076ec10d03deac7444dc717bc25bb3e2bc5373676b7b6c406 AS build
# Vendor APK includes the Java 21 CA security update before container tags catch up.
RUN apk upgrade --no-cache \
    && apk add --no-cache --upgrade amazon-corretto-21=21.0.13.0.11-r0 curl
ENV JAVA_VERSION=21.0.13.0.11
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 unset MAVEN_CONFIG && ./mvnw -B -ntp -pl notification-service -am clean package -DskipTests -Dcyclonedx.skip=true
FROM amazoncorretto:21.0.13-alpine3.24@sha256:d942d2e3e0f480c076ec10d03deac7444dc717bc25bb3e2bc5373676b7b6c406
ENV JAVA_VERSION=21.0.13.0.11
RUN apk upgrade --no-cache \
    && apk add --no-cache --upgrade amazon-corretto-21=21.0.13.0.11-r0 curl && addgroup -S orderflow && adduser -S -G orderflow -h /app orderflow
WORKDIR /app
COPY --from=build --chown=orderflow:orderflow /workspace/notification-service/target/app.jar /app/app.jar
USER orderflow
EXPOSE 8088
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=8s --start-period=30s --retries=15 CMD curl --fail --silent http://localhost:8088/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
