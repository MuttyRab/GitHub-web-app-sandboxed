package app.githubwebview.locked.net;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Locale;

import app.githubwebview.locked.policy.BlockReason;
import app.githubwebview.locked.policy.PolicyConfig;
import app.githubwebview.locked.policy.PolicyResult;

/**
 * The rules for saving a file, with no Android dependency: policy pre-flight, redirect budget, size
 * limit and file naming.
 *
 * <p><b>Why this is a separate class.</b> Everything here is pure string/limit arithmetic, so the JVM
 * unit tests can exhaustively check the hostile cases (traversal names, CR/LF header injection, control
 * characters, reserved characters, overlong names, a redirect into an unapproved host) without an
 * emulator. {@link FileDownloads} then only does the parts that need a device: the connection, the
 * byte copy and the storage.</p>
 *
 * <p><b>What a download is allowed to be.</b> A download is a <em>request</em>, not a page: the target
 * must be {@code https://} on an exact allow-listed host, which is the same question the policy already
 * answers for page resources ({@link PolicyConfig#evaluateRequest(String)}). That is deliberately the
 * only widening this feature makes - {@code raw.githubusercontent.com},
 * {@code objects.githubusercontent.com}, {@code codeload.github.com} and the release-asset hosts are
 * resource hosts, and saving their bytes is the reason those bytes were fetched in the first place.
 * Nothing becomes navigable: an asset host still can never become the visible page.</p>
 *
 * <p>{@code blob:}, {@code data:} and {@code about:} URLs are refused here. They are minted inside the
 * page and carry no host the app could fetch; saving them would need a JavaScript bridge, which this
 * project does not have and will not add.</p>
 */
public final class DownloadPlan {

    /** Hard ceiling for one saved file. Bounds storage, memory and a runaway server. */
    public static final long MAX_BYTES = 1024L * 1024L * 1024L;

    /** Redirect hops re-checked against the policy before the bytes are fetched. */
    public static final int MAX_REDIRECTS = 5;

    /** Longest file name kept; long enough for repository archives, short enough for every file system. */
    public static final int MAX_NAME_LENGTH = 120;

    /** Used when neither the header nor the URL yields a usable name. */
    public static final String FALLBACK_NAME = "github-download";

    /** Why a download was not started. All of these are fail-closed. */
    public enum Refusal {
        NONE(BlockReason.CAPABILITY_DISABLED),
        MISSING_URL(BlockReason.EMPTY_URL),
        NOT_HTTPS(BlockReason.SCHEME_NOT_HTTPS),
        IN_PAGE_URL(BlockReason.UNSUPPORTED_SCHEME),
        NOT_ALLOWED(BlockReason.HOST_NOT_ALLOWED),
        TOO_LARGE(BlockReason.CAPABILITY_DISABLED);

        private final BlockReason fallbackReason;

        Refusal(BlockReason fallbackReason) {
            this.fallbackReason = fallbackReason;
        }

        /** The reason recorded when the policy itself did not supply a more precise one. */
        public BlockReason blockReason() {
            return fallbackReason;
        }

        public boolean refused() {
            return this != NONE;
        }
    }

    /** An immutable decision: either a startable download, or a refusal with a reason. */
    public static final class Plan {
        private final String url;
        private final String fileName;
        private final String contentDisposition;
        private final String mimeType;
        private final String host;
        private final Refusal refusal;
        private final BlockReason reason;
        private final String detail;

        private Plan(String url, String fileName, String contentDisposition, String mimeType, String host,
                     Refusal refusal, BlockReason reason, String detail) {
            this.url = url;
            this.fileName = fileName;
            this.contentDisposition = contentDisposition;
            this.mimeType = mimeType;
            this.host = host;
            this.refusal = refusal;
            this.reason = reason;
            this.detail = detail;
        }

        public boolean isAllowed() {
            return refusal == Refusal.NONE;
        }

        public String url() {
            return url;
        }

        /** Sanitised name the file will be saved under. Never empty for an allowed plan. */
        public String fileName() {
            return fileName;
        }

