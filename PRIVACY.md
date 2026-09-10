# Privacy policy

Riverbank is a client, not a service. There is no Riverbank server.

## What Riverbank collects

Nothing. The app has no analytics, no telemetry, no crash reporter, no
advertising identifier, no remote configuration and no update check. It opens no
network connection of its own — every request you see comes from the storefront
page you are viewing.

## What is stored on your device

- Your settings, in the app's private preferences file.
- The storefront's cookies, cache and local storage, inside the app's private
  WebView profile.
- A local count of blocked requests.

All of it lives in the app's private storage. Backup and device-to-device
transfer are disabled in the manifest, so none of it is copied off the device by
Android's backup system. Uninstalling the app deletes all of it.

## What the storefront sees

Everything a mobile browser would show it: your IP address, your account
session, and whatever you do on the site. Riverbank reduces some of this — it
blocks third-party cookies, sends `DNT: 1` and `Sec-GPC: 1`, drops known
telemetry endpoints, and replaces the WebView user agent with a reduced one that
does not name your device model — but it cannot make you anonymous to a service
you are signed in to. **If you log in, the storefront knows who you are.**

## Permissions

- `INTERNET` — to load the storefront.
- `USE_BIOMETRIC` — only if you turn on the app lock. Authentication is
  performed by Android; Riverbank never receives biometric data.

## Contact

Open an issue at https://github.com/OWNER/riverbank/issues.
