package uk.darkbyte.deckscape;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Constructs and validates reduced-preview requests sent to the optional wsrv.nl service. */
final class PreviewUrl {
    private static final String ENDPOINT = "https://wsrv.nl/";

    private PreviewUrl() {}

    /** Returns a fixed-size JPEG preview URL for a validated repository item. */
    static String forItem(RepositorySource source, CatalogItem item) {
        try {
            String raw = source.rawUrl(item.path);
            String encoded = URLEncoder.encode(raw, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
            return ENDPOINT + "?url=" + encoded
                    + "&w=480&h=270&fit=cover&output=jpg&q=82";
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to build preview URL", exception);
        }
    }

    /** Returns whether a URL is an HTTPS request to the exact supported preview host. */
    static boolean isAllowedEndpoint(String value) {
        try {
            URI uri = URI.create(value);
            int port = uri.getPort();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "wsrv.nl".equalsIgnoreCase(uri.getHost())
                    && (port == -1 || port == 443);
        } catch (Exception ignored) {
            return false;
        }
    }
}
