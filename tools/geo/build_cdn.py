"""Builds feature-map/src/main/assets/geo/cdn.tsv: IP ranges of big anycast/CDN networks.

    python -I tools/geo/build_cdn.py <dir with cf4.txt cf6.txt goog.json aws.json fastly.json> <out dir>

Sources (each network's own published list):
  https://www.cloudflare.com/ips-v4, https://www.cloudflare.com/ips-v6
  https://www.gstatic.com/ipranges/goog.json
  https://ip-ranges.amazonaws.com/ip-ranges.json   (service CLOUDFRONT only)
  https://api.fastly.com/public-ip-list

Why: these networks answer from a city near the user, but IP-to-country databases file their
addresses under the owner's registration (usually the US). Warden shows them as a nearby edge.
Output: one "<cidr>\t<code>" per line, code in CF / GO / AW / FA.
"""
import json
import os
import sys


def main(src, out):
    rows = []
    for name in ("cf4.txt", "cf6.txt"):
        rows += [(line.strip(), "CF") for line in open(os.path.join(src, name)) if line.strip()]
    goog = json.load(open(os.path.join(src, "goog.json")))
    rows += [(p.get("ipv4Prefix") or p.get("ipv6Prefix"), "GO") for p in goog["prefixes"]]
    aws = json.load(open(os.path.join(src, "aws.json")))
    rows += [(p["ip_prefix"], "AW") for p in aws["prefixes"] if p["service"] == "CLOUDFRONT"]
    rows += [(p["ipv6_prefix"], "AW") for p in aws["ipv6_prefixes"] if p["service"] == "CLOUDFRONT"]
    fastly = json.load(open(os.path.join(src, "fastly.json")))
    rows += [(c, "FA") for c in fastly["addresses"] + fastly.get("ipv6_addresses", [])]
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "cdn.tsv"), "w", encoding="utf-8", newline="\n") as fh:
        for cidr, code in sorted(set(rows)):
            fh.write(f"{cidr}\t{code}\n")
    print(f"{len(set(rows))} ranges")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
