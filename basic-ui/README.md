# basic-ui

Minimal read-only Spring Boot frontend for blog content.

## Runtime configuration

- `BACKEND_SCHEME` (default: `http`)
- `BACKEND_HOST` (default: `backend:8080`)

The app listens on port `3003`.

## Build and run with Docker

```bash
./mvnw -am -pl basic-ui -DskipTests package

docker build --no-cache -f basic-ui/Dockerfile -t basic-ui .

docker run -d --name basic-ui \
  -e BACKEND_SCHEME=https \
  -e BACKEND_HOST=api.bytecode.news \
  --add-host host.docker.internal:host-gateway \
  -p 3003:3003 \
  basic-ui
```
