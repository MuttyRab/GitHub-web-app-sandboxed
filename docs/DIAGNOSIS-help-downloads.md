# Diagnosis: "Help" and "Downloads" do not work

Date: 2026-10-20 · Branch `arena/447b93fb-github-web-app-sandboxed` · App `app.githubwebview.locked` 1.0.0

## Summary

Both symptoms have the same shape, but **four different root causes**:

1. **Help is never reachable.** Every help entry point in GitHub's UI is a redirect to a *different
   domain* (`docs.github.com`, `support.github.com`, `www.githubstatus.com`, `github.blog`). Those four
   hosts are **deliberately excluded** from the allow-list, so the redirect dies and the app shows a
   block/error surface.
2. **Downloads fail twice over, by design.** The app installs a `DownloadListener` that refuses every
   download, *and* GitHub's download links all redirect to **resource-only (ASSET)** hosts
   (`raw.githubusercontent.com`, `codeload.github.com`, `objects.githubusercontent.com`) which the
   policy forbids from ever becoming the visible page. So the transfer is refused before it starts.
3. **The feedback is misleading.** When a blocked redirect fails at the network layer, the screen says
   *"github.com could not be reached"* even though `github.com` was fine and a third-party host was
   refused. A blocked download is reported only as a 2-second toast, so a click can look like "nothing
   happened".
4. **One genuine repo-level broken link.** The "Download the signed APK" link in `README.md` /
   `TEST_REPORT.md` is a workflow-artifact web URL. GitHub only serves artifacts to a **signed-in web
   session**; fetched anonymously it answers `404 Not Found`. `DELIVERY.md` warns about this,
   `README.md` does not.

This is **not a regression and not a crash**: it is the configured policy plus poor signposting. The
unit tests actively *assert* the current behaviour (`AllowlistRegressionTest.excludedHostsAreNotReachable`,
`PolicyCases`), so any change here is a product decision, not a bug fix in the usual sense.

---

## 1. Help

Verified live on 2026-10-20 (`curl -I`):

| Entry point in GitHub's UI | Server answer | Policy verdict |
|---|---|---|
| `github.com/help` (header "Help", old help URL) | `301 → https://docs.github.com/` | `HOST_NOT_ALLOWED` |
| Footer "Docs" → `docs.github.com` | `200` (host not listed) | `HOST_NOT_ALLOWED` |
| Footer "Contact"/"Support" → `github.com/support` | `301 → support.github.com/?tags=dotcom-direct` | `HOST_NOT_ALLOWED` |
| Footer "Status" → `www.githubstatus.com` | `200` (host not listed) | `HOST_NOT_ALLOWED` |
| Footer "Blog" → `github.blog` | `200` (host not listed) | `HOST_NOT_ALLOWED` |
| Footer "Terms" → `github.com/site/terms` | `301 → docs.github.com/site-policy/...` | `HOST_NOT_ALLOWED` (after the redirect) |
| Footer "Privacy" → `github.com/site/privacy` | `301 → docs.github.com/site-policy/...` | `HOST_NOT_ALLOWED` (after the redirect) |

Only `github.com` and `www.github.com` are page hosts:

* `app/src/main/java/app/githubwebview/locked/policy/GitHubAllowlist.java:92-98` — `firstPartyEntries()`
  returns exactly those two `anyPath` PRODUCT entries.
* `app/src/main/java/app/githubwebview/locked/policy/GitHubAllowlist.java` — `serviceEntries()` makes
  every other GitHub host `resourceOnly(...)` (ASSET).
* `app/src/test/java/app/githubwebview/locked/policy/AllowlistRegressionTest.java:271-291` — an
  explicit `excluded` array containing `docs.github.com`, `support.github.com`, `desktop.github.com`,
  `www.githubstatus.com`, `github.blog`.
* `app/src/test/java/app/githubwebview/locked/policy/PolicyCases.java:126` — asserts
  `https://docs.github.com/` is refused with `HOST_NOT_ALLOWED`.

### What the user actually sees, and why it is confusing

A server-side `301` on a **main-frame** request is not reported to `shouldOverrideUrlLoading`; Chromium
follows it internally. Two hooks can then fire:

1. `GitHubWebViewClient.shouldInterceptRequest()` (`web/GitHubWebViewClient.java:122-158`) classifies
   the redirected main-frame request as `DOCUMENT`, refuses it and returns a synthetic **403 with an
   empty body** (`web/BlockedResponses.java`), then calls `onPageLoadBlocked()` → native overlay
   ("Blocked by this app … that domain is not on the allow-list"). This is the good path.
