#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
	echo "Linux portable build requires Linux x86_64." >&2
	exit 1
fi
for command in cmake make g++ pkg-config dpkg-deb curl sha256sum tar python3; do
	if ! command -v "$command" >/dev/null 2>&1; then
		echo "Required command not found: $command" >&2
		exit 1
	fi
done
pkg-config --exists zlib || {
	echo "Required development dependency not found: zlib" >&2
	exit 1
}
bash "$ROOT_DIR/build-deb.sh"

release="$(python3 -c '
import pathlib
properties = dict(line.split(":", 1) for line in pathlib.Path("app.properties").read_text().splitlines() if ":" in line and not line.startswith("#"))
print(properties["application.name"].strip() + "_" + properties["application.version"].strip())
')"
tmp_dir="$(mktemp -d "$ROOT_DIR/dist/.linux-portable-build.XXXXXXXX")"
trap 'rm -rf -- "$tmp_dir"' EXIT
dpkg-deb --extract "$ROOT_DIR/dist/${release}_amd64.deb" "$tmp_dir/deb"
package="$tmp_dir/${release}"
mv "$tmp_dir/deb/usr/share/filebot" "$package"
chmod 755 "$package/lib/fpcalc"
bash "$ROOT_DIR/installer/tar/build-native.sh" "$package/lib"

curl --fail --location --retry 3 --silent --show-error \
	https://7-zip.org/a/7z2501-linux-x64.tar.xz --output "$tmp_dir/7zip.tar.xz"
printf '%s  %s\n' \
	4ca3b7c6f2f67866b92622818b58233dc70367be2f36b498eb0bdeaaa44b53f4 \
	"$tmp_dir/7zip.tar.xz" | sha256sum --check --status || {
	echo "7-Zip SHA256 verification failed." >&2
	exit 1
}
tar -xJf "$tmp_dir/7zip.tar.xz" -C "$tmp_dir" 7zzs License.txt
install -m 755 "$tmp_dir/7zzs" "$package/bin/7z"
mkdir -p "$package/licenses/7zip"
install -m 644 "$tmp_dir/License.txt" "$package/licenses/7zip/"
install -m 644 "$ROOT_DIR/LICENSE.md" "$package/licenses/LICENSE.md"
install -m 644 "$ROOT_DIR/installer/icons/filebot.svg" "$package/filebot.svg"

sed \
	-e 's|FILEBOT_HOME="/usr/share/filebot"|FILEBOT_HOME="$(CDPATH= cd -- "$(dirname -- "$0")" \&\& pwd)"|' \
	-e 's|APP_DATA="$HOME/.filebot"|APP_DATA="$FILEBOT_HOME/data"|' \
	-e '/^MODULE_PATH=/a export LD_LIBRARY_PATH="$LIBRARY_PATH${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"\nexport PATH="$FILEBOT_HOME/bin:$PATH"\nmkdir -p "$APP_DATA"' \
	-e 's|-Dapplication.deployment=deb|-Dapplication.deployment=tar -Duser.home="$APP_DATA" -Djava.util.prefs.userRoot="$APP_DATA/preferences"|' \
	"$package/bin/filebot.sh" > "$package/filebot.sh"
chmod 755 "$package/filebot.sh"
tar -cJf "$ROOT_DIR/dist/${release}-linux-x86_64-portable.tar.xz" -C "$tmp_dir" "$release"
echo "Linux portable archive built: $ROOT_DIR/dist/${release}-linux-x86_64-portable.tar.xz"
