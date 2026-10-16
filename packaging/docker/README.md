# Docker and Unraid

This follows the TigerVNC, noVNC, and Openbox approach used by
[Bulk Rename Utility Linux](https://github.com/SMAP-git/Bulk_Rename_Utility_Linux/tree/main/packaging/docker).
The image is Linux x86_64 and bundles the Debian application's Java and JavaFX.
Ubuntu's MediaInfo/Zen libraries replace the older Debian payload libraries to
ensure their dependencies are available. No privileged mode or host X server is needed.

## Build and run

```sh
./build-deb.sh
bash packaging/docker/build-docker.sh
mkdir -p "$HOME/fb-mod-config"
docker run --rm --name fb-mod \
  --user "$(id -u):$(id -g)" \
  -p 127.0.0.1:6081:6081 -p 127.0.0.1:5902:5902 \
  -e VNC_PASSWORD=changeit \
  -v "$HOME/fb-mod-config:/config" \
  -v "/path/to/media:/media:rw" \
  fb-mod:4.8.5
```

Open <http://localhost:6081/vnc.html?autoconnect=true&resize=scale>, or connect a
VNC client to `localhost:5902`. Replace the example password. `VNC_PASSWORD`
accepts 6-8 ASCII characters; leaving it empty disables authentication.
To connect without a password, omit the `-e VNC_PASSWORD=changeit` line or use
`-e VNC_PASSWORD=`. This applies to both native VNC and the browser WebUI.
Anyone who can reach either port can then control the app and modify mounted
files, so use passwordless access only on a trusted, isolated network.
VNC authentication does not encrypt traffic. Do not expose either port to the
Internet. Use a trusted LAN, SSH tunnel, or authenticated TLS reverse proxy.

`VNC_GEOMETRY` defaults to `1440x900`. Application preferences, provider credentials,
and cache persist in `/config`. Provider credentials are stored unencrypted by the
application. Only mount the media folders the app should be allowed to modify.
The container does not change ownership of host files; both mounts must be
writable by the configured UID/GID.

## Unraid

Use [unraid-template.xml](unraid-template.xml). The template uses
`ghcr.io/smap-git/fb-mod:4.8.5` from GitHub Container Registry:

```sh
docker pull ghcr.io/smap-git/fb-mod:4.8.5
```

Alternatively, download the
[loadable image archive](https://github.com/SMAP-git/FB-Mod/releases/download/v4.8.5-smap/FB-Mod_by_SMAP_4.8.5-unraid-docker.tar.gz)
from the GitHub release. It loads as `fb-mod:4.8.5`; set the template's
**Repository** field to `fb-mod:4.8.5` when using this offline alternative.
You can also transfer an image from the build machine:

```sh
docker save fb-mod:4.8.5 | gzip > fb-mod-4.8.5-docker.tar.gz
# On Unraid, after copying the archive:
gunzip -c fb-mod-4.8.5-docker.tar.gz | docker load
```

The locally exported build is
`dist/FB-Mod_by_SMAP_4.8.5-unraid-docker.tar.gz`. To load that file on Unraid:

```sh
gunzip -c FB-Mod_by_SMAP_4.8.5-unraid-docker.tar.gz | docker load
```

Copy the template into `/boot/config/plugins/dockerMan/templates-user/`,
then select it in **Docker > Add Container > User templates**.
Keep UID/GID `99:100` for Unraid's `nobody:users` or adjust `--user` as needed.
Map appdata to `/config` and your chosen media share to `/media`.
Set **VNC Password** for authentication, or leave it blank for passwordless
access, then open **WebUI** from the Unraid container menu.
Do not enable privileged mode.

## Publishing

The [GitHub Actions workflow](../../.github/workflows/publish-docker.yml) builds
from the checksum-verified Debian release asset and publishes the `4.8.5` and
`latest` tags to GHCR. It runs when Docker packaging changes reach `master` or
can be started manually. The GHCR package must be public for anonymous Unraid
pulls; package visibility is controlled in GitHub's package settings.
