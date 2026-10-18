# GitHub adaptation — test report

## Candidate under verification

Source: `531d1cff64e2fb14e8a37411bc2316584c61299d`.
[Android CI run 39165580281](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39165580281).
**Superseded candidate: JVM/build/install checks passed; device run had 58 passes, 2 failures and 1 skip.** Fixes are being verified in the next run; this is not a final release verdict.

* **4,063 JUnit cases passed, zero failed/skipped**, reported by the current CI JVM job. This count
  includes the inherited parameterized URL-table assertions, not 4,063 distinct end-user workflows.
* The original seeded fuzz tests, exact allowlist/role/path regressions, source/manifest/resource
  invariants and real loopback CONNECT socket tests run in that suite.
* All 20 production Java files other than the allowlist match the original executable tokens after
  recorded package/branding substitutions and removal of UA masking. The preservation script passed
  locally and is a blocking CI step.
* Debug and signed release APKs built; release badging/permissions/debug flag and `apksigner verify`
  checks passed. The separate installed ID is `app.githubwebview.locked`, version 1.0.0 / code 1.
* Release SHA-256: `48a8aa8f4c98c794b2204e45c8158ac041a17a69b6793dd545a1f6b968e5ba77`.
  Installation/launch of **these exact artifact bytes** passed on the API 34 emulator; the release
  guard became ready and opened a GitHub tunnel. This was actually performed, not inferred from build success.

## Device test scope

The emulator uses Android API 34 / x86_64 / Google APIs / Pixel 5, with a real WebView and software
keyboard enabled. Device tests exercise the original navigation, redirect, popup, auth-window,
back, recovery, settings, download/file-picker/intent and egress guards. Request-callback tests
use synthetic destinations; the WebSocket/CONNECT probes use real sockets through the real proxy.

Additional live tests deliberately distinguish the following:

| Test | What it can establish | What it cannot establish |
|---|---|---|
| Home page | GitHub HTML, loaded CDN styles, initialized site custom elements | Authentication or all site features |
| Repository/file/commits/issues/PR/Actions pages | Signed-out browsing in the app, styles and approved final URLs | Creating an issue/PR, committing a change, running or editing a workflow |
| Protected file/workflow edit links | GitHub's real login gate, not an invented editor or browser escape | Completing an authenticated edit/commit |
| GitHub login input | Native IME typing and selection/editing; keyboard visibility and reduced WebView height; typed-value/placeholder and prompt-geometry assertions; native submit validation with an intentionally empty password | A real password submission, 2FA or account login; arbitrary device/font/IME combinations |
| GitHub's actual Google button | Approved handoff in the main/in-app auth WebView; identifier typing/editing/validation only when the provider serves the form | Successful Google authentication; provider refusal is not bypassed and unavailable coverage is skipped |
| Direct Google identifier probe | What Google's generic sign-in document serves to this WebView | GitHub OAuth success |
| Signup/CAPTCHA observation | Referenced challenge resources and no refusal of configured challenge paths | Solving a CAPTCHA or creating an account |

Input tests use reserved `example.invalid` addresses and **no password**. DOM inspection, test event
observation, screenshots and IME calls exist only in `androidTest`, not the installed APK. The app has
no input script/CSS overrides or JavaScript bridge. Screenshots are evidence captured by CI; no manual
physical-device or visual review is claimed.

## Signed-out desktop observations (separate from Android)

The Chrome/Playwright job actually followed GitHub's Google button to
`accounts.google.com/v3/signin/identifier`. This proves a desktop handoff/resource observation,
**not** Android authentication. It saw GitHub `/signup` return HTTP 403 with a DataDome challenge;
the challenge frame, static image, CSS and font requests returned HTTP 200. No challenge was solved.
The repository, file, issues, PR, Actions and completed-job pages returned HTTP 200.

The default platform UA is retained in the APK. No desktop UA is substituted for a WebView UA.

## Failures found and corrected during development

| Run / source | Actual result | Follow-up |
|---|---|---|
| [39164490059](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39164490059) / `22a3168` | Unit/build passed; 55 device cases, 1 failed | A retargeted test still used an Arena Turnstile path on the Arkose host. Corrected the test; did not widen policy to accept the wrong path. |
| [39164988590](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39164988590) / `4d54812` | 4,015 JVM cases passed; build passed; 61 device cases, 7 failed | Five tests stopped at a screenshot helper that incorrectly expected `UiAutomation.executeShellCommand` to interpret `&&`; changed it to separate commands with file-size verification. One actual policy regression exposed DataDome `/i.js`, now explicitly allowed alongside `/c.js`; the stale Arkose test was also corrected. |

The desktop trace additionally exposed `static.captcha-delivery.com`, now an explicit ASSET-only
entry. Earlier APKs are superseded candidates, not the final deliverable. Failure records are retained
rather than relabeled as successful app tests.

## Additional observed results from candidate `531d1cf`

* GitHub native email entry/editing and client-validation test **passed**. The software keyboard was
  visible; WebView height changed from **2,138 to 1,371 pixels**. Both typed/edited snapshots reported
  `placeholderShown=false` and `promptOverlaps=[]`. No password or authenticated submission occurred.
* GitHub did **not offer a Google button in the WebView**. The actual Google-handoff input test was
  skipped, with that reason. The generic direct-Google probe did display an identifier form; that is
  not the GitHub OAuth flow and is not reported as successful Google authentication.
* Public repository, file, commit, issues and PR pages rendered; protected file/workflow edit routes
  displayed GitHub login. The Actions page's repository-name text assertion failed; its next test
  checks actual workflow-run links and captures the page before asserting, so failure is diagnosable.
* A second real challenge-resource gap appeared: `/i.js` loads a versioned root-level script on
  `ct.captcha-delivery.com`. This is now treated like gstatic: a reviewed path-unrestricted **ASSET**,
  never a page, instead of repeatedly pinning changing script filenames. Required frames still use
  the original SUBDOCUMENT rules.

## Not performed / remaining manual checks

* A completed GitHub or Google login, password/2FA/passkey/security-key authentication, persistent
  authenticated sessions, enterprise SSO, account creation or CAPTCHA completion.
* Authenticated file editing/saving/committing, issue/PR writes, workflow editing or dispatch, private
  repository access, and live authenticated Actions log streaming/backscroll.
* A physical phone test, keyboard/language/font-size/rotation matrix, Android API 24/35 device matrix,
  and visual review of all screenshots. Only the configured API 34 emulator is claimed.
* Downloads, external browser/app launches and file pickers are deliberately disabled as in Arena;
  they are not pending features. Raw/archive ASSET hosts never become browsing destinations.
* Some regional Actions transports are dynamic; no blanket `*.actions.githubusercontent.com` or
  cloud-host wildcard was added. Unknown hosts fail closed and require a reviewed exact entry.

Before relying on authenticated workflows, install the release alongside Arena, sign in on the device
with your own account (do not share credentials), and exercise those workflows. If a provider rejects
embedded sign-in, that limitation remains; the app does not hide its identity or launch a browser.

## Local versus CI checks

This editing sandbox cannot reach Android/Gradle artifact servers. Local work verified resource XML,
JavaScript/shell syntax, Git diffs and the preserved-core fingerprints. Android compilation, JVM tests,
signing verification and emulator tests actually run in GitHub Actions. The current report does not
borrow counts or results from the historical Arena reports in `docs/upstream-arena/`.
