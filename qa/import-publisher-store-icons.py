"""Fill catalog gaps using the product pages and repositories named by WinGet."""

import argparse
import asyncio
import hashlib
import json
import re
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urljoin, urlsplit

import httpx

from store_icon_render import raster


MAX_PAYLOAD = 2 * 1024 * 1024
PUBLIC_HOST = re.compile(r"(?:[a-z0-9-]+\.)+[a-z][a-z0-9-]*", re.I)
SHARED_SITES = ("github.com", "gitlab.com", "codeberg.org", "sourceforge.net", "docs.microsoft.com",
                "learn.microsoft.com", "docs.aws.amazon.com", "google.com", "facebook.com", "wikipedia.org")
REPO_ASSETS = ("assets/icon.png", "icon.png", "assets/logo.png", "logo.png", "src-tauri/icons/icon.png")
EXTENSIONS = (".png", ".webp", ".ico", ".svg", ".jpg", ".jpeg")
LOGO_LABEL = re.compile(r"\b(?:logo|icon)\b", re.I)
PROMOTIONAL_ART = re.compile(r"screen[-_ ]?shot|preview|banner|badge|demo", re.I)


def host(url):
    try:
        parsed = urlsplit(url)
        name = parsed.hostname
        if (parsed.scheme == "https" and parsed.username is None and parsed.port in (443, None)
                and name and PUBLIC_HOST.fullmatch(name) and not name.endswith((".internal", ".local", ".localhost"))):
            return name.lower().removeprefix("www.")
    except ValueError:
        return ""
    return ""


def public_source(url):
    if not isinstance(url, str):
        return ""
    if url.startswith("http://"):
        url = "https://" + url[7:]
    return url if host(url) else ""


def compact(value):
    return "".join(c for c in str(value).casefold() if c.isalnum())


class Page(HTMLParser):
    def __init__(self, data):
        super().__init__(convert_charrefs=True)
        self.titles = []
        self.icons = []
        self.logos = []
        self.in_title = False
        self.feed(data.decode("utf-8", errors="replace"))

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "title":
            self.in_title = True
        elif tag == "meta" and attrs.get("property") == "og:title":
            self.titles.append(attrs.get("content") or "")
        elif tag == "meta" and attrs.get("property") == "og:logo":
            content = attrs.get("content")
            if content:
                self.logos.append(("", content))
        elif tag == "link":
            rel = (attrs.get("rel") or "").lower().split()
            address = attrs.get("href")
            if address and set(rel).intersection(("icon", "apple-touch-icon", "apple-touch-icon-precomposed")):
                sizes = (attrs.get("sizes") or "").split("x")[0]
                priority = int(sizes) if sizes.isdecimal() and len(sizes) < 5 else 0
                if "apple-touch-icon" in rel or "apple-touch-icon-precomposed" in rel:
                    priority += 180
                self.icons.append((priority, address))
        elif tag == "img":
            alt = attrs.get("alt") or ""
            address = attrs.get("src")
            if address and LOGO_LABEL.search(alt) and not PROMOTIONAL_ART.search(alt):
                self.logos.append((alt, address))

    def handle_data(self, text):
        if self.in_title:
            self.titles.append(text)

    def handle_endtag(self, tag):
        if tag == "title":
            self.in_title = False


async def download(client, url):
    source_host = host(url)
    if not source_host:
        return None, url
    for _ in range(4):
        try:
            async with client.stream("GET", url) as response:
                if response.status_code == 200:
                    data = bytearray()
                    async for chunk in response.aiter_bytes():
                        data.extend(chunk)
                        if len(data) > MAX_PAYLOAD:
                            return None, url
                    return bytes(data), url
                if not response.is_redirect or not response.headers.get("Location"):
                    return None, url
                target = urljoin(url, response.headers["Location"])
                if host(target) != source_host:
                    return None, url
                url = target
        except httpx.HTTPError:
            return None, url
    return None, url


async def fetch_image(client, candidates):
    for address in dict.fromkeys(candidates):
        image, url = await download(client, address)
        if image:
            try:
                return await asyncio.to_thread(raster, image), url
            except (OSError, ValueError, SyntaxError, RuntimeError):
                continue
    return None, ""


def repository(url):
    parsed = urlsplit(url)
    parts = parsed.path.strip("/").split("/")
    if (host(url) == "github.com" and len(parts) >= 2
            and all(re.fullmatch(r"[a-zA-Z0-9_.-]+", part) and part not in (".", "..") for part in parts[:2])):
        return "/".join(parts[:2])
    return None


def repository_asset(url, alt=""):
    path = unquote(urlsplit(url).path).split("/", 4)[-1].lower()
    name = path.rsplit("/", 1)[-1]
    return (name.endswith(EXTENSIONS) and not PROMOTIONAL_ART.search(path) and not PROMOTIONAL_ART.search(alt)
            and ("logo" in name or "icon" in name or name.endswith(".ico") or LOGO_LABEL.search(alt)))


