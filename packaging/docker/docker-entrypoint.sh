#!/usr/bin/env bash
set -euo pipefail

umask 002
if [[ ! -w /config || ! -w /media ]]; then
    printf '/config and /media must be writable by UID %s / GID %s.\n' "$(id -u)" "$(id -g)" >&2
    exit 1
fi
export HOME=/config DISPLAY=:1
mkdir -p "$HOME/.filebot" "$HOME/.cache" "$HOME/.config"
RUNTIME_DIR="$(mktemp -d /tmp/filebot-vnc.XXXXXXXX)"
export XDG_RUNTIME_DIR="$RUNTIME_DIR"
PIDS=()
cleanup() {
    for pid in "${PIDS[@]}"; do
        kill "$pid" 2>/dev/null || true
    done
    for pid in "${PIDS[@]}"; do
        wait "$pid" 2>/dev/null || true
    done
    rm -f /tmp/.X1-lock /tmp/.X11-unix/X1
    rm -rf -- "$RUNTIME_DIR"
}
trap cleanup EXIT
trap 'exit 0' INT TERM
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1

SECURITY_ARGS=(-SecurityTypes None)
if [[ -n "${VNC_PASSWORD:-}" ]]; then
    if ! LC_ALL=C grep -qxE '[ -~]{6,8}' <<< "$VNC_PASSWORD" || [[ "$VNC_PASSWORD" == *$'\n'* ]]; then
        printf 'VNC_PASSWORD must contain 6-8 printable ASCII characters.\n' >&2
        exit 1
    fi
    printf '%s\n' "$VNC_PASSWORD" | tigervncpasswd -f > "$RUNTIME_DIR/passwd"
    chmod 600 "$RUNTIME_DIR/passwd"
    SECURITY_ARGS=(-SecurityTypes VncAuth -rfbauth "$RUNTIME_DIR/passwd")
else
    printf 'WARNING: VNC authentication is disabled. Use only on a trusted network.\n' >&2
fi
unset VNC_PASSWORD

Xtigervnc :1 -localhost no -rfbport "$VNC_PORT" "${SECURITY_ARGS[@]}" \
    -geometry "$VNC_GEOMETRY" -depth 24 -nolisten tcp &
VNC_PID=$!
PIDS+=("$VNC_PID")
ready=false
for attempt in {1..100}; do
    if xdpyinfo >/dev/null 2>&1; then
        ready=true
        break
    fi
    if ! kill -0 "$VNC_PID" 2>/dev/null; then
        printf 'TigerVNC exited before the display was ready.\n' >&2
        exit 1
    fi
    sleep 0.1
done
if [[ "$ready" != true ]]; then
    printf 'TigerVNC display did not become ready.\n' >&2
    exit 1
fi

websockify --web=/usr/share/novnc "$NOVNC_PORT" "127.0.0.1:$VNC_PORT" &
PIDS+=("$!")
openbox --config-file /etc/xdg/openbox/rc.xml &
PIDS+=("$!")
cd /media
/usr/share/filebot/bin/filebot.sh &
PIDS+=("$!")
wait -n "${PIDS[@]}"
