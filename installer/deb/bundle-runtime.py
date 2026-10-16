#!/usr/bin/env python3
"""Download and verify the current Temurin 11 Linux x64 JRE for the deb."""
import hashlib
import json
import pathlib
import shutil
import sys
import tarfile
import tempfile
import urllib.request


def open_url(url, timeout):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "FB-Mod-build"}), timeout=timeout)


def main():
    destination = pathlib.Path(sys.argv[1]).resolve()
    cache = pathlib.Path(sys.argv[2]).resolve()
    url = ("https://api.adoptium.net/v3/assets/latest/11/hotspot"
           "?architecture=x64&image_type=jre&os=linux&vendor=eclipse")
    with open_url(url, timeout=60) as response:
        package = json.load(response)[0]["binary"]["package"]
    archive = cache / package["name"]
    if not archive.exists():
        temporary = archive.with_suffix(archive.suffix + ".part")
        try:
            with open_url(package["link"], timeout=120) as response:
                with temporary.open("wb") as output:
                    while chunk := response.read(1024 * 1024):
                        output.write(chunk)
            temporary.replace(archive)
        finally:
            temporary.unlink(missing_ok=True)
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    if digest != package["checksum"]:
        raise RuntimeError("Temurin JRE SHA256 verification failed: " + str(archive))
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive) as bundle:
        for member in bundle.getmembers():
            relative = pathlib.PurePosixPath(*pathlib.PurePosixPath(member.name).parts[1:])
            if not relative.parts:
                continue
            if relative.is_absolute() or ".." in relative.parts:
                raise RuntimeError("Unsafe runtime archive path: " + member.name)
            if relative.parts[0] not in {"bin", "lib", "conf", "legal", "release", "NOTICE"}:
                continue
            output_path = destination.joinpath(*relative.parts)
            if not output_path.resolve().is_relative_to(destination):
                raise RuntimeError("Runtime output path escapes destination: " + str(output_path))
            if member.isdir():
                output_path.mkdir(parents=True, exist_ok=True)
            elif member.isfile() or member.issym() or member.islnk():
                # Resolve legal-notice links inside the verified TAR rather than
                # installing symlinks or following filesystem links.
                output_path.parent.mkdir(parents=True, exist_ok=True)
                with tempfile.NamedTemporaryFile(dir=output_path.parent, delete=False) as output:
                    temporary = pathlib.Path(output.name)
                    try:
                        with bundle.extractfile(member) as source:
                            shutil.copyfileobj(source, output)
                        output.flush()
                        temporary.chmod(member.mode & 0o777)
                        temporary.replace(output_path)
                    finally:
                        temporary.unlink(missing_ok=True)
            else:
                raise RuntimeError("Unexpected runtime archive entry: " + member.name)
    print("Bundled verified runtime:", package["name"])


if __name__ == "__main__":
    main()