        public String contentDisposition() {
            return contentDisposition;
        }

        public String mimeType() {
            return mimeType;
        }

        public String host() {
            return host;
        }

        public Refusal refusal() {
            return refusal;
        }

        /** Machine-readable reason for the diagnostics report; never null. */
        public BlockReason reason() {
            return reason == null ? Refusal.NOT_ALLOWED.blockReason() : reason;
        }

        /** A sentence for the user. */
        public String describe() {
            if (isAllowed()) {
                return "saving " + fileName;
            }
            if (Refusal.IN_PAGE_URL == refusal) {
                return "that link is generated inside the page, so the app cannot fetch it";
            }
            if (Refusal.TOO_LARGE == refusal) {
                return "that file is larger than the " + (MAX_BYTES / (1024L * 1024L)) + " MB limit";
            }
            if (detail != null && !detail.isEmpty()) {
                return detail;
            }
            return refusal.blockReason().humanMessage();
        }

        private Plan asRefused(Refusal newRefusal, BlockReason newReason, String newDetail) {
            return new Plan(url, fileName, contentDisposition, mimeType, host, newRefusal, newReason, newDetail);
        }

        @Override
        public String toString() {
            return isAllowed() ? "allow download " + host + " " + fileName : "refuse download " + refusal;
        }
    }

    private DownloadPlan() {
        // no instances
    }

    /**
     * Decide whether {@code url} may be fetched and saved, and under what name.
     *
     * @param url                exactly what the WebView reported
     * @param contentDisposition the {@code Content-Disposition} the page supplied, or null
     * @param mimeType           the MIME type the page supplied, or null
     * @param policy             the active policy (never null in the app; null is refused)
     */
    public static Plan plan(String url, String contentDisposition, String mimeType, PolicyConfig policy) {
        if (url == null || url.trim().isEmpty()) {
            return new Plan(url, "", contentDisposition, mimeType, "", Refusal.MISSING_URL,
                    BlockReason.EMPTY_URL, "the download had no address");
        }
        String candidate = url.trim();
        String host = "";
        if (!startsWithIgnoreCase(candidate, "https://")) {
            boolean mintedInPage = startsWithIgnoreCase(candidate, "blob:")
                    || startsWithIgnoreCase(candidate, "data:")
                    || startsWithIgnoreCase(candidate, "about:");
            return new Plan(candidate, "", contentDisposition, mimeType, "",
                    mintedInPage ? Refusal.IN_PAGE_URL : Refusal.NOT_HTTPS,
                    mintedInPage ? BlockReason.UNSUPPORTED_SCHEME : BlockReason.SCHEME_NOT_HTTPS,
                    mintedInPage ? "that link is generated inside the page" : "only HTTPS links can be saved");
        }
        if (policy == null) {
            return new Plan(candidate, "", contentDisposition, mimeType, "", Refusal.NOT_ALLOWED,
                    BlockReason.INVALID_HOST, "no policy configured");
        }
        PolicyResult verdict = policy.evaluateRequest(candidate);
        if (!verdict.isAllowed()) {
            // The policy's own reason is more precise than a generic refusal, so it is carried through
            // to the overlay and to the diagnostics report unchanged.
            return new Plan(candidate, "", contentDisposition, mimeType, verdict.canonicalHost(),
                    Refusal.NOT_ALLOWED, verdict.reason(), verdict.reason() == null
                    ? "that domain is not on the allow-list"
                    : verdict.reason().humanMessage());
        }
        host = verdict.canonicalHost();
        if (host == null || host.isEmpty()) {
            return new Plan(candidate, "", contentDisposition, mimeType, "", Refusal.NOT_ALLOWED,
                    BlockReason.EMPTY_HOST, "the link had no host");
        }
        return new Plan(candidate, fileName(contentDisposition, candidate, mimeType), contentDisposition,
                mimeType, host, Refusal.NONE, null, "");
    }

