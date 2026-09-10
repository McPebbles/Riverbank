# Contributing

## Ground rules

1. **No new permissions.** CI fails the build if the merged manifest contains
   anything beyond `INTERNET` and `USE_BIOMETRIC`. If a change genuinely needs
   one, it needs a design discussion first, not a pull request.
2. **No new dependencies without a reason in the PR description.** Every library
   is code that ships to users and code F-Droid has to build. AndroidX and
   Material only, unless there is no alternative.
3. **No analytics, crash reporting, remote config, or "just a ping".** Ever.
4. **No JavaScript bridge.** `addJavascriptInterface` is not used and must not
   be introduced; it is the single largest attack surface a WebView app can add.
5. **Everything must build from a clean checkout with `./gradlew assembleDebug`.**

## Blocklist changes

`app/src/main/res/raw/blocklist.txt` is a plain host list. Add a host only if
you can say what it does. Removing a host is fine if it breaks a real flow — say
which flow in the PR.

## Releasing

See `docs/FDROID.md`.
