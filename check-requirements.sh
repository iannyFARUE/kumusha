#!/usr/bin/env bash
#
# Kumusha requirement checker
#
# Verifies that the toolchain and configuration needed to run this project are in place.
# Nothing here modifies your machine; every check is read-only.
#
# Usage:
#   ./check-requirements.sh          Run every check
#   ./check-requirements.sh --pre    Only check the runtimes (before configuring .env)
#   ./check-requirements.sh --help   Show this help

set -uo pipefail

readonly REQUIRED_JAVA_MAJOR=21
readonly REQUIRED_NODE_MAJOR=20

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SERVER_DIR="${SCRIPT_DIR}/server"
readonly CLIENT_DIR="${SCRIPT_DIR}/client"

# Colours are disabled when stdout is not a terminal so logs stay readable
if [ -t 1 ]; then
  readonly RED=$'\033[0;31m'
  readonly GREEN=$'\033[0;32m'
  readonly YELLOW=$'\033[0;33m'
  readonly BOLD=$'\033[1m'
  readonly RESET=$'\033[0m'
else
  readonly RED='' GREEN='' YELLOW='' BOLD='' RESET=''
fi

failures=0
warnings=0

pass() { printf '  %s✓%s %s\n' "$GREEN" "$RESET" "$1"; }
warn() { printf '  %s!%s %s\n' "$YELLOW" "$RESET" "$1"; warnings=$((warnings + 1)); }
fail() { printf '  %s✗%s %s\n' "$RED" "$RESET" "$1"; failures=$((failures + 1)); }
section() { printf '\n%s%s%s\n' "$BOLD" "$1" "$RESET"; }

