# Riverbank

A privacy-respecting Android client for the Amazon storefront, built for
GrapheneOS. It wraps the storefront's own mobile website in a locked-down
WebView — Vanadium, on GrapheneOS — and gives it the navigation of a native app
without any of the native app's tracking.

**Complete permission list: `INTERNET`, `USE_BIOMETRIC`.**

> Riverbank is not affiliated with, endorsed by, or sponsored by Amazon.com,
> Inc. It contains no Amazon code, branding or artwork. All trademarks belong to
> their respective owners.

---

## Why

The official app asks for location, camera, microphone, contacts, nearby
devices, notifications and the advertising ID, ships a stack of attribution and
analytics SDKs, and runs background services. Almost none of that is needed to
buy things. The mobile website does the same job.

Riverbank is that website, in an app-shaped container, with the telemetry
stripped on the way through.

## What it does

- **Navigation that feels native** — a back arrow and the page title in the
  header, bottom bar for Home, Cart, Orders, Lists and Account, a search icon
  that expands into a search field, pull-to-refresh, launcher shortcuts,
  storefront links that open in the app, and a share target for product links.
  There is no address bar: the header shows what page you are on, and shows the
  host underneath it only when the page is off the storefront.
- **No “get the app” nagging** — the install banners and interstitials the site
  shows mobile browsers are filtered out of the page before they render, and
  links that hand off to the native app or an app store are defused. Toggle and
  editable rules in `res/raw/banner_filter.js`.
- **Tracker blocking** — a blocklist compiled into the APK drops known telemetry
  and ad endpoints, including the storefront's own beacons. Three modes:
  off, standard, and strict (which allows only the storefront and its image
  CDNs). A counter shows what has been dropped.
- **Cookie isolation** — third-party cookies refused by default; optional
  clear-on-exit; a one-tap "end session" that wipes cookies, storage and cache.
- **Reduced fingerprint** — the WebView user agent (device model, build
  fingerprint, the `; wv` marker) is replaced with Chrome's reduced UA.
  `DNT: 1` and `Sec-GPC: 1` are sent on page loads.
- **Nothing phones home** — no analytics, crash reporter, remote config, update
  check or advertising ID. WebView metrics upload and Safe Browsing are both
  disabled in the manifest.
- **Optional app lock** — biometric or device PIN, with `FLAG_SECURE` set so the
  recents preview and screenshots are blocked while it is on.
- **Downloads and uploads without storage permissions** — invoices go through
  the system download manager, photo uploads through the system file picker.
- **Checkout still works** — sign-in hand-offs and 3-D Secure bank redirects
  stay in the app; only links you tap that leave the storefront go to your
  browser.

## What it deliberately does not do

Push notifications, barcode scanning, Alexa, DRM video, and anything else that
needs the proprietary native app. There is no `addJavascriptInterface` bridge,
so the page cannot reach Android APIs at all. See
[docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) for the full list and the honest
limits — most importantly: **if you sign in, the storefront knows exactly who
you are.** Riverbank removes tracking channels, not your identity.

## Install

Add the F-Droid repository:

```
https://OWNER.github.io/riverbank/fdroid/repo
```

Repo fingerprint (SHA-256): `PUBLISH_YOUR_REPO_FINGERPRINT_HERE`
APK signing certificate (SHA-256): `PUBLISH_YOUR_APK_FINGERPRINT_HERE`

Or grab the APK from [Releases](https://github.com/OWNER/riverbank/releases) and
verify it:

```bash
apksigner verify --print-certs riverbank-*.apk
```

## Build

```bash
./gradlew assembleDebug          # debug build, installs as *.debug
./tools/preflight.sh             # signed release build + release checks
```

`preflight.sh` builds the release APK and refuses to hand it over unless the
signature verifies and the permission set read back out of the built APK is
exactly `INTERNET` and `USE_BIOMETRIC`.

JDK 17–24, `compileSdk 36`, `minSdk 29`. Full instructions in
[docs/BUILDING.md](docs/BUILDING.md).

## Project layout

```
app/src/main/java/org/riverbank/shop/
├── MainActivity.kt          WebView shell, navigation, intents, downloads
├── OnboardingActivity.kt    first-run storefront picker
├── SettingsActivity.kt      preferences screen
├── LockActivity.kt          biometric / device-credential lock
├── AppLock.kt               in-memory lock state
├── data/                    Storefront enum, preferences wrapper
├── privacy/                 domain policy, blocklist, blocked-request counter
├── ui/                      bottom-navigation destinations
├── util/                    download manager glue
└── web/                     WebView configuration, WebViewClient, ChromeClient

app/src/main/res/raw/blocklist.txt    the blocklist; plain text, edit and rebuild
fdroid/                               F-Droid repo config and metadata
fastlane/metadata/android/en-US/      store listing, read by F-Droid
docs/FDROID.md                        how to publish this through F-Droid
```

## Documentation

- [docs/FDROID.md](docs/FDROID.md) — GitHub repository setup and F-Droid
  distribution, end to end.
- [docs/BUILDING.md](docs/BUILDING.md) — building, signing, renaming the package.
- [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) — what this protects against and
  what it does not.
- [PRIVACY.md](PRIVACY.md) — the privacy policy.
- [CONTRIBUTING.md](CONTRIBUTING.md) — the rules, chiefly "no new permissions".

## Licence

MIT. See [LICENSE](LICENSE).
