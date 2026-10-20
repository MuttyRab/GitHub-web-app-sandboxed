# GitHub WebView (Locked)

An **unofficial GitHub adaptation of the existing Arena Android app**, not a new wrapper.
Source baseline: [Arena.ai-apk at 86008ec](https://github.com/markcollege2026-droid/Arena.ai-apk/tree/86008ec377e7dddf94f33c1b36a6eb8395cd867e), Arena 1.2.0.
The verbatim import is commit `334f840` in this repository. The original repository and both main branches are unchanged.

This session works only on `arena/d30b97e2-github-web-app-sandboxed`.
The install ID is `app.githubwebview.locked` (version 1.0.0 / code 1), separate from
`app.arenawebview.locked`; the two apps can coexist.

## Preserved

The original single Activity and WebView, dark native overlays, pull-to-refresh, back behavior,
OAuth popup window, watchdog, renderer recovery, cookies, hardware rendering and `adjustResize`.
The pure-Java URL parser and PRODUCT / ASSET / SIGN_IN roles, service-worker guard,
CONNECT egress proxy with no direct fallback, TLS checks, and escape/download/file-picker refusals.
No dashboards, domain management, approval flows, extra settings, script injection or input CSS fixes.

The start URL is `https://github.com/`. Only `github.com`, `www.github.com` and the approved
`accounts.google.com` handoff may be visible pages. GitHub resources and CAPTCHA/identity widgets
have ASSET roles: **being fetchable does not make a host navigable**. All hosts are exact; HTTPS/443 only.

## Authentication and input

The Arena email-field fix is retained: Google identity bundles on `www.gstatic.com` and
`ssl.gstatic.com` remain path-unrestricted **assets**, widget frames use SUBDOCUMENT rather than
DOCUMENT, and group merging cannot narrow those entries. The app never manipulates input fields.

One deliberate security difference: the inherited user-agent `; wv` removal is gone. Providers see
the genuine WebView identity; refusal of embedded sign-in is not bypassed. There is no browser or
external-app fallback. Google login, passkeys, security keys and enterprise SSO may be unavailable.
Passwords, 2FA, account permissions and completed authenticated workflows require real-device testing
with your own account; no personal credentials were requested or embedded in the project/test code.

## Builds and downloads

The original `.github/workflows/android.yml`, Gradle wrapper, SDK versions and Android dependencies
are reused. CI builds debug and **signed release** APKs, checks badging and SHA-256, and runs
unit/Android-emulator tests. The workflow artifact is **`github-webview-apk`**; install its
`app-release.apk`. Build output is not committed to Git.

**Get the APK:** open [run 39170410407](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39170410407)
and download the `github-webview-apk` artifact. GitHub only lets a **signed-in** browser download
artifacts, so a direct artifact link is not published here — fetched signed-out it answers `404`, which
is a GitHub rule and not a broken build. For a permanent, sign-in-free link, publish a tag; the
`release` job then attaches the APKs and checksums to a GitHub Release:

```sh
git tag v1.0.0 && git push origin v1.0.0   # → …/releases/download/v1.0.0/app-release.apk
```

[Delivery and installation](DELIVERY.md)

[Run 39170410407](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39170410407) passed: **4,151 JVM cases**, **60 Android tests passed / 1 skipped / 0 failed**,
and the exact signed release APK installed and launched. Native GitHub and generic Google identifier
entry/editing/resize/overlap checks passed. The actual GitHub → Google handoff was skipped because
GitHub did not offer the button in this WebView; no completed authentication is claimed.

See [TEST_REPORT.md](TEST_REPORT.md) for scope/earlier failures and [docs/EVIDENCE.md](docs/EVIDENCE.md)
for actual results. The original Arena reports are archived under `docs/upstream-arena/`, not reused
as evidence for this APK. [Exact changed files](docs/CHANGES.md) · [All domains/roles](docs/DOMAINS.md)
· [Source preservation](docs/PROVENANCE.md).

The original public demo signing key is retained for reproducible install/upgrade identity. It is
**not a private production key**. Use the existing `SIGNING_*` environment variables/secrets for a
private distribution (see `keystore/README.md`). Debug and release have the same package ID but
usually different signing keys: uninstall one before installing the other. Neither replaces Arena.

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

Android 7+ (minSdk 24), compile/target SDK 35, JDK 17. A WebView supporting AndroidX
`PROXY_OVERRIDE` is required; otherwise the original error screen fails closed.

## What the app will not open (and what you will see)

Two things on github.com leave the site on a click. Both are refused on purpose, and both now say so
instead of looking like a fault:

* **Help.** GitHub's Help, Docs, Support, Status and Blog entries are redirects to
  `docs.github.com`, `support.github.com`, `www.githubstatus.com` and `github.blog` — separate sites
  that are deliberately not on the allow-list (Terms and Privacy redirect to `docs.github.com` too).
  Clicking one shows the native "Blocked by this app" panel naming the host and the policy reason.
  By direct URL, only `github.com` and `www.github.com` are page hosts; every other listed host is a
  resource host for a github.com page and can never become the page.
* **Downloads.** Every GitHub download redirects to a resource-only host
  (`raw.githubusercontent.com` for a file, `codeload.github.com` for an archive,
  `objects.githubusercontent.com` for a release asset), and the app additionally installs a
  `DownloadListener` that refuses every transfer. Nothing is written to storage and no other app or
  browser is launched. A refused download now raises the same native panel ("downloads are disabled in
  this app, so nothing was saved") instead of only a short toast.

A load error that the policy did not cause (no network, TLS, renderer) still reports as a load
failure; one that *was* a refusal is reported as a refusal with the refused host. "Copy diagnostics"
in that panel gives the exact `BlockReason`.

Native file pickers stay disabled. Raw/archive links to resource-only hosts are
blocked as pages. Uploaded attachments, external images not served through approved proxies,
third-party SSO, GitHub Pages, Gist, Codespaces, Copilot and arbitrary external sites are not made
browsable. In-page repository browsing, editing, commits, issues, PRs and Actions depend on the real
website and authentication; an allowlist is not proof these workflows have completed.

The proxy checks tunnel **host and port**, not encrypted paths, SNI or WebSocket messages.
See [SECURITY.md](SECURITY.md) for the inherited security boundary and limitations.
