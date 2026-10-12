#!/usr/bin/env bash
# Uploads the R8 mapping of the release build that produced APK $1 to Sentry, under the
# ProGuard UUID that APK reports (assets/sentry-debug-meta.properties, see loakApp/build.gradle.kts).
# Skipped without SENTRY_AUTH_TOKEN; a failed upload only warns, it never blocks a release.
set -euo pipefail

apk="$1"
if [ -z "${SENTRY_AUTH_TOKEN:-}" ]; then
  echo "SENTRY_AUTH_TOKEN unset: not uploading the R8 mapping"
  exit 0
fi
uuid=$(unzip -p "$apk" assets/sentry-debug-meta.properties | sed -n 's/^io\.sentry\.ProguardUuids=//p' || true)
if [ -z "$uuid" ]; then
  echo "::warning::$apk has no Sentry ProGuard UUID; not uploading the R8 mapping"
  exit 0
fi
npx --yes @sentry/cli@2.58.6 upload-proguard --require-one --uuid "$uuid" \
  loakApp/build/outputs/mapping/release/mapping.txt \
  || echo "::warning::Sentry mapping upload failed; this build's stack traces stay unreadable"
