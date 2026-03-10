default:
    just --list

# Build without tests
build: clean
    mvnd -DskipTests=true package

clean:
    find . -name "output.log*" -exec rm {} \;
    mvnd clean

# Build with tests
test: clean
    mvnd package

# Run tests and generate aggregate coverage report
coverage:
    mvnd clean verify
    @echo "Coverage report: coverage-report/target/site/jacoco-aggregate/index.html"

# Build only if jar is missing
build-if-needed:
    [ -f app/target/app-1.0.jar ] && [ -f basic-ui/target/basic-ui-1.0.jar ] || just build

# Start db, app, reference-ui, basic-ui, and mailpit for local development
deploy-dev: build-if-needed
    docker compose --profile backend --profile reference-ui --profile basic-ui --profile mail build --no-cache
    docker compose --profile backend --profile reference-ui --profile basic-ui --profile mail up

# Start db, app, reference-ui, and basic-ui
deploy: build-if-needed
    docker compose --profile backend --profile reference-ui --profile basic-ui build --no-cache
    docker compose --profile backend --profile reference-ui --profile basic-ui up

# Rebuild and redeploy only the reference-ui container
redeploy-reference-ui:
    docker rm -f reference-ui || true
    docker build --no-cache \
      --build-arg VITE_UI_COMMIT=$(git rev-parse --short HEAD) \
      --build-arg VITE_UI_BRANCH=$(git rev-parse --abbrev-ref HEAD) \
      -t reference-ui reference-ui/
    docker run -d --name reference-ui \
      -e BACKEND_SCHEME=https \
      -e BACKEND_HOST=api.bytecode.news \
      --add-host host.docker.internal:host-gateway \
      -p 3001:3001 \
      reference-ui

# Rebuild and redeploy only the basic-ui container
redeploy-basic-ui:
    docker rm -f basic-ui || true
    ./mvnw -am -pl basic-ui -DskipTests package
    docker build --no-cache \
      -f basic-ui/Dockerfile \
      -t basic-ui .
    docker run -d --name basic-ui \
      -e BACKEND_SCHEME=https \
      -e BACKEND_HOST=api.bytecode.news \
      --add-host host.docker.internal:host-gateway \
      -p 3003:3003 \
      basic-ui

# Rebuild and redeploy both UI containers
redeploy-uis:
    just redeploy-reference-ui
    just redeploy-basic-ui

# Install repository-managed git hooks
install-git-hooks:
    chmod +x .githooks/pre-commit .githooks/pre-push
    git config core.hooksPath .githooks
    @echo "Installed git hooks from .githooks/"
