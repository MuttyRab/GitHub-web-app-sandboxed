package app.githubwebview.locked;

import static org.junit.Assert.assertTrue;

import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Live smoke test: prove actual GitHub HTML, stylesheet loading and site JavaScript initialization.
 * A blank page evaluating 1+1 is NOT evidence that GitHub works. Network failure fails this test.
 */
@RunWith(AndroidJUnit4.class)
public class LiveGitHubSmokeTest {
    @Test
    public void loadsTheRealSiteAndRunsItsJavaScript() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            LivePageSupport.load(scenario, "https://github.com/"); // Explicit: Arena is now the default.
            assertTrue("real GitHub homepage must render, not just accept a requested URL",
                    LivePageSupport.waitFor(scenario,
                            "location.hostname==='github.com' && document.readyState==='complete'"
                                    + " && document.body && document.body.innerText.length>200", 45_000));
            String initialized = "['relative-time','include-fragment','auto-check','qbsearch-input','tool-tip','action-menu']"
                    + ".some(function(n){return !!customElements.get(n)})";
            assertTrue("GitHub's own JS must initialize a real site custom element",
                    LivePageSupport.waitFor(scenario, initialized, 20_000));
            JSONObject probe = LivePageSupport.object(scenario, """
                    (function(){return {
                      title:document.title, host:location.hostname, textLength:document.body.innerText.length,
                      loadedGithubStyles:[...document.querySelectorAll('link[rel=stylesheet]')]
                        .filter(function(l){return l.href.includes('github.githubassets.com') && !!l.sheet}).length,
                      initializedElements:['relative-time','include-fragment','auto-check','qbsearch-input','tool-tip','action-menu']
                        .filter(function(n){return !!customElements.get(n)}),
                      resourceHosts:[...new Set(performance.getEntriesByType('resource').map(function(r){
                        try{return new URL(r.name).hostname}catch(e){return ''}
                      }))].filter(Boolean).sort()
                    };})()
                    """);
            Log.i("GitHubWebViewLocked", "live smoke probe: " + probe);
            assertTrue("GitHub CDN styles must load", probe.getInt("loadedGithubStyles") > 0);
            assertTrue("actual GitHub title", probe.getString("title").contains("GitHub"));
            LivePageSupport.screenshot("github-homepage");
        }
    }
}
