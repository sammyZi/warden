# Privacy report card

Each app's card covers the **last 7 days** of saved history on the phone. Nothing is uploaded.

## Grade

```
score = 100
      − 9 × tracker companies contacted (counted up to 6, so at most −54)
      − 30 × share of the app's DNS lookups and connections that went to tracker domains
      − 15 × share of its activity that happened while the screen was off
```

The score is rounded and clamped to 0–100.

| Grade | Score |
|---|---|
| A | 90 and up |
| B | 80–89 |
| C | 65–79 |
| D | 50–64 |
| F | below 50 |

## Definitions

- **Tracker domain / company:** a domain on Warden's own tracker list
  (`core-rules/src/main/assets/trackers.tsv`), matched with its subdomains.
- **Share to trackers:** events (lookups + connections) to tracker domains ÷ all events with a domain.
- **Screen off:** events saved while the phone's screen was off ÷ all events.

## Caveats

- Country, company and category data are approximate; the tracker list is short and curated.
- A grade reflects network behaviour Warden can see. It says nothing about what data was sent,
  because Warden never decrypts traffic.
