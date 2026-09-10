# Distributing Riverbank through F-Droid

This is the end-to-end procedure: create the GitHub repository, generate the two
signing keys, wire up CI, and publish a signed F-Droid repository on GitHub
Pages that users add with a URL and a fingerprint.

Two appendices at the end cover the alternative routes — IzzyOnDroid and the
official F-Droid repository — if you later want them as well.

---

## 0. The two keys, and why they are different

You will create **two** independent signing keys. Confusing them is the single
most common way people brick their own distribution.

| Key | Signs | Consequence of losing it |
|---|---|---|
| **App signing key** | the APK | Nobody can ever update the installed app again. Users must uninstall and reinstall, losing their session. There is no recovery. |
| **Repo index key** | the F-Droid repository index | Every user has to remove and re-add your repository. Annoying, not fatal. |

Both are RSA-4096 in PKCS#12 keystores. Keep offline copies. The only place they
should ever exist online is as base64 GitHub Actions secrets, and even that is a
trade-off you are choosing knowingly.

> **Decide the application ID before the first release.** It is permanent. Run
> `./tools/rename-package.sh io.github.yourname.riverbank` now if you want
> something other than `org.riverbank.shop`.

---

## 1. Create the GitHub repository

You need **one** repository. The app source and the published F-Droid repo can
live in the same one: source on `main`, the repo index published to GitHub Pages
from CI. (Splitting them into `riverbank` and `riverbank-fdroid` also works and
keeps `git clone` small — see §8.)

```bash
cd riverbank

git init -b main
git add .
git commit -m "Riverbank 1.0.0: privacy-respecting storefront client"

# Create the repo and push. With the gh CLI:
gh repo create riverbank --public --source=. --remote=origin --push

# Or by hand, after creating it in the web UI:
#   git remote add origin git@github.com:OWNER/riverbank.git
#   git push -u origin main
```

**It must be public.** F-Droid distributes free software from public source; a
private repo also cannot serve GitHub Pages on a free plan.

### Replace the placeholders

Several files ship with `OWNER` in them. Fix them in one pass:

```bash
grep -rl 'OWNER' --exclude-dir=.git . | xargs sed -i 's|OWNER|your-github-username|g'
git commit -am "Point metadata at the real repository"
```

Files affected: `README.md`, `PRIVACY.md`, `fdroid/config.yml.template`,
`fdroid/metadata/*.yml`, `docs/pages-index.html`, and the source-code link in
`app/src/main/res/values/strings.xml`.

---

## 2. Generate the app signing key

```bash
keytool -genkeypair -v \
  -keystore riverbank-upload.p12 \
  -storetype PKCS12 \
  -alias riverbank \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -dname "CN=Riverbank, OU=, O=, L=, S=, C=CA"
```

Record the certificate fingerprint and publish it in your README so users can
verify any APK they download:

```bash
keytool -list -v -keystore riverbank-upload.p12 -alias riverbank | grep SHA256
```

Back the `.p12` up somewhere offline. It is not in git and must never be.

---

## 3. Generate the F-Droid repo index key

Install the tooling first.

```bash
# Debian/Ubuntu
sudo apt update && sudo apt install -y fdroidserver

# or, for a newer version
pipx install fdroidserver
```

Initialise the repository skeleton. Run this from `fdroid/` inside the project
so the generated layout matches what CI expects:

```bash
cd fdroid
fdroid init --keystore keystore.p12 --repo-keyalias riverbank-repo
```

`fdroid init` creates `config.yml`, `keystore.p12` and an empty `repo/`. It
prints the keystore passwords it generated — copy them somewhere safe
immediately, they are not recoverable.

Then get the fingerprint users will verify:

```bash
fdroid update --create-metadata   # needs at least one APK in repo/ to succeed
grep -A2 'repo_url' config.yml
keytool -list -v -keystore keystore.p12 -alias riverbank-repo | grep SHA-256
```

The fingerprint is the SHA-256 of the repo certificate with the colons stripped.
Put it in `docs/pages-index.html` and your README.

### Fill in `config.yml`

Start from the committed template rather than the generated file, so the
description and URLs are right:

```bash
cp config.yml.template config.yml
$EDITOR config.yml     # set repo_url, keystorepass, keypass, keydname
chmod 600 config.yml
```

`config.yml` and `keystore.p12` are both git-ignored. Confirm before committing:

```bash
git status --short fdroid/
git check-ignore -v fdroid/config.yml fdroid/keystore.p12
```

---

## 4. Build the repository locally once, by hand

Do this once so you understand what CI is automating, and so you can publish
manually if Actions is ever down.

```bash
cd ..
./gradlew assembleRelease            # signed with the app key from §2
cp app/build/outputs/apk/release/*.apk fdroid/repo/

cd fdroid
fdroid update --pretty --create-metadata
```

