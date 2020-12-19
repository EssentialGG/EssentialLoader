package net.modcore.loader;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

class HttpUtils {
    public static JsonHolder fetchJson(String url) {
        return new JsonHolder(fetchString(url));
    }

    public static String fetchString(String url) {
        try {
            final HttpURLConnection connection = HttpUtils.prepareConnection(url);
            try (final InputStream is = connection.getInputStream()) {
                return IOUtils.toString(is, Charset.defaultCharset());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Failed to fetch";
    }

    public static HttpURLConnection prepareConnection(final String url) throws IOException {
        return prepareConnection(new URL(url));
    }

    public static HttpURLConnection prepareConnection(final URL url) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setUseCaches(true);
        connection.addRequestProperty("User-Agent", "Mozilla/5.0 (Sk1er ModCore Initializer)");
        connection.setReadTimeout(15000);
        connection.setConnectTimeout(15000);
        connection.setDoOutput(true);

        return connection;
    }
}
