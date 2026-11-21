"""Build the app catalog from an uncompressed microsoft/winget-pkgs tar snapshot."""

import argparse
import gzip
import json
import re
import tarfile
from collections import defaultdict
from pathlib import Path
from urllib.parse import urlsplit

import yaml


FEATURED = (
    "7zip.7zip", "VideoLAN.VLC", "Notepad++.Notepad++", "Audacity.Audacity",
    "KDE.Krita", "Inkscape.Inkscape", "FileZilla.Client", "SumatraPDF.SumatraPDF",
)
INSTALLER_TYPES = {"exe", "inno", "nullsoft", "burn", "msi", "wix", "installshield", "advancedinstaller"}
ARCHITECTURES = ("x64", "x86", "neutral")
CATEGORIES = (
    ("development", "developer|programming|editor|compiler|debugger|git|terminal|sdk|ide|database|python"),
    ("creative", "graphic|design|draw|paint|image|photo|vector|modeling|cad|illustration|3d|animation"),
    ("media", "audio|music|video|multimedia|stream|record|codec|player|subtitle|podcast"),
    ("productivity", "office|pdf|productivity|document|notes|calendar|task|markdown|ebook|spreadsheet"),
    ("internet", "browser|internet|network|communication|chat|email|ftp|ssh|vpn|remote|messaging"),
    ("games", "game|gaming|emulator|arcade|steam"),
    ("utilities", "utility|utilities|compression|archive|files|backup|system|file|security|disk|clipboard"),
)
FEATURED_CATEGORIES = {
    "7zip.7zip": "utilities", "VideoLAN.VLC": "media", "Notepad++.Notepad++": "development",
    "Audacity.Audacity": "media", "KDE.Krita": "creative", "Inkscape.Inkscape": "creative",
    "FileZilla.Client": "internet", "SumatraPDF.SumatraPDF": "productivity",
}


def version_key(version):
    stable = not re.search(r"preview|beta|alpha|nightly|canary|dev|snapshot|prerelease|\brc\b", version, re.I)
    tokens = tuple((1, int(item)) if item.isdigit() else (0, item.lower())
                   for item in re.findall(r"[0-9]+|[A-Za-z]+", version))
    return stable, tokens


def clean(value, limit):
    return " ".join(str(value or "").split())[:limit]


def manifest(archive, member):
    return yaml.load(archive.extractfile(member), Loader=yaml.CSafeLoader)


def installers(manifest):
    result = {}
    for installer in manifest.get("Installers") or []:
        kind = str(installer.get("InstallerType", manifest.get("InstallerType", ""))).lower()
        arch = installer.get("Architecture")
        url = installer.get("InstallerUrl", "")
        digest = str(installer.get("InstallerSha256", "")).lower()
        location = urlsplit(url)
        if (kind not in INSTALLER_TYPES or arch not in ARCHITECTURES or
                location.scheme != "https" or not location.hostname or location.username is not None or
                not re.fullmatch(r"[a-f0-9]{64}", digest) or
                installer.get("DownloadCommandProhibited", manifest.get("DownloadCommandProhibited", False))):
            continue
        extension = "msi" if kind in {"msi", "wix"} else "exe"
        if location.path.lower().endswith((".zip", ".msix", ".appx", ".appxbundle", ".msixbundle", ".cab", ".7z")):
            continue
        priority = (installer.get("Scope") == "user", extension == "msi")
        if arch not in result or priority > result[arch][0]:
            result[arch] = priority, [arch, extension, url, digest]
    return [result[arch][1] for arch in ARCHITECTURES if arch in result]


def category(identifier, tags, description):
    if identifier in FEATURED_CATEGORIES:
        return FEATURED_CATEGORIES[identifier]
    for text in (tags, description):
        for key, words in CATEGORIES:
            if re.search(r"(?<![a-z])(?:" + words + r")(?![a-z])", text, re.I):
                return key
    return "other"


def build(archive, websites=None):
    packages = defaultdict(lambda: defaultdict(dict))
    for member in archive:
        parts = member.name.split("/")
        if not member.isfile() or len(parts) < 6 or parts[1] != "manifests":
            continue
        *_, version, filename = parts
        identifier = ".".join(parts[3:-2])
        if filename == identifier + ".installer.yaml":
            packages[identifier][version]["installer"] = member
        elif filename.startswith(identifier + ".locale.") and filename.endswith(".yaml"):
            packages[identifier][version][filename[len(identifier) + 8:-5]] = member

    rows = []
    for identifier, versions in packages.items():
        version, files = max(versions.items(), key=lambda entry: version_key(entry[0]))
        if "installer" not in files:
            continue
        info = manifest(archive, files["installer"])
        downloads = installers(info)
        if not downloads:
            continue
        default = files.get("en-US") or next((v for k, v in files.items() if k != "installer"), None)
        if not default:
            continue
        locale = manifest(archive, default)
        chinese = manifest(archive, files["zh-CN"]) if "zh-CN" in files else {}
        name = clean(locale.get("PackageName"), 120) or identifier
        publisher = clean(locale.get("Publisher"), 120) or identifier.split(".")[0]
        description = clean(locale.get("ShortDescription"), 280)
        local_description = clean(chinese.get("ShortDescription"), 280)
        tags = clean(" ".join(map(str, locale.get("Tags") or [])), 400)
        rows.append([identifier, name, publisher, version, category(identifier, tags, name + " " + description),
                     description, local_description, tags, downloads])
        if websites is not None and locale.get("PackageUrl"):
            websites[identifier] = {
                "home": locale["PackageUrl"], "name": name, "publisher": publisher,
            }
    featured = {identifier: rank for rank, identifier in enumerate(FEATURED)}
    return sorted(rows, key=lambda row: (featured.get(row[0], len(featured)), row[1].casefold(), row[0]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--websites", type=Path, help="Also export the package's declared product homepage")
    args = parser.parse_args()
    websites = {} if args.websites else None
    # An uncompressed local tar keeps the second pass seekable without re-decoding the snapshot.
    with tarfile.open(args.snapshot, "r:") as archive:
        rows = build(archive, websites)
    if len(rows) < 1000:
        raise SystemExit(f"The source snapshot contained only {len(rows)} supported applications")
    missing = set(FEATURED) - {row[0] for row in rows}
    if missing:
        print("Featured apps without supported installers:", ", ".join(sorted(missing)))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    data = json.dumps(rows, ensure_ascii=False, separators=(",", ":")).encode()
    args.output.write_bytes(gzip.compress(data, compresslevel=9, mtime=0))
    if args.websites:
        args.websites.write_text(json.dumps(websites, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{len(rows)} apps, {sum(len(row[-1]) for row in rows)} installer options, {args.output.stat().st_size} bytes")


if __name__ == "__main__":
    main()
