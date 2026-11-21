"""Normalize catalog artwork to a small, bounded raster image."""

import io
from collections import defaultdict
from xml.etree import ElementTree

import resvg_py
from PIL import Image


MAX_SOURCE_PIXELS = 8_388_608
Image.MAX_IMAGE_PIXELS = MAX_SOURCE_PIXELS


def raster(data):
    if data.lstrip().startswith((b"<svg", b"<?xml", b"<!DOCTYPE svg", b"\xef\xbb\xbf")):
        root = ElementTree.fromstring(data)
        if root.tag.rsplit("}", 1)[-1] != "svg":
            raise ValueError("Unsupported image document")
        for element in root.iter():
            for name, value in list(element.attrib.items()):
                if name.rsplit("}", 1)[-1] == "href" and not value.startswith(("#", "data:image/")):
                    del element.attrib[name]
        data = resvg_py.svg_to_bytes(svg_string=ElementTree.tostring(root, encoding="unicode"), width=96, height=96)
    try:
        with Image.open(io.BytesIO(data)) as source:
            if source.width * source.height > MAX_SOURCE_PIXELS or min(source.size) < 12:
                raise ValueError("Unsupported icon resolution")
            image = source.convert("RGBA")
    except Image.DecompressionBombError as error:
        raise ValueError("Unsupported icon resolution") from error
    bounds = image.getbbox()
    if bounds is None:
        raise ValueError("Blank icon")
    image = image.crop(bounds)
    if max(image.size) > 96:
        image.thumbnail((96, 96), Image.Resampling.LANCZOS)
    encoded = io.BytesIO()
    image.save(encoded, format="WEBP", lossless=True, method=4)
    return encoded.getvalue()


def shared_placeholders(provenance):
    by_image = defaultdict(list)
    for identifier, entry in provenance.items():
        by_image[entry["sha256"]].append(identifier)
    return {
        identifier
        for group in by_image.values()
        if len({name.partition(".")[0].lower() for name in group}) >= 4
        for identifier in group
    }