`fdroid update` reads every APK in `repo/`, merges it with
`metadata/<applicationId>.yml`, pulls the description and screenshots out of
`fastlane/metadata/android/en-US/`, generates `index-v1.jar` and `index-v2.json`,
and signs them with the repo key.

Resulting layout:

```
fdroid/
├── config.yml            (secret, git-ignored)
├── keystore.p12          (secret, git-ignored)
├── metadata/
│   └── org.riverbank.shop.yml
└── repo/                 (generated; this whole directory is what you publish)
    ├── org.riverbank.shop_1.apk
    ├── index-v1.jar
    ├── index-v2.json
    ├── entry.jar
    ├── entry.json
    └── icons-*/
```

Everything under `repo/` must be served over **HTTPS**. GitHub Pages does that
for you.

---

## 5. Turn on GitHub Pages and Actions

In the repository's **Settings**:

1. **Pages → Build and deployment → Source: GitHub Actions.**
   Do *not* pick "Deploy from a branch"; the release workflow uploads a Pages
   artifact directly.
2. **Actions → General → Workflow permissions: Read and write permissions.**
   The release job needs `contents: write` to create the GitHub Release.
3. **Environments → github-pages → Deployment branches:** restrict to tags
   matching `v*` if you want releases to be the only thing that can publish.

Your repository will be served at:

```
https://OWNER.github.io/riverbank/
```

and the F-Droid repo URL users add is:

```
https://OWNER.github.io/riverbank/fdroid/repo
```

---

## 6. Add the CI secrets

**Settings → Secrets and variables → Actions → New repository secret.**

Encode each keystore first:

```bash
base64 -w0 riverbank-upload.p12 > upload.b64
base64 -w0 fdroid/keystore.p12  > repo.b64
```

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_B64` | contents of `upload.b64` |
| `SIGNING_KEYSTORE_PASS` | app keystore password |
| `SIGNING_KEY_ALIAS` | `riverbank` |
| `SIGNING_KEY_PASS` | app key password |
| `FDROID_KEYSTORE_B64` | contents of `repo.b64` |
| `FDROID_KEYSTORE_PASS` | repo keystore password |
| `FDROID_KEY_PASS` | repo key password |
| `FDROID_KEY_ALIAS` | `riverbank-repo` |

Then delete the `.b64` files: `shred -u upload.b64 repo.b64`.

> If you would rather not put the app signing key in GitHub at all, build the
> release APK locally and upload it to the GitHub Release by hand; the `fdroid`
> job can be changed to download the APK from the release instead of from a
> build artifact. That keeps the key offline at the cost of a manual step.

---

## 7. Release

```bash
# 1. Bump the version
$EDITOR app/build.gradle.kts        # versionCode 2, versionName "1.0.1"
$EDITOR CHANGELOG.md
cp fastlane/metadata/android/en-US/changelogs/1.txt \
   fastlane/metadata/android/en-US/changelogs/2.txt
$EDITOR fastlane/metadata/android/en-US/changelogs/2.txt

git commit -am "Release 1.0.1"

# 2. Tag it. The tag is what triggers the release workflow.
git tag -s v1.0.1 -m "Riverbank 1.0.1"     # -a instead of -s if you have no GPG key
git push origin main --tags
```

`.github/workflows/release.yml` then:

1. builds and signs `app-release.apk` with the app key,
2. attaches it to a GitHub Release,
3. installs `fdroidserver`, restores the repo key, runs `fdroid update`,
4. publishes `fdroid/repo/` plus the landing page to GitHub Pages.

The `versionCode` **must** increase on every release, or F-Droid clients will
not offer the update.

> `fdroid update` keeps the newest `archive_older: 6` versions in `repo/` and
> moves older ones to `archive/`. Because CI starts from a clean checkout, only
> the APK it just built is present — so if you want a real archive, commit the
> published `repo/` back to a `gh-pages` branch, or use the two-repository
> layout in §8 where the repo directory is version-controlled.

---

## 8. Optional: split into two repositories

Committing built APKs into the source repository bloats every clone. A common
split is:

- `OWNER/riverbank` — source, CI, releases.
- `OWNER/riverbank-fdroid` — only `fdroid/` (config, metadata, `repo/` with the
  signed APKs), served by GitHub Pages, with the APK history preserved.

The release workflow then checks out the second repository with a deploy key or
a fine-grained PAT stored as `FDROID_REPO_TOKEN`, copies the new APK in, runs
`fdroid update`, and commits. Set `repo_url` in `config.yml.template` to
`https://OWNER.github.io/riverbank-fdroid/fdroid/repo` and adjust the Pages
setup to that repository.

---

## 9. Tell users how to add it

