"""Build offline app logos from a WinGet manifest snapshot and the UniGetUI icon index."""

import argparse
import gzip
import hashlib
import ipaddress
import json
import re
import tarfile
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.error import URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
import yaml

from store_icon_render import raster, shared_placeholders


BUILTIN = {"7zip.7zip", "Audacity.Audacity", "Notepad++.Notepad++", "VideoLAN.VLC"}
PUBLISHER_LOGOS = {
    "Inkscape.Inkscape": "https://media.inkscape.org/static/images/inkscape-logo.svg",
    "KDE.Krita": "https://apps.kde.org/app-icons/org.kde.krita.svg",
    "Mozilla.Firefox": "https://raw.githubusercontent.com/mozilla-firefox/firefox/main/browser/branding/official/default128.png",
    "Mozilla.Firefox.DeveloperEdition": "https://raw.githubusercontent.com/mozilla-firefox/firefox/main/browser/branding/aurora/default128.png",
    "Microsoft.VisualStudioCode": "https://code.visualstudio.com/favicon.ico",
    "Valve.Steam": "https://store.steampowered.com/favicon.ico",
    "Tencent.WeChat": "https://res.wx.qq.com/a/wx_fed/assets/res/NTI4MWU5.ico",
    "Tencent.QQ.NT": "https://im.qq.com/favicon.ico",
    "GitHub.GitHubDesktop": "https://desktop.github.com/favicon.ico",
    "Git.Git": "https://git-scm.com/favicon.ico",
    "OpenJS.NodeJS": "https://nodejs.org/static/images/favicons/favicon.png",
    "Telegram.TelegramDesktop": "https://telegram.org/img/t_logo.png",
    "SumatraPDF.SumatraPDF": "https://www.sumatrapdfreader.org/favicon.ico",
    "Brave.Brave": "https://brave.com/favicon.ico",
}
MAX_SOURCE_BYTES = 2 * 1024 * 1024


def public_https(url):
    location = urlsplit(url)
    host = location.hostname
    if location.scheme != "https" or not host or location.username is not None or "." not in host:
        return False
    if host.endswith((".local", ".localhost", ".internal", ".test")):
        return False
    try:
        return ipaddress.ip_address(host).is_global
    except ValueError:
        return not re.fullmatch(r"[0-9.]+", host)


class SecureRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, target):
        if not public_https(target):
            raise URLError("Icon redirected outside a public HTTPS URL")
        return super().redirect_request(request, fp, code, message, headers, target)


def fetch(url):
    if not public_https(url):
        raise ValueError("Unsupported icon URL")
    request = Request(url, headers={"User-Agent": "WinDroid icon catalog builder/1.0",
                                    "Accept": "image/*,application/octet-stream"})
    with build_opener(SecureRedirect()).open(request, timeout=6) as result:
        data = result.read(MAX_SOURCE_BYTES + 1)
    if len(data) > MAX_SOURCE_BYTES:
        raise ValueError("Icon download is too large")
    return data


def manifest_sources(snapshot, versions):
    result = defaultdict(list)
    with tarfile.open(snapshot, "r:") as archive:
        for file in archive:
            parts = file.name.split("/")
            if not file.isfile() or len(parts) < 6 or parts[1] != "manifests" or ".locale." not in parts[-1]:
                continue
            identifier = ".".join(parts[3:-2])
            if versions.get(identifier) != parts[-2]:
                continue
            data = archive.extractfile(file).read()
            if b"IconUrl:" not in data:
                continue
            manifest = yaml.load(data, Loader=yaml.CSafeLoader)
            for icon in manifest.get("Icons") or []:
                address = icon.get("IconUrl", "")
                checksum = str(icon.get("IconSha256", "")).lower()
                if re.fullmatch(r"[a-f0-9]{64}", checksum) and public_https(address):
                    candidate = (address, checksum, "winget")
                    if candidate not in result[identifier]:
                        result[identifier].append(candidate)
    return result


