# Source provenance and preservation

* Original: `https://github.com/markcollege2026-droid/Arena.ai-apk`
* Exact source revision: `86008ec377e7dddf94f33c1b36a6eb8395cd867e` (merged main, Arena 1.2.0 / code 3).
* That upstream revision has a successful [Android CI run](https://github.com/markcollege2026-droid/Arena.ai-apk/actions/runs/38487448876).
  This establishes source/build provenance; it is **not** a test of this GitHub version.
* Verbatim import into this repository: `334f840`. All 80 source/build/doc files were imported unchanged
  before the adaptation. The original APK was not decompiled or reverse-engineered.
* The GitHub version is isolated on `arena/d30b97e2-github-web-app-sandboxed` in this session's
  `GitHub-web-app-sandboxed` repository. Its `main` remains `7afae3c`; the Arena repository is not modified.
* Previous from-scratch work is not used. Its old session/APK/log branches were removed at the user's
  request; historical Actions run records are not rewritten into evidence for this version.

## Executable-code comparison

Run `python3 scripts/verify-preserved-core.py`. It compares every production Java file except the
site allowlist against token fingerprints derived **from the imported original source**. The
manifest `upstream-core-fingerprints.json` records original paths and raw SHA-256, exact package/class/
label substitutions and expected adapted code hashes. Comments and whitespace are ignored;
string contents remain significant. Added/removed production classes also fail the check.

The comparison covers 20 production files: MainActivity, EgressProxy, all URL/policy helpers,
BackNavigator, metrics, AuthWindow, PullToRefreshFrame, StatusOverlay, WebView/Chrome clients,
PopupRouterClient, EgressGuard, hardening and guard interfaces/blocked responses.

The sole recorded runtime exception outside site configuration is **removing user-agent masking**
from WebViewHardening. The original `; wv` stripping was intended to avoid WebView detection.
The new requirement explicitly forbids bypassing identity-provider restrictions, so the default
platform UA is retained. No replacement UA, CSS/JS injection, input hook or rendering workaround
was added. New IME/form inspection code exists only in `androidTest`, not the APK.

This comparison demonstrates implementation reuse, not a proof that every Android/provider behavior
works. It complements, rather than replaces, the JVM, socket, emulator and live-page tests.

## Build/UI preservation

The root Gradle script, Gradle properties, Gradle 8.11 wrapper/JAR/checksum, dependency versions,
SDK 24/35/35 configuration, signing mechanism/public demo keystore, native colors/layout/theme
properties, `adjustResize`, hardware acceleration and permissions are unchanged. App ID,
namespace, labels/icon monogram, start URL, policy entries and corresponding CI package/artifact
names are the app-specific differences. Original architecture and escape refusals remain intact.

The original reports are archived verbatim in `upstream-arena/`. Current reports do not reuse their
pass counts. See `CHANGES.md` for the exact file inventory and `DOMAINS.md` for the complete host roles.

## Delivered build

The delivered signed APK was built and tested from `258e549d889bbec62f1e6735a7e4a1ba939dd089`
in [run 39170410407](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39170410407).
The final handover commit changes documentation only; app, build and test sources remain those of that
verified run. See `../DELIVERY.md` for the artifact and exact byte hash.
