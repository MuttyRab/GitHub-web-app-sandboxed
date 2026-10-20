package app.githubwebview.locked.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import app.githubwebview.locked.MainActivity;
import app.githubwebview.locked.policy.GitHubAllowlist;
import app.githubwebview.locked.policy.PolicyMetrics;
import app.githubwebview.locked.web.EgressGuard;

/**
 * End-to-end proof that a download is fetched <b>by the app</b>, through the app's own egress guard, and
 * lands in the system Downloads collection - with no download manager, no browser and no storage
 * permission.
 *
 * <p>The file is a small public text file from this repository's own {@code main} branch, fetched from
 * {@code raw.githubusercontent.com} (an allow-listed resource host). The guard only runs inside the
 * activity, so the activity is launched first and the test waits for the guard to report ready; if it
 * never does (an emulator without {@code PROXY_OVERRIDE}), the live case is skipped rather than failed,
 * exactly like the other live tests in this project.</p>
 */
@RunWith(AndroidJUnit4.class)
public class FileDownloadTest {

    private static final String SOURCE_URL =
            "https://raw.githubusercontent.com/markcollege2026-droid/GitHub-web-app-sandboxed/main/README.md";
    private static final long GUARD_TIMEOUT_MS = 45_000L;
    private static final long DOWNLOAD_TIMEOUT_MS = 90_000L;

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static final class WaitingListener implements FileDownloads.Listener {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> savedName = new AtomicReference<>();
        final AtomicReference<String> savedLocation = new AtomicReference<>();
        final AtomicReference<String> failure = new AtomicReference<>();
        final AtomicReference<String> refused = new AtomicReference<>();
        final AtomicReference<String> cancellation = new AtomicReference<>();
        final AtomicLong bytes = new AtomicLong(-1L);
        volatile boolean started;

        @Override
        public void onDownloadRefused(String url, String host, String reason) {
            refused.set(reason);
            done.countDown();
        }

        @Override
        public void onDownloadStarted(String fileName, long totalBytes) {
            started = true;
        }

        @Override
        public void onDownloadProgress(String fileName, long bytesRead, long totalBytes) {
            bytes.set(bytesRead);
        }

        @Override
        public void onDownloadSaved(String fileName, String location) {
            savedName.set(fileName);
            savedLocation.set(location);
            done.countDown();
        }

        @Override
        public void onDownloadFailed(String fileName, String reason) {
            failure.set(reason);
            done.countDown();
        }

        @Override
        public void onDownloadCancelled(String fileName) {
            cancellation.set(fileName);
            done.countDown();
        }
    }

    /** The guard is process-wide; wait until the activity has installed it. */
    private static boolean awaitGuard(ActivityScenario<MainActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + GUARD_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (EgressGuard.port() > 0) {
                return true;
            }
            SystemClock.sleep(250L);
        }
        return false;
    }

    @Test
    public void approvedFileIsSavedIntoTheDownloadsCollection() throws Exception {
        Assume.assumeTrue("MediaStore Downloads needs Android 10+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Assume.assumeTrue("the egress guard never became ready on this device", awaitGuard(scenario));

            WaitingListener listener = new WaitingListener();
            FileDownloads downloads = new FileDownloads(context(), GitHubAllowlist.productionConfig(),
                    PolicyMetrics.get(), new Handler(Looper.getMainLooper()), listener);

            assertTrue("the download must be accepted", downloads.download(SOURCE_URL, null, "text/plain", -1L));
            assertTrue("the transfer must finish", listener.done.await(DOWNLOAD_TIMEOUT_MS, TimeUnit.SECONDS));

            assertNull("nothing may be refused, but: " + listener.refused.get(), listener.refused.get());
            assertNull("nothing may fail, but: " + listener.failure.get(), listener.failure.get());
            assertEquals("README.md", listener.savedName.get());
            assertNotNull(listener.savedLocation.get());
            assertTrue("bytes must have arrived", listener.bytes.get() > 0L);

            Resume resume = findSaved(listener.savedName.get());
            try {
                assertNotNull("the file must exist in the Downloads collection", resume);
                assertTrue("the saved file must not be empty", resume.size > 0L);
            } finally {
                if (resume != null) {
                    context().getContentResolver().delete(resume.uri, null, null);
                }
            }
        }
    }

    private static final class Resume {
        final Uri uri;
        final long size;

        Resume(Uri uri, long size) {
            this.uri = uri;
            this.size = size;
        }
    }

    /** Newest completed row with this display name, or null. */
    private static Resume findSaved(String displayName) {
        ContentResolver resolver = context().getContentResolver();
        String[] projection = {MediaStore.Downloads._ID, MediaStore.Downloads.SIZE};
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.IS_PENDING + "=0";
        try (Cursor cursor = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection,
                new String[]{displayName}, MediaStore.Downloads.DATE_ADDED + " DESC")) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            long id = cursor.getLong(0);
            long size = cursor.getLong(1);
            return new Resume(Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, String.valueOf(id)), size);
        }
    }
}
