#!/usr/bin/env python3
"""Build the production 50-state FieldOps geometry pack from Census 2025 KML.

This builder deliberately consumes the official Census national States
1:20,000,000 KML artifact rather than scraping coordinates from callsigns,
third-party maps, or test fixtures.

Outputs are deterministic:
- generated Kotlin production pack
- machine-readable metadata JSON
- source pin + upstream SHA-256
- per-feature canonical SHA-256
- overall canonical geometry-pack SHA-256

The builder fails closed if:
- the upstream archive does not contain exactly one KML;
- any WAS state is missing or duplicated;
- unexpected state-code mapping occurs;
- District of Columbia/territories enter the 50-state output;
- a ring is malformed;
- a ring contains an unsplit antimeridian jump (>180 degrees);
- geometry is empty.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
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
    normalized = format(value.normalize(), "f")
    if "." in normalized:
        normalized = normalized.rstrip("0").rstrip(".")
    return normalized


def parse_coordinate_token(token: str) -> tuple[str, str]:
    parts = token.strip().split(",")
    if len(parts) < 2:
        raise ValueError(f"Malformed KML coordinate token: {token!r}")
    lon = canonical_decimal(parts[0])
    lat = canonical_decimal(parts[1])
    lon_f = float(lon)
    lat_f = float(lat)
    if not -180.0 <= lon_f <= 180.0:
        raise ValueError(f"Longitude outside [-180,180]: {lon}")
    if not -90.0 <= lat_f <= 90.0:
        raise ValueError(f"Latitude outside [-90,90]: {lat}")
    return lon, lat


def parse_ring(coordinates_text: str) -> Ring:
    tokens = coordinates_text.split()
    points = [parse_coordinate_token(token) for token in tokens if token.strip()]
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
                f"{a[0]},{a[1]} -> {b[0]},{b[1]} (Δlon={delta})"
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
        tag = local_name(element.tag)
        if not name:
            continue
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
        normalized = stusps.strip().upper()
        if normalized in WAS_STATES:
            return normalized
        return None

    statefp = data.get("STATEFP") or data.get("STATEFP20") or data.get("STATEFP25")
    if statefp:
        return STATE_FIPS_TO_ABBR.get(statefp.zfill(2))

    # Census KML commonly carries a GEOID/STATE field even if STUSPS is absent.
    geoid = data.get("GEOID") or data.get("STATE")
    if geoid and re.fullmatch(r"\d{1,2}", geoid.strip()):
        return STATE_FIPS_TO_ABBR.get(geoid.strip().zfill(2))

    return None


def polygons_from_placemark(placemark: ET.Element) -> tuple[Polygon, ...]:
    polygons: list[Polygon] = []
    for poly in placemark.iter():
        if local_name(poly.tag) != "Polygon":
            continue

        outer_ring: Ring | None = None
        holes: list[Ring] = []
        for boundary in list(poly):
            boundary_name = local_name(boundary.tag)
            if boundary_name not in {"outerBoundaryIs", "innerBoundaryIs"}:
                continue
            coords = child_text(boundary, "coordinates")
            if not coords:
                raise ValueError("Polygon boundary lacks coordinates")
            ring = parse_ring(coords)
            if boundary_name == "outerBoundaryIs":
                if outer_ring is not None:
                    raise ValueError("Polygon has multiple outer rings")
                outer_ring = ring
            else:
                holes.append(ring)

        if outer_ring is None:
            # Some generators add wrapper nodes; fall back to descendant search.
            boundaries = [
                e for e in poly.iter()
                if local_name(e.tag) in {"outerBoundaryIs", "innerBoundaryIs"}
            ]
            for boundary in boundaries:
                coords = child_text(boundary, "coordinates")
                if not coords:
                    continue
                ring = parse_ring(coords)
                if local_name(boundary.tag) == "outerBoundaryIs" and outer_ring is None:
                    outer_ring = ring
                elif local_name(boundary.tag) == "innerBoundaryIs":
                    holes.append(ring)

        if outer_ring is None:
            raise ValueError("Polygon has no outer boundary")
        polygons.append(Polygon(outer_ring, tuple(holes)))

    if not polygons:
        raise ValueError("Placemark contains no polygon geometry")
    return tuple(polygons)


def load_state_geometry(kml_bytes: bytes) -> tuple[dict[str, tuple[Polygon, ...]], dict]:
    root = ET.fromstring(kml_bytes)
    state_polygons: dict[str, list[Polygon]] = {}
    upstream_feature_count = 0
    ignored_placemarks = 0

    for placemark in root.iter():
        if local_name(placemark.tag) != "Placemark":
            continue
        upstream_feature_count += 1
        abbr = placemark_state_abbr(placemark)
        if abbr is None:
            ignored_placemarks += 1
            continue
        polygons = polygons_from_placemark(placemark)
        state_polygons.setdefault(abbr, []).extend(polygons)

    actual = set(state_polygons)
    missing = sorted(WAS_STATES - actual)
    unexpected = sorted(actual - WAS_STATES)
    if missing or unexpected:
        raise ValueError(
            f"WAS state set mismatch; missing={missing} unexpected={unexpected}"
        )

    frozen = {abbr: tuple(polys) for abbr, polys in sorted(state_polygons.items())}
    stats = {
        "upstreamPlacemarkCount": upstream_feature_count,
        "ignoredNonWasPlacemarkCount": ignored_placemarks,
    }
    return frozen, stats


def ring_canonical(ring: Ring) -> str:
    return ";".join(f"{lon},{lat}" for lon, lat in ring.points)


def polygon_canonical(poly: Polygon) -> str:
    parts = ["O:" + ring_canonical(poly.outer)]
    parts.extend("H:" + ring_canonical(hole) for hole in poly.holes)
    return "|".join(parts)


def feature_canonical(abbr: str, polygons: tuple[Polygon, ...]) -> str:
    return "\n".join(
        [f"STATE\t{abbr}"]
        + [f"POLYGON\t{index}\t{polygon_canonical(poly)}" for index, poly in enumerate(polygons)]
    ) + "\n"


def fmt_double(text: str) -> str:
    if "." not in text and "e" not in text.lower():
        return text + ".0"
    return text


def kotlin_ring(ring: Ring, indent: str) -> list[str]:
    lines = [indent + "GeoLinearRing(listOf("]
    for lon, lat in ring.points:
        lines.append(
            indent + "    GeoCoordinate(" + fmt_double(lon) + ", " + fmt_double(lat) + "),"
        )
    lines.append(indent + "))")
    return lines


def kotlin_polygon(poly: Polygon, indent: str) -> list[str]:
    lines = [indent + "GeoPolygon("]
    lines.append(indent + "    outer = ")
    outer = kotlin_ring(poly.outer, indent + "        ")
    # Merge "outer =" with first ring line for readable generated code.
    lines[-1] += outer[0].lstrip()
    lines.extend(outer[1:])
    if poly.holes:
        lines[-1] += ","
        lines.append(indent + "    holes = listOf(")
        for hole in poly.holes:
            h = kotlin_ring(hole, indent + "        ")
            lines.extend(h[:-1])
            lines.append(h[-1] + ",")
        lines.append(indent + "    ),")
    lines.append(indent + ")")
    return lines


def generate_kotlin(
    states: dict[str, tuple[Polygon, ...]],
    source_sha256: str,
    feature_hashes: dict[str, str],
    pack_hash: str,
    source_size: int,
    stats: dict,
) -> str:
    lines = [
        "package dev.n0png.fieldops.core.map",
        "",
        "/**",
        " * GENERATED FILE — DO NOT HAND EDIT.",
        " *",
        " * Built by scripts/build_us_state_geometry_pack.py from the official",
        " * U.S. Census Bureau 2025 national States 1:20,000,000 KML artifact.",
        " * Only the 50 ARRL WAS state identities are emitted.",
        " */",
        "object Census2025UsState20mGeometryPack {",
        f'    const val UPSTREAM_FILENAME = "{SOURCE_FILENAME}"',
        f'    const val UPSTREAM_URL = "{SOURCE_URL}"',
        f'    const val UPSTREAM_SHA256 = "{source_sha256}"',
        f"    const val UPSTREAM_SIZE_BYTES = {source_size}L",
        f'    const val PACK_ID = "{PACK_ID}"',
        f'    const val PACK_VERSION = "{PACK_VERSION}"',
        f'    const val PACK_SHA256 = "{pack_hash}"',
        f'    const val SOURCE_VINTAGE = "{SOURCE_VINTAGE}"',
        f'    const val SOURCE_SCALE = "{SOURCE_SCALE}"',
        f'    const val RETRIEVED_ON = "{RETRIEVED_ON}"',
        f'    const val RIGHTS = "{RIGHTS.replace(chr(34), chr(92)+chr(34))}"',
        f'    const val STATISTICAL_BOUNDARY_DISCLAIMER = "{DISCLAIMER.replace(chr(34), chr(92)+chr(34))}"',
        f"    const val UPSTREAM_PLACEMARK_COUNT = {stats['upstreamPlacemarkCount']}",
        f"    const val IGNORED_NON_WAS_PLACEMARK_COUNT = {stats['ignoredNonWasPlacemarkCount']}",
        "    const val FEATURE_COUNT = 50",
        "",
        "    val featureSha256: Map<String, String> = linkedMapOf(",
    ]
    for abbr in sorted(states):
        lines.append(f'        "{abbr}" to "{feature_hashes[abbr]}",')
    lines.extend([
        "    )",
        "",
        "    val manifest: OfflineGeometryPackManifest =",
        "        CensusStateGeometryPackContract.productionManifest(",
        "            packVersion = PACK_VERSION,",
        "            buildVersion = BUILD_VERSION,",
        "            declaredFeatureCount = FEATURE_COUNT,",
        '            scaleLabel = "1:20,000,000 national States (KML)",',
        "        )",
        "",
        f'    const val BUILD_VERSION = "{BUILD_VERSION}"',
        "",
        "    val records: List<OfflineGeometryPackRecord> = listOf(",
    ])

    for abbr, polygons in sorted(states.items()):
        lines.extend([
            "        OfflineGeometryPackRecord(",
            f'            targetValue = "{abbr}",',
            f'            assetId = "census/2025/state/20m/{abbr}",',
            "            geometry = MultiPolygonGeometry(",
            "                polygons = listOf(",
        ])
        for poly in polygons:
            poly_lines = kotlin_polygon(poly, "                    ")
            lines.extend(poly_lines[:-1])
            lines.append(poly_lines[-1] + ",")
        lines.extend([
            "                ),",
            "            ),",
            "        ),",
        ])

    lines.extend([
        "    )",
        "",
        "    val provider: UsStateGeometryPackProvider by lazy {",
        "        UsStateGeometryPackProvider(manifest, records)",
        "    }",
        "}",
        "",
    ])
    return "\n".join(lines)


def analyze_alaska(polygons: tuple[Polygon, ...]) -> dict:
    lons = [
        float(lon)
        for polygon in polygons
        for ring in (polygon.outer, *polygon.holes)
        for lon, _ in ring.points
    ]
    east = any(lon > 0 for lon in lons)
    west = any(lon < 0 for lon in lons)
    max_segment_jump = 0.0
    for polygon in polygons:
        for ring in (polygon.outer, *polygon.holes):
            for a, b in zip(ring.points, ring.points[1:]):
                max_segment_jump = max(
                    max_segment_jump,
                    abs(float(b[0]) - float(a[0])),
                )
    return {
        "polygonCount": len(polygons),
        "containsPositiveLongitudes": east,
        "containsNegativeLongitudes": west,
        "maxRingSegmentLongitudeJump": max_segment_jump,
        "antimeridianPolicy": (
            "Preserve Census multipart geometry verbatim after coordinate canonicalization; "
            "fail build if any ring segment jumps more than 180 degrees. No clipping or "
            "implicit dateline wrapping."
        ),
    }


def build(source_zip: pathlib.Path, output_kotlin: pathlib.Path, output_metadata: pathlib.Path) -> None:
    source_bytes = source_zip.read_bytes()
    source_sha = sha256_bytes(source_bytes)

    with zipfile.ZipFile(source_zip) as archive:
        kml_names = [n for n in archive.namelist() if n.lower().endswith(".kml")]
        if len(kml_names) != 1:
            raise ValueError(f"Expected exactly one KML in archive, found {kml_names}")
        kml_bytes = archive.read(kml_names[0])

    states, stats = load_state_geometry(kml_bytes)
    feature_canon = {
        abbr: feature_canonical(abbr, polygons)
        for abbr, polygons in states.items()
    }
    feature_hashes = {
        abbr: sha256_bytes(canonical.encode("utf-8"))
        for abbr, canonical in feature_canon.items()
    }
    pack_canonical = "".join(feature_canon[abbr] for abbr in sorted(feature_canon))
    pack_hash = sha256_bytes(pack_canonical.encode("utf-8"))

    alaska = analyze_alaska(states["AK"])
    if alaska["maxRingSegmentLongitudeJump"] > 180.0 + 1e-9:
        raise ValueError("Alaska contains an unsplit antimeridian segment")

    generated = generate_kotlin(
        states=states,
        source_sha256=source_sha,
        feature_hashes=feature_hashes,
        pack_hash=pack_hash,
        source_size=len(source_bytes),
        stats=stats,
    )
    output_kotlin.parent.mkdir(parents=True, exist_ok=True)
    output_kotlin.write_text(generated, encoding="utf-8")

    metadata = {
        "schemaVersion": 1,
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
        "statePolygonCounts": {
            abbr: len(polygons)
            for abbr, polygons in sorted(states.items())
        },
        "alaska": alaska,
        **stats,
    }
    output_metadata.parent.mkdir(parents=True, exist_ok=True)
    output_metadata.write_text(
        json.dumps(metadata, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    print(f"source_sha256={source_sha}")
    print(f"pack_sha256={pack_hash}")
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
        "--output-kotlin",
        type=pathlib.Path,
        default=pathlib.Path(
            "core/src/main/kotlin/dev/n0png/fieldops/core/map/"
            "Census2025UsState20mGeometryPack.kt"
        ),
    )
    parser.add_argument(
        "--output-metadata",
        type=pathlib.Path,
        default=pathlib.Path("research/maps/US_STATE_2025_20M_PACK.json"),
    )
    parser.add_argument("--download-only", action="store_true")
    args = parser.parse_args()

    if args.source_zip:
        source_zip = args.source_zip
        if not source_zip.exists():
            download_source(source_zip)
    else:
        temp_dir = tempfile.TemporaryDirectory()
        source_zip = pathlib.Path(temp_dir.name) / SOURCE_FILENAME
        download_source(source_zip)

    if args.download_only:
        print(f"downloaded={source_zip}")
        print(f"sha256={sha256_bytes(source_zip.read_bytes())}")
        return

    build(source_zip, args.output_kotlin, args.output_metadata)


if __name__ == "__main__":
    main()