def index_key(identifier):
    # UniGetUI's WinGet community keys omit the publisher and use hyphens for separators.
    return re.sub(r"[_. /,]", "-", identifier.partition(".")[2].lower())


def compact_name(name):
    return re.sub(r"[^a-z0-9]", "", name.lower())


def download_icon(task):
    identifier, candidates = task
    for url, checksum, source in candidates:
        try:
            data = fetch(url)
            if checksum and hashlib.sha256(data).hexdigest() != checksum:
                continue
            return identifier, raster(data), url, source
        except Exception:
            continue
    return identifier, None, "", ""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path, help="Uncompressed Microsoft winget-pkgs tar")
    parser.add_argument("catalog", type=Path)
    parser.add_argument("index", type=Path, help="UniGetUI screenshot-database-v2.json")
    parser.add_argument("output", type=Path)
    parser.add_argument("provenance", type=Path)
    parser.add_argument("--alternate-names", action="store_true", help="Fetch strict spelling matches missing from the direct index")
    args = parser.parse_args()

    catalog = json.loads(gzip.decompress(args.catalog.read_bytes()))
    versions = {row[0]: row[3] for row in catalog}
    community = json.loads(args.index.read_bytes())["icons_and_screenshots"]
    community_exact = {key.casefold(): data for key, data in community.items()}
    groups = Counter(map(index_key, versions))
    official = {} if args.alternate_names else manifest_sources(args.snapshot, versions)
    names = Counter(compact_name(row[1]) for row in catalog)
    alternate = defaultdict(list)
    if args.alternate_names:
        for key, entry in community.items():
            if entry.get("icon"):
                alternate[compact_name(key)].append(entry)
    tasks = []
    for identifier, name, *_ in catalog:
        if identifier in BUILTIN or not re.fullmatch(r"[A-Za-z0-9+_.-]+", identifier):
            continue
        candidates = list(official.get(identifier, ()))
        if identifier in PUBLISHER_LOGOS and not args.alternate_names:
            candidates.append((PUBLISHER_LOGOS[identifier], "", "publisher"))
        key = index_key(identifier)
        entry = community_exact.get(identifier.casefold()) or (community.get(key) if groups[key] == 1 else None)
        if args.alternate_names:
            match = compact_name(name)
            if ((entry and entry.get("icon")) or len(match) < 5 or names[match] != 1
                    or compact_name(identifier.partition(".")[2]) != match or len(alternate[match]) != 1):
                continue
            entry = alternate[match][0]
        url = entry.get("icon", "") if entry else ""
        if url.startswith("http://"):
            url = "https://" + url[7:]
        if public_https(url):
            candidates.append((url, "", "unigetui"))
        if candidates:
            tasks.append((identifier, candidates))
    print(f"Retrieving logos for {len(tasks)} catalog entries", flush=True)

    args.output.mkdir(parents=True, exist_ok=True)
    provenance = {}
    # The shared upstream hosts see bounded fan-out and no requests from shop visitors.
    with ThreadPoolExecutor(max_workers=24) as pool:
        for count, (identifier, image, url, source) in enumerate(pool.map(download_icon, tasks), 1):
            if image:
                (args.output / (identifier + ".webp")).write_bytes(image)
                provenance[identifier] = {"url": url, "source": source, "sha256": hashlib.sha256(image).hexdigest()}
            if count % 250 == 0:
                print(f"Checked {count}/{len(tasks)} sources, saved {len(provenance)} logos", flush=True)
    for identifier in shared_placeholders(provenance):
        (args.output / (identifier + ".webp")).unlink()
        del provenance[identifier]
    args.provenance.write_text(json.dumps(provenance, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
                               encoding="utf-8")
    print(f"Saved {len(provenance)} logos from {dict(Counter(row['source'] for row in provenance.values()))}", flush=True)


if __name__ == "__main__":
    main()
