"""Fill missing store logos using Microsoft's signed WinGet catalog and CDN."""

import argparse
import asyncio
import gzip
import hashlib
import json
import re
import shutil
import sqlite3
from collections import Counter
from contextlib import closing
from pathlib import Path
from tempfile import TemporaryDirectory
from urllib.parse import quote
from zipfile import ZipFile

import httpx
import yaml

from store_icon_render import raster


CDN = "https://cdn.winget.microsoft.com"
ICON = re.compile(CDN.replace(".", r"\.") + r"/icons/([a-f0-9]{64})\.(?:ico|png|jpe?g)")
BUILTIN = {"7zip.7zip", "Audacity.Audacity", "Notepad++.Notepad++", "VideoLAN.VLC"}
LOCALE_FAMILY = re.compile(r"Mozilla\.Firefox\.(?:ESR(?:\..+)?|DeveloperEdition\..+|[a-z]{2,3}(?:-[a-zA-Z]{2,8})*)")
MAX_PAYLOAD = 2 * 1024 * 1024


def indexed_paths(msix, wanted, extra_versions, other_versions_only=False):
    with TemporaryDirectory() as directory:
        index = Path(directory) / "index.db"
        with ZipFile(msix) as package:
            entry = package.getinfo("Public/index.db")
            if entry.file_size > 256 * 1024 * 1024:
                raise ValueError("WinGet index is larger than expected")
            with package.open(entry) as source, index.open("wb") as dest:
                shutil.copyfileobj(source, dest)

        with closing(sqlite3.connect(index)) as db:
            paths = {key: (parent, part) for key, parent, part in db.execute(
                "SELECT rowid, parent, pathpart FROM pathparts")}
            result = {identifier: [] for identifier in wanted}
            for identifier, version, pointer, digest in db.execute("""
                SELECT ids.id, versions.version, manifest.pathpart, manifest.hash
                FROM manifest JOIN ids ON manifest.id = ids.rowid
                JOIN versions ON manifest.version = versions.rowid
                ORDER BY manifest.rowid DESC
            """):
                if identifier not in wanted or not digest or len(digest) != 32:
                    continue
                candidates = result[identifier]
                exact = version == wanted[identifier]
                if exact and other_versions_only:
                    continue
                if (not exact and sum(not row[0] for row in candidates) >= extra_versions
                        or any(row[1] == version for row in candidates)):
                    continue
                parts = []
                while pointer:
                    pointer, part = paths[pointer]
                    parts.append(quote(part, safe=""))
                path = CDN + "/cache/" + "/".join(reversed(parts))
                candidates.append((exact, version, path, digest.hex()))
            return [(app, sorted(candidates, key=lambda row: not row[0]))
                    for app, candidates in result.items() if candidates]


async def downloaded(client, address, expected, counts):
    for attempt in range(3):
        delay = 0
        try:
            async with client.stream("GET", address) as response:
                if response.status_code == 200:
                    payload = bytearray()
                    async for part in response.aiter_bytes():
                        payload.extend(part)
                        if len(payload) > MAX_PAYLOAD:
                            return None
                    if hashlib.sha256(payload).hexdigest() == expected:
                        return bytes(payload)
                    counts["digest mismatch"] += 1
                    return None
                counts[f"HTTP {response.status_code}"] += 1
                if response.status_code not in (408, 429, 500, 502, 503, 504):
                    return None
                retry_after = response.headers.get("Retry-After", "")
                if retry_after.isdigit():
                    delay = min(60, int(retry_after))
        except httpx.HTTPError:
            counts["network retry"] += 1
        if attempt < 2:
            await asyncio.sleep(max(delay, 1 << attempt))
    return None


def icon_resources(manifest):
    icons = list(manifest.get("Icons") or [])
    for locale in manifest.get("Localization") or []:
        icons.extend(locale.get("Icons") or [])
    icons.sort(key=lambda entry: entry.get("IconTheme", "default") != "default")
    for entry in icons:
        url = entry.get("IconUrl", "")
        expected = str(entry.get("IconSha256", "")).lower()
        match = ICON.fullmatch(url)
        if match and match.group(1) == expected:
            yield url, expected


