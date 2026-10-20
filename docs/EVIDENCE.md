# GitHub APK — evidence

Current successful [run 39170410407](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39170410407), source [`258e549d889bbec62f1e6735a7e4a1ba939dd089`](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/commit/258e549d889bbec62f1e6735a7e4a1ba939dd089).
Results below are from GitHub's Checks API annotations for this run, not invented expected output (trailing whitespace trimmed).
The full XML, logs, screenshots, badging and checksums are in its artifacts.

## Downloads

| Artifact | Retention / contents |
|---|---|
| [github-webview-apk](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39170410407) (open the run; artifact downloads need a signed-in GitHub session) | 90 days; signed release, debug APK, hashes, badging, signature output |
| [instrumented-test-evidence](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39170410407/artifacts/12152014368) | 30 days; JUnit XML/HTML, device logs/provider info, live-form/page screenshots, exact release install/launch evidence |
| [unit-test-results](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39170410407/artifacts/12152008489) | 30 days; JVM JUnit XML/HTML |
| [signed-out-site-observations](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39170410407/artifacts/12151709888) | 30 days; separate desktop browser resource/status observations and screenshots, not Android login proof |

Release SHA-256: `ab044ecc6ea2da7c76747c83fee35739e0de0324fd8eb93eb36d8f16c8aa1dd8`. Debug SHA-256: `8be2a58dfb60f14a565ee87dc7ab983d911ad183999ffa88b7524c4b4d0f82b4`.
Rebuilds can produce different signed bytes; use the hash of the artifact actually downloaded.

## Selected verbatim annotations

### JVM results

```text
4151 tests; 4151 passed; 0 failed; 0 skipped
```

### APK SHA-256

```text
ab044ecc6ea2da7c76747c83fee35739e0de0324fd8eb93eb36d8f16c8aa1dd8  app/build/outputs/apk/release/app-release.apk
```

### APK SHA-256

```text
8be2a58dfb60f14a565ee87dc7ab983d911ad183999ffa88b7524c4b4d0f82b4  app/build/outputs/apk/debug/app-debug.apk
```

### APK identity

```text
package: name='app.githubwebview.locked' versionCode='1' versionName='1.0.0' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15' targetSdkVersion:'35' launchable-activity: name='app.githubwebview.locked.MainActivity'  label='' icon=''
```

### Android results

```text
61 tests; 60 passed; 0 failed; 1 skipped
```

### SKIPPED Android

```text
app.githubwebview.locked.EmailInputRegressionTest.googleIdentifierInputAndValidationOnlyWhenProviderServesTheForm
```

### Actions page and completed-job probes (2 lines)

```text
22:08:18 github workflow probe actions rendered=true: {"actionRunLinks":25,"loadedStyles":40,"logRows":0,"path":"\/markcollege2026-droid\/Arena.ai-apk\/actions","ready":"complete","resourceHosts":["github.githubassets.com"],"signInText":false,"title":"All Workflow Runs · GitHub"}
22:08:20 github workflow probe completed-job rendered=true: {"actionRunLinks":1,"loadedStyles":40,"logRows":0,"path":"\/markcollege2026-droid\/Arena.ai-apk\/actions\/runs\/38487448876\/job\/115604618730","ready":"complete","resourceHosts":["github.githubassets.com"],"signInText":false,"title":"Merge pull request #2 from markcollege2026-droid\/arena\/a6a0b1d6-arena… · markcollege2026-droid\/Arena.ai-apk@86008ec · GitHub"}
```

### Generic Google input diagnostic (not OAuth) (4 lines)

```text
22:07:50 input probe google-generic keyboard visible=true WebViewHeight=2138 -> 1371
22:07:51 input probe google-generic prompt geometry={"fontSize":16,"inputHeight":52,"placeholderShown":false,"promptOverlaps":[],"valueLength":21}
22:07:51 input probe google-generic prompt geometry={"fontSize":16,"inputHeight":52,"placeholderShown":false,"promptOverlaps":[],"valueLength":22}
22:07:52 input probe generic Google: PASS native identifier entry/editing, keyboard resize and prompt geometry; rendering diagnostic only, NO submission or GitHub OAuth claim
```