usage() {
  # Prints the header comment block (lines 2-11) as the help text
  sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

# Reads a KEY=value pair from an env file, stripping surrounding whitespace and quotes
read_env_value() {
  local file="$1" key="$2"

  grep -E "^[[:space:]]*${key}[[:space:]]*=" "$file" 2>/dev/null \
    | tail -1 \
    | cut -d= -f2- \
    | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' \
          -e 's/^"//' -e 's/"$//' \
          -e "s/^'//" -e "s/'$//" \
    | tr -d '\r'
}

# Extracts the major version from a version string such as "21.0.10" or "1.8.0_452"
major_version() {
  local version="$1"
  if [[ "$version" == 1.* ]]; then
    printf '%s' "$(printf '%s' "$version" | cut -d. -f2)"
  else
    printf '%s' "$(printf '%s' "$version" | cut -d. -f1)"
  fi
}

check_java() {
  section "Java"

  if ! command -v java >/dev/null 2>&1; then
    fail "java was not found on PATH. Install JDK ${REQUIRED_JAVA_MAJOR} or higher."
    return
  fi

  # java -version writes to stderr on every JDK, hence the redirect
  local raw major
  raw="$(java -version 2>&1 | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
  major="$(major_version "$raw")"

  if [ -z "$major" ] || ! [[ "$major" =~ ^[0-9]+$ ]]; then
    warn "Could not parse the Java version from: ${raw}"
  elif [ "$major" -lt "$REQUIRED_JAVA_MAJOR" ]; then
    fail "Java ${raw} found, but ${REQUIRED_JAVA_MAJOR} or higher is required."
  else
    pass "Java ${raw}"
  fi

  if [ -z "${JAVA_HOME:-}" ]; then
    warn "JAVA_HOME is not set. The Maven Wrapper needs it on most systems."
  elif [ ! -x "${JAVA_HOME}/bin/java" ]; then
    fail "JAVA_HOME points at '${JAVA_HOME}', which has no bin/java."
  else
    local home_raw home_major
    home_raw="$("${JAVA_HOME}/bin/java" -version 2>&1 | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
    home_major="$(major_version "$home_raw")"

    if [[ "$home_major" =~ ^[0-9]+$ ]] && [ "$home_major" -lt "$REQUIRED_JAVA_MAJOR" ]; then
      fail "JAVA_HOME points at Java ${home_raw}; the build needs ${REQUIRED_JAVA_MAJOR} or higher."
    else
      pass "JAVA_HOME -> ${JAVA_HOME} (Java ${home_raw})"
    fi
  fi
}

check_node() {
  section "Node.js"

  if ! command -v node >/dev/null 2>&1; then
    fail "node was not found on PATH. Install Node.js ${REQUIRED_NODE_MAJOR} or higher."
    return
  fi

  local raw major
  raw="$(node --version | sed 's/^v//')"
  major="$(major_version "$raw")"

  if [ "$major" -lt "$REQUIRED_NODE_MAJOR" ]; then
    fail "Node.js ${raw} found, but ${REQUIRED_NODE_MAJOR} or higher is required."
  else
    pass "Node.js ${raw}"
  fi

  if command -v npm >/dev/null 2>&1; then
    pass "npm $(npm --version)"
  else
    fail "npm was not found on PATH."
  fi
}

check_project_layout() {
  section "Project layout"

  [ -d "$SERVER_DIR" ] && pass "server/ present" || fail "server/ is missing"
  [ -d "$CLIENT_DIR" ] && pass "client/ present" || fail "client/ is missing"

  if [ -x "${SERVER_DIR}/mvnw" ]; then
    pass "Maven Wrapper is executable"
  elif [ -f "${SERVER_DIR}/mvnw" ]; then
    warn "server/mvnw is not executable. Run: chmod +x server/mvnw"
  else
    fail "server/mvnw is missing"
  fi

  if [ -d "${CLIENT_DIR}/node_modules" ]; then
    pass "Client dependencies installed"
  else
    warn "Client dependencies are not installed. Run: cd client && npm install"
  fi
}

check_configuration() {
  section "Configuration"

  local env_file="${SERVER_DIR}/.env"

  if [ ! -f "$env_file" ]; then
    fail "server/.env is missing. Create it with: cp server/.env.example server/.env"
    return
  fi

  pass "server/.env exists"

  local mongodb_uri
  mongodb_uri="$(read_env_value "$env_file" MONGODB_URI)"

  if [ -z "$mongodb_uri" ]; then
    fail "MONGODB_URI is not set in server/.env"
  elif [[ "$mongodb_uri" == *"<username>"* || "$mongodb_uri" == *"<password>"* || "$mongodb_uri" == *"<cluster>"* ]]; then
    fail "MONGODB_URI still contains placeholder values. Replace them with your credentials."
  elif [[ "$mongodb_uri" != mongodb://* && "$mongodb_uri" != mongodb+srv://* ]]; then
    fail "MONGODB_URI does not look like a MongoDB connection string."
  else
    pass "MONGODB_URI is set"

    if [[ "$mongodb_uri" != *"sample_airbnb"* ]]; then
      warn "MONGODB_URI does not mention sample_airbnb. The app reads that database regardless, but check you loaded the sample data."
    fi
  fi

  local voyage_key
  voyage_key="$(read_env_value "$env_file" VOYAGE_API_KEY)"

  if [ -z "$voyage_key" ] || [ "$voyage_key" = "your_voyage_api_key" ]; then
    warn "VOYAGE_API_KEY is not set. Vector search and the embedding backfill will be unavailable; everything else works."
  else
    pass "VOYAGE_API_KEY is set"
  fi
}

print_summary() {
  section "Summary"

  if [ "$failures" -gt 0 ]; then
    printf '  %s%d check(s) failed%s' "$RED" "$failures" "$RESET"
    [ "$warnings" -gt 0 ] && printf ', %d warning(s)' "$warnings"
    printf '\n\n'
    return 1
  fi

  if [ "$warnings" -gt 0 ]; then
    printf '  %sAll required checks passed, with %d warning(s).%s\n\n' "$YELLOW" "$warnings" "$RESET"
    return 0
  fi

  printf '  %sEverything looks good.%s\n\n' "$GREEN" "$RESET"
  return 0
}

main() {
  local pre_only=false

  case "${1:-}" in
    --help|-h)
      usage
      exit 0
      ;;
    --pre)
      pre_only=true
      ;;
    '')
      ;;
    *)
      printf 'Unknown option: %s\n\n' "$1"
      usage
      exit 2
      ;;
  esac

  printf '%sKumusha requirement check%s\n' "$BOLD" "$RESET"

  check_java
  check_node

  if [ "$pre_only" = false ]; then
    check_project_layout
    check_configuration
  else
    printf '\n  Skipping project and configuration checks (--pre).\n'
  fi

  print_summary
}

main "$@"