async def collect(jobs, args, provenance, websites):
    counts = Counter()
    todo = iter(jobs)
    checked = 0
    icon_flights = {}
    config = httpx.Limits(max_connections=args.workers, max_keepalive_connections=args.workers)
    async with httpx.AsyncClient(http2=True, timeout=14, limits=config, follow_redirects=False,
                                headers={"User-Agent": "WinDroid catalog builder/1.0", "Accept-Encoding": "identity"}) as client:
        async def fetch_icon(url, expected):
            data = await downloaded(client, url, expected, counts)
            if data is not None:
                try:
                    return await asyncio.to_thread(raster, data)
                except (OSError, ValueError):
                    counts["unsupported icon"] += 1
            return None

        async def process(identifier, candidates):
            for exact, version, url, expected in candidates:
                payload = await downloaded(client, url, expected, counts)
                if payload is None:
                    continue
                try:
                    manifest = yaml.load(payload, Loader=yaml.CSafeLoader)
                except yaml.YAMLError:
                    counts["invalid manifest"] += 1
                    continue
                if not isinstance(manifest, dict) or manifest.get("PackageIdentifier") != identifier:
                    counts["different package ID"] += 1
                    continue
                if (exact or identifier not in websites) and manifest.get("PackageUrl"):
                    websites[identifier] = {
                        "home": manifest["PackageUrl"],
                        "publisherHome": manifest.get("PublisherUrl", ""),
                        "name": manifest.get("PackageName", ""),
                        "publisher": manifest.get("Publisher", ""),
                        "manifestUrl": url,
                    }
                for icon, digest in dict(icon_resources(manifest)).items():
                    task = icon_flights.get(icon)
                    if task is None:
                        task = asyncio.create_task(fetch_icon(icon, digest))
                        icon_flights[icon] = task
                    image = await task
                    if image is None:
                        continue
                    (args.output / (identifier + ".webp")).write_bytes(image)
                    provenance[identifier] = {
                        "source": "winget-cdn", "url": icon, "sha256": hashlib.sha256(image).hexdigest(),
                        "manifestUrl": url, "manifestSha256": expected, "packageVersion": version,
                    }
                    return
            counts["without usable icon"] += 1

        async def worker():
            nonlocal checked
            for identifier, candidates in todo:
                await process(identifier, candidates)
                checked += 1
                if checked % 250 == 0:
                    print(f"{checked}/{len(jobs)} packages, {len(provenance)} logos saved", flush=True)
                if checked % 500 == 0:
                    save(args.provenance, provenance)
                    save(args.websites, websites)

        await asyncio.gather(*(worker() for _ in range(args.workers)))
    print(dict(counts), flush=True)


def save(path, data):
    pending = path.with_suffix(".part")
    pending.write_text(json.dumps(data, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    pending.replace(path)


def read(path):
    return json.loads(path.read_bytes()) if path.is_file() else {}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Authenticode-verified WinGet source.msix")
    parser.add_argument("catalog", type=Path)
    parser.add_argument("existing", type=Path, help="Provenance JSON for previously bundled icons")
    parser.add_argument("output", type=Path)
    parser.add_argument("provenance", type=Path)
    parser.add_argument("websites", type=Path)
    parser.add_argument("--extra-versions", type=int, default=0, choices=range(4))
    parser.add_argument("--other-versions-only", action="store_true")
    parser.add_argument("--exclude", action="append", default=[], type=Path)
    parser.add_argument("--workers", type=int, default=24, choices=range(1, 41))
    args = parser.parse_args()
    if args.other_versions_only and not args.extra_versions:
        parser.error("--other-versions-only needs at least one --extra-versions candidate")
    args.output.mkdir(parents=True, exist_ok=True)
    catalog = json.loads(gzip.decompress(args.catalog.read_bytes()))
    provenance = read(args.provenance)
    websites = read(args.websites)
    baseline = read(args.existing)
    for path in args.exclude:
        baseline.update(read(path))
    wanted = {row[0]: row[3] for row in catalog if row[0] not in baseline and row[0] not in provenance
              and row[0] not in BUILTIN and not LOCALE_FAMILY.fullmatch(row[0])
              and re.fullmatch(r"[A-Za-z0-9+_.-]+", row[0])}
    jobs = indexed_paths(args.source, wanted, args.extra_versions, args.other_versions_only)
    print(f"{len(jobs)} indexed packages missing artwork", flush=True)
    asyncio.run(collect(jobs, args, provenance, websites))
    save(args.provenance, provenance)
    save(args.websites, websites)
    print(f"Saved {len(provenance)} logo files and {len(websites)} package websites", flush=True)


if __name__ == "__main__":
    main()
