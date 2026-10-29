"""Builds the offline geo assets for :feature-map. Run with the downloaded sources:

    python -I tools/geo/build_geo.py <dbip-country-lite.csv.gz> <ne_110m_land.geojson> \
        <ne_50m_admin_0_countries.geojson> feature-map/src/main/assets/geo

Sources (see docs/licenses.md):
  DB-IP IP to Country Lite (CC BY 4.0)  https://db-ip.com/db/download/ip-to-country-lite
  Natural Earth land + admin-0 (public domain)  https://github.com/nvkelso/natural-earth-vector

Outputs, all big-endian:
  countries.tsv  iso2<TAB>lat<TAB>lon; line order = country index
  land.bin       float32 lat, lon per land dot (Fibonacci sphere sampled, kept if on land)
  ip4.bin        uint32 count, then per range: uint32 start, uint8 country (255 = unknown)
  ip6.bin        uint32 count, then per range: int64 high 64 bits of start, uint8 country
"""
import gzip
import ipaddress
import json
import math
import os
import struct
import sys

DOTS = 24000  # ~1.3 degree spacing


def rings(geometry):
    polys = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
    for poly in polys:
        yield from poly


def on_land(lon, lat, land_rings):
    inside = False  # even-odd over every ring, so holes cancel out
    for ring in land_rings:
        for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
            if (y1 > lat) != (y2 > lat) and lon < (x2 - x1) * (lat - y1) / (y2 - y1) + x1:
                inside = not inside
    return inside


def main(dbip, land_path, countries_path, out):
    os.makedirs(out, exist_ok=True)

    centroids = {}
    for f in json.load(open(countries_path, encoding="utf-8"))["features"]:
        p = f["properties"]
        if len(p["ISO_A2_EH"]) == 2:
            centroids[p["ISO_A2_EH"]] = (p["LABEL_Y"], p["LABEL_X"])

    land_rings = [r for f in json.load(open(land_path, encoding="utf-8"))["features"] for r in rings(f["geometry"])]
    dots = []
    golden = math.pi * (3 - math.sqrt(5))
    for i in range(DOTS):
        y = 1 - 2 * (i + 0.5) / DOTS
        lat = math.degrees(math.asin(y))
        lon = math.degrees((golden * i) % (2 * math.pi)) - 180
        if on_land(lon, lat, land_rings):
            dots.append((lat, lon))
    with open(os.path.join(out, "land.bin"), "wb") as fh:
        for lat, lon in dots:
            fh.write(struct.pack(">ff", lat, lon))

    codes = sorted(centroids)
    index = {c: i for i, c in enumerate(codes)}
    with open(os.path.join(out, "countries.tsv"), "w", encoding="utf-8", newline="\n") as fh:
        for c in codes:
            fh.write(f"{c}\t{centroids[c][0]:.3f}\t{centroids[c][1]:.3f}\n")

    v4, v6 = [], []
    with gzip.open(dbip, "rt", encoding="utf-8") as fh:
        for line in fh:
            start, _, country = line.strip().split(",")
            ip = ipaddress.ip_address(start)
            idx = index.get(country, 255)
            target, key = (v4, int(ip)) if ip.version == 4 else (v6, int(ip) >> 64)
            if target and target[-1][1] == idx:
                continue  # contiguous ranges, so a start only matters when the country changes
            if target and target[-1][0] == key:
                target[-1] = (key, idx)  # sub-/64 boundary collapses onto the same key; last wins
                continue
            target.append((key, idx))

    with open(os.path.join(out, "ip4.bin"), "wb") as fh:
        fh.write(struct.pack(">I", len(v4)))
        for start, idx in v4:
            fh.write(struct.pack(">IB", start, idx))
    with open(os.path.join(out, "ip6.bin"), "wb") as fh:
        fh.write(struct.pack(">I", len(v6)))
        for start, idx in v6:
            fh.write(struct.pack(">qB", start - (1 << 64) if start >= 1 << 63 else start, idx))

    print(f"{len(codes)} countries, {len(dots)} land dots, {len(v4)} v4 ranges, {len(v6)} v6 ranges")


if __name__ == "__main__":
    main(*sys.argv[1:5])
