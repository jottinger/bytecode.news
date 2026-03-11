#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT_DIR"

if command -v rg >/dev/null 2>&1; then
  FIND_SRC_MATCHES() {
    rg -n \
      "org\\.springframework\\.web|jakarta\\.servlet|javax\\.servlet|@RestController|@Controller|@RequestMapping|@GetMapping|@PostMapping|@PutMapping|@DeleteMapping|ResponseEntity|MultipartFile|MediaType|HttpServlet|ServerHttp" \
      operation-*/src
  }
  FIND_POM_MATCHES() {
    rg -n \
      "spring-boot-starter-web|spring-webmvc|spring-web|jakarta.servlet-api|jakarta.servlet" \
      operation-*/pom.xml
  }
else
  FIND_SRC_MATCHES() {
    grep -R -n -E \
      "org\\.springframework\\.web|jakarta\\.servlet|javax\\.servlet|@RestController|@Controller|@RequestMapping|@GetMapping|@PostMapping|@PutMapping|@DeleteMapping|ResponseEntity|MultipartFile|MediaType|HttpServlet|ServerHttp" \
      operation-*/src || true
  }
  FIND_POM_MATCHES() {
    grep -R -n -E \
      "spring-boot-starter-web|spring-webmvc|spring-web|jakarta.servlet-api|jakarta.servlet" \
      operation-*/pom.xml || true
  }
fi

SRC_MATCHES="$(FIND_SRC_MATCHES || true)"
POM_MATCHES="$(FIND_POM_MATCHES || true)"

if [ -z "$SRC_MATCHES" ] && [ -z "$POM_MATCHES" ]; then
  echo "[operation-web-coupling] OK: no web-layer coupling detected in operation-* modules."
  exit 0
fi

echo "============================================================"
echo "WARNING: operation-* web-coupling drift detected"
echo "This is advisory only (non-blocking), but should be reviewed."
echo "============================================================"
if [ -n "$SRC_MATCHES" ]; then
  echo
  echo "[source matches]"
  echo "$SRC_MATCHES"
fi
if [ -n "$POM_MATCHES" ]; then
  echo
  echo "[pom matches]"
  echo "$POM_MATCHES"
fi
echo
echo "Recommendation: keep web concerns in service-* modules."
exit 0
