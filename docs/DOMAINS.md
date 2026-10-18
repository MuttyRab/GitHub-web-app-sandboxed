# Reviewed host/role inventory

This is the entire configured set, not a suffix/wildcard policy. Every host permits TLS tunnels
on **443 only**. `PRODUCT` / `SIGN_IN` may be top-level pages; `ASSET` may only be a request or
embedded document. Redirects and pop-ups do not convert an ASSET into a navigation destination.

## Evidence and selection

* Primary HTML and Content-Security-Policy retrieved from `https://github.com/login`, a public
  repository/file page and an Actions page on 2026-10-18. A cookie-free/header-only snapshot is in
  [observed-github-csp.txt](observed-github-csp.txt). JS/CSS on `github.githubassets.com`, avatars,
  APIs, file-preview origins, exact GitHub S3 buckets and the 20 exact Actions result-store accounts
  are identified there. Presence in CSP is evidence of intended use, not proof a feature was exercised.
* Homepage HTML explicitly uses Contentful space `8aevphvgewt8`; only that prefix is approved on
  `images.ctfassets.net` and `videos.ctfassets.net`, not every customer's content.
* GitHub's [connectivity documentation source](https://github.com/github/docs/blob/dae83f7d28f3b8e10015bd6b6d8b72267cedceca/content/get-started/using-github/troubleshooting-connectivity-problems.md)
  identifies Octocaptcha/Arkose for account verification. Exact challenge client endpoints are
  scoped to `/fc/` and `/v2/`, not the general Arkose site. Actual per-run observations are reported
  separately; not every optional challenge is forced or encountered by CI.
* From this editing network, `/signup` returned a DataDome interstitial referencing
  `ct.captcha-delivery.com/c.js` and `geo.captcha-delivery.com`. These are explicitly resource-only
  challenge allowances; this observation is network-specific, not a claim that all GitHub users
  see DataDome. The Android signup probe additionally requested `/i.js` on `ct.captcha-delivery.com`. The signed-out Chrome trace also requested `static.captcha-delivery.com`; it is resource-only. No challenge was solved or bypassed.
