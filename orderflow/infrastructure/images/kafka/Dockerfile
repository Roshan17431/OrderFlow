# OrderFlow-maintained candidate. Build context: infrastructure/images/kafka.
# This is an OrderFlow-maintained derivative, not an Apache-published image.
# No broker JAR, vendor launcher, CA entrypoint or storage guard is modified.
FROM apache/kafka:4.3.2@sha256:852a0d49fc4e656b022e7f23785411802c9a088c78a7c5e39f33969de9c227a7

ARG CORRETTO_APK_VERSION=21.0.13.0.11-r0
ARG BROTLI_APK_VERSION=1.2.1-r0
ARG EXPAT_APK_VERSION=2.9.0-r0

USER root
COPY corretto-signing-key.pub /etc/apk/keys/amazoncorretto.rsa.pub
RUN set -eux; \
    case "$(cat /etc/alpine-release)" in 3.24.*) ;; *) exit 1 ;; esac; \
    printf '%s\n' '6cfdf08be09f32ca298e2d5bd4a359ee2b275765c09b56d514624bf831eafb91  /etc/apk/keys/amazoncorretto.rsa.pub' | sha256sum -c -; \
    { \
      sha256sum /__cacert_entrypoint.sh; \
      find /opt/kafka /etc/kafka -type f \
        ! -path /opt/kafka/kafka.jsa ! -path /opt/kafka/storage.jsa \
        -exec sha256sum '{}' +; \
    } > /tmp/orderflow-vendor-files.sha256; \
    printf '%s\n' 'https://apk.corretto.aws' >> /etc/apk/repositories; \
    apk upgrade --no-cache; \
    apk add --no-cache \
      "amazon-corretto-21=${CORRETTO_APK_VERSION}" \
      "brotli-libs=${BROTLI_APK_VERSION}" \
      "libexpat=${EXPAT_APK_VERSION}"

# APK3 info verbosity differs from APK2; inspect installed identities explicitly.
RUN set -eux; \
    apk --no-network list --installed amazon-corretto-21 | cut -d ' ' -f1 | grep -Fx "amazon-corretto-21-${CORRETTO_APK_VERSION}"; \
    apk --no-network list --installed brotli-libs | cut -d ' ' -f1 | grep -Fx "brotli-libs-${BROTLI_APK_VERSION}"; \
    apk --no-network list --installed libexpat | cut -d ' ' -f1 | grep -Fx "libexpat-${EXPAT_APK_VERSION}"; \
    test -x /usr/lib/jvm/java-21-amazon-corretto/bin/java; \
    test -f /usr/lib/jvm/java-21-amazon-corretto/lib/security/cacerts; \
    test -d /opt/java/openjdk; \
    test ! -L /opt/java/openjdk; \
    rm -rf /opt/java/openjdk; \
    ln -s /usr/lib/jvm/java-21-amazon-corretto /opt/java/openjdk; \
    test "$(readlink -f /opt/java/openjdk/bin/java)" = /usr/lib/jvm/java-21-amazon-corretto/bin/java; \
    rm -f /opt/kafka/kafka.jsa /opt/kafka/storage.jsa; \
    /opt/java/openjdk/bin/java -Xshare:off -XX:SharedArchiveFile=/opt/kafka/storage.jsa -version; \
    /opt/java/openjdk/bin/java -Xshare:off -XX:SharedArchiveFile=/opt/kafka/kafka.jsa -version; \
    sha256sum -c /tmp/orderflow-vendor-files.sha256; \
    rm -f /tmp/orderflow-vendor-files.sha256

# Keep the original JAVA_HOME/PATH. The old Temurin tree is removed, and that
# path is now a symlink into the signed APK-managed Corretto installation.
# The vendor launch wrapper appends SharedArchiveFile twice. The documented
# JVM -Xshare:off flag makes those now-absent, incompatible old archives inert.
# JAVA_TOOL_OPTIONS leaves Kafka's own JVM default/tuning-option logic intact.
ENV JAVA_VERSION="jdk-21.0.13+11" \
    ORDERFLOW_JAVA_PACKAGE="amazon-corretto-21=21.0.13.0.11-r0" \
    JAVA_TOOL_OPTIONS="-Xshare:off"

LABEL org.opencontainers.image.title="OrderFlow Kafka 4.3.2 patched runtime candidate" \
    org.opencontainers.image.authors="OrderFlow maintainers" \
    org.opencontainers.image.base.name="apache/kafka:4.3.2@sha256:852a0d49fc4e656b022e7f23785411802c9a088c78a7c5e39f33969de9c227a7" \
    io.orderflow.kafka.runtime.package="amazon-corretto-21=21.0.13.0.11-r0" \
    io.orderflow.kafka.cds="disabled with documented JVM -Xshare:off; old JVM-specific archives removed"

USER appuser
# Inherit Entrypoint=[/__cacert_entrypoint.sh], Cmd=[/etc/kafka/docker/run],
# volumes, working directory, exposed port and all vendor Kafka assets.
# Cloud Compose continues to replace only Cmd with the existing storage guard.
