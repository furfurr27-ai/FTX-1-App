#!/usr/bin/env python3
"""Build the production 50-state FieldOps geometry pack from Census 2025 KML.

Outputs:
- compact canonical offline geometry asset (.pack)
- machine-readable research metadata JSON
- small generated Kotlin metadata constants (no coordinate literals)

The builder fails closed on missing/duplicate WAS states, malformed geometry,
or an unsplit >180-degree antimeridian ring segment.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import tempfile
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation

SOURCE_URL = "https://www2.census.gov/geo/tiger/GENZ2025/kml/cb_2025_us_state_20m.zip"
SOURCE_FILENAME = "cb_2025_us_state_20m.zip"
SOURCE_VINTAGE = "2025"
SOURCE_SCALE = "1:20,000,000"
PACK_ID = "us-census-cartographic-states"
PACK_VERSION = "2025-20m-kml-v1"
BUILD_VERSION = "fieldops-cp0007c"
RETRIEVED_ON = "2026-10-06"
RIGHTS = (
    "U.S. Government work; U.S. copyright unavailable under 17 U.S.C. §105; "
    "Census Bureau source attribution requested"
)
DISCLAIMER = (
    "Census cartographic boundaries are intended for statistical mapping and "
    "are not legal land descriptions."
)

STATE_FIPS_TO_ABBR = {
    "01": "AL", "02": "AK", "04": "AZ", "05": "AR", "06": "CA",
    "08": "CO", "09": "CT", "10": "DE", "12": "FL", "13": "GA",
    "15": "HI", "16": "ID", "17": "IL", "18": "IN", "19": "IA",
    "20": "KS", "21": "KY", "22": "LA", "23": "ME", "24": "MD",
    "25": "MA", "26": "MI", "27": "MN", "28": "MS", "29": "MO",
    "30": "MT", "31": "NE", "32": "NV", "33": "NH", "34": "NJ",
    "35": "NM", "36": "NY", "37": "NC", "38": "ND", "39": "OH",
    "40": "OK", "41": "OR", "42": "PA", "44": "RI", "45": "SC",
    "46": "SD", "47": "TN", "48": "TX", "49": "UT", "50": "VT",
    "51": "VA", "53": "WA", "54": "WV", "55": "WI", "56": "WY",
}
WAS_STATES = frozenset(STATE_FIPS_TO_ABBR.values())


@dataclass(frozen=True)
class Ring:
    points: tuple[tuple[str, str], ...]


@dataclass(frozen=True)
class Polygon:
    outer: Ring
    holes: tuple[Ring, ...]


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_decimal(raw: str) -> str:
    try:
        value = Decimal(raw)
    except InvalidOperation as exc:
        raise ValueError(f"Invalid coordinate decimal: {raw!r}") from exc
    if not value.is_finite():
        raise ValueError(f"Non-finite coordinate: {raw!r}")
    if value == 0:
        return "0"
    text = format(value.normalize(), "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text


def parse_coordinate_token(token: str) -> tuple[str, str]:
    parts = token.strip().split(",")
    if len(parts) < 2:
        raise ValueError(f"Malformed KML coordinate token: {token!r}")
    lon = canonical_decimal(parts[0])
    lat = canonical_decimal(parts[1])
    if not -180.0 <= float(lon) <= 180.0:
        raise ValueError(f"Longitude outside [-180,180]: {lon}")
    if not -90.0 <= float(lat) <= 90.0:
        raise ValueError(f"Latitude outside [-90,90]: {lat}")
    return lon, lat


def parse_ring(coordinates_text: str) -> Ring:
    points = [
        parse_coordinate_token(token)
        for token in coordinates_text.split()
        if token.strip()
    ]
    if len(points) < 4:
        raise ValueError("Ring contains fewer than four coordinates")
    if points[0] != points[-1]:
        points.append(points[0])

    deduped = [points[0]]
    for point in points[1:]:
        if point != deduped[-1]:
            deduped.append(point)
    if deduped[0] != deduped[-1]:
        deduped.append(deduped[0])
    if len(set(deduped[:-1])) < 3:
        raise ValueError("Ring contains fewer than three distinct vertices")

    for a, b in zip(deduped, deduped[1:]):
        delta = abs(float(b[0]) - float(a[0]))
        if delta > 180.0 + 1e-9:
            raise ValueError(
                "Unsplit antimeridian segment detected: "
                f"{a[0]},{a[1]} -> {b[0]},{b[1]} (delta_lon={delta})"
            )

    return Ring(tuple(deduped))


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child_text(element: ET.Element, wanted_local: str) -> str | None:
    for child in element.iter():
        if local_name(child.tag) == wanted_local and child.text:
            return child.text.strip()
    return None


def extended_data(placemark: ET.Element) -> dict[str, str]:
    values: dict[str, str] = {}
    for element in placemark.iter():
        name = element.attrib.get("name")
        if not name:
            continue
        tag = local_name(element.tag)
        if tag == "SimpleData" and element.text:
            values[name.upper()] = element.text.strip()
        elif tag == "Data":
            value = child_text(element, "value")
            if value is not None:
                values[name.upper()] = value
    return values


def placemark_state_abbr(placemark: ET.Element) -> str | None:
    data = extended_data(placemark)
    stusps = data.get("STUSPS")
    if stusps:
        value = stusps.strip().upper()
        return value if value in WAS_STATES else None

    statefp = data.get("STATEFP") or data.get("STATEFP20") or data.get("STATEFP25")
    if statefp:
        return STATE_FIPS_TO_ABBR.get(statefp.zfill(2))

    geoid = data.get("GEOID") or data.get("STATE")
    if geoid and re.fullmatch(r"\d{1,2}", geoid.strip()):
        return STATE_FIPS_TO_ABBR.get(geoid.strip().zfill(2))
    return None


def polygons_from_placemark(placemark: ET.Element) -> tuple[Polygon, ...]:
    polygons: list[Polygon] = []
    for poly in placemark.iter():
        if local_name(poly.tag) != "Polygon":
            continue
        outer: Ring | None = None
        holes: list[Ring] = []
        for boundary in poly.iter():
            kind = local_name(boundary.tag)
            if kind not in {"outerBoundaryIs", "innerBoundaryIs"}:
                continue
            coords = child_text(boundary, "coordinates")
            if not coords:
                raise ValueError("Polygon boundary lacks coordinates")
            ring = parse_ring(coords)
            if kind == "outerBoundaryIs":
                if outer is not None:
                    raise ValueError("Polygon has multiple outer rings")
                outer = ring
            else:
                holes.append(ring)
        if outer is None:
            raise ValueError("Polygon has no outer boundary")
        polygons.append(Polygon(outer, tuple(holes)))
    if not polygons:
        raise ValueError("Placemark contains no polygon geometry")
    return tuple(polygons)


def load_state_geometry(kml_bytes: bytes) -> tuple[dict[str, tuple[Polygon, ...]], dict]:
    root = ET.fromstring(kml_bytes)
    states: dict[str, list[Polygon]] = {}
    upstream_count = 0
    ignored_count = 0

    for placemark in root.iter():
        if local_name(placemark.tag) != "Placemark":
            continue
        upstream_count += 1
        abbr = placemark_state_abbr(placemark)
        if abbr is None:
            ignored_count += 1
            continue
        states.setdefault(abbr, []).extend(polygons_from_placemark(placemark))

    actual = set(states)
    missing = sorted(WAS_STATES - actual)
    unexpected = sorted(actual - WAS_STATES)
    if missing or unexpected:
        raise ValueError(f"WAS state set mismatch missing={missing} unexpected={unexpected}")

    return (
        {abbr: tuple(polys) for abbr, polys in sorted(states.items())},
        {
            "upstreamPlacemarkCount": upstream_count,
            "ignoredNonWasPlacemarkCount": ignored_count,
        },
    )


def ring_canonical(ring: Ring) -> str:
    return ";".join(f"{lon},{lat}" for lon, lat in ring.points)


def polygon_canonical(poly: Polygon) -> str:
    parts = ["O:" + ring_canonical(poly.outer)]
    parts.extend("H:" + ring_canonical(hole) for hole in poly.holes)
    return "|".join(parts)


def feature_canonical(abbr: str, polygons: tuple[Polygon, ...]) -> str:
    return "\n".join(
        [f"STATE\t{abbr}"]
        + [
            f"POLYGON\t{index}\t{polygon_canonical(poly)}"
            for index, poly in enumerate(polygons)
        ]
    ) + "\n"


def analyze_alaska(polygons: tuple[Polygon, ...]) -> dict:
    all_rings = [
        ring
        for polygon in polygons
        for ring in (polygon.outer, *polygon.holes)
    ]
    lons = [float(lon) for ring in all_rings for lon, _ in ring.points]
    max_jump = max(
        (
            abs(float(b[0]) - float(a[0]))
            for ring in all_rings
            for a, b in zip(ring.points, ring.points[1:])
        ),
        default=0.0,
    )
    return {
        "polygonCount": len(polygons),
        "containsPositiveLongitudes": any(lon > 0 for lon in lons),
        "containsNegativeLongitudes": any(lon < 0 for lon in lons),
        "maxRingSegmentLongitudeJump": max_jump,
        "antimeridianPolicy": (
            "Preserve Census multipart geometry verbatim after coordinate "
            "canonicalization; fail build if any ring segment jumps more than "
            "180 degrees. No clipping or implicit dateline wrapping."
        ),
    }


def kotlin_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def generate_metadata_kotlin(
    source_sha: str,
    source_size: int,
    pack_hash: str,
    feature_hashes: dict[str, str],
    polygon_counts: dict[str, int],
    stats: dict,
    alaska: dict,
) -> str:
    lines = [
        "package dev.n0png.fieldops.core.map",
        "",
        "/** GENERATED by scripts/build_us_state_geometry_pack.py. */",
        "object Census2025UsState20mPackMetadata {",
        f'    const val UPSTREAM_FILENAME = "{SOURCE_FILENAME}"',
        f'    const val UPSTREAM_URL = "{SOURCE_URL}"',
        f'    const val UPSTREAM_SHA256 = "{source_sha}"',
        f"    const val UPSTREAM_SIZE_BYTES = {source_size}L",
        f'    const val PACK_ID = "{PACK_ID}"',
        f'    const val PACK_VERSION = "{PACK_VERSION}"',
        f'    const val PACK_SHA256 = "{pack_hash}"',
        f'    const val SOURCE_VINTAGE = "{SOURCE_VINTAGE}"',
        f'    const val SOURCE_SCALE = "{SOURCE_SCALE}"',
        f'    const val RETRIEVED_ON = "{RETRIEVED_ON}"',
        f'    const val BUILD_VERSION = "{BUILD_VERSION}"',
        f'    const val RIGHTS = "{kotlin_string(RIGHTS)}"',
        f'    const val STATISTICAL_BOUNDARY_DISCLAIMER = "{kotlin_string(DISCLAIMER)}"',
        f"    const val UPSTREAM_PLACEMARK_COUNT = {stats['upstreamPlacemarkCount']}",
        f"    const val IGNORED_NON_WAS_PLACEMARK_COUNT = {stats['ignoredNonWasPlacemarkCount']}",
        "    const val FEATURE_COUNT = 50",
        f"    const val ALASKA_POLYGON_COUNT = {alaska['polygonCount']}",
        f"    const val ALASKA_HAS_POSITIVE_LONGITUDES = {str(alaska['containsPositiveLongitudes']).lower()}",
        f"    const val ALASKA_HAS_NEGATIVE_LONGITUDES = {str(alaska['containsNegativeLongitudes']).lower()}",
        f"    const val ALASKA_MAX_LONGITUDE_JUMP = {alaska['maxRingSegmentLongitudeJump']}",
        "",
        "    val featureSha256: Map<String, String> = linkedMapOf(",
    ]
    for abbr in sorted(feature_hashes):
        lines.append(f'        "{abbr}" to "{feature_hashes[abbr]}",')
    lines.extend([
        "    )",
        "",
        "    val polygonCounts: Map<String, Int> = linkedMapOf(",
    ])
    for abbr in sorted(polygon_counts):
        lines.append(f'        "{abbr}" to {polygon_counts[abbr]},')
    lines.extend([
        "    )",
        "}",
        "",
    ])
    return "\n".join(lines)


def build(
    source_zip: pathlib.Path,
    output_pack: pathlib.Path,
    output_metadata: pathlib.Path,
    output_metadata_kotlin: pathlib.Path,
) -> None:
    source_bytes = source_zip.read_bytes()
    source_sha = sha256_bytes(source_bytes)

    with zipfile.ZipFile(source_zip) as archive:
        kml_names = [n for n in archive.namelist() if n.lower().endswith(".kml")]
        if len(kml_names) != 1:
            raise ValueError(f"Expected exactly one KML in archive, found {kml_names}")
        kml_bytes = archive.read(kml_names[0])

    states, stats = load_state_geometry(kml_bytes)
    feature_text = {
        abbr: feature_canonical(abbr, polygons)
        for abbr, polygons in states.items()
    }
    feature_hashes = {
        abbr: sha256_bytes(text.encode("utf-8"))
        for abbr, text in feature_text.items()
    }
    pack_text = "".join(feature_text[abbr] for abbr in sorted(feature_text))
    pack_bytes = pack_text.encode("utf-8")
    pack_hash = sha256_bytes(pack_bytes)

    alaska = analyze_alaska(states["AK"])
    if alaska["maxRingSegmentLongitudeJump"] > 180.0 + 1e-9:
        raise ValueError("Alaska contains an unsplit antimeridian segment")

    output_pack.parent.mkdir(parents=True, exist_ok=True)
    output_pack.write_bytes(pack_bytes)

    polygon_counts = {
        abbr: len(polygons)
        for abbr, polygons in sorted(states.items())
    }
    metadata = {
        "schemaVersion": 1,
        "packFormat": "FIELDOPS_US_STATE_CANONICAL_V1",
        "packId": PACK_ID,
        "packVersion": PACK_VERSION,
        "buildVersion": BUILD_VERSION,
        "featureCount": len(states),
        "stateIdentities": sorted(states),
        "upstream": {
            "format": "KML_ZIP",
            "filename": SOURCE_FILENAME,
            "url": SOURCE_URL,
            "sha256": source_sha,
            "sizeBytes": len(source_bytes),
            "vintage": SOURCE_VINTAGE,
            "scale": SOURCE_SCALE,
            "retrievedOn": RETRIEVED_ON,
            "zipKmlEntry": kml_names[0],
        },
        "rights": RIGHTS,
        "statisticalBoundaryDisclaimer": DISCLAIMER,
        "excludedFromWas": ["DC", "PR", "AS", "GU", "MP", "VI"],
        "canonicalPackSha256": pack_hash,
        "featureSha256": dict(sorted(feature_hashes.items())),
        "statePolygonCounts": polygon_counts,
        "alaska": alaska,
        **stats,
    }
    output_metadata.parent.mkdir(parents=True, exist_ok=True)
    output_metadata.write_text(
        json.dumps(metadata, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    output_metadata_kotlin.parent.mkdir(parents=True, exist_ok=True)
    output_metadata_kotlin.write_text(
        generate_metadata_kotlin(
            source_sha=source_sha,
            source_size=len(source_bytes),
            pack_hash=pack_hash,
            feature_hashes=feature_hashes,
            polygon_counts=polygon_counts,
            stats=stats,
            alaska=alaska,
        ),
        encoding="utf-8",
    )

    print(f"source_sha256={source_sha}")
    print(f"pack_sha256={pack_hash}")
    print(f"pack_bytes={len(pack_bytes)}")
    print(f"features={len(states)}")
    print(f"upstream_placemarks={stats['upstreamPlacemarkCount']}")
    print(f"ignored_non_was={stats['ignoredNonWasPlacemarkCount']}")
    print("alaska=" + json.dumps(alaska, sort_keys=True))


def download_source(destination: pathlib.Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(
        SOURCE_URL,
        headers={"User-Agent": "FTX-1-FieldOps-CP0007C/1.0"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read()
    if not data.startswith(b"PK"):
        raise ValueError("Downloaded Census artifact is not a ZIP archive")
    destination.write_bytes(data)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-zip", type=pathlib.Path)
    parser.add_argument(
        "--output-pack",
        type=pathlib.Path,
        default=pathlib.Path(
            "core/src/main/resources/dev/n0png/fieldops/maps/"
            "us_states_2025_20m.pack"
        ),
    )
    parser.add_argument(
        "--output-metadata",
        type=pathlib.Path,
        default=pathlib.Path("research/maps/US_STATE_2025_20M_PACK.json"),
    )
    parser.add_argument(
        "--output-metadata-kotlin",
        type=pathlib.Path,
        default=pathlib.Path(
            "core/src/main/kotlin/dev/n0png/fieldops/core/map/"
            "Census2025UsState20mPackMetadata.kt"
        ),
    )
    args = parser.parse_args()

    if args.source_zip:
        source_zip = args.source_zip
        if not source_zip.exists():
            download_source(source_zip)
        temp_dir = None
    else:
        temp_dir = tempfile.TemporaryDirectory()
        source_zip = pathlib.Path(temp_dir.name) / SOURCE_FILENAME
        download_source(source_zip)

    build(
        source_zip,
        args.output_pack,
        args.output_metadata,
        args.output_metadata_kotlin,
    )


if __name__ == "__main__":
    main()