* GitHub supports Google social sign-in. [1](https://github.blog/changelog/2025-07-15-social-login-with-google-is-now-generally-available/)
  The original Google identity entries and reCAPTCHA support are retained. The exact opaque
  gstatic identity-bundle paths must not be repinned to `/accounts/`: that caused the original
  superimposed-email/dead-form bug. Arkose replaces the Arena-only Cloudflare Turnstile exception.
* `alive.github.com` is the production real-time channel; it is **not proof that every Actions
  log transport works**. The encrypted proxy checks its host, not WebSocket frames. Dynamic
  regional `*.actions.githubusercontent.com` hosts are NOT blanket-approved. Such a host needs a
  concrete observation and reviewed exact-host entry; otherwise that part of live logs may fail closed.

The automated desktop observations and Android probes in the CI artifacts are different tests.
A signed-out page load, a policy allow decision and a completed authenticated workflow are not
interchangeable evidence. See [../TEST_REPORT.md](../TEST_REPORT.md) for actual outcomes.

## Complete configuration

| Exact host | Role | Allowed path prefixes |
|---|---|---|
| `github.com` | PRODUCT | Any path |
| `www.github.com` | PRODUCT | Any path |
| `github.githubassets.com` | ASSET | Any path |
| `api.github.com` | ASSET | Any path |
| `avatars.githubusercontent.com` | ASSET | Any path |
| `private-avatars.githubusercontent.com` | ASSET | Any path |
| `identicons.github.com` | ASSET | Any path |
| `user-images.githubusercontent.com` | ASSET | Any path |
| `private-user-images.githubusercontent.com` | ASSET | Any path |
| `secured-user-images.githubusercontent.com` | ASSET | Any path |
| `camo.githubusercontent.com` | ASSET | Any path |
| `raw.githubusercontent.com` | ASSET | Any path |
| `media.githubusercontent.com` | ASSET | Any path |
| `objects.githubusercontent.com` | ASSET | Any path |
| `objects-origin.githubusercontent.com` | ASSET | Any path |
| `release-assets.githubusercontent.com` | ASSET | Any path |
| `codeload.github.com` | ASSET | Any path |
| `uploads.github.com` | ASSET | Any path |
| `viewscreen.githubusercontent.com` | ASSET | Any path |
| `notebooks.githubusercontent.com` | ASSET | Any path |
| `repository-images.githubusercontent.com` | ASSET | Any path |
| `opengraph.githubassets.com` | ASSET | Any path |
| `alive.github.com` | ASSET | Any path |
| `github-cloud.s3.amazonaws.com` | ASSET | Any path |
| `github-production-repository-file-5c1aeb.s3.amazonaws.com` | ASSET | Any path |
| `github-production-upload-manifest-file-7fdce7.s3.amazonaws.com` | ASSET | Any path |
| `github-production-user-asset-6210df.s3.amazonaws.com` | ASSET | Any path |
| `github-production-repository-image-32fea6.s3.amazonaws.com` | ASSET | Any path |
| `github-production-release-asset-2e65be.s3.amazonaws.com` | ASSET | Any path |
| `productionresultssa0.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa1.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa2.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa3.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa4.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa5.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa6.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa7.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa8.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa9.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa10.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa11.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa12.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa13.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa14.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa15.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa16.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa17.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa18.blob.core.windows.net` | ASSET | Any path |
| `productionresultssa19.blob.core.windows.net` | ASSET | Any path |
| `images.ctfassets.net` | ASSET | `/8aevphvgewt8/` |
| `videos.ctfassets.net` | ASSET | `/8aevphvgewt8/` |
| `accounts.google.com` | SIGN_IN | Any path |
| `ssl.gstatic.com` | ASSET | Any path |
| `www.gstatic.com` | ASSET | Any path |
| `apis.google.com` | ASSET | `/js/`, `/gsi/`, `/_/` |
| `fonts.gstatic.com` | ASSET | `/s/` |
| `lh3.googleusercontent.com` | ASSET | `/a/` |
| `www.google.com` | ASSET | `/recaptcha/` |
| `www.recaptcha.net` | ASSET | `/recaptcha/` |
| `octocaptcha.com` | ASSET | Any path |
| `github-api.arkoselabs.com` | ASSET | `/fc/`, `/v2/` |
| `client-api.arkoselabs.com` | ASSET | `/fc/`, `/v2/` |
| `static.captcha-delivery.com` | ASSET | Any path |
| `geo.captcha-delivery.com` | ASSET | `/captcha/`, `/interstitial/` |
| `ct.captcha-delivery.com` | ASSET | `/c.js`, `/i.js` |

**65 exact hosts:** 2 PRODUCT,
1 SIGN_IN,
62 ASSET.
`AllowlistRegressionTest` pins the literal list, roles and reviewed path exceptions.

ASSET is a URL role, not an enforced MIME type. Paths are **prefixes**, using the original engine's
raw-path checks (no percent escapes or `..` in pinned paths). For example `/c.js` is a prefix,
not an exact-file matcher. The proxy cannot inspect encrypted paths or every redirect hop; see
[../SECURITY.md](../SECURITY.md).

## Intentionally not approved

All Arena hosts; arbitrary external links, downloads, app/deep-link schemes and insecure HTTP;
GitHub Pages, Gist, Docs/Support/blog external sites, Codespaces/VS Code/Copilot; telemetry such as
`collector.github.com`; staging hosts; arbitrary S3/Azure accounts; arbitrary enterprise SSO;
Apple ID and other identity providers; Google Search outside reCAPTCHA, Gmail, Drive, Play and
unlisted Google hosts. A GitHub-owned name is not by itself approval.

Raw and archive hosts are ASSET-only: inline requests may work but navigating a Raw/ZIP link or
saving an artifact from inside the wrapper is intentionally still blocked by the original guards.
Download the **APK** from the workflow artifact in your browser, not through this locked wrapper.

## Google handoff boundary

Only `accounts.google.com` is approved as a non-product page. It is allowed in the main WebView
or the original in-app sign-in window (retaining opener/cookies); the inherited policy is host-based,
not a stateful OAuth client-ID/redirect-URI verifier. Google resource hosts never become pages.
The app keeps the platform user agent, makes no browser substitution and does not add any external
fallback. A provider restriction remains a restriction, even if the initial email form is displayed.
