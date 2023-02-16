package gg.essential.loader.stage2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import gg.essential.loader.stage2.data.ModId;
import gg.essential.loader.stage2.data.ModJarMetadata;
import gg.essential.loader.stage2.data.ModVersion;
import gg.essential.loader.stage2.diff.DiffPatcher;
import gg.essential.loader.stage2.jvm.ForkedJvmLoaderSwingUI;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static gg.essential.loader.stage2.Utils.findMostRecentFile;
import static gg.essential.loader.stage2.Utils.findNextMostRecentFile;
import static gg.essential.loader.stage2.util.Checksum.getChecksum;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public abstract class EssentialLoaderBase {

    private static final Logger LOGGER = LogManager.getLogger(EssentialLoaderBase.class);
    private static final String BASE_URL = System.getProperty(
        "essential.download.url",
        System.getenv().getOrDefault("ESSENTIAL_DOWNLOAD_URL", "https://api.essential.gg/mods")
    );
    private static final String VERSION_BASE_URL = BASE_URL + "/v1/%s/versions/%s";
    private static final String VERSION_URL = VERSION_BASE_URL + "/platforms/%s";
    private static final String DOWNLOAD_URL = VERSION_URL + "/download";
    private static final String DIFF_URL = VERSION_BASE_URL + "/diff/%s/platforms/%s";
    protected static final String CLASS_NAME = "gg.essential.api.tweaker.EssentialTweaker";
    private static final String FILE_BASE_NAME = "Essential (%s)";
    private static final String FILE_EXTENSION = "jar";

    private final Path gameDir;
    private final String gameVersion;
    private final String apiGameVersion;
    private final LoaderUI ui;

    public EssentialLoaderBase(final Path gameDir, final String gameVersion) {
        this.gameDir = gameDir;
        this.gameVersion = gameVersion;
        this.apiGameVersion = gameVersion.replace(".", "-");

        String stage2Branch = System.getProperty(
            "essential.stage2.branch",
            System.getenv().getOrDefault("ESSENTIAL_STAGE2_BRANCH", "stable")
        );
        if (!stage2Branch.equals("stable")) {
            LOGGER.info("Essential Loader (stage2) branch set to \"{}\".", stage2Branch);
        }

        this.ui = LoaderUI.all(
            new LoaderLoggingUI().updatesEveryMillis(1000),
            new ForkedJvmLoaderSwingUI().updatesEveryMillis(1000 / 60)
        );
    }

    public void load() throws IOException {
        // Check if Essential is already loaded as a regular mod. If so, there's not much for us to do here.
        if (isInClassPath()) {
            if (!Boolean.getBoolean("essential.loader.relaunched")) {
                LOGGER.warn("Essential loaded as a regular mod. No automatic updates will be applied.");
            }
            loadPlatform();
            return;
        }

        List<Mod> modList = findMods();
        Map<Mod, ModJarMetadata> loadedMods = new HashMap<>();
        for (Mod mod : modList) {
            if (Files.notExists(mod.dataDir)) { // check first, symlinks may exist but Java does not consider them directories
                Files.createDirectories(mod.dataDir);
            }

            ModJarMetadata loadedMeta = loadMod(mod);

            if (loadedMeta == null) {
                continue;
            }
            loadedMods.put(mod, loadedMeta);

            // Put the mod version into the system properties, so the mod can read it to know its own version
            ModVersion version = loadedMeta.getVersion();
            if (version.getVersion() != null) {
                System.setProperty(mod.safeSlug() + ".version", version.getVersion());
            }
        }

        if (loadedMods.keySet().stream().anyMatch(Mod::isEssential)) {
            loadPlatform();
        }
    }

    private List<Mod> findMods() {
        List<Mod> modList = new ArrayList<>();
        try {
            Enumeration<URL> urls = getClass().getClassLoader().getResources("essential-loader.properties");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                LOGGER.trace("Reading properties file at {}", url);

                Properties properties = new Properties();
                try (InputStream in = url.openStream()) {
                    properties.load(in);
                } catch (IOException e) {
                    LOGGER.warn("Failed to read properties file at `" + url + "`:", e);
                }

                Mod mod = new Mod();
                mod.id = new ModId(
                    properties.getProperty("publisherSlug"),
                    properties.getProperty("publisherId"),
                    properties.getProperty("modSlug"),
                    properties.getProperty("modId")
                );
                mod.displayName = properties.getProperty("displayName");
                mod.branch = properties.getProperty("branch", "stable");
                mod.pinnedFile = properties.getProperty("pinnedFile");
                mod.pinnedFileMd5 = properties.getProperty("pinnedFileMd5");
                mod.pinnedFileVersion = new ModVersion(
                    properties.getProperty("pinnedFileVersionId"),
                    properties.getProperty("pinnedFileVersion")
                );
                mod.autoUpdate = mod.pinnedFile == null;
                if (!mod.validate(url)) {
                    continue;
                }
                modList.add(mod);
            }
        } catch (IOException e) {
            LOGGER.error("Error looking for essential-loader property files:", e);
        }

        if (modList.stream().noneMatch(Mod::isEssential)) {
            Mod mod = new Mod();
            mod.id = ModId.ESSENTIAL;
            mod.displayName = "Essential";
            mod.autoUpdate = true;
            mod.branch = "stable";
            assert mod.validate(null);
            modList.add(mod);
        }

        Path dataDir = this.gameDir.resolve("essential");
        for (Mod mod : modList) {
            if (mod.isEssential()) {
                // For legacy reasons, Essential does not have its own folder and a different file name pattern
                mod.dataDir = dataDir;
                mod.fileBaseName = String.format(FILE_BASE_NAME, this.gameVersion);
            } else {
                mod.dataDir = dataDir.resolve("mods").resolve(mod.safeSlug());
                mod.fileBaseName = this.gameVersion;
            }

            Path configFile = mod.dataDir.resolve("essential-loader.properties");
            Properties config = new Properties();
            if (Files.exists(configFile)) {
                try (InputStream in = Files.newInputStream(configFile)) {
                    config.load(in);
                } catch (Exception e) {
                    LOGGER.error("Failed to read config at " + configFile + ":", e);
                }
            }

            String configuredBranch = config.getProperty("branch");
            mod.branch = determineBranch(mod, configuredBranch);

            String configuredAutoUpdate = System.getProperty(mod.safeSlug() + ".autoUpdate", config.getProperty("autoUpdate"));
            if (configuredAutoUpdate != null) {
                mod.autoUpdate = Boolean.parseBoolean(configuredAutoUpdate);
            }
        }

        return modList;
    }

    private ModJarMetadata loadMod(Mod mod) throws IOException {
        Path essentialFile = findMostRecentFile(mod.dataDir, mod.fileBaseName, FILE_EXTENSION).getKey();

        boolean needUpdate = false;

        // Load current metadata from existing jar (if one exists)
        ModJarMetadata currentMeta = ModJarMetadata.EMPTY;
        if (Files.exists(essentialFile)) {
            try {
                currentMeta = ModJarMetadata.read(essentialFile);
            } catch (IOException e) {
                LOGGER.warn("Failed to read existing " + mod + " jar metadata", e);
            }
        }
        String currentChecksum = currentMeta.getChecksum();

        if (currentChecksum == null) {
            needUpdate = true;
        }

        if (mod.pinnedFile != null && !mod.autoUpdate) {
            LOGGER.info("Skipping update check for {}, found pinned jar: {}", mod, mod.pinnedFile);
            needUpdate = false;

            if (!mod.pinnedFileMd5.equals(currentChecksum)) {
                URL url;
                if (mod.pinnedFile.startsWith("/")) {
                    url = getClass().getClassLoader().getResource(mod.pinnedFile.substring(1));
                    if (url == null) {
                        LOGGER.fatal("Failed to find pinned jar file at {}", mod.pinnedFile);
                        return null;
                    }
                } else {
                    url = new URL(mod.pinnedFile);
                }

                Path downloadedFile = Files.createTempFile("essential-extract-", "");
                if (!downloadFile(mod, url, downloadedFile, mod.pinnedFileMd5)) {
                    return null; // failed to download file
                }
                downloadedFile = postProcessDownload(downloadedFile);

                currentMeta = new ModJarMetadata(mod.id, mod.pinnedFileVersion, null, mod.pinnedFileMd5);
                currentMeta.write(downloadedFile);

                try {
                    Files.deleteIfExists(essentialFile);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete old " + mod + " file, will try again later.", e);
                }

                // If we succeeded in deleting that file, we might now be able to write to a lower-numbered one
                // and if not, we need to write to the next higher one.
                essentialFile = findNextMostRecentFile(mod.dataDir, mod.fileBaseName, FILE_EXTENSION);

                Files.move(downloadedFile, essentialFile);
            }
        }

        // Fetch latest version metadata (if required)
        ModJarMetadata latestMeta = null;
        if (needUpdate || mod.autoUpdate) {
            latestMeta = fetchLatestVersion(mod, mod.branch);
            if (latestMeta == null && needUpdate) {
                return null;
            }
        }

        // Check if our local version matches the latest
        if (!needUpdate && latestMeta != null && !latestMeta.getChecksum().equals(currentChecksum)) {
            needUpdate = true;
        }


        if (needUpdate) {
            Path downloadedFile;

            this.ui.start();
            try {
                downloadedFile = update(mod, essentialFile, currentMeta, latestMeta);
                if (downloadedFile != null) {
                    downloadedFile = postProcessDownload(downloadedFile);
                }
            } finally {
                this.ui.complete();
            }

            if (downloadedFile != null) {
                latestMeta.write(downloadedFile);

                try {
                    Files.deleteIfExists(essentialFile);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete old " + mod + " file, will try again later.", e);
                }

                // If we succeeded in deleting that file, we might now be able to write to a lower-numbered one
                // and if not, we need to write to the next higher one.
                essentialFile = findNextMostRecentFile(mod.dataDir, mod.fileBaseName, FILE_EXTENSION);

                Files.move(downloadedFile, essentialFile);
                currentMeta = latestMeta;
            } else {
                LOGGER.warn("Unable to download {}, please check your internet connection. If the problem persists, please contact Essential Support.", mod);
            }
        }

        // Check if we can continue, otherwise do not even try
        if (!Files.exists(essentialFile)) {
            return null;
        }

        this.addToClasspath(mod, essentialFile, this.extractJarsInJar(mod, essentialFile));

        return currentMeta;
    }

    private String determineBranch(Mod mod, String configuredBranch) {
        final String DEFAULT_SOURCE = "default";
        List<Pair<String, String>> configs = Arrays.asList(
            Pair.of("property", System.getProperty(mod.safeSlug() + ".branch")),
            Pair.of("environment", System.getenv().get(mod.safeSlug().toUpperCase(Locale.ROOT) + "_BRANCH")),
            Pair.of("config", configuredBranch),
            Pair.of("file", determineBranchFromFile(mod)),
            Pair.of(DEFAULT_SOURCE, mod.branch)
        );

        String resultBranch = null;
        String resultSource = null;
        for (Pair<String, String> config : configs) {
            String source = config.getKey();
            String branch = config.getValue();

            if (branch == null) {
                LOGGER.trace("Checked {} for {} branch, was not supplied.", source, mod);
                continue;
            }

            if (resultBranch != null) {
                if (!source.equals(DEFAULT_SOURCE)) {
                    LOGGER.warn(
                        "{} branch supplied via {} as \"{}\" but ignored because {} is more important.",
                        mod, source, branch, resultSource
                    );
                }
                continue;
            }

            Level level = source.equals(DEFAULT_SOURCE) ? Level.DEBUG : Level.INFO;
            LOGGER.log(level, "{} branch set to \"{}\" via {}.", mod, branch, source);

            resultBranch = branch;
            resultSource = source;
        }
        assert resultBranch != null;

        // Write the result back to the system property, so the mod can read it to know the branch too
        System.setProperty(mod.safeSlug() + ".branch", resultBranch);

        return resultBranch;
    }

    private String determineBranchFromFile(Mod mod) {
        final String BRANCH_FILE_NAME = mod.safeSlug() + "_branch.txt";
        try {
            Enumeration<URL> resources = getClass().getClassLoader().getResources(BRANCH_FILE_NAME);
            if (!resources.hasMoreElements()) {
                return null;
            }

            URL url = resources.nextElement();
            String branch = IOUtils.toString(url, StandardCharsets.UTF_8).trim();
            LOGGER.info("Found {} for branch \"{}\".", url, branch);

            while (resources.hasMoreElements()) {
                LOGGER.warn("Found extra branch file, ignoring: {}", resources.nextElement());
            }

            return branch;
        } catch (Exception e) {
            LOGGER.warn("Failed to check for " + BRANCH_FILE_NAME + " file on classpath:", e);
            return null;
        }
    }

    private Path update(Mod mod, Path essentialFile, ModJarMetadata currentMeta, ModJarMetadata latestMeta) throws IOException {
        // If we can, try fetching a diff first
        if (!currentMeta.getVersion().isUnknown()) {
            Path updatedFile = updateViaDiff(mod, essentialFile, currentMeta, latestMeta);
            if (updatedFile != null) {
                return updatedFile;
            }
        }

        // Otherwise fall back to downloading the full file
        return updateViaDownload(mod, latestMeta);
    }

    private Path updateViaDiff(Mod mod, Path essentialFile, ModJarMetadata currentMeta, ModJarMetadata latestMeta) throws IOException {
        FileMeta meta = fetchDiffUrl(latestMeta.getMod(), currentMeta.getVersion(), latestMeta.getVersion());
        if (meta == null) {
            return null; // no diff available
        }

        Path downloadedFile = Files.createTempFile("essential-download-", "");
        if (!downloadFile(mod, meta.url, downloadedFile, meta.checksum)) {
            return null; // failed to download diff
        }

        Path patchedFile = Files.createTempFile("essential-patched-", "");
        Files.copy(essentialFile, patchedFile, REPLACE_EXISTING);
        try {
            DiffPatcher.apply(patchedFile, downloadedFile);
            Files.delete(downloadedFile);
        } catch (Exception e) {
            LOGGER.error("Error while applying diff:", e);
            Files.deleteIfExists(patchedFile);
            Files.deleteIfExists(downloadedFile);
            return null; // failed to apply diff
        }

        return patchedFile; // success
    }

    private Path updateViaDownload(Mod mod, ModJarMetadata latestMeta) throws IOException {
        FileMeta meta = fetchDownloadUrl(latestMeta.getMod(), latestMeta.getVersion());
        if (meta == null) {
            return null; // no download available, this is bad
        }

        Path downloadedFile = Files.createTempFile("essential-download-", "");
        if (!downloadFile(mod, meta.url, downloadedFile, meta.checksum)) {
            return null; // failed to download file
        }

        return downloadedFile; // success
    }

    private JsonObject fetchJsonObject(String endpoint, boolean allowEmpty) {
        URLConnection connection = null;
        try {
            connection = this.prepareConnection(new URL(endpoint));

            String response;
            try (final InputStream inputStream = connection.getInputStream()) {
                response = IOUtils.toString(inputStream, Charset.defaultCharset());
            }

            JsonElement jsonElement = new JsonParser().parse(response);
            if (!jsonElement.isJsonObject()) {
                if (allowEmpty && jsonElement.isJsonNull()) {
                    return new JsonObject();
                } else {
                    throw new IOException("Excepted json object, got " + response);
                }
            }
            return jsonElement.getAsJsonObject();
        } catch (final IOException | JsonParseException e) {
            LOGGER.error("Error occurred fetching " + endpoint + ": ", e);
            logConnectionInfoOnError(connection);
            return null;
        }
    }

    private ModJarMetadata fetchLatestVersion(Mod mod, String branch) {
        JsonObject responseObject = fetchJsonObject(String.format(VERSION_URL, mod.id.getFullSlug(), branch, this.apiGameVersion), true);

        if (responseObject == null) {
            LOGGER.warn("{} does not support the following game version: {}", mod, this.gameVersion);
            return null;
        }

        JsonElement jsonId = responseObject.get("id");
        JsonElement jsonVersion = responseObject.get("version");
        JsonElement jsonChecksum = responseObject.get("checksum");
        String id = jsonId != null && jsonId.isJsonPrimitive() ? jsonId.getAsString() : null;
        String version = jsonVersion != null && jsonVersion.isJsonPrimitive() ? jsonVersion.getAsString() : null;
        String checksum = jsonChecksum != null && jsonChecksum.isJsonPrimitive() ? jsonChecksum.getAsString() : null;

        if (StringUtils.isEmpty(id) || StringUtils.isEmpty(version)) {
            LOGGER.warn("Unexpected response object data (id={}, version={}, checksum={})", jsonId, jsonVersion, jsonChecksum);
            return null;
        }

        return new ModJarMetadata(mod.id, new ModVersion(id, version), this.apiGameVersion, checksum);
    }

    private FileMeta fetchDownloadUrl(ModId modId, ModVersion modVersion) {
        return fetchFileMeta(String.format(DOWNLOAD_URL, modId.getFullSlug(), modVersion.getVersion(), this.apiGameVersion));
    }

    private FileMeta fetchDiffUrl(ModId modId, ModVersion oldVersion, ModVersion modVersion) {
        return fetchFileMeta(String.format(DIFF_URL, modId.getFullSlug(), oldVersion.getVersion(), modVersion.getVersion(), this.apiGameVersion));
    }

    private FileMeta fetchFileMeta(String endpoint) {
        JsonObject responseObject = fetchJsonObject(endpoint, false);

        if (responseObject == null) {
            return null;
        }

        final JsonElement
            jsonUrl = responseObject.get("url"),
            jsonChecksum = responseObject.get("checksum");
        final String
            url = jsonUrl != null && jsonUrl.isJsonPrimitive() ? jsonUrl.getAsString() : null,
            checksum = jsonChecksum != null && jsonChecksum.isJsonPrimitive() ? responseObject.get("checksum").getAsString() : null;

        if (StringUtils.isEmpty(url) || StringUtils.isEmpty(checksum)) {
            LOGGER.warn("Unexpected response object data (url={}, checksum={})", jsonUrl, jsonChecksum);
            return null;
        }

        try {
            return new FileMeta(new URL(url), checksum);
        } catch (MalformedURLException e) {
            LOGGER.error("Received invalid url `" + url + "`:", e);
            return null;
        }
    }

    private URLConnection prepareConnection(final URL url) throws IOException {
        final URLConnection urlConnection = url.openConnection();

        if (urlConnection instanceof HttpURLConnection) {
            final HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;

            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setUseCaches(true);
            httpURLConnection.setConnectTimeout(30_000);
            httpURLConnection.setReadTimeout(30_000);
            httpURLConnection.setDoOutput(true);

            httpURLConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (Essential Initializer)");
        }

        return urlConnection;
    }

    protected Path postProcessDownload(Path downloadedFile) {
        return downloadedFile;
    }

    protected Path getExtractedJarsRoot(Mod mod) {
        return mod.dataDir
            .resolve("libraries")
            .resolve(gameVersion);
    }

    private List<Path> extractJarsInJar(Mod mod, Path outerJar) throws IOException {
        final Path extractedJarsRoot = getExtractedJarsRoot(mod);
        Files.createDirectories(extractedJarsRoot);

        final List<Path> extractedJars = new ArrayList<>();

        try (FileSystem fileSystem = FileSystems.newFileSystem(outerJar, (ClassLoader) null)) {
            // FIXME: For third-party mods we must not simply extract everything in this directory, fabric's JiJ may or
            //        may not use it as well and we should be handling both cases correctly
            final Path innerJarsRoot = fileSystem.getPath("META-INF", "jars");
            if (!Files.isDirectory(innerJarsRoot)) {
                return extractedJars;
            }
            List<Path> innerJars;
            try (Stream<Path> stream = Files.list(innerJarsRoot)) {
                innerJars = stream.collect(Collectors.toList());
            }
            for (Path innerJar : innerJars) {
                // For now, we'll assume that the file name is sufficiently unique of an identifier
                final Path extractedJar = extractedJarsRoot.resolve(innerJar.getFileName().toString());
                if (Files.exists(extractedJar)) {
                    LOGGER.debug("Already extracted: {}", innerJar);
                } else {
                    LOGGER.debug("Extracting {} to {}", innerJar, extractedJar);
                    // Copy to tmp jar first, so we do not leave behind incomplete jars
                    final Path tmpJar = Files.createTempFile(extractedJarsRoot, "tmp", ".jar");
                    Files.copy(innerJar, tmpJar, REPLACE_EXISTING);
                    // Then (if successful) perform an atomic rename
                    Files.move(tmpJar, extractedJar, StandardCopyOption.ATOMIC_MOVE);
                }
                // Store the extracted path for later
                extractedJars.add(extractedJar);
            }
        }

        return extractedJars;
    }

    protected abstract void loadPlatform();

    @Nullable
    protected abstract ClassLoader getModClassLoader();

    protected void addToClasspath(Mod mod, Path mainJar, final List<Path> innerJars) {
        this.addToClasspath(mainJar);
        for (final Path jar : innerJars) {
            this.addToClasspath(jar);
        }
    }

    protected abstract void addToClasspath(final Path path);

    /**
     * Whether {@link #addToClasspath(Path)} takes effect immediately or only at a later stage of loading.
     */
    protected boolean classpathUpdatesImmediately() {
        return true;
    }

    protected boolean isInClassPath() {
        ClassLoader loader = this.getModClassLoader();
        if (loader == null) {
            return false;
        }
        return loader.getResource(CLASS_NAME.replace('.', '/') + ".class") != null;
    }

    public final void initialize() {
        if (!isInClassPath()) {
            return;
        }
        doInitialize();
    }

    protected void doInitialize() {
        try {
            ClassLoader loader = getModClassLoader();
            if (loader == null) {
                throw new IllegalStateException("Essential is about to be initialized but no associated class loader was found.");
            }
            Class.forName(CLASS_NAME, false, loader)
                .getDeclaredMethod("initialize", File.class)
                .invoke(null, gameDir.toFile());
        } catch (Throwable e) {
            throw new RuntimeException("Unexpected error", e);
        }
    }

    public static URI asJar(URI uri) throws URISyntaxException {
        return new URI("jar:" + uri.getScheme(), uri.getHost(), uri.getPath(), uri.getFragment());
    }

    private boolean downloadFile(final Mod mod, final URL url, final Path target, String expectedHash) throws IOException {
        if (!this.attemptDownload(url, target)) {
            LOGGER.warn("Unable to download {}, please check your internet connection. If the problem persists, please contact Essential Support.", mod);

            // Do not keep the file they downloaded if the download failed half way through
            Files.deleteIfExists(target);

            return false;
        }

        final String downloadedChecksum = getChecksum(target);

        if (downloadedChecksum.equals(expectedHash)) {
            return true;
        }

        LOGGER.warn(
            "Downloaded {} file checksum did not match what we expected (downloaded={}, expected={}",
            mod, downloadedChecksum, expectedHash
        );

        // Do not keep the file they downloaded if validation failed.
        Files.deleteIfExists(target);

        return false;
    }

    private boolean attemptDownload(final URL url, final Path target) {
        URLConnection connection = null;
        try {
            connection = this.prepareConnection(url);
            final int contentLength = connection.getContentLength();
            this.ui.setDownloadSize(contentLength);

            final long startTime = System.nanoTime();

            int totalRead = 0;
            try (
                final InputStream inputStream = connection.getInputStream();
                final OutputStream fileOutputStream = Files.newOutputStream(target)
            ) {
                final byte[] buffer = new byte[1024];

                int read;
                while ((read = inputStream.read(buffer)) > 0) {
                    fileOutputStream.write(buffer, 0, read);
                    totalRead += read;
                    this.ui.setDownloaded(totalRead);
                }

                long endTime = System.nanoTime();
                long millis = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
                System.setProperty("essential.stage2.downloaded.bytes", String.valueOf(contentLength));
                System.setProperty("essential.stage2.downloaded.millis", String.valueOf(millis));

                return true;
            }
        } catch (final IOException e) {
            LOGGER.error("Error occurred when downloading file '{}'.", url, e);
            logConnectionInfoOnError(connection);
            return false;
        }
    }

    private void logConnectionInfoOnError(URLConnection connection) {
        if (connection == null) {
            return;
        }
        LOGGER.error("url: {}", connection.getURL());
        LOGGER.error("cf-ray: {}", connection.getHeaderField("cf-ray"));
    }

    private static class FileMeta {
        URL url;
        String checksum;

        public FileMeta(URL url, String checksum) {
            this.url = url;
            this.checksum = checksum;
        }
    }

    public static class Mod {
        // Configurable via essential-loader.properties
        ModId id;
        String displayName;
        String pinnedFile;
        String pinnedFileMd5;
        ModVersion pinnedFileVersion;
        String branch;
        boolean autoUpdate;

        // Internal to stage2
        Path dataDir;
        String fileBaseName;

        String slug() {
            String publisherSlug = id.getPublisherSlug();
            String modSlug = id.getModSlug();
            return Objects.equals(publisherSlug, modSlug) ? modSlug : publisherSlug + ":" + modSlug;
        }

        String safeSlug() {
            // Windows doesn't allow `:` in file names.
            return slug().replace(":", "_");
        }

        boolean isEssential() {
            return slug().equals("essential");
        }

        boolean validate(URL source) {
            return Stream.of(
                validateNotNull(source, "publisherSlug", id.getPublisherSlug()),
                validateNotNull(source, "modSlug", id.getModSlug()),
                validateNotNull(source, "displayName", displayName),
                pinnedFile == null || validateNotNull(source, "pinnedFileMd5", pinnedFileMd5)
            ).allMatch(it -> it);
        }

        private boolean validateNotNull(URL source, String name, Object value) {
            if (value != null) {
                return true;
            }
            LOGGER.error("Mod configuration at {} is missing `{}` entry!", source, name);
            return false;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}