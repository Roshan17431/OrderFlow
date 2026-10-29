# OrderFlow maintained official-source build; provenance in source-provenance.json.
FROM --platform=$BUILDPLATFORM golang:1.27.3-alpine3.24@sha256:d200d2dd76b03a99b52651fd1325aff7dcd35422c35c01bac56571b0507f3deb AS build
ENV GOTOOLCHAIN=local GOSUMDB=sum.golang.org CGO_ENABLED=0 GOMAXPROCS=2 GOMEMLIMIT=768MiB
WORKDIR /src
ADD --checksum=sha256:cfec7478aa9bfd011f29df084584b7c7b68dfef85a24d28e9d6d5f88224f83c3 https://codeload.github.com/prometheus/node_exporter/tar.gz/6044da783597cc3b57aef7580ddcdcff58a4ee99 /tmp/source.tar.gz
RUN tar -xzf /tmp/source.tar.gz --strip-components=1 -C /src \
    && echo '0e4614c617f16e308698ed1afdd0f0f055b119d4cdf015b336c095eb35fa34ed  go.mod' | sha256sum -c - \
    && echo 'e9457fad10a31b707ebfb68dc8378ec71f3271d04faf84f7ec8f9d231d81ea57  go.sum' | sha256sum -c -
COPY infrastructure/images/node-exporter/go.mod infrastructure/images/node-exporter/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    echo '57c83f68e6b65f17c4235ca6c2a6de2b55324ebc9806db6199d9abaf25f41d9b  go.mod' | sha256sum -c - \
    && echo '900eae667063c7b94f4fc994ced89cd41e4bec219a93ec743f20c330eee34f13  go.sum' | sha256sum -c - \
    && go mod download && go mod verify
ARG TARGETARCH
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    GOOS=linux GOARCH=$TARGETARCH go build -mod=readonly -p=2 -trimpath -buildvcs=false \
    -ldflags '-X github.com/prometheus/common/version.Version=1.12.1+orderflow.20261028 -X github.com/prometheus/common/version.Revision=6044da783597cc3b57aef7580ddcdcff58a4ee99 -X github.com/prometheus/common/version.Branch=orderflow-maintained -X github.com/prometheus/common/version.BuildUser=orderflow -X github.com/prometheus/common/version.BuildDate=20261028-00:00:00' \
    -o /out/node_exporter .

FROM prom/node-exporter:v1.12.1@sha256:1b4e4438faca4dd7e001dd445d161a4a2091b0fededa84093b3a8dfeae1f1be0
COPY --from=build --chown=0:0 --chmod=0755 /out/node_exporter /bin/node_exporter
LABEL org.opencontainers.image.source="https://github.com/prometheus/node_exporter" \
      org.opencontainers.image.revision="6044da783597cc3b57aef7580ddcdcff58a4ee99" \
      org.opencontainers.image.description="OrderFlow maintained node-exporter; Go 1.27.3 and compatible locked module security patches; not an upstream prebuilt image"