2. If the request escapes the intercept hook (WebView versions differ on redirect hops — the class
   comment at `GitHubWebViewClient.java:35-37` admits this), the CONNECT proxy still refuses the TLS
   tunnel (`net/EgressProxy.java:241-249`, `403` + close). Chromium then reports
   `ERR_TUNNEL_CONNECTION_FAILED` to `onReceivedError()` → `MainActivity.onLoadError()`
   (`MainActivity.java:716-721`) which hard-codes:

   ```java
   overlay.showError("github.com could not be reached (" + description + "). …")
   ```

   → the user is told GitHub itself is down while the real cause is "docs.github.com is not allowed".

There is no in-app escape hatch: no "open in browser" (intentional, `startActivity` is overridden at
`MainActivity.java:566-611`), and the overlay offers only "Back to github.com" / "Copy diagnostics".

---

## 2. Downloads

Two independent gates, either of which is enough to refuse:

**Gate A — page-level.** GitHub never serves a download from `github.com` itself; it always 302s to a
host that is ASSET-only in this policy. Verified live:

| GitHub action | Redirect target | Verdict as a *document* |
|---|---|---|
| File page → **Download raw file** / `/raw/…` | `raw.githubusercontent.com/…` | `DOCUMENT_NOT_ALLOWED` |
| **Code → Download ZIP**, `archive/refs/heads/…zip` | `codeload.github.com/…` | `DOCUMENT_NOT_ALLOWED` |
| Release asset `releases/download/…` | `objects.githubusercontent.com/…?response-content-disposition=attachment` (via `release-assets.githubusercontent.com`) | `DOCUMENT_NOT_ALLOWED` |

(`GitHubAllowlist.java` `serviceEntries()`: `raw`, `codeload`, `objects`, `objects-origin`,
`release-assets` are all `resourceOnly(..., "…download guard remains in place")`.)

**Gate B — capability-level.** Even if a download were served from an allowed page host, the app
refuses it:

* `web/WebViewHardening.java:125` — `webView.setDownloadListener(new BlockingDownloadListener(host))`
* `web/WebViewHardening.java:172-197` — every `onDownloadStart` increments
  `PolicyMetrics.Counter.BLOCKED_DOWNLOADS` and calls `host.onDownloadBlocked(...)`
* `MainActivity.java:904-907` — `onDownloadBlocked()` → `onMessage("Downloads are disabled in this app")`
* `MainActivity.java:1076-1091` — `onMessage()` is a **`Toast.LENGTH_SHORT`** toast, throttled and
  best-effort (`catch (Throwable ignored)`).

**Gate C — platform.** `AndroidManifest.xml` declares only `INTERNET` + `ACCESS_NETWORK_STATE`: no
storage permission, no `DownloadManager` (asserted in `SourceInvariantsTest.downloadsAreRefused`,
`app/src/test/.../SourceInvariantsTest.java:248-253`), and `startActivity` is overridden so nothing can
hand the URL to a browser or another app.

### Why it feels "broken" rather than "disabled"

* If Gate A fires first, the visible result is the native "Blocked by this app" overlay over a
  **blank page** (the synthetic 403 body is empty by design so nothing is fetched), plus a short toast
  reading "Blocked … (that domain may serve page resources but must never become the page)". That
  sentence describes the *host role*, not the thing the user tried to do, and never mentions downloads.
  If the intercept hook is skipped for the redirect hop, the CONNECT proxy refuses the tunnel and the
  result is a generic network error instead (§1, defect D).
* If Gate B fires first, the only feedback is the transient toast "Downloads are disabled in this app";
  the page behind it is unchanged, so a missed toast looks like a dead button.
* Diagnostics attribute the cause to whichever gate ran first, so "Copy diagnostics" may show
  `CAPABILITY_DISABLED` or `DOCUMENT_NOT_ALLOWED` for the same user action.

---

## 3. The repo's own download link (separate defect)

`README.md:42`, `DELIVERY.md:7`, `TEST_REPORT.md:26` all point at:

```
https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39170410407/artifacts/12151879163
```

Fetched without a GitHub web session it answers `307 → /suites/106144669411/artifacts/12151879163 →
404 Not Found` (verified today). The artifact does exist and is not expired —
`gh api …/actions/runs/39170410407/artifacts` returns `github-webview-apk`, 2 150 037 bytes,
`expires_at 2027-01-16`. So the link works **only for a signed-in browser**, and for everyone else it is
an unlabelled 404. `DELIVERY.md` says "GitHub may require you to sign in to download artifacts";
`README.md` does not. A CI-run link (e.g. `…/actions/runs/39170410407`) plus the artifact name, or a
GitHub Release with the APK attached, would be a stable, anonymous-friendly target.

