# Building Riverbank

## Toolchain versions

| Component | Version | Where it is pinned |
|---|---|---|
| Gradle | 8.14.3 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.13.0 | `gradle/libs.versions.toml` (`agp`) |
| Kotlin | 2.2.0 | `gradle/libs.versions.toml` (`kotlin`) |
| Java source/target | 17 | `app/build.gradle.kts` → `compileOptions` |
| Kotlin `jvmTarget` | 17 | `app/build.gradle.kts` → `kotlin { compilerOptions }` |
| `compileSdk` / `targetSdk` | 36 | `app/build.gradle.kts` |
| `minSdk` | 29 | `app/build.gradle.kts` |

**JDK to run Gradle with: 17 through 24.** AGP 8.13 requires at least 17;
Gradle 8.14.3 does not support JDK 25 or newer. The bytecode target is 17
regardless of which of those JDKs you build with.

```bash
java -version          # must report 17..24
./gradlew --version    # confirms the JVM Gradle actually picked
```

If your default JDK is outside that range, point Gradle at a suitable one
without changing your system default:

```bash
./gradlew -Dorg.gradle.java.home=/path/to/jdk-17 assembleDebug
# or persist it in gradle.properties (git-ignored line, machine-specific):
#   org.gradle.java.home=/path/to/jdk-17
```

## Requirements

- A JDK in the 17–24 range (CI uses Temurin 17)
- Android SDK with `compileSdk 36` and build-tools installed
- No Android Studio needed; the Gradle wrapper is committed

## Debug build

```bash
git clone https://github.com/OWNER/riverbank.git
cd riverbank
echo "sdk.dir=$ANDROID_HOME" > local.properties   # or set ANDROID_HOME
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The debug build uses the application ID `org.riverbank.shop.debug`, so it
installs alongside a release build.

## Release build

Create a signing key once and keep it offline:

```bash
keytool -genkeypair -v \
  -keystore riverbank-upload.p12 -storetype PKCS12 \
  -alias riverbank -keyalg RSA -keysize 4096 -validity 10000
```

Then either write `keystore.properties` in the project root (git-ignored):

```properties
storeFile=/absolute/path/riverbank-upload.p12
storePassword=…
keyAlias=riverbank
keyPassword=…
```

or export the environment variables `RIVERBANK_KEYSTORE`,
`RIVERBANK_KEYSTORE_PASSWORD`, `RIVERBANK_KEY_ALIAS`, `RIVERBANK_KEY_PASSWORD`.

```bash
./gradlew assembleRelease
```

## Changing the application ID

The published ID must be one you control, and it can never change after the
first release — F-Droid and Android both treat it as the app's identity.

```bash
./tools/rename-package.sh io.github.YOURNAME.riverbank
```

Run this **before** your first release, then commit the result.

## Getting it onto a real device

### The one-command path

```bash
./tools/preflight.sh
```

It checks the decisions that become permanent at first release, builds a signed
release APK, verifies the signature, and — the check that matters most for this
project — reads the permission set back out of the **built APK** and fails if it
is anything other than `INTERNET` and `USE_BIOMETRIC`. The CI check only ever
sees the debug manifest; this one sees what people actually install.

Then:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build uses the bare application ID and the debug build uses the
`.debug` suffix, so both can sit on the device at once.

### Without a cable

Copy the APK to the phone (USB storage, Syncthing, a local HTTP server) and open
it in a file manager. Android asks whether that file manager may install apps;
grant it, install, and revoke it afterwards if you like. GrapheneOS has no Play
Protect scan and no Google-side approval step, so nothing else is involved.

### What release differs on

The debug build you tested has `isMinifyEnabled = false`. The release build runs
R8 and resource shrinking, which the emulator run never exercised. If something
works in debug and breaks only in release, R8 is the first suspect: set
`isMinifyEnabled = false` to confirm, then fix it by adding the missing `-keep`
rule to `app/proguard-rules.pro` rather than leaving minification off.

Worth clicking through once on the release build specifically: the settings
screen (androidx.preference inflates preferences by class name), the biometric
lock, and a download.

### GrapheneOS specifics

- **Network permission.** GrapheneOS turns `INTERNET` into a revocable runtime
  permission. Riverbank needs it; if it is off, the app shows its offline page
  rather than crashing. Settings → Apps → Riverbank → Permissions.
- **Vanadium is already the WebView provider** on GrapheneOS, so no action is
  needed. Nothing in the app depends on which WebView is installed — the
  hardening applies to whatever is there — but Vanadium is what makes it airtight.
- **Nothing else to grant.** No storage permission means Storage Scopes are
  irrelevant, and the app never requests sensors, location, camera or contacts.
- **A separate user profile** is worth considering if you want the shopping
  session fully walled off from the rest of the phone. GrapheneOS profiles do not
  share app data, so a signed-in session in one profile is invisible to the other.

## Verifying an APK you downloaded

```bash
apksigner verify --print-certs riverbank-1.0.0.apk
sha256sum riverbank-1.0.0.apk
```

Compare the certificate fingerprint against the one published in the README.

## Reproducibility notes

The release build already disables the Play dependency-metadata blob, native
debug symbols and v1 signing. Two things still make byte-for-byte reproduction
awkward and are worth fixing before you promise reproducible builds:

1. Pin the Gradle distribution by adding `distributionSha256Sum` to
   `gradle/wrapper/gradle-wrapper.properties` (the checksum is published next to
   the distribution on services.gradle.org).
2. Pin the build-tools and NDK versions explicitly in `app/build.gradle.kts`.
