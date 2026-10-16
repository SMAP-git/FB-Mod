#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
	echo "Debian package build requires Linux x86_64." >&2
	exit 1
fi

for command in ant curl sha256sum python3; do
	if ! command -v "$command" >/dev/null 2>&1; then
		echo "Required command not found: $command" >&2
		exit 1
	fi
done

ivy_version=2.5.3
ivy_sha256=9502aa5aabf0b1484924a13ef4b5d24f121f7f91aab6768f11cc9d0906c0803b
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/filebot-deb-build.XXXXXXXX")"
trap 'rm -rf -- "$tmp_dir"' EXIT

ivy_jar="$tmp_dir/ivy-${ivy_version}.jar"
curl --fail --location --retry 3 --retry-delay 2 --silent --show-error \
	"https://repo.maven.apache.org/maven2/org/apache/ivy/ivy/${ivy_version}/ivy-${ivy_version}.jar" \
	--output "$ivy_jar"
printf '%s  %s\n' "$ivy_sha256" "$ivy_jar" | sha256sum --check --status || {
	echo "Apache Ivy checksum verification failed." >&2
	exit 1
}

ant -lib "$ivy_jar" resolve jar deb

echo "Debian package built in: $ROOT_DIR/dist/"