    /** Mark an already-validated plan as too large, keeping everything else about it. */
    public static Plan tooLarge(Plan allowed) {
        if (allowed == null || !allowed.isAllowed()) {
            return allowed;
        }
        return allowed.asRefused(Refusal.TOO_LARGE, BlockReason.CAPABILITY_DISABLED, null);
    }

    /** True when {@code bytes} exceeds the hard ceiling. A negative (unknown) length is never "too large". */
    public static boolean tooLarge(long bytes) {
        return bytes > MAX_BYTES;
    }

    /**
     * The name the file is saved under: from {@code Content-Disposition} when it has one, otherwise from
     * the last path segment, otherwise a fallback plus the extension implied by the MIME type.
     *
     * <p>Every input is hostile: the header comes from a server, and the URL comes from a page. The
     * result is a bare file name - no directory, no separators, no control characters - so it cannot
     * escape the folder it is written to, and it is never empty.</p>
     */
    public static String fileName(String contentDisposition, String url, String mimeType) {
        String fromHeader = sanitize(fileNameFromDisposition(contentDisposition));
        if (!fromHeader.isEmpty()) {
            return ensureExtension(fromHeader, mimeType);
        }
        String fromUrl = sanitize(fileNameFromUrl(url));
        if (!fromUrl.isEmpty()) {
            return ensureExtension(fromUrl, mimeType);
        }
        return FALLBACK_NAME + extensionFor(mimeType);
    }

