#!/usr/bin/env python3
"""Build the LSPosed companion with pinned API 102 dependencies and Android SDK tools."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
PINS = {
    "api": "423484a6e1807e7a423c4b88fcd8176d104318259d91791877fed88fe91479d0",
    "service": "a665e4af4638924047f5d5cb610553e15bc2c0ea2ce64a27afb6dee257ff014c",
    "interface": "4e0b76d6050be1d3dc0d35525180ad7d9b60a47fd8d8fe1d18c2bdc840be9ddb",
}

def run(args):
    subprocess.run([str(x) for x in args], check=True)

def dependency(name, output):
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1/io.github.libxposed" / name / "102.0.0"
    choices = list(cache.rglob("*.aar")) if cache.exists() else []
    data = choices[0].read_bytes() if choices else urllib.request.urlopen(
        f"https://repo.maven.apache.org/maven2/io/github/libxposed/{name}/102.0.0/{name}-102.0.0.aar", timeout=30).read()
    if hashlib.sha256(data).hexdigest() != PINS[name]:
        raise RuntimeError("Dependency checksum mismatch: " + name)
    aar = output / (name + ".aar"); aar.write_bytes(data)
    with zipfile.ZipFile(aar) as z: (output / (name + ".jar")).write_bytes(z.read("classes.jar"))
    return output / (name + ".jar")

def build():
    version = json.loads((HERE / "version.json").read_text())
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
    tools = sdk / "build-tools/36.0.0"
    android = sdk / "platforms/android-36/android.jar"
    key = Path(os.environ.get("CNBRIDGE_KEYSTORE", Path.home() / ".codex/keys/cn-bridge-companion.jks"))
    if not key.exists():
        raise RuntimeError("Set CNBRIDGE_KEYSTORE to the existing release signing key; a different key cannot update the installed companion")
    with tempfile.TemporaryDirectory(prefix="cnbridge-companion-") as tmp:
        temp = Path(tmp); classes = temp / "classes"; dex = temp / "dex"
        classes.mkdir(); dex.mkdir()
        generated = temp / "generated"
        generated.mkdir()
        resources = temp / "resources.zip"
        unsigned = temp / "unsigned.apk"
        run([tools / "aapt2", "compile", "--dir", HERE / "res", "-o", resources])
        run([tools / "aapt2", "link", "-I", android, "--manifest", HERE / "AndroidManifest.xml",
             "--version-code", str(version["code"]), "--version-name", version["name"],
             "--java", generated, "-o", unsigned, resources])
        jars = {n: dependency(n, temp) for n in PINS}
        info = temp / "BuildInfo.java"
        info.write_text("package com.dalong.cnbridge; final class BuildInfo { static final int VERSION_CODE = " + str(int(version["code"])) + "; }\n")
        run(["javac", "--release", "8", "-cp", os.pathsep.join(map(str, [android, *jars.values()])),
             "-d", classes, info, *sorted(generated.rglob("*.java")),
             *sorted((HERE / "src").rglob("*.java"))])
        run(["java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "36",
             "--lib", android, "--lib", jars["api"], "--output", dex,
             *sorted(classes.rglob("*.class")), jars["service"], jars["interface"]])
        source = (HERE / "src/com/dalong/cnbridge/Scope.java").read_text()
        source += (HERE / "src/com/dalong/cnbridge/AiSearchCompat.java").read_text()
        packages = sorted(set(re.findall(r'"((?:com\.)[a-zA-Z0-9_.]+)"', source)))
        with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
            for p in sorted(dex.glob("*.dex")): z.write(p, p.name)
            z.writestr("META-INF/xposed/java_init.list", "com.dalong.cnbridge.Entry\n")
            z.writestr("META-INF/xposed/module.prop", "minApiVersion=102\ntargetApiVersion=102\nstaticScope=true\n")
            z.writestr("META-INF/xposed/scope.list", "\n".join(packages) + "\n")
        aligned = temp / "aligned.apk"
        run([tools / "zipalign", "-f", "4", unsigned, aligned])
        output = ROOT / "dist/cn-bridge-companion.apk"; output.parent.mkdir(exist_ok=True)
        run([tools / "apksigner", "sign", "--ks", key, "--ks-key-alias", "cnbridge", "--ks-pass", "pass:android",
             "--key-pass", "pass:android", "--out", output, aligned])
        certificate = subprocess.check_output([str(tools / "apksigner"), "verify", "--print-certs", str(output)], text=True)
        if "certificate SHA-256 digest: 42bdc277acad5eccfc85ecacb60ecbe7765527b33e6bcc444a0cc70c29221079" not in certificate:
            raise RuntimeError("Companion signer differs from the installed/release key")
        print(certificate)
        output.with_suffix(".apk.sha256").write_text(hashlib.sha256(output.read_bytes()).hexdigest() + "  " + output.name + "\n")
        print(output)

if __name__ == "__main__":
    build()
