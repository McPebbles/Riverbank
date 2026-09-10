# Threat model

## What Riverbank protects against

| Threat | Mitigation |
|---|---|
| Native-app SDK telemetry (ads, attribution, session replay) | There are no SDKs. The app is AndroidX + Material and ~1,200 lines of Kotlin. |
| Advertising identifier and install attribution | `com.google.android.gms.permission.AD_ID` is explicitly removed in the manifest; nothing queries it. |
| Cross-site tracking cookies | `CookieManager.setAcceptThirdPartyCookies(false)` by default. |
| First-party telemetry beacons | Compiled-in blocklist drops the storefront's own metrics endpoints as subresources. |
| Device fingerprinting via user agent | The WebView UA (device model, build, `; wv`) is replaced with Chrome's reduced UA. |
| Google Safe Browsing URL reporting | Disabled in the manifest and in `WebSettings`. |
| WebView variations/metrics upload | `android.webkit.WebView.MetricsOptOut` = true. |
| Location, camera, microphone, contacts | Not requested. `WebChromeClient` denies every `PermissionRequest` and geolocation prompt without asking. |
| Malicious page reaching Android APIs | No `addJavascriptInterface`, no `setSupportMultipleWindows`, no file/content URL access. The banner filter injects a script *into* the page, which is one-directional and exposes nothing to it. |
| App-install banners and native-app hand-off | Cosmetic filter (`res/raw/banner_filter.js`) injected at document start, plus `intent:`, `market:`, `amzn:` and `android-app:` URLs swallowed rather than handed to the system. |
| Cleartext downgrade / TLS interception by a user-installed CA | `usesCleartextTraffic=false`, `MIXED_CONTENT_NEVER_ALLOW`, and a network security config that trusts only the system CA store. |
| Data leaving the device via backup | `allowBackup=false` plus explicit cloud-backup and device-transfer exclusions. |
| Shoulder-surfing / recents preview | Optional app lock sets `FLAG_SECURE` while enabled. |
| Page output leaking into logcat | `onConsoleMessage` swallows page console output. |

## What Riverbank does not protect against

- **Your account.** Signing in identifies you completely. Riverbank changes
  nothing about that.
- **Your IP address.** Use a VPN or Tor if that matters; the app has no proxy
  of its own.
- **Server-side fingerprinting.** Canvas, WebGL, font metrics, timing and TLS
  fingerprints are all still available to the page. Vanadium reduces some of
  these; Riverbank does not add its own protections on top.
- **Behavioural profiling of what you actually do on the site.** Blocking
  beacons removes a channel, not the server logs.
- **A compromised WebView.** The app is only as sandboxed as the WebView
  implementation. On GrapheneOS that is Vanadium, which is why this project
  targets it.

## Deliberate omissions

- **No barcode scanner.** It would put `CAMERA` in the manifest, and a manifest
  permission is visible and grantable whether or not the feature is used.
- **No push notifications.** They require the proprietary native app's backend.
- **No blob: download handling.** It would require a JavaScript bridge.
  (Injecting a script into the page, as the banner filter does, is not a
  bridge: it sends code in, and nothing comes back out.)
- **No remote blocklist updates.** A list that updates itself is a channel that
  contacts a server on your behalf.
