# Building and signing

## Debug build

```sh
pnpm tauri android build --debug --apk                   # universal APK
pnpm tauri android build --debug --apk --target aarch64  # arm64 only (faster, smaller)
```

Output: `src-tauri/gen/android/app/build/outputs/apk/<variant>/debug/app-<variant>-debug.apk` (for example `apk/universal/debug/app-universal-debug.apk`). Install with `adb install -r <apk>`.

A debug build **embeds the built frontend** (`dist/`); it does not use Vite. Do not use it as the dev environment — use `pnpm android:dev`.

## Release build and signing

`pnpm tauri android build --apk` (and `--aab`) produce an **unsigned** APK/bundle because the Gradle project has no `signingConfig`. Use the script, which signs outside Gradle so it survives regeneration of `gen/android`:

```sh
pnpm android:sign --init    # first time: create ~/.moga-release.keystore (asks for a password and details)
pnpm android:sign --build   # build the release APK, then align, sign and verify it
pnpm android:sign           # re-sign the APK that is already built
```

`scripts/sign-apk.sh` runs `zipalign` **before** `apksigner sign` (aligning afterwards invalidates the signature) and `apksigner verify`, using the newest installed build-tools. The result is `src-tauri/gen/android/app/build/outputs/apk/universal/release/moga-release-signed.apk`.

| Variable | Default | |
|---|---|---|
| `KEYSTORE` | `~/.moga-release.keystore` | Keystore path. |
| `KEY_ALIAS` | `moga` | Key alias. |
| `KS_PASS` | prompted | Keystore password (use for CI). |
| `ANDROID_HOME` | `~/Android/Sdk` | SDK location. |

> **The keystore is the app's identity.** Keep it outside the repository and back it up; without it you cannot ship updates that install over an existing install. Never commit it.

A Play Store `.aab` has to be signed separately (`jarsigner` or Play App Signing).

## App identity

The bundle identifier / Android `applicationId` is **`dev.mogabridge.app`** and the display name is **MOGA Bridge** (`src-tauri/tauri.conf.json`). The identifier also appears in:

- the Kotlin package (`gen/android/app/src/main/java/dev/mogabridge/app/`) and `buildSrc`,
- `namespace` / `applicationId` in `gen/android/app/build.gradle.kts`,
- `PLUGIN_ID` in `src-tauri/src/constants/android.rs`,
- the helper's file path (`/sdcard/Android/data/dev.mogabridge.app/…`) in docs and scripts.

If you change it, update all of them; a different identifier installs as a different app.

## Icons

The icons derive from the original app's launcher icon. Regenerate every size (including the Android mipmaps) from a ≥ 512 px source image:

```sh
pnpm tauri icon path/to/source.png
```

The notification icon is separate: `res/drawable-*/ic_stat_moga.png` (white glyph).

## Versioning

Bump `version` in `src-tauri/tauri.conf.json` and `src-tauri/Cargo.toml` together; Tauri derives the Android version code from it.

## Regenerating the Android project

`gen/android/` is created by `tauri android init`. Parts of it are **project source** that init does not know about; if you ever regenerate it, keep or restore:

- the whole Kotlin package `…/dev/mogabridge/app/moga/`,
- `AndroidManifest.xml` additions (permissions, `MogaConnectionService`, the IME service),
- `res/raw/helper_script.sh`, `res/drawable-*/ic_stat_moga.png`, `res/xml/moga_input_method.xml`, the strings `moga_ime_*`.

## CI (not set up yet)

A pipeline would run the checks in [CONTRIBUTING.md](../../CONTRIBUTING.md#4-before-you-open-a-pull-request) and build a signed release with `KS_PASS` and the keystore as secrets. Tracked in the [roadmap](../project/roadmap.md).