---

## 4. Root-cause map (file:line)

| # | Symptom | Cause | File |
|---|---|---|---|
| 1 | Help/Docs/Support/Status/Blog blocked | only `github.com`/`www.github.com` are page hosts; redirects leave the allow-list | `policy/GitHubAllowlist.java:92-98`, `:120-170` |
| 2 | Help redirects are asserted-blocked | tests freeze the exclusion | `AllowlistRegressionTest.java:271-291`, `PolicyCases.java:126` |
| 3 | Blank page on a refused redirect | empty 403 synthetic response | `web/BlockedResponses.java`, `web/GitHubWebViewClient.java:122-158` |
| 4 | Wrong host in the error text | `onLoadError` hard-codes "github.com" and ignores its `url` argument | `MainActivity.java:716-721` |
| 5 | Download buttons produce nothing | every GitHub download 302s to an ASSET-only host | `GitHubAllowlist.serviceEntries()` |
| 6 | Download refusal is easy to miss | `onMessage` is a throttled short toast | `MainActivity.java:1076-1091` |
| 7 | No way to save a file at all | blocking `DownloadListener`, no storage permission, no `DownloadManager`, `startActivity` overridden | `web/WebViewHardening.java:125,172-197`, `AndroidManifest.xml`, `MainActivity.java:566-611` |
| 8 | README APK link 404s anonymously | artifact web URLs are sign-in gated | `README.md:42`, `TEST_REPORT.md:26` |

---

## 5. Constraint on any fix

`scripts/verify-preserved-core.py` re-hashes **20 production Java files** (including `MainActivity`,
`GitHubWebViewClient`, `WebViewHardening`) against upstream Arena `86008ec` and currently passes:

```
PASS: 20 production Java files match upstream executable tokens after recorded
identifier/label substitutions and the single UA-honesty exception.
Only GitHubAllowlist is site-policy configuration.
```

So:

* **Policy** can be changed in exactly one file without touching that invariant:
  `policy/GitHubAllowlist.java`.
* **UX wording** changes in `MainActivity` / `GitHubWebViewClient` require a **new recorded exception**
  in `docs/upstream-core-fingerprints.json` + `verify-preserved-core.py`, i.e. an explicit decision to
  diverge from the "verbatim import" promise in `README.md` / `DELIVERY.md`.
* Overlay strings in `app/src/main/res/values/strings.xml` are not fingerprinted, so title/footer copy
  there is cheap to adjust.

---

## 6. Options, cheapest first

| Option | Change | Effect | Risk |
|---|---|---|---|
| **A. Tell the truth (docs only)** | Add a "Known limits inside the app" section; say Help → docs/support and every download are refused by design, and how to read the diagnostics overlay. Fix the README artifact link (point at the run + artifact name, or publish a Release). | No behaviour change, users stop filing this as a bug. | None |
| **B. Allow GitHub's help sites as pages** | In `GitHubAllowlist.firstPartyEntries()` add exact entries for `docs.github.com`, `support.github.com` (+ optional `www.githubstatus.com`) with a new `reference`/PRODUCT role; update `AllowlistRegressionTest` / `PolicyCases`; document in `docs/DOMAINS.md` + `SECURITY.md`. | Help, Terms, Privacy, Support, Status all work; the app becomes "GitHub + GitHub's help", still exact-host, 443-only. | Widens the page surface to GitHub-controlled content outside the repository UI; costs the "only the product itself" property. Needs your sign-off. |
| **C. Make downloads work** | Real feature work: intercept allowed attachment responses, stream them through the in-process path or a fresh request, and write via `ACTION_CREATE_DOCUMENT` (SAF) — never `DownloadManager`, never a browser hand-off; add a MIME/size policy and a new metric. | `.zip`/`.apk`/release assets become savable. | Breaks the documented "the app never writes a file" invariant (`SECURITY.md`, `docs/CHANGES.md`); needs a new threat review, storage-permission-free SAF flow and new instrumented tests. Not a small patch. |
| **D. Fix only the messaging** | Make `onLoadError` name the real host, and surface download refusals through the native overlay (with "Copy diagnostics") instead of a toast. | Same refusals, comprehensible feedback; kills the "app is broken" impression. | Touches two fingerprinted files → requires a recorded exception and a documentation update. |

