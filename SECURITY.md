## Current Arena integration (proposed branch)

Arena is the default page; the original Arena first-party host list was added to
the existing GitHub and Google Drive entries. The user-visible WebView and the
loopback egress guard continue to require an exact approved HTTPS host on port 443.
The app does **not** grant client-side scripts unrestricted web traffic.

Arena's remote Agent Mode tools are server-side services. The APK's policy neither
blocks those remote calls nor gives them new abilities; it cannot distinguish the
agent from a user using client-side fetch traffic. This separation is a trust
boundary, not a privilege bypass.

One exception to the general refusal of external activities is the user-requested
Android `ACTION_OPEN_DOCUMENT` picker. Its dispatch is limited to a pending
WebView upload from an approved product page with an in-flight flag; content URI
access is scoped to files the user explicitly selects. Do not interpret this as
permission for camera capture, filesystem browsing or arbitrary intents.

No current CI/device run establishes real Arena sign-in, Drive functionality
or successful file submission. The prior GitHub-only evidence below predates
these Arena integration changes.

---

# Security scope — GitHub adaptation

This is the original Arena security architecture with site configuration changes, not a new sandbox.
The original full review is archived in `docs/upstream-arena/SECURITY.md` and `REVIEW.md`.

* Exact canonical hosts, no wildcards or suffix matches; HTTPS and port 443 only.
* PRODUCT and SIGN_IN pages are distinct from ASSET requests/embedded documents.
* Every WebView in the process uses the original IPv4-loopback CONNECT proxy via ProxyController.
  It checks the allowlist before DNS/connection, has no direct fallback, and is installed before loads.
* Navigation hooks, request hooks, popup routing, service-worker requests, history checks and the
  three-second watchdog remain. No extra Activity or browser/app launches, URL intents or deep links.
* No JavaScript bridge or platform download manager; a user-selected document picker is the sole
  external Activity exception. No camera/microphone/location grants,
  no mixed content, no file/content URL access, no backup, system CAs only. TLS errors cancel.
* Release WebView debugging is off. The application contains no credential collection or login automation.
* The genuine platform UA is retained: no disguising WebView to evade provider restrictions.

## Boundaries, not promises

ASSET means a URL role, **not MIME/content validation**. An image host can still return script or
other content in the requests the browser allows. Shared user-content hosts, Camo, cloud buckets
and GitHub itself are trusted endpoints for this wrapper; the app cannot prove ownership of content
or stop an approved origin relaying data. Accounts.google.com is an approved sign-in host in the
main view as well as popup; the inherited policy is not a stateful OAuth client-ID/callback validator.

Android request interception does not expose every resource redirect hop or WebSocket. The proxy
still enforces exact tunnel host/443, but cannot read TLS paths, MIME, SNI, HTTP methods or socket
messages. Path restrictions apply only where WebView exposes URLs. No TLS interception, custom
root CA, response rewriting or certificate bypass is introduced. Browser/DNS/proxy assumptions and
loopback/HTTP3 constraints from the upstream review remain; this is not an OS firewall for other apps. The tests cover the documented URL hooks, HTTPS/CONNECT
and WebSockets; they are not an independent proof of every browser transport or platform service.

GitHub server CSP is evidence for choosing service hosts, not permission to allow everything it lists.
No wildcard `*.githubusercontent.com`, `*.actions.githubusercontent.com`, S3, Azure or Google rule is
used. Unlisted services fail closed; dynamic regional Actions hosts may require a reviewed source edit.

The committed demo signing key is public. For a private deployment supply your own signing key via
existing CI secrets; never treat the demo key as proof a downloaded APK was produced by this project.


## User-selected uploads and Google Drive

The *only* external Activity exception is a document-picker `ACTION_OPEN_DOCUMENT`
request, guarded by its exact request code and in-flight flag. Only `content://` URI
results from explicit user selection are passed back to the website; unapproved intents
and `file://` navigation remain blocked. Google Drive is added as a navigable host,
and Google Drive API and thumbnail endpoints are added as exact asset-only entries.

This APK has no native agent principal and does not weaken WebView CONNECT restrictions to
provide universal agent access; a general HTTPS proxy exception for scripts would compromise
the user-facing allowlist.
