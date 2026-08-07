package uk.darkbyte.horizondeck;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class PreviewUrl {
    private static final String ENDPOINT = "https://wsrv.nl/";

    private PreviewUrl() {}

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
