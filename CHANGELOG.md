# Changelog

All notable changes are recorded here. Version numbers follow semantic
versioning; `versionCode` increases by one on every release.

## [1.0.0] — unreleased

First release.

### Added
- WebView shell with bottom navigation (Home, Cart, Orders, Lists, Account),
  pull-to-refresh and launcher shortcuts.
- Header bar showing a back arrow and the page title, with the host shown
  underneath whenever the page is off the storefront. Search collapses behind a
  toolbar icon rather than occupying a permanent address-bar-shaped field.
- First-run storefront picker covering 17 regional storefronts.
- App-install banner filter: a cosmetic filter injected at document start that
  hides "get the app" banners and interstitials, plus refusal of `intent:`,
  `market:`, `amzn:` and `android-app:` hand-off URLs. On by default.
- Compiled-in tracker blocklist with three modes: off, standard, strict.
- Blocked-request counter, stored locally.
- Third-party cookie blocking, DNT and Sec-GPC headers, reduced user agent.
- WebView metrics opt-out and Safe Browsing disabled via manifest metadata.
- Optional biometric/device-credential app lock with `FLAG_SECURE`.
- Optional clear-cookies and clear-cache on exit, plus a manual "end session".
- Downloads through `DownloadManager` and uploads through the system file
  picker, neither requiring a storage permission.
- Deep links for storefront URLs and a share target for product links.
