# Compiling on MacOS

## Requirements
* [Xcode](https://itunes.apple.com/gb/app/xcode/id497799835)
* [OpenJDK 11](https://jdk.java.net/11/)
* [JavaFX 11](https://gluonhq.com/products/javafx/)
* [Apache Ant](https://ant.apache.org/bindownload.cgi)
* [Apache Ivy](https://ant.apache.org/ivy/download.cgi)
* [NodeJS](https://nodejs.org/en/download/)
* [Node-AppDMG](https://github.com/LinusU/node-appdmg)

## Run following commands on Terminal

`ant resolve`
`ant jar`
`ant app`


Output files in /dist (.dmg file)



# Compiling on Windows

## Requirements
* [OpenJDK 11](https://jdk.java.net/11/)
* [JavaFX 11](https://gluonhq.com/products/javafx/)
* [Apache Ant](https://ant.apache.org/bindownload.cgi)
* [Apache Ivy](https://ant.apache.org/ivy/download.cgi)
* [WiX Toolset](https://github.com/wixtoolset/wix3/releases/latest)

## Run following commands on CMD

`ant resolve`
`ant jar`
`ant msi`
`ant zip`

Output files in /dist 


# Compiling a Debian package on Linux

## Cross-building a portable Windows ZIP on Linux

Run `./build-windows-portable.sh` on Linux x86_64 with Java 11, Ant, curl and
sha256sum installed. It verifies Ivy and the Windows Java/JavaFX downloads,
builds the current source and writes `dist/FB-Mod_by_SMAP_4.8.5-portable.zip`.
PowerShell, Wine and Windows build tools are not needed on the build host.

Extract the entire ZIP into a writable folder on Windows x64 and run
`filebot.launcher.exe` (GUI) or `filebot.exe` (CLI). Java and JavaFX are bundled;
settings and cache live in the portable `data` folder. Archive extraction still
requires a Windows 7-Zip installation (`7z` on PATH, or set
`-Dnet.filebot.Archive.7z=C:\path\to\7z.exe` in the launcher INI).
The existing Windows packaging uses pinned Java/JavaFX 11.0.2; these old runtimes
are not the updated Temurin runtime used by the Debian package.
Execution on Windows must be tested on a Windows machine.

## Building a Linux portable archive

Run `bash build-linux-portable.sh` on Linux x86_64 with the Debian build
requirements below, `dpkg-deb`, CMake 3.15 or newer, Make, G++, pkg-config, and
zlib development headers.
It writes `dist/FB-Mod_by_SMAP_4.8.5-linux-x86_64-portable.tar.xz`.
Extract the archive into a writable folder and run `./filebot.sh` from the
extracted application directory. CLI arguments can be passed to the same
launcher, for example `./filebot.sh -version`.

Java, JavaFX, MediaInfo, the audio fingerprint helper, and standalone 7-Zip are
bundled. Settings and cache stay in the application's `data` folder. No package
installation or system Java is needed. The host still needs GTK 3 / X11 (or
XWayland), zlib, and compatible glibc and C++ runtimes. Native libraries are built
with the host toolchain, so build on the oldest Linux distribution you intend
to support. The already-built 4.8.5 archive requires glibc 2.42 or newer.
This is an x86_64 build, not an ARM build.
MediaInfo is built for local files without optional HTTP/MMS streaming support.

## Requirements
* Linux on x86_64
* OpenJDK 11
* Apache Ant
* curl
* sha256sum
* Python 3.9 or newer

Run `./build-deb.sh` from any directory. The script resolves project dependencies, builds the application, and creates the amd64 Debian package in `/dist`.

The amd64 package bundles a checksum-verified current Temurin 11 JRE and JavaFX,
so users no longer need a system Java installation. Building still requires Java
and Ant. The runtime download comes from Adoptium and requires Internet access.
Bundling Java increases the package size even though the application dependencies
have been reduced.

Run `ant test-features` for the offline unit tests, including provider filename
searches and TheTVDB credential changes (requires `7z` on PATH).
Run `xvfb-run -a ant test-ui` to additionally smoke-test the Swing panels, dark
theme and TheTVDB preferences/menu updates on Linux without using your desktop.
Test settings and data are isolated
under `dist/`.

Archive extraction now uses `7z` instead of the old Java native binding and
Commons VFS. The amd64 package depends on `7zip` or `p7zip-full`; on other
platforms install 7-Zip and put `7z` on PATH or set
`-Dnet.filebot.Archive.7z=/path/to/7z`. Ant and Groovy Ant are build-time tools
only and are no longer bundled in the application; scripts using `AntBuilder`
must be migrated. The universal package still requires system Java.