    /** {@code filename*} (RFC 5987) first, then {@code filename="..."} or {@code filename=...}. */
    static String fileNameFromDisposition(String header) {
        if (header == null || header.isEmpty()) {
            return null;
        }
        String lower = header.toLowerCase(Locale.ROOT);
        int extended = lower.indexOf("filename*=");
        if (extended >= 0) {
            String value = header.substring(extended + "filename*=".length()).trim();
            int end = value.indexOf(';');
            if (end >= 0) {
                value = value.substring(0, end).trim();
            }
            // RFC 5987: charset'language'percent-encoded-name
            int secondQuote = value.indexOf('\'', value.indexOf('\'') + 1);
            if (value.indexOf('\'') >= 0 && secondQuote > 0 && secondQuote + 1 <= value.length()) {
                String encoded = value.substring(secondQuote + 1);
                try {
                    return URLDecoder.decode(encoded, "UTF-8");
                } catch (UnsupportedEncodingException | IllegalArgumentException e) {
                    return encoded;
                }
            }
            return value;
        }
        int plain = lower.indexOf("filename=");
        if (plain < 0) {
            return null;
        }
        String value = header.substring(plain + "filename=".length()).trim();
        int end = value.indexOf(';');
        if (end >= 0) {
            value = value.substring(0, end).trim();
        }
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\""))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    /** Last path segment of the URL, without query or fragment. */
    static String fileNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String path = url;
        int fragment = path.indexOf('#');
        if (fragment >= 0) {
            path = path.substring(0, fragment);
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int schemeEnd = path.indexOf("://");
        int searchFrom = schemeEnd < 0 ? 0 : schemeEnd + 3;
        int slash = path.lastIndexOf('/');
        if (slash < searchFrom || slash == path.length() - 1) {
            return null;
        }
        String segment = path.substring(slash + 1);
        try {
            return URLDecoder.decode(segment, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return segment;
        }
    }

    /**
     * Reduce anything to a single safe file name component.
     *
     * <p>Drops directory parts ({@code /} and {@code \}), control characters (which is what makes header
     * injection possible), the characters that are reserved on Windows/Android media storage, and
     * leading dots (hidden files / {@code ..}). Whitespace is collapsed and trailing dots and spaces are
     * removed. Returns an empty string when nothing usable is left.</p>
     */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder cleaned = new StringBuilder(name.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                continue; // control characters never survive into a file name
            }
            if (c == '<' || c == '>' || c == ':' || c == '"' || c == '|' || c == '?' || c == '*'
                    || c == '/' || c == '\\') {
                cleaned.append('_');
                lastWasSpace = false;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (!lastWasSpace && cleaned.length() > 0) {
                    cleaned.append(' ');
                }
                lastWasSpace = true;
                continue;
            }
            cleaned.append(c);
            lastWasSpace = false;
        }
        String result = cleaned.toString().trim();
        while (result.startsWith(".")) {
            result = result.substring(1);
        }
        while (result.endsWith(".") || result.endsWith(" ")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isEmpty() || ".".equals(result) || "..".equals(result)) {
            return "";
        }
        return truncate(result);
    }

    /** Keep the extension - including a compound one such as {@code .tar.gz} - when the name is shortened. */
    private static String truncate(String name) {
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        String extension = trailingExtension(name);
        int keep = Math.max(1, MAX_NAME_LENGTH - extension.length());
        return name.substring(0, keep) + extension;
    }

    /**
     * The suffix that must survive truncation: {@code .gz} when there is one, or a compound
     * {@code .tar.gz} / {@code .min.js} when both parts are short and alphanumeric.
     */
    private static String trailingExtension(String name) {
        int lastDot = name.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == name.length() - 1) {
            return "";
        }
        String tail = name.substring(lastDot);
        if (!isExtensionPart(tail)) {
            return "";
        }
        int previousDot = name.lastIndexOf('.', lastDot - 1);
        if (previousDot > 0) {
            String compound = name.substring(previousDot);
            if (compound.length() <= 16 && isExtensionPart(name.substring(previousDot, lastDot))) {
                return compound;
            }
        }
        return tail;
    }

    private static boolean isExtensionPart(String part) {
        if (part.length() < 2 || part.length() > 10 || part.charAt(0) != '.') {
            return false;
        }
        for (int i = 1; i < part.length(); i++) {
            if (!Character.isLetterOrDigit(part.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Add the MIME-derived extension when the name has none, so a saved file stays recognisable. */
    private static String ensureExtension(String name, String mimeType) {
        if (name.lastIndexOf('.') > 0) {
            return name;
        }
        return truncate(name + extensionFor(mimeType));
    }

    /** Extension implied by a MIME type, or "" when it is unknown or deliberately opaque. */
    public static String extensionFor(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        String type = mimeType;
        int semicolon = type.indexOf(';');
        if (semicolon >= 0) {
            type = type.substring(0, semicolon);
        }
        type = type.trim().toLowerCase(Locale.ROOT);
        if ("application/zip".equals(type) || "application/x-zip-compressed".equals(type)) {
            return ".zip";
        }
        if ("application/vnd.android.package-archive".equals(type)) {
            return ".apk";
        }
        if ("application/pdf".equals(type)) {
            return ".pdf";
        }
        if ("application/json".equals(type)) {
            return ".json";
        }
        if ("text/plain".equals(type)) {
            return ".txt";
        }
        if ("text/markdown".equals(type)) {
            return ".md";
        }
        if ("text/csv".equals(type)) {
            return ".csv";
        }
        if ("application/gzip".equals(type) || "application/x-gzip".equals(type)) {
            return ".tar.gz";
        }
        if ("image/png".equals(type)) {
            return ".png";
        }
        if ("image/jpeg".equals(type)) {
            return ".jpg";
        }
        if ("image/svg+xml".equals(type)) {
            return ".svg";
        }
        return "";
    }

    /** The MIME type to store: the response's own value when it has one, else the page's, else opaque. */
    public static String mimeType(String responseContentType, String pageMimeType) {
        String type = responseContentType;
        if (type == null || type.trim().isEmpty()) {
            type = pageMimeType;
        }
        if (type == null || type.trim().isEmpty()) {
            return "application/octet-stream";
        }
        type = type.trim();
        int semicolon = type.indexOf(';');
        if (semicolon >= 0) {
            type = type.substring(0, semicolon).trim();
        }
        // Only the shape "type/subtype" is stored; anything else is opaque, never interpreted.
        if (type.indexOf('/') <= 0 || type.length() > 127) {
            return "application/octet-stream";
        }
        return type;
    }

    /** True for the HTTP status codes redirects use. */
    public static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307
                || statusCode == 308;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
