FROM ubuntu:24.04

ARG DEBIAN_FRONTEND=noninteractive
COPY filebot.deb /tmp/filebot.deb
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates novnc openbox python3-xdg tigervnc-standalone-server \
        tigervnc-tools websockify x11-utils libmediainfo0v5 libzen0v5 \
        /tmp/filebot.deb \
    && rm -f /tmp/filebot.deb \
        /usr/share/filebot/lib/libmediainfo.so /usr/share/filebot/lib/libzen.so \
    && ln -s /usr/lib/x86_64-linux-gnu/libmediainfo.so.0 /usr/share/filebot/lib/libmediainfo.so \
    && ln -s /usr/lib/x86_64-linux-gnu/libzen.so.0 /usr/share/filebot/lib/libzen.so \
    && chmod 755 /usr/share/filebot/lib/fpcalc \
    && sed -i \
        -e 's|-Dapplication.deployment=deb|-Dapplication.deployment=docker -Duser.home=/config -Djava.util.prefs.userRoot=/config/preferences|' \
        -e 's|^"$FILEBOT_HOME/jre/bin/java"|exec "$FILEBOT_HOME/jre/bin/java"|' \
        /usr/share/filebot/bin/filebot.sh \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /config /media /tmp/.X11-unix \
    && chown 1000:1000 /config /media \
    && chmod 1777 /tmp/.X11-unix
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY openbox-rc.xml /etc/xdg/openbox/rc.xml
RUN chmod 755 /usr/local/bin/docker-entrypoint.sh

ENV HOME=/config \
    VNC_GEOMETRY=1440x900 \
    VNC_PORT=5902 \
    NOVNC_PORT=6081
EXPOSE 5902/tcp 6081/tcp
USER 1000:1000
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
