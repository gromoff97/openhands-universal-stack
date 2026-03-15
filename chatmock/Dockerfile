FROM golang:1.24-bookworm AS build

ARG GO_CHATMOCK_COMMIT=0f12a129a62739d4e32c06161a244a844f420b32

WORKDIR /src

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN git clone https://github.com/n0madic/go-chatmock.git . \
    && git checkout "$GO_CHATMOCK_COMMIT"

RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/go-chatmock .

FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /out/go-chatmock /usr/local/bin/go-chatmock

ENV CHATGPT_LOCAL_HOME=/config

VOLUME ["/config"]

ENTRYPOINT ["go-chatmock"]
