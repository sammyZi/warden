# Architecture

```
:app                 Activity, navigation shell, onboarding, app labels/icons
:core-design         Tokens (colour, type, motion), components, gallery previews
:core-engine         WardenVpnService (VpnService + firestack Bridge), Engine event stream
:core-rules          Rules (decision), Blocklists (download/parse/match), AppRules, DomainSet
:feature-dashboard   Home
:feature-apps        Apps list, app detail
:feature-map         Offline GeoIP, globe, country detail
:feature-logs        Live log
:feature-settings    Appearance, blocklists, about
```

## Data flow

1. `WardenVpnService` establishes the TUN interface (IPv4, plus IPv6 when the network has it), excludes
   Warden itself, and hands a dup of the fd to firestack (`Settings.dupTunFd`).
2. firestack calls back through the `Bridge`:
   - `onQuery` for every DNS question: Warden answers with `BlockAll` or the system resolver.
   - `preflow`/`flow` for every connection: Warden resolves the owner UID
     (`getConnectionOwnerUid`, or firestack's `/proc/net` below Android 10) and returns
     `Block`, `Exit` (direct) or `Base` (our own DNS).
   - `onResponse`/`postflow` report answers and byte counts.
3. Every decision goes through `Rules.blocks(uid, domains, cellular)`:
   per-app domain rule > per-app network rule > global domain rule > blocklists > allow.
4. Events go into `Engine`, an in-memory ring of 300 published to the UI at most every 500 ms.

## Threading

firestack calls arrive on Go threads; callbacks never block. Rule state lives in `StateFlow`s that are
read lock-free; blocklist rebuilds swap a new `DomainSet` atomically.

## Not yet built

Room history, tracker-company mapping, report cards, DNS settings (DoH upstream, block response),
per-app routing, PCAP export.
