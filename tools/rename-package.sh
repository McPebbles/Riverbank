#!/usr/bin/env bash
# Rename the application ID / package across the whole project.
#   ./tools/rename-package.sh io.github.yourname.riverbank
set -euo pipefail

NEW="${1:-}"
OLD="org.riverbank.shop"
if [[ -z "$NEW" ]]; then
  echo "usage: $0 <new.application.id>" >&2
  exit 1
fi
if ! [[ "$NEW" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
  echo "error: '$NEW' is not a valid Android application ID" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

OLD_PATH="${OLD//./\/}"
NEW_PATH="${NEW//./\/}"
SRC="app/src/main/java"

echo "Moving sources $OLD -> $NEW"
mkdir -p "$SRC/$NEW_PATH"
if [[ -d "$SRC/$OLD_PATH" ]]; then
  cp -r "$SRC/$OLD_PATH/." "$SRC/$NEW_PATH/"
  rm -rf "$SRC/${OLD_PATH%%/*}"
fi

echo "Rewriting references"
grep -rl --binary-files=without-match "$OLD" \
  app/src fdroid .github docs *.md 2>/dev/null | while read -r f; do
  sed -i "s|$OLD|$NEW|g" "$f"
done
sed -i "s|$OLD|$NEW|g" app/build.gradle.kts
mv "fdroid/metadata/$OLD.yml" "fdroid/metadata/$NEW.yml" 2>/dev/null || true

echo "Done. Review with: git diff --stat"
