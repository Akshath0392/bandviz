#!/usr/bin/env zsh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

# Auto-load env vars from project .env if present.
if [[ -f ".env" ]]; then
  set -a
  source ".env"
  set +a
fi

ruby scripts/seed_from_jira.rb "$@"
