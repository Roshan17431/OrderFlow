# Maintained dependency-only rebuild; provenance recorded in source-provenance.json.
# Supported target for this draft: linux/amd64 only.
FROM --platform=$BUILDPLATFORM golang:1.27.3-alpine3.24@sha256:d200d2dd76b03a99b52651fd1325aff7dcd35422c35c01bac56571b0507f3deb AS builder
ENV GOTOOLCHAIN=local GOTELEMETRY=off CGO_ENABLED=0 GOOS=linux GOARCH=amd64 GOAMD64=v1 \
    GOMAXPROCS=2 GOMEMLIMIT=2400MiB GOGC=100 GOFLAGS="-p=2 -mod=readonly -v"
RUN apk add --no-cache bash make git patch
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
WORKDIR /src
ADD --checksum=sha256:f3649301d9b20d046b8d30fdd350df8c7fb9a8ad7c56e9bf3811008d2ae3a18f https://codeload.github.com/grafana/grafana/tar.gz/3c98af3ba0773ad12a8a54b1faec7dc62d8d6725 /tmp/grafana-source.tar.gz
RUN echo "f3649301d9b20d046b8d30fdd350df8c7fb9a8ad7c56e9bf3811008d2ae3a18f  /tmp/grafana-source.tar.gz" | sha256sum -c - \
    && tar -xzf /tmp/grafana-source.tar.gz --strip-components=1 -C /src
COPY infrastructure/images/grafana/dependency-update.patch /tmp/dependency-update.patch
RUN echo "acd062305fbe34b7b81c6ada0082e49bf6d713f6199caf33893765488adf3d3e  /tmp/dependency-update.patch" | sha256sum -c - \
    && patch -p1 < /tmp/dependency-update.patch
ENV COMMIT_SHA=3c98af3ba0773ad12a8a54b1faec7dc62d8d6725 \
    BUILD_BRANCH=release-12.4.14 SOURCE_DATE_EPOCH=1793099935
RUN --mount=type=cache,id=orderflow-grafana-go1273-mod,target=/go/pkg/mod \
    --mount=type=cache,id=orderflow-grafana-go1273-build,target=/root/.cache/go-build \
    test "$(go env GOVERSION)" = "go1.27.3" \
    && test -f pkg/server/wire_gen.go \
    && test -f pkg/services/preference/themes_generated.go \
    && find . -type f \( -name go.mod -o -name go.sum -o -name go.work -o -name go.work.sum \) -exec sha256sum {} \; | LC_ALL=C sort > /tmp/module-locks.before
RUN --mount=type=cache,id=orderflow-grafana-go1273-mod,target=/go/pkg/mod \
    --mount=type=cache,id=orderflow-grafana-go1273-build,target=/root/.cache/go-build \
    make build-go GO_BUILD_TAGS=oss WIRE_TAGS=oss \
      GO_LDFLAGS="-s -w -X main.version=12.4.14 -X main.commit=$COMMIT_SHA -X main.buildBranch=$BUILD_BRANCH -X main.buildstamp=$SOURCE_DATE_EPOCH" \
    && find . -type f \( -name go.mod -o -name go.sum -o -name go.work -o -name go.work.sum \) -exec sha256sum {} \; | LC_ALL=C sort > /tmp/module-locks.after \
    && cmp /tmp/module-locks.before /tmp/module-locks.after \
    && go version -m /src/bin/linux/amd64/grafana > /tmp/grafana-build-info.txt \
    && grep -F 'github.com/go-jose/go-jose/v4' /tmp/grafana-build-info.txt | grep -F 'v4.1.6' \
    && sha256sum /src/bin/linux/amd64/grafana > /tmp/grafana-binary.sha256

FROM scratch AS evidence
COPY --from=builder /src/bin/linux/amd64/grafana /grafana
COPY --from=builder /tmp/grafana-build-info.txt /grafana-build-info.txt
COPY --from=builder /tmp/grafana-binary.sha256 /grafana-binary.sha256
COPY --from=builder /tmp/module-locks.after /module-locks.after

FROM grafana/grafana:12.4.14@sha256:1b59d2076a5e08e0e9929a1225f37869ee7719f4a11c004fbfcfa8b772858e89 AS runtime
LABEL org.opencontainers.image.source="https://github.com/grafana/grafana" \
      org.opencontainers.image.revision="3c98af3ba0773ad12a8a54b1faec7dc62d8d6725" \
      io.orderflow.security.patch="CVE-2026-116587;go-jose-v4.1.6" \
      io.orderflow.security.source-sha256="f3649301d9b20d046b8d30fdd350df8c7fb9a8ad7c56e9bf3811008d2ae3a18f" \
      io.orderflow.security.dependency-patch-sha256="acd062305fbe34b7b81c6ada0082e49bf6d713f6199caf33893765488adf3d3e"
COPY --from=builder --chown=0:0 --chmod=0755 /src/bin/linux/amd64/grafana /usr/share/grafana/bin/grafana
