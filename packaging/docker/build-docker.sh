#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="$(sed -n 's/^application.version: *//p' "$ROOT_DIR/app.properties")"
APP_NAME="$(sed -n 's/^application.name: *//p' "$ROOT_DIR/app.properties")"
DEB_PACKAGE="${DEB_PACKAGE:-$ROOT_DIR/dist/${APP_NAME}_${VERSION}_amd64.deb}"
IMAGE_NAME="${IMAGE_NAME:-fb-mod:$VERSION}"
if [[ ! -f "$DEB_PACKAGE" ]]; then
    printf 'Debian package not found: %s\nRun ./build-deb.sh first.\n' "$DEB_PACKAGE" >&2
    exit 1
fi
docker info >/dev/null
BUILD_DIR="$(mktemp -d /tmp/filebot-docker-build.XXXXXXXX)"
trap 'rm -rf -- "$BUILD_DIR"' EXIT
cp "$ROOT_DIR/packaging/docker/"{Dockerfile,docker-entrypoint.sh,openbox-rc.xml,unraid-icon.svg} "$BUILD_DIR/"
cp "$DEB_PACKAGE" "$BUILD_DIR/filebot.deb"
docker build --platform linux/amd64 --tag "$IMAGE_NAME" "$BUILD_DIR"
printf 'Created Docker image %s\n' "$IMAGE_NAME"
