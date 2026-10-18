package app.githubwebview.locked.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Verifies the URL policy against the case table in {@link PolicyCases}.
 *
 * <p>Every row is a claim about behaviour, so this test is the evidence for statements like "redirects
 * to a third party are refused" or "a Cyrillic 'а' look-alike is refused": if the implementation and the
 * table ever disagree, the build fails.</p>
 *
 * <p>The table covered by the shipping policy is {@link PolicyCases#all()} plus - because this build
 * enables them - {@link PolicyCases#withGoogleSignInAndCaptchaEnabled()}.</p>
 */
@RunWith(Parameterized.class)
public class UrlPolicyTest {

    private static final PolicyConfig PRODUCTION = GitHubAllowlist.productionConfig();

    private final PolicyCases.Case testCase;

    public UrlPolicyTest(PolicyCases.Case testCase) {
        this.testCase = testCase;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        List<Object[]> rows = new ArrayList<Object[]>();
        for (PolicyCases.Case testCase : PolicyCases.all()) {
            rows.add(new Object[]{testCase});
        }
        if (GitHubAllowlist.ENABLE_GOOGLE_SIGN_IN || GitHubAllowlist.ENABLE_CAPTCHA_WIDGETS) {
            // This build enables the Google sign-in and CAPTCHA hosts, so the rows that describe them -
            // including everything that must stay refused next to them - are part of this run.
            for (PolicyCases.Case testCase : PolicyCases.withGoogleSignInAndCaptchaEnabled()) {
                rows.add(new Object[]{testCase});
            }
        }
        return rows;
    }

    /** True when the case describes something embedded in a page rather than the app's own page. */
    private static boolean inPage(UrlPolicy.TrafficClass trafficClass) {
        return trafficClass != UrlPolicy.TrafficClass.DOCUMENT;
    }

    @Test
    public void matchesExpectedOutcome() {
        PolicyResult result = UrlPolicy.evaluate(testCase.url, PRODUCTION, testCase.trafficClass);
        assertNotNull("policy must always return a result", result);
        if (testCase.allowed) {
            assertTrue(testCase.name + ": expected ALLOW for " + testCase.url + " but got "
                    + result.describe(), result.isAllowed());
        } else {
            assertFalse(testCase.name + ": expected BLOCK for " + testCase.url
                    + " but the policy allowed it", result.isAllowed());
            assertEquals(testCase.name + ": wrong reason for " + testCase.url,
                    testCase.reason, result.reason());
        }
    }

    /** A block must always carry a reason: the app shows it and the report records it. */
    @Test
    public void everyRefusalCarriesAReason() {
        if (testCase.allowed) {
            return;
        }
        PolicyResult result = UrlPolicy.evaluate(testCase.url, PRODUCTION, testCase.trafficClass);
        assertNotNull(result.reason());
        assertNotNull(result.reason().humanMessage());
        assertNotNull(result.reason().category());
        assertFalse(result.reason().humanMessage().isEmpty());
    }

    /** Allowed navigations must report the canonical host they resolved to. */
    @Test
    public void allowedDocumentReportsItsHost() {
        if (!testCase.allowed || testCase.trafficClass != UrlPolicy.TrafficClass.DOCUMENT) {
            return;
        }
        PolicyResult result = UrlPolicy.evaluate(testCase.url, PRODUCTION, testCase.trafficClass);
        if (testCase.url != null && testCase.url.startsWith("https")) {
            assertTrue("host must be canonical and lower case: " + result.canonicalHost(),
                    result.canonicalHost().equals(result.canonicalHost().toLowerCase(java.util.Locale.ROOT)));
            assertTrue("host must be on the allow-list: " + result.canonicalHost(),
                    GitHubAllowlist.allowedHosts().contains(result.canonicalHost()));
            assertNotNull(result.matchedEntry());
        }
    }

    /**
     * The general fail-closed property, independent of the table: a hostile or unverifiable URL must
     * never be allowed, for either traffic class.
     */
    @Test
    public void hostileUrlsAreNeverAllowed() {
        String[] hostile = {
                "https://evil.example/",
                "http://github.com/",
                "//github.com/",
                "javascript:alert(1)",
                "intent://x/#Intent;end",
                "file:///data/data/app.githubwebview.locked/databases/webview.db",
                "https://github.com@evil.example/",
                "https://github.com.evil.example/",
                "https://g\u0456thub.com/",
                "https://github.com%2e.evil.example/",
                "https://evil.example\\@github.com/",
                "https://github.com:80443/",
                "https://127.0.0.1/",
                "https://[::1]/",
                "https://sub.github.com/",
                "https://mail.github.com/",
                "https://xn--rena-4qa.ai/",
                "data:text/html,<script>1</script>",
                "blob:https://evil.example/x",
        };
        for (String url : hostile) {
            for (UrlPolicy.TrafficClass trafficClass : UrlPolicy.TrafficClass.values()) {
                if (trafficClass == UrlPolicy.TrafficClass.SUBRESOURCE && url.startsWith("data:")) {
                    // data: is inert and is deliberately allowed for sub-resources; it is refused as a
                    // document, which the table asserts separately.
                    continue;
                }
                PolicyResult result = UrlPolicy.evaluate(url, PRODUCTION, trafficClass);
                assertFalse("must never be allowed (" + trafficClass + "): " + url, result.isAllowed());
            }
        }
    }

    /** A null policy must fail closed rather than throw or allow. */
    /**
     * A frame and a page are asked different questions, and the answers must stay different: whatever a
     * widget host is allowed to do as an embedded frame, the app itself is never navigated to it. This is
     * the invariant that keeps "the CAPTCHA can render" from becoming "the app can browse a third party".
     */
    @Test
    public void anEmbeddableWidgetHostIsStillNeverAReachablePage() {
        for (String widgetUrl : new String[]{
                "https://www.google.com/recaptcha/enterprise/anchor?ar=1&k=x",
                "https://github-api.arkoselabs.com/fc/gc/game/index.html",
                "https://www.gstatic.com/_/mss/boq-identity/_/js/k=boq-identity.x/y"}) {
            assertFalse("a widget frame may never replace the app's page: " + widgetUrl,
                    PRODUCTION.evaluateNavigation(widgetUrl).isAllowed());
            assertEquals("and the refusal names the reason: " + widgetUrl,
                    BlockReason.DOCUMENT_NOT_ALLOWED,
                    PRODUCTION.evaluateNavigation(widgetUrl).reason());
        }
    }

    @Test
    public void missingPolicyFailsClosed() {
        assertFalse(UrlPolicy.evaluate("https://github.com/", null, UrlPolicy.TrafficClass.DOCUMENT).isAllowed());
    }

    /** Host canonicalisation is exposed for the allow-list itself; check the strict rules directly. */
    @Test
    public void hostCanonicalisationIsStrict() {
        assertEquals("github.com", UrlPolicy.canonicalizeHost("GITHUB.COM"));
        assertEquals("www.github.com", UrlPolicy.canonicalizeHost("www.github.com"));

        String[] invalid = {
                "", ".", "..", "github", "github.", "github..com", ".github.com", "-github.com", "github.com-",
                "github.com%2e", "github_com.example", "127.0.0.1", "2130706433", "0x7f.0.0.1",
                "g\u0456thub.com", "github\u3002com", "github.com ", " github.com", "github.com:443", "[::1]",
                "github.1", "github.c",
                // A trailing dot is a different browser origin, not a spelling of the same host.
                "github.com.", "GITHUB.COM.", "www.github.com.", "github.com..",
        };
        for (String host : invalid) {
            org.junit.Assert.assertNull("must be rejected: '" + host + "'",
                    UrlPolicy.canonicalizeHost(host));
        }
        // A punycode label canonicalises (it is valid ASCII DNS), but it can never match an allow-list
        // entry, which is why it is refused by the policy rather than by the canonicaliser.
        assertEquals("xn--rena-4qa.ai", UrlPolicy.canonicalizeHost("xn--rena-4qa.ai"));
    }

    /** Sanity check that the table is actually being exercised. */
    @Test
    public void tableIsNotEmpty() {
        assertTrue(PolicyCases.all().size() > 100);
    }
}
