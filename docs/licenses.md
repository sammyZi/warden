# Third-party code, data and assets

Every dependency, dataset and asset is checked before use. "Checked" = date the license and activity were last verified.

## In use

| Component | Use | License | Bundled? | Activity / notes | Checked |
|---|---|---|---|---|---|
| [firestack](https://github.com/celzero/firestack) | Network engine (TUN, TCP/UDP flows, DNS) via `com.celzero:firestack:d3d530ea3e` (Maven Central, published 2026-10-05) | MPL-2.0 | Yes (AAR, unmodified) | Last push 2026-10-25, ~177 stars, 28 open issues. Version is a commit hash; same pin Rethink ships. MPL is file-level copyleft and GPL-3.0 compatible. | 2026-10-29 |
| [rethink-app](https://github.com/celzero/rethink-app) | Integration reference for firestack (`GoVpnAdapter`, `BraveVPNService`, `TunFlowManager`, `TunDnsManager`) | Apache-2.0 (LICENSE file confirmed) | No code copied; `WardenVpnService` follows its call patterns and constants (fake LAN ranges) with attribution in the source | Last push 2026-10-26 | 2026-10-29 |
| [Phosphor Icons](https://github.com/phosphor-icons/core) | Icons (vector drawables converted from SVG) | MIT | Yes (only icons used) | | 2026-10-29 |
| AndroidX / Jetpack Compose / Material 3 | UI | Apache-2.0 | Yes | Google | 2026-10-29 |
| [Haze](https://github.com/chrisbanes/haze) (`haze`, `haze-blur` 2.0.1) | Frosted tab bar (backdrop blur) | Apache-2.0 | Yes | Last push 2026-10-29, ~2.6k stars. GPU RenderEffect on API 31+, translucent fallback below. | 2026-10-30 |
| [3dicons](https://3dicons.co) (lock, shield, bell, battery; premium style) | Onboarding art, background removed and gold shifted to lime | CC0-1.0 | Yes (`app/src/main/res/drawable-nodpi/onb_*.webp`) | Open-source 3D icon library | 2026-10-30 |
| kotlinx.coroutines | Flows, concurrency | Apache-2.0 | Yes | JetBrains | 2026-10-29 |
| [Rubik](https://github.com/googlefonts/rubik) | The only UI font (variable TTF in `res/font`) | SIL OFL 1.1 | Yes (+ OFL text in assets) | | 2026-10-29 |
| DB-IP IP to Country Lite | Offline IP-to-country (`assets/geo`) | CC BY 4.0 | Yes, credited in Settings → About | Monthly releases | 2026-10-29 |
| Natural Earth (land, admin-0) | Globe land dots, country centroids | Public domain | Yes | | 2026-10-29 |
| Cloudflare / Google / CloudFront / Fastly IP ranges | Anycast edge detection (`assets/geo/cdn.tsv`) | Published facts by each network | Yes | Rebuild with tools/geo/build_cdn.py | 2026-10-30 |
| HaGeZi, OISD (GPL-3.0), StevenBlack (MIT) blocklists | Blocking | as listed | **No**, downloaded at runtime when switched on | Updated daily upstream | 2026-10-30 |

## Rejected

| Component | Why |
|---|---|
| DuckDuckGo Tracker Radar (data) | CC BY-NC-SA 4.0: non-commercial and share-alike, not GPL-compatible. Do not bundle or redistribute. |
| NetGuard, TrackerControl, PCAPdroid code | Reference only (GPL) unless Warden itself is GPL-3.0 and that is decided explicitly. |

## Still to verify before use

Exodus Privacy tracker data, Disconnect list, StevenBlack / HaGeZi / OISD blocklists, dnsjava, hev-socks5-tunnel (fallback engine only), Vico, PCAPdroid license.
