# Threat model

## What Warden protects against
- Apps contacting trackers, ad networks and analytics endpoints (blocked by DNS and by connection).
- Apps you don't trust using the network at all, or on mobile data (per-app rules).
- Not knowing what your apps do: every lookup and connection is visible and attributable.

## What it does not protect against
- **Content inspection.** Warden never decrypts TLS; payloads stay private, and also unexamined.
- **Hardcoded IPs and DoH inside apps.** An app that resolves names itself over HTTPS (or uses
  fixed IPs) bypasses DNS blocking; connections are still logged and per-app rules still apply.
- **Strict Private DNS** sends system DNS past the VPN; Warden detects and warns.
- **Root-level or system processes** that bypass the VPN.
- **A compromised device.**

## Warden's own footprint
- No analytics, accounts or crash reporting.
- Network calls: blocklist downloads the user enables (from the list publisher), nothing else.
- Logs stay in memory; nothing is written to disk except settings and downloaded lists.
- Fail-open: if Warden's process dies, Android removes the VPN and traffic flows normally
  (unless the user enabled always-on lockdown, which Android enforces).
