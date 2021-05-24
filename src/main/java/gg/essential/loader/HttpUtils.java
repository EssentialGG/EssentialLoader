package gg.essential.loader;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

class HttpUtils {
    public static int failed = 0;

    public static JsonHolder fetchJson(String url) {
        return new JsonHolder(fetchString(url));
    }

    public static String fetchString(String url) {
        if (failed > 3) return new JsonHolder().put("failed", true).toString();
        try {
            final HttpURLConnection connection = HttpUtils.prepareConnection(url);
            try (final InputStream is = connection.getInputStream()) {
                return IOUtils.toString(is, Charset.defaultCharset());
            }
        } catch (Exception e) {
            e.printStackTrace();
            failed++;
        }
        return new JsonHolder().put("failed", true).toString();
    }

    public static HttpURLConnection prepareConnection(final String url) throws IOException {
        return prepareConnection(new URL(url));
    }

    public static HttpURLConnection prepareConnection(final URL url) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setUseCaches(true);
        connection.addRequestProperty("User-Agent", "Mozilla/5.0 (Essential Initializer)");
        connection.setReadTimeout(3000);
        connection.setConnectTimeout(3000);
        connection.setDoOutput(true);

        return connection;
    }
}