Publish, in the README and on the Pages site, all three of these:

```
Repository URL: https://OWNER.github.io/riverbank/fdroid/repo
Repo fingerprint (SHA-256): <from §3>
APK signing certificate (SHA-256): <from §2>
```

A QR code is worth adding — the F-Droid client scans it:

```bash
# fdroidserver ships this
cd fdroid && fdroid server update --help   # see also: fdroid nightly
# or generate one directly
python3 -c "
import qrcode
qrcode.make('https://OWNER.github.io/riverbank/fdroid/repo?fingerprint=YOURFINGERPRINT').save('docs/repo-qr.png')"
```

The `?fingerprint=` parameter makes the client verify the key automatically
instead of asking the user to compare hex by eye. Use it.

### What users do

1. Install F-Droid, Droid-ify or Neo Store.
2. Settings → Repositories → **+** → paste the URL (or scan the QR).
3. Confirm the fingerprint matches.
4. Refresh, search "Riverbank", install.

---

## 10. Troubleshooting

| Symptom | Cause |
|---|---|
| `fdroid update` says "No signing keys found" | `config.yml` is missing `keystore`, `repo_keyalias`, `keystorepass` or `keypass`, or `keystore.p12` is not beside it. |
| Client says "repository index signature is invalid" | The index was signed with a different key than the one the client pinned. If you regenerated the repo key, users must remove and re-add the repository. |
| Client sees the repo but shows no apps | The APK is unsigned, or `fdroid update` ran before the APK was copied into `repo/`. Check `fdroid update --verbose` output. |
| App shows up but will not install | `versionCode` did not increase, or the APK is signed with a different app key than the installed version. |
| Pages 404 on `/fdroid/repo/index-v2.json` | Pages source is still "Deploy from a branch", or the `site/` layout in the workflow was changed. |
| `fdroid update` fails on `androguard` | Old `fdroidserver`. Use `pipx install fdroidserver` instead of the distro package. |

---

## Appendix A — IzzyOnDroid

IzzyOnDroid is a well-known F-Droid-compatible repository that accepts
**developer-signed** APKs pulled straight from GitHub Releases, usually within
days rather than months. It is the least-effort way to reach an audience beyond
people who add your own repo.

1. Make sure every release attaches an APK to a GitHub Release (this project's
   workflow already does).
2. Open a request at <https://gitlab.com/IzzyOnDroid/repo/-/issues> using the
   "Add app" template, giving the repository URL and the applicationId.
3. Include `fastlane/metadata/android/en-US/` in the repo — Izzy's tooling reads
   the same structure this project already ships.
4. Once accepted, new releases are picked up automatically from your tags.

Users then add `https://apt.izzysoft.de/fdroid/repo`.

## Appendix B — the official F-Droid repository

Official inclusion means F-Droid builds the app from source on their
infrastructure and signs it with **their** key. Users get it without adding
anything. The costs are real:

- **Review takes weeks to months.** It is volunteer-run.
- **F-Droid's key, not yours.** An app already installed from your repo cannot
  be updated by the F-Droid build; the signature differs. Pick one channel per
  user, or ship both and warn people.
- **A client for a proprietary network service gets the `NonFreeNet`
  anti-feature.** That is expected and already declared in
  `fdroid/metadata/org.riverbank.shop.yml`.
- **Trademark scrutiny.** The app name, icon and description must not imply
  affiliation with, or use the branding of, the service it talks to. This is why
  Riverbank is called Riverbank, carries an original mark, and states the
  disclaimer in the description. Keep it that way.

The process:

1. Open a **Request For Packaging** issue at
   <https://gitlab.com/fdroid/rfp/-/issues> with the source URL, license, and a
   note that the app builds with `./gradlew assembleRelease` and has no
   proprietary dependencies.
2. Or submit directly: fork <https://gitlab.com/fdroid/fdroiddata>, add
   `metadata/org.riverbank.shop.yml` (the file in `fdroid/metadata/` is already
   in the right format), and open a merge request.
3. Test the recipe before submitting:
   ```bash
   git clone https://gitlab.com/fdroid/fdroiddata
   cd fdroiddata
   cp ../riverbank/fdroid/metadata/org.riverbank.shop.yml metadata/
   fdroid readmeta && fdroid rewritemeta org.riverbank.shop
   fdroid lint org.riverbank.shop
   fdroid build -v -l org.riverbank.shop
   ```
4. Tag every release as `vX.Y.Z` and keep `Builds:` in the metadata updated, or
   set `UpdateCheckMode: Tags` and `AutoUpdateMode: Version` so F-Droid picks up
   new tags on its own.

Running your own repo (§1–§9) and being in the official one are not mutually
exclusive, but a given user should install from one or the other, not both.
