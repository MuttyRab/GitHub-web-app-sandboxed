package app.githubwebview.locked;

import static org.junit.Assert.assertTrue;

import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import app.githubwebview.locked.policy.GitHubAllowlist;

/** Signed-out, read-only site coverage. Does NOT claim edits, commits or authenticated log streaming. */
@RunWith(AndroidJUnit4.class)
public class LiveGitHubWorkflowTest {
    private static final String REPO = "/markcollege2026-droid/Arena.ai-apk";

    @Test
    public void publicRepositoryFileCommitIssuePullRequestAndActionsPagesRender() throws Exception {
        String[][] routes = {
                {"repository", REPO},
                {"file", REPO + "/blob/main/README.md"},
                {"commits", REPO + "/commits/main"},
                {"issues", REPO + "/issues"},
                {"pull-request", REPO + "/pull/2"},
                {"actions", REPO + "/actions"},
                {"completed-job", REPO + "/actions/runs/38487448876/job/115604618730"}
        };
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            for (String[] route : routes) {
                LivePageSupport.load(scenario, "https://github.com" + route[1]);
                String content = route[0].equals("actions")
                        ? "!!document.querySelector('a[href*=\"/actions/runs/\"]')"
                        : (route[0].equals("completed-job")
                                ? "document.body.innerText.includes('Instrumented tests')"
                                : "document.body.innerText.includes('Arena.ai-apk')");
                boolean rendered = LivePageSupport.waitFor(scenario,
                        "location.hostname==='github.com' && location.pathname===" + JSONObject.quote(route[1])
                                + " && document.readyState==='complete' && document.body && (" + content + ")", 45_000);
                JSONObject probe = LivePageSupport.object(scenario, """
                        (function(){return {path:location.pathname, ready:document.readyState, title:document.title,
                          bodyPreview:document.body ? document.body.innerText.substring(0,900) : null,
                          actionRunLinks:document.querySelectorAll('a[href*="/actions/runs/"]').length,
                          loadedStyles:[...document.querySelectorAll('link[rel=stylesheet]')].filter(l=>!!l.sheet).length,
                          logRows:document.querySelectorAll('.check-log-line,.check-log-text,[data-testid=log-viewer]').length,
                          signInText:/sign in.*(?:view|logs)|must be logged in/i.test(document.body.innerText),
                          resourceHosts:[...new Set(performance.getEntriesByType('resource').map(function(r){
                            try{return new URL(r.name).hostname}catch(e){return ''}
                          }))].filter(Boolean).sort()};})()
                        """);
                if (rendered) probe.remove("bodyPreview");
                // Capture the failing page BEFORE the assertion so a failed render is diagnosable.
                Log.i("GitHubWebViewLocked", "github workflow probe " + route[0] + " rendered=" + rendered + ": " + probe);
                LivePageSupport.screenshot("github-" + route[0]);
                assertTrue("signed-out GitHub route must render " + route[0] + ": " + probe, rendered);
                assertTrue("styles should not be missing on " + route[0], probe.getInt("loadedStyles") > 0);
                assertTrue(GitHubAllowlist.productionConfig().isProductNavigation(LivePageSupport.url(scenario)));
            }
        }
    }

    @Test
    public void editingAndWorkflowEditingAreAllowedRoutesButRemainAuthenticationGated() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            for (String file : new String[]{"README.md", ".github/workflows/android.yml"}) {
                String target = "https://github.com" + REPO + "/edit/main/" + file;
                assertTrue(GitHubAllowlist.productionConfig().evaluateNavigation(target).isAllowed());
                LivePageSupport.load(scenario, target);
                assertTrue("editing must use GitHub's real login, not leave the app or show an invented editor",
                        LivePageSupport.waitFor(scenario,
                                "location.hostname==='github.com' && location.pathname==='/login'"
                                        + " && !!document.querySelector('#login_field')", 45_000));
                Log.i("GitHubWebViewLocked", "github workflow probe protected edit " + file
                        + ": real GitHub login required; no edit/commit attempted");
            }
            LivePageSupport.screenshot("github-protected-edit-login");
        }
    }
}
