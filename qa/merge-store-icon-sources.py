"""Add checked images to the catalog and resolve verified variants of the same app."""

import argparse
import gzip
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path
from urllib.parse import urlsplit

from PIL import Image

from store_icon_render import shared_placeholders


DIRECT = {"7zip.7zip", "Audacity.Audacity", "Notepad++.Notepad++", "VideoLAN.VLC"}
FIREFOX = re.compile(r"Mozilla\.Firefox\.(?:ESR(?:\..+)?|DeveloperEdition\..+|[a-z]{2,3}(?:-[a-zA-Z]{2,8})*)")
SHARED_KDE_INSTALLER_ICON = "685381f4aa27c4992dfe073bb4e8ecad73c932d51d27427a9b107b870b71066d"


def product_name(value):
    value = re.sub(r"\s*\((?:x64|x86|32.bit|64.bit|Windows|arm64)\)\s*$", "", value, flags=re.I)
    value = re.sub(r"(?:[-_ v]+[0-9][0-9._-]*)+$", "", value, flags=re.I)
    return "".join(char for char in value.casefold() if char.isalnum())


def upstream(value):
    parsed = urlsplit(value)
    return (parsed.hostname or "").casefold().removeprefix("www."), parsed.path.rstrip("/")


def product_family(identifier):
    parts = identifier.split(".")
    while len(parts) > 2 and re.fullmatch(r"x86|x64|amd64|arm64|32bit|64bit|lts|v?[0-9]+(?:_[0-9]+)*",
                                           parts[-1], flags=re.I):
        parts.pop()
    return ".".join(parts).casefold()


def variant_groups(rows, websites):
    groups = defaultdict(set)
    for identifier, name, publisher, *details, installers in rows:
        family = product_family(identifier)
        label = product_name(name)
        page = upstream(websites.get(identifier, {}).get("home", ""))
        if label:
            if page[0]:
                groups[("family", publisher.casefold(), family, label, page)].add(identifier)
            for _, _, url, digest in installers:
                groups[("same-installer", publisher.casefold(), label, digest)].add(identifier)
                if not page[0]:
                    host, _ = upstream(url)
                    if host:
                        groups[("family-download", publisher.casefold(), family, label, host)].add(identifier)
    return groups


def validate(path, expected):
    image = path.read_bytes()
    if hashlib.sha256(image).hexdigest() != expected:
        raise ValueError(f"Icon checksum mismatch for {path}")
    with Image.open(path) as icon:
        if icon.format != "WEBP" or not 1 <= min(icon.size) <= max(icon.size) <= 96:
            raise ValueError(f"Unexpected icon format or size for {path}")
        icon.verify()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("websites", type=Path)
    parser.add_argument("assets", type=Path)
    parser.add_argument("provenance", type=Path)
    parser.add_argument("--source", nargs=2, type=Path, metavar=("ICON_DIR", "PROVENANCE_JSON"),
                        action="append", required=True, help="Sources in preference order")
    args = parser.parse_args()
    rows = json.loads(gzip.decompress(args.catalog.read_bytes()))
    ids = {row[0] for row in rows}
    baseline = json.loads(args.provenance.read_bytes())
    sources = dict(baseline)
    files = {}
    for folder, index in args.source:
        for identifier, record in json.loads(index.read_bytes()).items():
            if (identifier in sources or identifier not in ids or identifier in DIRECT
                    or record["sha256"] == SHARED_KDE_INSTALLER_ICON
                    or not re.fullmatch(r"[A-Za-z0-9+_.-]+", identifier)):
                continue
            file = folder / (identifier + ".webp")
            validate(file, record["sha256"])
            files[identifier] = file
            sources[identifier] = record

    rejected = shared_placeholders(sources).intersection(files)
    for identifier in rejected:
        del files[identifier]
        del sources[identifier]

    variants = {}
    for group in variant_groups(rows, json.loads(args.websites.read_bytes())).values():
        available = group.intersection(sources)
        if not available or len({sources[key]["sha256"] for key in available}) != 1:
            continue
        original = min(available)
        for candidate in group.difference(sources, DIRECT):
            if not FIREFOX.fullmatch(candidate):
                variants.setdefault(candidate, set()).add(original)

    for identifier, matches in variants.items():
        if len({sources[key]["sha256"] for key in matches}) != 1:
            continue
        original = min(matches)
        record = sources[original]
        files[identifier] = files.get(original, args.assets / (original + ".webp"))
        sources[identifier] = {"source": "same-product-variant", "iconOf": original,
                               "url": record["url"], "sha256": record["sha256"]}

    args.assets.mkdir(parents=True, exist_ok=True)
    for identifier, file in files.items():
        target = args.assets / (identifier + ".webp")
        target.write_bytes(file.read_bytes())
    pending = args.provenance.with_suffix(".part")
    pending.write_text(json.dumps(sources, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    pending.replace(args.provenance)
    coverage = sum(identifier in sources or identifier in DIRECT or bool(FIREFOX.fullmatch(identifier)) for identifier in ids)
    print(f"{coverage}/{len(ids)} catalog entries covered; {len(rejected)} false or shared placeholders left out")
    print(dict(Counter(sources[key]["source"] for key in sources.keys() - baseline.keys())))


if __name__ == "__main__":
    main()
