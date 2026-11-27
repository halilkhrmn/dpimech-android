#!/usr/bin/env bash
# Runs ./gradlew with the given arguments. When it fails only because Maven Central answered
# "429 Too Many Requests" (shared CI machines hit its rate limit while fetching dependencies),
# it waits a minute and runs again, at most twice. Any other failure (a test, lint, the build)
# fails at once, as before.
set -o pipefail
for attempt in 1 2 3; do
  log=$(mktemp)
  ./gradlew "$@" 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  [ "$status" -eq 0 ] && exit 0
  if [ "$attempt" -lt 3 ] && grep -q "status code 429" "$log" && ! grep -qE "tests completed, [0-9]+ failed|Lint found [0-9]+ error" "$log"; then
    echo "::warning::Maven Central rate limit (429), running Gradle again in 60 s (attempt $((attempt + 1)) of 3)"
    sleep 60
    continue
  fi
  exit "$status"
done