Recommendation: **A + D** now (cheap, honest, no security change), and treat **B**/**C** as explicit
product decisions you make, not something to slip in.

---

## 7. Reproducing on a device

```sh
adb logcat -c
# in the app: open the footer link "Docs", then a repo's "Download ZIP", then a release asset
adb logcat -s GitHubWebViewLocked | grep -E "refused|blocked|recovery|load error"
```

Expected lines (from `GitHubWebViewClient.logRefusal` / `recordBlock`):

```
refused document: HOST_NOT_ALLOWED host=docs.github.com path=/
refused document: DOCUMENT_NOT_ALLOWED host=codeload.github.com path=/<owner>/<repo>/zip/refs/heads/main
refused document: DOCUMENT_NOT_ALLOWED host=objects.githubusercontent.com path=/github-production-release-asset-…
```

If you instead see `main frame load error: … ERR_TUNNEL_CONNECTION_FAILED …`, the request took the
`shouldInterceptRequest`-miss path described in §1 and the egress proxy refused it — same verdict,
worse message (defect D).

## 8. Evidence collected for this report

* Live HTTP, 2026-10-20: `github.com/help`, `/support`, `/site/terms`, `/site/privacy` → `301` to
  `docs.github.com` / `support.github.com`; `github.com/{owner}/{repo}/raw/…` → `raw.githubusercontent.com`;
  `/archive/refs/heads/main.zip` → `codeload.github.com`; a release asset → `objects.githubusercontent.com`
  with `response-content-disposition=attachment`.
* `github.com/` homepage HTML (2026-10-20) links `docs.github.com`, `support.github.com`,
  `www.githubstatus.com`, `github.blog` in the footer.
* `gh api` on run `39170410407`: the `github-webview-apk` artifact exists, 2 150 037 bytes, expires
  2027-01-16; the browser artifact URL returns `404` when fetched without a web session.
* `python3 scripts/verify-preserved-core.py` → PASS (20 files), which is what constrains §5.
* No JDK/Android SDK is available in this sandbox, so no JVM/instrumented run was executed here; the
  code-path claims above are read from source and the HTTP claims are live.

---

## 9. What was implemented in this branch (2026-10-20)

Chosen: **A + D**. Nothing about the allow-list, the download listener or the fail-closed behaviour
changes, so help and downloads are still refused — but a refusal is now reported as a refusal instead
of as a github.com outage or a missed toast. B (open GitHub's help sites as pages) and C (real
downloads) remain product decisions, described in §6, and are deliberately **not** applied here.

| Change | Where |
|---|---|
| A load error asks the policy what happened; a refused destination shows the native overlay with the refused host and the policy reason. A genuine network/TLS failure keeps the old wording. | `MainActivity.onLoadError` |
| A refused download shows the same overlay ("downloads are disabled in this app, so nothing was saved") instead of only a 2-second toast. | `MainActivity.onDownloadBlocked` |
| Error-overlay title no longer claims github.com; the overlay footer now names Help/Docs/Support/Status/Blog and downloads as refused destinations, so the explanation travels with every refusal panel. | `res/values/strings.xml` |
| The direct workflow-artifact link (404 when fetched signed-out) is replaced by the run page, with the `v*` tag → Release path documented for a sign-in-free download. | `README.md`, `DELIVERY.md`, `TEST_REPORT.md`, `docs/EVIDENCE.md`, `docs/RELEASE_NOTES.md` |

Preservation contract: `MainActivity` is part of the 20-file fingerprint set, so the message change is
recorded as a **reviewed exception** in `docs/upstream-core-fingerprints.json` (rationale + reviewed
hash + the tokens it added/still requires and the token it replaced). `scripts/verify-preserved-core.py`
now *verifies* such an exception rather than skipping the file: a stale exception, a missing required
token, a resurrected old token, or any edit outside the reviewed hash all fail CI. This was tested by
appending an unrelated declaration to `MainActivity` (verifier exits 1, naming the file) and reverting.

JVM coverage added: `SourceInvariantsTest.refusalsAreReportedAsRefusalsNotAsOutages` asserts both
messages go through the overlay, that the load-error path consults the policy, and that the download
message states nothing was saved.

Not verified here: no JDK/Android SDK exists in this diagnosis environment, so the Gradle and emulator
jobs were not run locally. `python3 scripts/verify-preserved-core.py` passes, the resource XML parses,
and the source assertions were replayed against the real files; CI re-runs the full JVM and device
suites on push.
