package app.githubwebview.locked.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.webkit.WebView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import app.githubwebview.locked.policy.AllowEntry;
import app.githubwebview.locked.policy.GitHubAllowlist;

/** Real Android guard callbacks, synthetic URLs: resource approval never grants a page or popup. */
@RunWith(AndroidJUnit4.class)
public class GitHubResourceRolesTest {
    @Test
    public void githubServiceResourcesAndFramesAreAllowedButNavigationAndRedirectsAreNot() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            WebView view = new WebView(InstrumentationRegistry.getInstrumentation().getTargetContext());
            RecordingGuardHost host = new RecordingGuardHost(GitHubAllowlist.productionConfig());
            GitHubWebViewClient client = new GitHubWebViewClient(host);
            try {
                for (AllowEntry entry : GitHubAllowlist.serviceEntries()) {
                    String path = entry.pathPrefixes().isEmpty() ? "/owner/repo/resource" : entry.pathPrefixes().get(0) + "resource";
                    String url = "https://" + entry.host() + path;
                    assertNull(url, client.shouldInterceptRequest(view, FakeWebResourceRequest.subresource(url)));
                    assertFalse(url, client.shouldOverrideUrlLoading(view, FakeWebResourceRequest.subframe(url)));
                    assertTrue(url, client.shouldOverrideUrlLoading(view, FakeWebResourceRequest.navigation(url)));
                    assertTrue(url, client.shouldOverrideUrlLoading(view, FakeWebResourceRequest.redirect(url)));
                    assertNotNull(url, client.shouldInterceptRequest(view, FakeWebResourceRequest.navigation(url)));
                }
            } finally { view.destroy(); }
        });
    }

    @Test
    public void githubAndWidgetResourcesCannotBecomePopupDestinations() {
        for (String url : new String[]{
                "https://github.githubassets.com/assets/app.js",
                "https://raw.githubusercontent.com/owner/repo/main/file.html",
                "https://productionresultssa0.blob.core.windows.net/actions-results/log",
                "https://octocaptcha.com/captcha",
                "https://www.gstatic.com/_/mss/boq-identity/_/js/k=opaque/asset"}) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                RecordingGuardHost host = new RecordingGuardHost(GitHubAllowlist.productionConfig());
                WebView popup = new WebView(InstrumentationRegistry.getInstrumentation().getTargetContext());
                PopupRouterClient client = new PopupRouterClient(host, popup, null);
                assertTrue(url, client.shouldOverrideUrlLoading(popup, FakeWebResourceRequest.navigation(url)));
                assertNull("no navigation routed", host.lastLoadedUrl);
                assertNull("no sign-in window promoted", host.lastSignInWindowUrl);
                assertNotNull("resource popup was refused", host.lastPopupBlocked);
                // The original router owns/destructs the rejected window; don't double-destroy it.
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }
}
