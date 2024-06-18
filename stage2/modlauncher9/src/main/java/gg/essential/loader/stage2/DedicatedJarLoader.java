package gg.essential.loader.stage2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

public class DedicatedJarLoader {
    private static final String BASE_URL = System.getProperty(
            "essential.download.url",
            System.getenv().getOrDefault("ESSENTIAL_DOWNLOAD_URL", "https://api.essential.gg/mods")
    );
    private static final String VERSION_URL = BASE_URL + "/v1/essential:essential-pinned/versions/stable/platforms/%s";
    private static final String DOWNLOAD_URL = VERSION_URL + "/download";

    protected static void downloadDedicatedJar(LoaderUI ui, Path modsDir, String gameVersion) throws IOException {
        final JsonObject meta = getEssentialDownloadMeta(gameVersion);
        final URL url = new URL(meta.get("url").getAsString());
        final URLConnection connection = url.openConnection();

        ui.setDownloadSize(connection.getContentLength());

        final String essentialVersion = getEssentialVersionMeta(gameVersion).get("version").getAsString();
        final Path target = modsDir.resolve(String.format("Essential %s (%s).jar", gameVersion, essentialVersion));

        try (
                final InputStream in = connection.getInputStream();
                final OutputStream out = Files.newOutputStream(target);
        ) {

            final byte[] buffer = new byte[1024];
            int totalRead = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalRead += read;
                ui.setDownloaded(totalRead);
            }
        }
    }

    private static JsonObject getEssentialMeta(URL url) throws IOException {
        String response;
        try (final InputStream in = url.openStream()) {
            response = new String(in.readAllBytes());
        }
        JsonElement json = new JsonParser().parse(response);
        return json.getAsJsonObject();
    }

    private static JsonObject getEssentialVersionMeta(String gameVersion) throws IOException {
        return getEssentialMeta(new URL(String.format(VERSION_URL, gameVersion)));
    }

    private static JsonObject getEssentialDownloadMeta(String gameVersion) throws IOException {
        return getEssentialMeta(new URL(String.format(DOWNLOAD_URL, gameVersion)));
    }
}
