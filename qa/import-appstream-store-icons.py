"""Use Flathub artwork when product name and an upstream project URL both agree."""

import argparse
import gzip
import hashlib
import json
import re
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urlsplit
from xml.etree import ElementTree

import httpx

from store_icon_render import raster


CDN = "https://dl.flathub.org"
MAX_ICON_BYTES = 2 * 1024 * 1024


def display_key(value):
    value = re.sub(r"\s+[vV]?[0-9]+(?:\.[0-9]+)*\s*$", "", value or "")
    return "".join(char for char in value.casefold() if char.isalnum())


def site_key(value):
    parsed = urlsplit(value)
    host = (parsed.hostname or "").casefold().removeprefix("www.")
    path = parsed.path.strip("/").casefold()
    if host in ("github.com", "gitlab.com", "codeberg.org"):
        parts = path.split("/")
        if len(parts) < 2:
            return ""
        path = "/".join(parts[:2]).removesuffix(".git")
    return host + "/" + path if host and parsed.scheme in ("https", "http") else ""


def appstream_index(snapshot):
    index = defaultdict(dict)
    with gzip.open(snapshot, "rb") as source:
        for _, component in ElementTree.iterparse(source, events=("end",)):
            if component.tag != "component":
                continue
            id = component.findtext("id", "")
            if component.get("type") != "desktop-application" or not re.fullmatch(r"[a-zA-Z0-9_.-]+", id):
                component.clear()
                continue
            name = display_key(component.findtext("name", ""))
            address = f"{CDN}/repo/appstream/x86_64/icons/128x128/{id}.png"
            for icon in component.findall("icon"):
                if (icon.get("type") == "remote" and icon.get("width") == "128"
                        and icon.get("scale", "1") == "1" and (icon.text or "").startswith(CDN + "/media/")):
                    address = icon.text
                    break
            for element in component.findall("url"):
                if name and element.text and element.get("type") in ("homepage", "vcs-browser"):
                    key = site_key(element.text)
                    if key:
                        index[(name, key)][id] = address
            component.clear()
    return index


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("appstream", type=Path)
    parser.add_argument("websites", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("provenance", type=Path)
    parser.add_argument("--exclude", action="append", type=Path, default=[])
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    index = appstream_index(args.appstream)
    excluded = set()
    for source in args.exclude:
        if source.is_file():
            excluded.update(json.loads(source.read_bytes()))
    jobs = []
    for identifier, record in json.loads(args.websites.read_bytes()).items():
        if identifier in excluded or not re.fullmatch(r"[A-Za-z0-9+_.-]+", identifier):
            continue
        matches = index.get((display_key(record["name"]), site_key(record["home"])), {})
        if len(matches) == 1:
            flatpak, address = next(iter(matches.items()))
            jobs.append((identifier, flatpak, address, record["home"]))
    print(f"{len(jobs)} exact upstream matches with Flathub", flush=True)
    records = {}
    with httpx.Client(timeout=10, follow_redirects=False) as client:
        def retrieve(job):
            identifier, flatpak, address, home = job
            try:
                with client.stream("GET", address) as response:
                    response.raise_for_status()
                    payload = bytearray()
                    for part in response.iter_bytes():
                        payload.extend(part)
                        if len(payload) > MAX_ICON_BYTES:
                            return
                image = raster(bytes(payload))
            except (httpx.HTTPError, OSError, ValueError):
                return
            (args.output / (identifier + ".webp")).write_bytes(image)
            return identifier, {
                "source": "flathub-appstream", "url": address, "sha256": hashlib.sha256(image).hexdigest(),
                "appStreamId": flatpak, "productPage": home,
            }

        with ThreadPoolExecutor(max_workers=12) as pool:
            for result in pool.map(retrieve, jobs):
                if result:
                    identifier, record = result
                    records[identifier] = record
    args.provenance.write_text(json.dumps(records, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(f"Saved {len(records)} Flathub product logos", flush=True)


if __name__ == "__main__":
    main()
