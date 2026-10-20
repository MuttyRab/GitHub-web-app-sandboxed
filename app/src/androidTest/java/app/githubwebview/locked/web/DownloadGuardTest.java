package app.githubwebview.locked.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import app.githubwebview.locked.net.FileDownloads;
import app.githubwebview.locked.policy.GitHubAllowlist;
import app.githubwebview.locked.policy.PolicyMetrics;

/**
 * What the download listener does with a link it must not fetch.
 *
 * <p>Downloads are saved by the app itself now (see {@code FileDownloadTest}), so the guarantee this
 * test protects is the one that must never regress: a link outside the policy is refused
 * <b>synchronously, before anything is opened</b> - no connection, no file, no other app, no platform
 * download manager - and the refusal is counted and explained.</p>
 */
@RunWith(AndroidJUnit4.class)
public class DownloadGuardTest {

    /** Records what the user would have been told. */
    private static final class RecordingListener implements FileDownloads.Listener {
        volatile String refusedUrl;
        volatile String refusedHost;
        volatile String refusedReason;
        volatile String failedReason;

        @Override
        public void onDownloadRefused(String url, String host, String reason) {
            refusedUrl = url;
            refusedHost = host;
            refusedReason = reason;
        }

        @Override
        public void onDownloadStarted(String fileName, long totalBytes) {
            // a refusal must never get here
        }

        @Override
        public void onDownloadProgress(String fileName, long bytesRead, long totalBytes) {
            // not exercised
        }

        @Override
        public void onDownloadSaved(String fileName, String location) {
            // a refusal must never get here
        }

        @Override
        public void onDownloadFailed(String fileName, String reason) {
            failedReason = reason;
        }

        @Override
        public void onDownloadCancelled(String fileName) {
            // not exercised
        }
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static FileDownloads downloads(RecordingListener listener) {
        return new FileDownloads(context(), GitHubAllowlist.productionConfig(), PolicyMetrics.get(),
                new Handler(Looper.getMainLooper()), listener);
    }

    @Test
    public void everyUnapprovedLinkIsRefusedBeforeAnyNetwork() {
        RecordingListener listener = new RecordingListener();
        FileDownloads downloads = downloads(listener);
        WebViewHardening.GuardedDownloadListener guard = new WebViewHardening.GuardedDownloadListener(downloads);
        long before = PolicyMetrics.get().count(PolicyMetrics.Counter.BLOCKED_DOWNLOADS);

        guard.onDownloadStart("https://evil.example/payload.apk", "UA", "attachment",
                "application/vnd.android.package-archive", 4096);
        guard.onDownloadStart("blob:https://github.com/8f1c", "UA", "attachment", "image/png", 2048);
        guard.onDownloadStart("http://raw.githubusercontent.com/o/r/main/x.zip", "UA", "attachment",
                "application/zip", 1024);
        guard.onDownloadStart("https://github.com.evil.example/x.zip", "UA", "attachment",
                "application/zip", 1024);

        assertEquals("every one of them must be counted as a refused download",
                before + 4, PolicyMetrics.get().count(PolicyMetrics.Counter.BLOCKED_DOWNLOADS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertNull("nothing may be saved", listener.failedReason);
        assertEquals("https://github.com.evil.example/x.zip", listener.refusedUrl);
        assertEquals("github.com.evil.example", listener.refusedHost);
        assertTrue("the reason must be readable", listener.refusedReason != null
                && listener.refusedReason.length() > 5);
    }

    /**
     * An allow-listed URL is still not fetched when the egress guard is not running: this class has no
     * direct-connection branch, so the failure is "nothing happened", never "went around the guard".
     */
    @Test
    public void anApprovedLinkFailsClosedWhenTheGuardIsNotRunning() {
        Assume.assumeTrue("the guard is installed in this process; the live test covers the happy path",
                EgressGuard.port() <= 0);
        RecordingListener listener = new RecordingListener();
        FileDownloads downloads = downloads(listener);
        WebViewHardening.GuardedDownloadListener guard = new WebViewHardening.GuardedDownloadListener(downloads);
        long blockedBefore = PolicyMetrics.get().count(PolicyMetrics.Counter.BLOCKED_DOWNLOADS);

        guard.onDownloadStart("https://raw.githubusercontent.com/o/r/main/README.md", "UA", "attachment",
                "text/plain", 128);

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertTrue("the failure must be explained, not swallowed",
                listener.failedReason != null && listener.failedReason.contains("guard"));
        assertEquals("an allowed link is not a policy refusal", blockedBefore,
                PolicyMetrics.get().count(PolicyMetrics.Counter.BLOCKED_DOWNLOADS));
        assertNull("nothing may be refused here", listener.refusedUrl);
    }
}
