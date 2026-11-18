# Publishing an update

Reel checks an `update.json` when Home opens. If the version there is newer, it downloads a
small patch for the version the phone has, rebuilds the new APK from the installed one,
checks its SHA-256, then asks Android to install it. Projects and settings are kept.

1. Build and sign the release: `gradle :app:assembleRelease`.
2. Keep the APKs of versions people already have (for example `Reel-1.3.0.apk`).
3. Make the update folder:

       python3 tools/update/make_update.py --new app/build/outputs/apk/release/app-release.apk \
           --old Reel-1.3.0.apk --old Reel-1.2.0.apk --notes "What's new" --out site

   It writes `update.json`, the full APK and one `.rpatch` per old version, and re-applies
   every patch to prove it rebuilds the new APK byte for byte.
4. Put every file in `site/` on any HTTPS host, in one folder. A GitHub release works: upload
   the files as release assets, and the link that always points at the newest release is
   `https://github.com/<owner>/<repo>/releases/latest/download/update.json`.
5. Point the app at that `update.json`, either once at build time
   (`-Preel.updateUrl=https://…/update.json`) or in the app (Home, update button).

Phones on a version with no patch download the full APK instead. Every update must be signed
with the same key as the installed app (`reel-release.jks`), or Android refuses it.
