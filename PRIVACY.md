# Privacy policy

Last updated: 19 November 2026 · Applies to Warden 0.2.1 and later

Warden is a firewall that runs on your phone. It has no account, no analytics, no ads and no servers of its
own. The developer never receives anything about you or your traffic.

## What stays on your phone

Warden stores these in its private app storage. Other apps can't read them, and they're excluded from
Android backups:

- **Connection history:** which app looked up or connected to which domain or IP, when, and whether it was
  blocked. Kept for 30 days, then deleted. You can clear it any time in Settings → Clear history.
- **Your rules and settings:** blocked apps, domain rules, lockdown, routes (Direct, Tor, Your VPN) and DNS
  choice.
- **WireGuard servers you add**, including their private keys.
- **A crash report**, only if Warden crashes. It stays on the phone until you choose to copy it.

Warden never decrypts HTTPS. It sees *where* apps connect (domain, IP address, port), never *what* they
send. Countries on the map come from a database bundled in the app; nothing is looked up online.

## When Warden itself goes online

Warden's own traffic goes straight to these services, each with its own privacy policy:

| What | When | Who sees it |
|---|---|---|
| Update check | Once a day. **On by default**; switch off in Settings → Update alerts | GitHub (api.github.com) sees your IP address and that you run Warden |
| Blocklist downloads | Only for lists you switch on, about weekly | The list's host: jsDelivr (HaGeZi), GitHub (StevenBlack), oisd.nl (OISD), or the address of a list you added |
| DNS lookups for your apps | While protection is on | The DNS service you pick: your network's, Cloudflare, Quad9, or your own |
| Tor | Only while an app is set to Tor | The Tor network. Relays see encrypted traffic; exits see where it goes, not who you are |
| Your VPN | Only while an app uses it | The WireGuard server you added, run by you or your provider |

Nothing else is sent anywhere. There is no telemetry and no automatic crash reporting.

## Permissions

- **VPN:** Android's local VPN interface, so Warden can see and filter connections. Traffic isn't sent to
  the developer.
- **See all installed apps:** to name the app behind each connection.
- **Notifications:** the status notification and update alerts.
- **Usage access** (optional, you grant it in system settings): per-app data use on the Data screen.
- **Camera** (optional, asked only when you tap Scan): reads a WireGuard QR code. Frames are decoded on the
  phone and never saved or sent.

## Children

Warden collects no personal data from anyone, children included.

## Changes and contact

Changes to this policy are published here with a new date, and the full history is in this repository.
Questions: open an issue at https://github.com/sammyZi/warden/issues.
