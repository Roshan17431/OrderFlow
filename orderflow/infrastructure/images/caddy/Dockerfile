# Supported upstream standard-module build; see ../README.md for provenance.
FROM --platform=$BUILDPLATFORM golang:1.27.3-alpine3.24@sha256:d200d2dd76b03a99b52651fd1325aff7dcd35422c35c01bac56571b0507f3deb AS build
ENV GOTOOLCHAIN=local GOSUMDB=sum.golang.org CGO_ENABLED=0 GOMAXPROCS=2 GOMEMLIMIT=768MiB
WORKDIR /src
COPY infrastructure/images/caddy/go.mod infrastructure/images/caddy/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download && go mod verify
COPY infrastructure/images/caddy/main.go ./
ARG TARGETARCH
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    GOOS=linux GOARCH=$TARGETARCH go build -mod=readonly -p=2 -trimpath -buildvcs=false -ldflags='-s -w' -o /out/caddy .

FROM caddy:2.11.7-alpine@sha256:eb4d35b7452d1ab6171fbdbe8d9275b2301dad6dad92115d91e19fb9b24d510f
# Signed Alpine updates also refresh native packages and certificate roots.
RUN apk upgrade --no-cache
COPY --from=build /out/caddy /usr/bin/caddy
LABEL org.opencontainers.image.source="https://github.com/caddyserver/caddy" \
      org.opencontainers.image.revision="72dd0fb067f6d7826c7f79907670ba4a713bfe37" \
      org.opencontainers.image.description="OrderFlow maintained standard Caddy build; Go 1.27.3 and x/net 0.62.0; not an upstream prebuilt image"
