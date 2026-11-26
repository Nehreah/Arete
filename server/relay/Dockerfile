# Multi-stage build para binary estático mínimo y seguro
FROM golang:1.22-alpine AS builder

WORKDIR /app
COPY main.go .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o arete-relay main.go

FROM scratch
COPY --from=builder /app/arete-relay /arete-relay
EXPOSE 8080
ENTRYPOINT ["/arete-relay"]