async def repository_icon(client, repo):
    base = "https://raw.githubusercontent.com/" + repo + "/HEAD/"
    readme, _ = await download(client, base + "README.md")
    addresses = []
    if readme:
        text = readme.decode("utf-8", errors="replace")
        page = Page(readme)
        references = page.logos + re.findall(r"!\[([^\]]*)\]\(([^\s)]+)", text)
        for alt, url in references:
            address = urljoin(base, url)
            if address.startswith(base) and repository_asset(address, alt):
                addresses.append(address)
    addresses.extend(base + path for path in REPO_ASSETS)
    return await fetch_image(client, addresses[:8])


async def product_icon(client, url, terms, distinct_products):
    document, resolved = await download(client, url)
    if not document:
        return None, ""
    page = Page(document)
    title = compact(" ".join(page.titles))
    domain = host(resolved)
    if not any(term in title for term in terms):
        return None, ""

    # Official product domains can supply their standard favicon. Shared company sites
    # need an image explicitly marked with the application's name.
    hostname = compact(domain.split(".", 1)[0])
    dedicated = distinct_products == 1 and any(
        term in hostname or (len(hostname) >= 5 and term.startswith(hostname)) for term in terms)
    addresses = [urljoin(resolved, path) for alt, path in page.logos
                 if any(term in compact(alt) for term in terms) or (dedicated and not alt)]
    if dedicated:
        addresses.extend(urljoin(resolved, path) for _, path in sorted(page.icons, reverse=True)[:4])
        if not page.icons:
            addresses.append(urljoin(resolved, "/favicon.ico"))
    return await fetch_image(client, [url for url in addresses if host(url) == domain][:5])


async def collect(jobs, args, provenance):
    todo = iter(jobs)
    tried = 0
    async with httpx.AsyncClient(http2=True, timeout=8, limits=httpx.Limits(max_connections=args.workers),
                                headers={"User-Agent": "WinDroid catalog builder/1.0"}) as client:
        async def worker():
            nonlocal tried
            for identifier, address, repo, terms, product_count in todo:
                try:
                    if repo:
                        image, url = await repository_icon(client, repo)
                    else:
                        image, url = await product_icon(client, address, terms, product_count)
                except (OSError, ValueError, UnicodeError):
                    image = None
                if image is not None:
                    (args.output / (identifier + ".webp")).write_bytes(image)
                    provenance[identifier] = {
                        "source": "publisher-repository" if repo else "product-website", "url": url,
                        "sha256": hashlib.sha256(image).hexdigest(), "productPage": address,
                    }
                tried += 1
                if tried % 250 == 0:
                    print(f"{tried}/{len(jobs)} product sources, {len(provenance)} logos saved", flush=True)
                if tried % 500 == 0:
                    save(args.provenance, provenance)

        await asyncio.gather(*(worker() for _ in range(args.workers)))


def save(path, data):
    pending = path.with_suffix(".part")
    pending.write_text(json.dumps(data, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    pending.replace(path)


def read(path):
    return json.loads(path.read_bytes()) if path.is_file() else {}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("websites", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("provenance", type=Path)
    parser.add_argument("--exclude", action="append", type=Path, default=[])
    parser.add_argument("--workers", type=int, default=24, choices=range(1, 41))
    args = parser.parse_args()
    sites = read(args.websites)
    args.output.mkdir(parents=True, exist_ok=True)
    provenance = read(args.provenance)
    excluded = set(provenance)
    for path in args.exclude:
        excluded.update(read(path))
    addresses = [public_source(site.get("home", "")) for site in sites.values()]
    origins = Counter(map(host, addresses))
    repos = Counter(map(repository, addresses))
    jobs = []
    for identifier, site in sites.items():
        if identifier in excluded or not re.fullmatch(r"[A-Za-z0-9+_.-]+", identifier):
            continue
        address = public_source(site["home"])
        domain = host(address)
        if not domain:
            continue
        repo = repository(address)
        if repo and repos[repo] != 1:
            continue
        if not repo and any(domain == site or domain.endswith("." + site) for site in SHARED_SITES):
            continue
        terms = {compact(site.get("name", "")), compact(identifier.partition(".")[2])}
        terms = {term for term in terms if len(term) >= 5}
        if terms or repo:
            jobs.append((identifier, address, repo, terms, origins[domain]))
    print(f"Checking {len(jobs)} publisher product sources", flush=True)
    asyncio.run(collect(jobs, args, provenance))
    save(args.provenance, provenance)
    print(f"Saved {len(provenance)} logos", flush=True)


if __name__ == "__main__":
    main()
