default:
    just --list

# Build without tests
build: clean
    mvnd -DskipTests=true package

clean-docker:
    docker builder prune -af
    docker container prune -f
    docker image prune -af

clean:
    find . -name "output.log*" -exec rm {} \;
    mvnd clean

clean-all: clean-docker clean

# Build with tests
test: clean
    mvnd package

# Run tests and generate aggregate coverage report
coverage:
    mvnd clean verify
    @echo "Coverage report: coverage-report/target/site/jacoco-aggregate/index.html"

# Build only if jar is missing
build-if-needed:
    [ -f app/target/app-1.0.jar ] && [ -f ui-basic/target/ui-basic-1.0.jar ] || just build

# Start db, app, ui-nextjs, ui-reference, ui-basic, and mailpit for local development
deploy-dev: build-if-needed
    NEXT_PUBLIC_UI_COMMIT=$(git rev-parse --short HEAD) NEXT_PUBLIC_UI_BRANCH=$(git rev-parse --abbrev-ref HEAD) docker compose --profile backend --profile ui-nextjs --profile ui-reference --profile ui-basic --profile mail build
    docker compose --profile backend --profile ui-nextjs --profile ui-reference --profile ui-basic --profile mail up

# Start db, app, ui-nextjs, ui-reference, and ui-basic
deploy: build-if-needed
    NEXT_PUBLIC_UI_COMMIT=$(git rev-parse --short HEAD) NEXT_PUBLIC_UI_BRANCH=$(git rev-parse --abbrev-ref HEAD) docker compose --profile backend --profile ui-nextjs --profile ui-reference --profile ui-basic build --no-cache
    docker compose --profile backend --profile ui-nextjs --profile ui-reference --profile ui-basic up

# Rebuild and redeploy only the ui-nextjs container
redeploy-ui-nextjs:
    docker build \
      --build-arg NEXT_PUBLIC_UI_COMMIT=$(git rev-parse --short HEAD) \
      --build-arg NEXT_PUBLIC_UI_BRANCH=$(git rev-parse --abbrev-ref HEAD) \
      -t ui-nextjs:next ui-nextjs/
    docker rm -f ui-nextjs-old || true
    docker rename ui-nextjs ui-nextjs-old || true
    docker stop ui-nextjs-old || true
    docker run -d --name ui-nextjs \
      -e BACKEND_SCHEME=https \
      -e BACKEND_HOST=api.bytecode.news \
      --add-host host.docker.internal:host-gateway \
      -p 3000:3000 \
      ui-nextjs:next
    docker rm -f ui-nextjs-old || true

# Rebuild and redeploy only the ui-reference container
redeploy-ui-reference:
    docker build  \
      --build-arg VITE_UI_COMMIT=$(git rev-parse --short HEAD) \
      --build-arg VITE_UI_BRANCH=$(git rev-parse --abbrev-ref HEAD) \
      -t ui-reference:next ui-reference/
    docker rm -f ui-reference-old || true
    docker rename ui-reference ui-reference-old || true
    docker stop ui-reference-old || true
    docker run -d --name ui-reference \
      -e BACKEND_SCHEME=https \
      -e BACKEND_HOST=api.bytecode.news \
      --add-host host.docker.internal:host-gateway \
      -p 3001:3001 \
      ui-reference:next
    docker rm -f ui-reference-old || true

# Rebuild and redeploy only the ui-basic container
redeploy-ui-basic:
    ./mvnw -am -pl ui-basic -DskipTests package
    docker build  \
      -f ui-basic/Dockerfile \
      -t ui-basic:next .
    docker rm -f ui-basic-old || true
    docker rename ui-basic ui-basic-old || true
    docker stop ui-basic-old || true
    docker run -d --name ui-basic \
      -e BACKEND_SCHEME=https \
      -e BACKEND_HOST=api.bytecode.news \
      --add-host host.docker.internal:host-gateway \
      -p 3003:3003 \
      ui-basic:next
    docker rm -f ui-basic-old || true

# Rebuild and redeploy both UI containers
redeploy-uis:
    just redeploy-ui-nextjs
    just redeploy-ui-reference
    just redeploy-ui-basic

# Run non-blocking warnings for operation-layer web coupling drift
warn-operation-web-coupling:
    ./mvnw -q -N exec:exec@warn-operation-web-coupling

# Install repository-managed git hooks
install-git-hooks:
    chmod +x .githooks/pre-commit .githooks/pre-push
    git config core.hooksPath .githooks
    @echo "Installed git hooks from .githooks/"
