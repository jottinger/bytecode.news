# Build without tests
build:
    mvnd -DskipTests=true clean package

# Build with tests
test:
    mvnd clean package

# Build only if jar is missing
build-if-needed:
    [ -f app/target/app-1.0.jar ] || just build

# Start db, app, reference-ui, and mailpit for local development
deploy-dev: build-if-needed
    docker compose --profile backend --profile reference-ui --profile mail up --build

# Start db, app, and reference-ui
deploy: build-if-needed
    docker compose --profile backend --profile reference-ui up --build
