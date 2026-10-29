# Testing

## Automated
```bash
./gradlew detekt testDebugUnitTest
```
Covers colour contrast, GeoIP lookups against the bundled data, blocklist parsing and matching,
and rule precedence.

## On a device (VPN behaviour is unreliable on emulators)
1. Install: `./gradlew assembleRelease` then `adb install -r app/build/outputs/apk/release/app-release.apk`.
2. Turn protection on. Browse; the Live log fills and apps appear with icons.
3. Settings → enable a blocklist. Visit a listed tracker (for example `doubleclick.net`): it fails, and
   the log shows it as Blocked.
4. Open an app's detail, switch off Wi-Fi: the app loses network on Wi-Fi only.
5. Tap a domain in the log → Block everywhere: new lookups for it fail.
6. Set Private DNS to a hostname in system settings: Home shows the warning.
7. Stop protection: the VPN key icon disappears and browsing works.

## Frame timing
```bash
adb shell dumpsys gfxinfo dev.warden reset
# use the app for 30 s
adb shell dumpsys gfxinfo dev.warden
```

## Verifying traffic with PCAPdroid or Wireshark
Only one VPN can run at a time, so capture on the router or a computer acting as a hotspot with
Wireshark, and compare: blocked domains should show DNS queries answered by Warden but no
following TCP/QUIC connection.
