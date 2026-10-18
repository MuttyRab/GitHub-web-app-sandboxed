# GitHub adaptation — test report

Status: **verification in progress**. No original Arena test result is claimed for this build.

* Source baseline: Arena 1.2.0 at `86008ec377e7dddf94f33c1b36a6eb8395cd867e`.
* Current branch: `arena/d30b97e2-github-web-app-sandboxed`.
* Android builds and emulator testing run through GitHub Actions; this editing sandbox cannot reach
  Android/Gradle artifact hosts. Its local checks are source/XML checks, not an Android build.
* Existing unit, fuzz, loopback-proxy and device guard tests are retained and retargeted to GitHub.
* Live page observations must be distinguished from successful authentication. No real credentials,
  CAPTCHA solutions, commits, issues or PR writes are performed by these signed-out tests.

Results, exact counts, APK identity/hashes, skipped tests and remaining manual checks will be added
after the workflow completes. Historical Arena evidence remains separately archived under
`docs/upstream-arena/`.
