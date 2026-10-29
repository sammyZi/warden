# Contributing to Warden

Thanks for helping. A few things keep the project healthy:

- **Open an issue first** for anything bigger than a small fix, so we can agree on the approach.
- **Small, focused pull requests.** One change per PR, with a clear description of what and why.
- **Run the checks** before pushing: `./gradlew detekt testDebugUnitTest assembleDebug`.
- **Test on a real device.** VPN behaviour on emulators is unreliable.
- **No new dependencies** without discussing them in an issue first, including license and maintenance.
- **Privacy is a feature.** No analytics, tracking or network calls beyond blocklist downloads.
- **Don't copy GPL-incompatible code**, and credit any adapted code in `docs/licenses.md`.

Design rules (colours, type, motion) live in [docs/design-system.md](docs/design-system.md).
