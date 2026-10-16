#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
	echo "Windows portable cross-build requires Linux x86_64." >&2
	exit 1
fi

for command in ant curl sha256sum; do
	if ! command -v "$command" >/dev/null 2>&1; then
		echo "Required command not found: $command" >&2
		exit 1
	fi
done

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/filebot-windows-build.XXXXXXXX")"
trap 'rm -rf -- "$tmp_dir"' EXIT

download_verified() {
	local url="$1" checksum="$2" destination="$3"
	if [[ -f "$destination" ]] && printf '%s  %s\n' "$checksum" "$destination" | sha256sum --check --status; then
		return
	fi
	local temporary="$tmp_dir/download-$(basename -- "$destination")"
	curl --fail --location --retry 3 --retry-delay 2 --silent --show-error "$url" --output "$temporary"
	if ! printf '%s  %s\n' "$checksum" "$temporary" | sha256sum --check --status; then
		echo "SHA256 verification failed: $url" >&2
		exit 1
	fi
	mv -- "$temporary" "$destination"
}

mkdir -p cache
download_verified \
	"https://repo.maven.apache.org/maven2/org/apache/ivy/ivy/2.5.3/ivy-2.5.3.jar" \
	"9502aa5aabf0b1484924a13ef4b5d24f121f7f91aab6768f11cc9d0906c0803b" \
	"$tmp_dir/ivy.jar"
download_verified \
	"https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_windows-x64_bin.zip" \
	"cf39490fe042dba1b61d6e9a395095279a69e70086c8c8d5466d9926d80976d8" \
	"$ROOT_DIR/cache/openjdk-11.0.2_windows-x64_bin.zip"
download_verified \
	"https://download2.gluonhq.com/openjfx/11.0.2/openjfx-11.0.2_windows-x64_bin-sdk.zip" \
	"2dd008e0c865f9bc02abd4aaf11ceeb15ca5bfe8c434e613501feda60528ce61" \
	"$ROOT_DIR/cache/openjfx-11.0.2_windows-x64_bin-sdk.zip"

ant -lib "$tmp_dir/ivy.jar" -Dwindows.runtime.prepared=true resolve jar zip
echo "Windows x64 portable ZIP built in: $ROOT_DIR/dist/"
