#!/usr/bin/env bash
set -euo pipefail

version="3.7.2"
cache_dir="${GRADLE_USER_HOME:-${HOME}/.gradle}/caches/modules-2/files-2.1/com.formdev/flatlaf/${version}"
flatlaf_jar="$(find "${cache_dir}" -type f -name "flatlaf-${version}.jar" -print -quit 2>/dev/null || true)"

if [[ -z "${flatlaf_jar}" ]]; then
    printf 'FlatLaf %s was not found in the Gradle cache. Run `gradle compileJava` first.\n' "${version}" >&2
    exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec java --class-path "${flatlaf_jar}" "${script_dir}/list-ui-colors.java" "${1:-docs/SUPPORTED_COLORS.md}"
