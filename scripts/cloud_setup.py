#!/usr/bin/env python3
"""Reproducible Linux x86_64 cloud toolchain; uses the inherited HTTPS proxy and CA trust."""
import hashlib
import os
import platform
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.parse
import urllib.request
import zipfile

assert platform.system() == "Linux" and platform.machine() == "x86_64", "For other platforms use Android Studio; see README.md"
root = Path(__file__).resolve().parents[1]
tools = Path(os.environ.get("MI_DEPARTAMENTO_TOOLS", "/workspace/toolchains"))
tools.mkdir(parents=True, exist_ok=True)
cache = tools / "downloads"
cache.mkdir(exist_ok=True)
def archive(name, url, checksum):
    target = cache / name
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != checksum:
        partial = target.with_suffix(".partial")
        with urllib.request.urlopen(url, timeout=180) as source, partial.open("wb") as dest:
            shutil.copyfileobj(source, dest)
        assert hashlib.sha256(partial.read_bytes()).hexdigest() == checksum, f"Checksum mismatch: {name}"
        partial.replace(target)
    return target

jdk = tools / "jdk-21.0.13+9"
if not (jdk / "bin/javac").exists():
    with tarfile.open(archive("jdk.tar.gz", "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz", "d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c")) as packed:
        packed.extractall(tools, filter="data")
gradle = tools / "gradle-8.11.1"
if not (gradle / "bin/gradle").exists():
    with zipfile.ZipFile(archive("gradle.zip", "https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip", "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6")) as packed:
        packed.extractall(tools)
(gradle / "bin/gradle").chmod(0o755)
sdk = tools / "android-sdk"
cli = sdk / "cmdline-tools/latest"
if not (cli / "bin/sdkmanager").exists():
    parent = sdk / "cmdline-tools"
    parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive("android-tools.zip", "https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip", "0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8")) as packed:
        packed.extractall(parent)
    (parent / "cmdline-tools").rename(cli)
    for f in (cli / "bin").iterdir():
        f.chmod(0o755)

gradle_user = tools / "gradle-user"
gradle_user.mkdir(exist_ok=True)
properties = []
for scheme in ("https", "http"):
    proxy = os.environ.get(f"{scheme.upper()}_PROXY") or os.environ.get(f"{scheme}_proxy")
    if proxy:
        parsed = urllib.parse.urlsplit(proxy)
        properties += [f"systemProp.{scheme}.proxyHost={parsed.hostname}", f"systemProp.{scheme}.proxyPort={parsed.port or 8080}"]
# Trust the platform's configured CA certificates without disabling TLS validation.
trust = Path("/etc/ssl/certs/java/cacerts")
if trust.exists():
    properties.append(f"systemProp.javax.net.ssl.trustStore={trust}")
(gradle_user / "gradle.properties").write_text("\n".join(properties) + "\n")
env = dict(os.environ, JAVA_HOME=str(jdk), ANDROID_HOME=str(sdk), GRADLE_USER_HOME=str(gradle_user))
env["PATH"] = str(jdk / "bin") + os.pathsep + env["PATH"]
# This pinned official CLI delegates sdkmanager to Android CLI and handles licenses during package installation.
if not all((sdk / p).exists() for p in ("platforms/android-35/android.jar", "build-tools/35.0.0/aapt2", "platform-tools/adb")):
    subprocess.run([str(cli / "bin/sdkmanager"), f"--sdk_root={sdk}", "platforms;android-35", "build-tools;35.0.0", "platform-tools"], env=env, check=True)
local = root / "local.properties"
if not local.exists():
    local.write_text(f"sdk.dir={sdk}\n")
subprocess.run([str(root / "gradlew"), "testDebugUnitTest", "lintDebug", "assembleDebug", "--console=plain"], cwd=root, env=env, check=True)
