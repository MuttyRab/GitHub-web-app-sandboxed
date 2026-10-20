# GitHub WebView (Locked) 1.0.0

Configuration adaptation of Arena WebView (Locked) 1.2.0 at `86008ec`, imported verbatim before
adaptation. Separate `app.githubwebview.locked` identity/G monogram; GitHub start URL and exact-host,
role-based service policy. The original WebView/UI/proxy/security/input implementation is retained;
the only non-configuration behavior change removes UA masking to respect provider restrictions.

Built from `258e549d889bbec62f1e6735a7e4a1ba939dd089` in [run 39170410407](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39170410407).
**4,151 JVM cases passed; 60 Android tests passed, 0 failed, 1 skipped.** The exact signed release
artifact installed/launched successfully. GitHub and generic Google email-input/resize/overlap tests
passed; the actual GitHub Google handoff was unavailable and skipped. No completed authentication
or authenticated repository mutation/live log stream is claimed.

[Build run](https://github.com/markcollege2026-droid/GitHub-web-app-sandboxed/actions/runs/39170410407) · [Delivery](../DELIVERY.md) · [Test report](../TEST_REPORT.md).
Artifact downloads require a signed-in GitHub session; this Release asset does not.
The original public demo signing key is retained. **Downloads are saved by the app itself** (fetched
through the same loopback egress guard, written with `MediaStore` on Android 10+ or the app's own folder
on 7-9, no storage permission) and file pickers plus external app/browser launches remain disabled. No
dashboards, approval screens or extra settings were added.
