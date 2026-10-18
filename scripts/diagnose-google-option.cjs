// Read-only diagnosis of GitHub's Google button. NO sign-in, OAuth navigation,
// credentials, cookie export, UA changes in the APK, or provider bypass.
// Altered UA/Client-Hint samples are diagnostic controls, NOT implementation advice.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const out = 'site-observations/signin-diagnosis';
const safe = value => { try { const u = new URL(value); return u.origin + u.pathname; } catch { return ''; } };
const escape = value => value.replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A');
const nativeUA = 'Mozilla/5.0 (Linux; Android 14; sdk_gphone64_x86_64 Build/UE1A.230829.050; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/113.0.5672.136 Mobile Safari/537.36';
function hints(version, webview) {
  return { brands: [{ brand: webview ? 'Android WebView' : 'Google Chrome', version: version.split('.')[0] },
      { brand: 'Chromium', version: version.split('.')[0] }, { brand: 'Not-A.Brand', version: '24' }],
    fullVersionList: [{ brand: webview ? 'Android WebView' : 'Google Chrome', version }, { brand: 'Chromium', version },
      { brand: 'Not-A.Brand', version: '24.0.0.0' }],
    fullVersion: version, platform: 'Android', platformVersion: '14.0.0', architecture: 'x86', bitness: '64',
    model: 'sdk_gphone64_x86_64', mobile: true };
}
(async () => {
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true });
  const modern = browser.version();
  const mobile = version => 'Mozilla/5.0 (Linux; Android 14; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/' + version + ' Mobile Safari/537.36';
  const profiles = [
    { name: 'real-desktop-chrome-control' },
    { name: 'webview113-ua-only', ua: nativeUA },
    { name: 'webview113-with-webview-client-hints', ua: nativeUA, hints: hints('113.0.5672.136', true) },
    { name: 'newer-version-still-webview', ua: nativeUA.replace('113.0.5672.136', modern), hints: hints(modern, true) },
    { name: 'old-arena-stripped-wv-DIAGNOSTIC-ONLY', ua: nativeUA.replace('; wv', '') },
    { name: 'wv-kept-without-Version-token-DIAGNOSTIC-ONLY', ua: nativeUA.replace('Version/4.0 ', ''), hints: hints('113.0.5672.136', true) },
    { name: 'mobile-chrome113-control', ua: mobile('113.0.5672.136'), hints: hints('113.0.5672.136', false) },
    { name: 'mobile-modern-chrome-control', ua: mobile(modern), hints: hints(modern, false) },
    { name: 'real-desktop-chrome-without-PublicKeyCredential', disableWebAuthn: true },
    { name: 'real-desktop-chrome-repeat' },
    { name: 'webview113-native-repeat', ua: nativeUA, hints: hints('113.0.5672.136', true) },
  ];
  const results = [];
  const sources = new Map();
  for (const profile of profiles) {
    const context = await browser.newContext({ viewport: { width: 412, height: 880 }, locale: 'en-US',
      ...(profile.ua ? { userAgent: profile.ua } : {}) });
    // Even a site-triggered authorization request is out of scope for these altered-UA controls.
    await context.route('**/*', route => {
      const u = new URL(route.request().url());
      if (u.hostname === 'accounts.google.com' && /^\/(?:o\/oauth2|signin\/oauth|v3\/signin)/.test(u.pathname))
        return route.abort('blockedbyclient');
      return route.continue();
    });
    if (profile.disableWebAuthn) {
      // Remove a capability in this read-only control. Never add/fake it in the app.
      await context.addInitScript(() => Object.defineProperty(window, 'PublicKeyCredential', { value: undefined, configurable: true }));
    }
    const page = await context.newPage();
    const fragmentRequests = [];
    page.on('response', r => {
      const u = new URL(r.url());
      if (u.pathname.includes('login_fragment')) fragmentRequests.push({ url: safe(r.url()), status: r.status() });
    });
    if (profile.hints) {
      const cdp = await context.newCDPSession(page);
      await cdp.send('Network.setUserAgentOverride', { userAgent: profile.ua,
        acceptLanguage: 'en-US,en;q=0.9', platform: 'Linux armv8l', userAgentMetadata: profile.hints });
    }
    const pending = [];
    page.on('response', response => {
      const u = new URL(response.url());
      if (response.request().resourceType() !== 'script' || u.hostname !== 'github.githubassets.com') return;
      const url = safe(response.url());
      if (sources.has(url)) return;
      sources.set(url, { url, pending: true });
      pending.push((async () => {
        try {
          const text = await response.text();
          const snippets = [];
          const pattern = /social.?login|sign.?in.?with.?google|web.?view|webauthn-status|login_fragment|PublicKeyCredential|isConditionalMediationAvailable|isUserVerifyingPlatformAuthenticatorAvailable|in.?app.?browser|disallowed_useragent|google.{0,35}(?:sign|auth)|(?:sign|auth).{0,35}google/gi;
          for (const match of text.matchAll(pattern)) {
            snippets.push(text.slice(Math.max(0, match.index - 180), match.index + match[0].length + 230));
            if (snippets.length >= 8) break;
          }
          sources.set(url, { url, bytes: text.length, snippets });
        } catch { sources.set(url, { url, unavailable: true }); }
      })());
    });
    const record = { profile: profile.name, engine: modern, observedAt: new Date().toISOString(), diagnosticOnly: true };
    try {
      const response = await page.goto('https://github.com/login', { waitUntil: 'domcontentloaded', timeout: 45000 });
      record.status = response.status();
      const html = await response.text(); // inspect locally; do not persist HTML, tokens or cookies
      record.serverGooglePhrase = /(?:sign in|continue|sign up) with google/i.test(html);
      record.serverSocialForm = /<form[^>]*action=["'][^"']*(?:google|social)/i.test(html);
      const headers = await response.request().allHeaders();
      record.identificationHeaders = Object.fromEntries(Object.entries(headers)
        .filter(([k]) => k === 'user-agent' || k.startsWith('sec-ch-ua')));
      await page.waitForTimeout(3500);
      record.dom = await page.evaluate(() => {
        const controls = [...document.querySelectorAll('button,a,input[type=submit]')].map(e => ({
          text: (e.getAttribute('aria-label') || e.innerText || e.value || e.textContent || '').replace(/\s+/g, ' ').trim(),
          visible: e.getClientRects().length > 0 && getComputedStyle(e).visibility !== 'hidden',
        })).filter(c => /google|apple/i.test(c.text));
        let flags = [];
        try { const data = JSON.parse(document.getElementById('client-env')?.textContent || '{}');
          flags = (data.featureFlags || []).filter(f => /auth|google|social|login|in.?app/i.test(f)); } catch { }
        return { title: document.title, controls, formActions: [...document.forms].map(f => {
            const u = new URL(f.action); return u.origin + u.pathname;
          }), ua: navigator.userAgent, hints: navigator.userAgentData?.toJSON(),
          fragments: [...document.querySelectorAll('webauthn-status, include-fragment')].map(e => ({ tag:e.tagName,
            src:e.getAttribute('src')?.split('?')[0], dataSrc:e.getAttribute('data-src')?.split('?')[0],
            credentialsAttribute:e.getAttribute('data-target'), text:e.innerText.slice(0,120) })),
          webauthnElementDefined: !!customElements.get('webauthn-status'),
          secureContext: isSecureContext, credentialsApi: !!navigator.credentials,
          publicKeyCredential: !!window.PublicKeyCredential, federatedCredential: !!window.FederatedCredential,
          flags, scripts: [...document.scripts].filter(s => s.src).map(s => {
            const u = new URL(s.src); return u.origin + u.pathname;
          }) };
      });
      record.googleCount = record.dom.controls.filter(c => c.visible && /google/i.test(c.text)).length;
      await page.screenshot({ path: path.join(out, profile.name + '.png') });
    } catch (e) { record.error = e.name; }
    await Promise.allSettled(pending);
    record.fragmentRequests = fragmentRequests;
    results.push(record);
    fs.writeFileSync(path.join(out, profile.name + '.json'), JSON.stringify(record, null, 2));
    const summary = { profile: record.profile, status: record.status, engine: modern,
      serverGooglePhrase: record.serverGooglePhrase, serverSocialForm: record.serverSocialForm,
      googleCount: record.googleCount, controls: record.dom?.controls, formActions: record.dom?.formActions,
      publicKeyCredential: record.dom?.publicKeyCredential, fragments: record.dom?.fragments, fragmentRequests,
      hints: record.dom?.hints, identificationHeaders: record.identificationHeaders, error: record.error };
    console.log('::notice title=Sign-in diagnosis ' + profile.name + '::' + escape(JSON.stringify(summary)));
    await context.close();
  }
  const relevant = [...sources.values()].filter(s => s.snippets?.length);
  fs.writeFileSync(path.join(out, 'summary.json'), JSON.stringify(results, null, 2));
  fs.writeFileSync(path.join(out, 'public-script-snippets.json'), JSON.stringify(relevant, null, 2));
  for (const source of relevant.slice(0, 10)) {
    console.log('::notice title=Public sign-in script::' + escape(JSON.stringify(source).slice(0, 4500)));
  }
  await browser.close();
})().catch(e => { console.error(e.name + ': diagnostic failed'); process.exitCode = 1; });
