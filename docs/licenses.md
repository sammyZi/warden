# Third-party code, data and assets

Every dependency, dataset and asset is checked before use. "Checked" = date the license and activity were last verified.

## In use

| Component | Use | License | Bundled? | Activity / notes | Checked |
|---|---|---|---|---|---|
| [firestack](https://github.com/celzero/firestack) | Network engine (TUN, TCP/UDP flows, DNS) via `com.celzero:firestack:d3d530ea3e` (Maven Central, published 2026-10-05) | MPL-2.0 | Yes (AAR, unmodified) | Last push 2026-10-25, ~177 stars, 28 open issues. Version is a commit hash; same pin Rethink ships. MPL is file-level copyleft and GPL-3.0 compatible. | 2026-10-29 |
| [rethink-app](https://github.com/celzero/rethink-app) | Integration reference for firestack (`GoVpnAdapter`, `BraveVPNService`, `TunFlowManager`, `TunDnsManager`) | Apache-2.0 (LICENSE file confirmed) | No code copied; `WardenVpnService` follows its call patterns and constants (fake LAN ranges) with attribution in the source | Last push 2026-10-26 | 2026-10-29 |
| [Phosphor Icons](https://github.com/phosphor-icons/core) | Icons (vector drawables converted from SVG) | MIT | Yes (only icons used) | | 2026-10-29 |
| AndroidX / Jetpack Compose / Material 3 | UI | Apache-2.0 | Yes | Google | 2026-10-29 |
| kotlinx.coroutines | Flows, concurrency | Apache-2.0 | Yes | JetBrains | 2026-10-29 |

## Planned (license checked, not yet added)

| Component | License | Notes |
|---|---|---|
| [Rubik](https://github.com/googlefonts/rubik) font | SIL OFL 1.1 | Bundle the variable TTF in `res/font` (wght 300–900, has `tnum`). Ship `OFL.txt` alongside. |

## Rejected

| Component | Why |
|---|---|
| DuckDuckGo Tracker Radar (data) | CC BY-NC-SA 4.0: non-commercial and share-alike, not GPL-compatible. Do not bundle or redistribute. |
| NetGuard, TrackerControl, PCAPdroid code | Reference only (GPL) unless Warden itself is GPL-3.0 and that is decided explicitly. |

## Still to verify before use

Exodus Privacy tracker data, Disconnect list, StevenBlack / HaGeZi / OISD blocklists, dnsjava, hev-socks5-tunnel (fallback engine only), Vico, PCAPdroid license.