### Email input probes (15 lines)

```text
22:07:28 input probe GitHub Google handoff offered=false
22:07:28 input probe Google control evidence={"controls":[],"ua":"Mozilla\/5.0 (Linux; Android 14; sdk_gphone64_x86_64 Build\/UE1A.230829.050; wv) AppleWebKit\/537.36 (KHTML, like Gecko) Version\/4.0 Chrome\/113.0.5672.136 Mobile Safari\/537.36"}
22:07:30 input probe GitHub form present=true url=https://github.com/login
22:07:32 input probe editor: type=161 options=301989893 connection=true
22:07:32 input probe IME request: windowFocus=true viewFocus=true served=true accepted=true
22:07:33 input probe github keyboard visible=true WebViewHeight=2138 -> 1371
22:07:34 input probe github prompt geometry={"fontSize":16,"inputHeight":40,"placeholderShown":false,"promptOverlaps":[],"valueLength":21}
22:07:34 input probe github prompt geometry={"fontSize":16,"inputHeight":40,"placeholderShown":false,"promptOverlaps":[],"valueLength":22}
22:07:36 input probe GitHub: PASS native typing, selection/replacement, keyboard resize, prompt geometry and client-validation submit; NO password or authenticated submission tested
22:07:49 input probe editor: type=32929 options=301989890 connection=true
22:07:50 input probe IME request: windowFocus=true viewFocus=true served=true accepted=true
22:07:50 input probe google-generic keyboard visible=true WebViewHeight=2138 -> 1371
22:07:51 input probe google-generic prompt geometry={"fontSize":16,"inputHeight":52,"placeholderShown":false,"promptOverlaps":[],"valueLength":21}
22:07:51 input probe google-generic prompt geometry={"fontSize":16,"inputHeight":52,"placeholderShown":false,"promptOverlaps":[],"valueLength":22}
22:07:52 input probe generic Google: PASS native identifier entry/editing, keyboard resize and prompt geometry; rendering diagnostic only, NO submission or GitHub OAuth claim
```

### Signed release install verification

```text
PASS installed and launched the exact build-apk artifact; egress guard ready and github.com tunnel observed; ab044ecc6ea2da7c76747c83fee35739e0de0324fd8eb93eb36d8f16c8aa1dd8  /home/runner/work/_temp/verified-apks/app/build/outputs/apk/release/app-release.apk
```

### WebView provider

```text
Current WebView Update Service state   Multiprocess enabled: true   Current WebView package (name, version): (com.google.android.webview, 113.0.5672.136)   Minimum targetSdkVersion: 33   Minimum WebView version code: 567263637   Number of relros started: 1   Number of relros finished: 1   WebView package dirty: false   Any WebView package installed: true   Preferred WebView package (name, version): (com.google.android.webview, 113.0.5672.136)   WebView packages:     Valid package com.google.android.webview (versionName: 113.0.5672.136, versionCode: 567263637, targetSdkVersion: 34) is  installed/enabled for all users     com.google.android.webview.beta is NOT installed.     com.google.android.webview.dev is NOT installed.     com.google.android.webview.canary is NOT installed.     com.google.android.webview.debug is NOT installed.     com.android.webview is NOT installed.
```

## Interpretation

The skipped actual GitHub/Google handoff is not a successful sign-in. Generic Google identifier
input is a separate rendering diagnostic with no submission. The Actions job page is not evidence
of authenticated log streaming (the probe reported zero log rows). See `../TEST_REPORT.md` for the
full scope, failures encountered, manual checks and limitations.

All 80 imported files were locally compared with the pinned Arena checkout; all six historical
reports are verbatim copies. Their old results in `upstream-arena/` are not results for this APK.
The original Arena main remained `86008ec377e7dddf94f33c1b36a6eb8395cd867e`; this repository's main
remained `7afae3c183503634585b261888b34b8bdc94cddc` at final verification.
