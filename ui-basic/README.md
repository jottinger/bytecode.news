# ui-basic

Minimal read-only Spring Boot frontend for blog content.

## Runtime configuration

- `BACKEND_SCHEME` (default: `http`)
- `BACKEND_HOST` (default: `backend:8080`)

The app listens on port `3003`.

## Build and run with Docker

```bash
./mvnw -am -pl ui-basic -DskipTests package

docker build --no-cache -f ui-basic/Dockerfile -t ui-basic .

docker run -d --name ui-basic \
  -e BACKEND_SCHEME=https \
  -e BACKEND_HOST=api.bytecode.news \
  --add-host host.docker.internal:host-gateway \
  -p 3003:3003 \
  ui-basic
```
