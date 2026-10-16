#!/usr/bin/env bash
set -euo pipefail

destination="$(realpath "$1")"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/filebot-native.XXXXXXXX")"
trap 'rm -rf -- "$work_dir"' EXIT

download_verified() {
	local url="$1" checksum="$2" archive="$3"
	curl --fail --location --retry 3 --silent --show-error "$url" --output "$archive"
	printf '%s  %s\n' "$checksum" "$archive" | sha256sum --check --status || {
		echo "Native source checksum verification failed: $url" >&2
		exit 1
	}
}

download_verified https://mediaarea.net/download/source/libzen/0.4.41/libzen_0.4.41.tar.xz \
	933bad3b7ecd29dc6bdc88a83645c83dfd098c15b0b90d6177a37fa1536704e8 "$work_dir/zen.tar.xz"
download_verified https://mediaarea.net/download/source/libmediainfo/25.09/libmediainfo_25.09.tar.xz \
	8562e8ea03e2af8bde27f66d79a0f573859b8f13193ec115abf10f735b313a12 "$work_dir/mediainfo.tar.xz"
tar -xJf "$work_dir/zen.tar.xz" -C "$work_dir"
tar -xJf "$work_dir/mediainfo.tar.xz" -C "$work_dir"

cmake -S "$work_dir/ZenLib/Project/CMake" -B "$work_dir/zen-build" \
	-DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$work_dir/prefix" \
	-DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
	-DCMAKE_INSTALL_LIBDIR=lib -DBUILD_SHARED_LIBS=ON
cmake --build "$work_dir/zen-build" --parallel 2
cmake --install "$work_dir/zen-build"
cmake -S "$work_dir/MediaInfoLib/Project/CMake" -B "$work_dir/mediainfo-build" \
	-DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$work_dir/prefix" \
	-DCMAKE_PREFIX_PATH="$work_dir/prefix" -DBUILD_SHARED_LIBS=ON \
	-DCMAKE_DISABLE_FIND_PACKAGE_CURL=ON
cmake --build "$work_dir/mediainfo-build" --parallel 2
cmake --install "$work_dir/mediainfo-build"

cp -a "$work_dir/prefix/lib/"*.so* "$destination/"
mkdir -p "$destination/../licenses/mediainfo" "$destination/../licenses/zen"
cp "$work_dir/MediaInfoLib/License.html" "$destination/../licenses/mediainfo/"
cp "$work_dir/ZenLib/License.txt" "$destination/../licenses/zen/"
