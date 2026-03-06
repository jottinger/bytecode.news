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
    [ -f app/target/app-1.0.jar ] || just build

# Start db, app, reference-ui, and mailpit for local development
deploy-dev: build-if-needed
    docker compose --profile backend --profile reference-ui --profile mail up --build

# Start db, app, and reference-ui
deploy: build-if-needed
    docker compose --profile backend --profile reference-ui up --build
