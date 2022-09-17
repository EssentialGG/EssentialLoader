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
import java.util.List;
import java.util.Locale;
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
    private static final String DEFAULT_BRANCH = "stable";
    private static final String VERSION_BASE_URL = BASE_URL + "/v1/essential:essential/versions/%s";
    private static final String VERSION_URL = VERSION_BASE_URL + "/platforms/%s";
    private static final String DOWNLOAD_URL = VERSION_URL + "/download";
    private static final String DIFF_URL = VERSION_BASE_URL + "/diff/%s/platforms/%s";
    protected static final String CLASS_NAME = "gg.essential.api.tweaker.EssentialTweaker";
    private static final String FILE_BASE_NAME = "Essential (%s)";
    private static final String FILE_EXTENSION = "jar";
    private static final boolean AUTO_UPDATE = "true".equals(System.getProperty("essential.autoUpdate", "true"));

    private final Path gameDir;
    private final String gameVersion;
    private final String apiGameVersion;
    private final String fileBaseName;
    private final LoaderUI ui;

    public EssentialLoaderBase(final Path gameDir, final String gameVersion, final boolean lwjgl3) {
        this.gameDir = gameDir;
        this.gameVersion = gameVersion;
        this.apiGameVersion = gameVersion.replace(".", "-");
        this.fileBaseName = String.format(FILE_BASE_NAME, this.gameVersion);

        String stage2Branch = System.getProperty(
            "essential.stage2.branch",
            System.getenv().getOrDefault("ESSENTIAL_STAGE2_BRANCH", "stable")
        );
        if (!stage2Branch.equals("stable")) {
            LOGGER.info("Essential Loader (stage2) branch set to \"{}\".", stage2Branch);
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        LoaderUI gui;
        if (lwjgl3 && (os.contains("mac") || os.contains("darwin"))) {
            gui = new ForkedJvmLoaderSwingUI();
        } else {
            gui = new LoaderSwingUI();
        }
        this.ui = LoaderUI.all(new LoaderLoggingUI().updatesEveryMillis(1000), gui.updatesEveryMillis(1000 / 60));
    }

    public void load() throws IOException {
        final Path dataDir = this.gameDir.resolve("essential").toRealPath();
        if (Files.notExists(dataDir)) { // check first, symlinks may exist but Java does not consider them directories
            Files.createDirectories(dataDir);
        }

        // Check if Essential is already loaded as a regular mod. If so, there's not much for us to do here.
        if (isInClassPath()) {
            if (!Boolean.getBoolean("essential.loader.relaunched")) {
                LOGGER.warn("Essential loaded as a regular mod. No automatic updates will be applied.");
            }
            loadPlatform();
            return;
        }

        Path essentialFile = findMostRecentFile(dataDir, this.fileBaseName, FILE_EXTENSION).getKey();

        boolean needUpdate = false;

        // Load current metadata from existing jar (if one exists)
        ModJarMetadata currentMeta = ModJarMetadata.EMPTY;
        if (Files.exists(essentialFile)) {
            try {
                currentMeta = ModJarMetadata.read(essentialFile);
            } catch (IOException e) {
                LOGGER.warn("Failed to read existing Essential jar metadata", e);
            }
        }
        String currentChecksum = currentMeta.getChecksum();

        if (currentChecksum == null) {
            needUpdate = true;
        }

        String branch = determineBranch();

        // Fetch latest version metadata (if required)
        ModJarMetadata latestMeta = null;
        if (needUpdate || AUTO_UPDATE) {
            latestMeta = fetchLatestVersion(ModId.ESSENTIAL, branch);
            if (latestMeta == null && needUpdate) {
                return;
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
                downloadedFile = update(essentialFile, currentMeta, latestMeta);
                downloadedFile = postProcessDownload(downloadedFile);
            } finally {
                this.ui.complete();
            }

            if (downloadedFile != null) {
                latestMeta.write(downloadedFile);

                try {
                    Files.deleteIfExists(essentialFile);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete old Essential file, will try again later.", e);
                }

                // If we succeeded in deleting that file, we might now be able to write to a lower-numbered one
                // and if not, we need to write to the next higher one.
                essentialFile = findNextMostRecentFile(dataDir, this.fileBaseName, FILE_EXTENSION);

                Files.move(downloadedFile, essentialFile);
                currentMeta = latestMeta;
            } else {
                LOGGER.warn("Unable to download Essential, please check your internet connection. If the problem persists, please contact Support.");
            }
        }

        // Check if we can continue, otherwise do not even try
        if (!Files.exists(essentialFile)) {
            return;
        }

        // Put the mod version into the system properties, so the mod can read it to know its own version
        ModVersion version = currentMeta.getVersion();
        if (version.getVersion() != null) {
            System.setProperty("essential.version", version.getVersion());
        }

        this.addToClasspath(essentialFile, this.extractJarsInJar(essentialFile));

        if (this.classpathUpdatesImmediately() && !this.isInClassPath()) {
            throw new IllegalStateException("Could not find Essential in the classpath even though we added it without errors (fileExists=" + Files.exists(essentialFile) + ").");
        }

        loadPlatform();
    }

    private String determineBranch() {
        final String DEFAULT_SOURCE = "default";
        List<Pair<String, String>> configs = Arrays.asList(
            Pair.of("property", System.getProperty("essential.branch")),
            Pair.of("environment", System.getenv().get("ESSENTIAL_BRANCH")),
            Pair.of("file", determineBranchFromFile()),
            Pair.of(DEFAULT_SOURCE, DEFAULT_BRANCH)
        );

        String resultBranch = null;
        String resultSource = null;
        for (Pair<String, String> config : configs) {
            String source = config.getKey();
            String branch = config.getValue();

            if (branch == null) {
                LOGGER.trace("Checked {} for Essential branch, was not supplied.", source);
                continue;
            }

            if (resultBranch != null) {
                if (!source.equals(DEFAULT_SOURCE)) {
                    LOGGER.warn(
                        "Essential branch supplied via {} as \"{}\" but ignored because {} is more important.",
                        source, branch, resultSource
                    );
                }
                continue;
            }

            Level level = source.equals(DEFAULT_SOURCE) ? Level.DEBUG : Level.INFO;
            LOGGER.log(level, "Essential branch set to \"{}\" via {}.", branch, source);

            resultBranch = branch;
            resultSource = source;
        }
        assert resultBranch != null;

        // Write the result back to the system property, so the mod can read it to know the branch too
        System.setProperty("essential.branch", resultBranch);

        return resultBranch;
    }

    private String determineBranchFromFile() {
        final String BRANCH_FILE_NAME = "essential_branch.txt";
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

    private Path update(Path essentialFile, ModJarMetadata currentMeta, ModJarMetadata latestMeta) throws IOException {
        // If we can, try fetching a diff first
        if (!currentMeta.getVersion().isUnknown()) {
            Path updatedFile = updateViaDiff(essentialFile, currentMeta, latestMeta);
            if (updatedFile != null) {
                return updatedFile;
            }
        }

        // Otherwise fall back to downloading the full file
        return updateViaDownload(latestMeta);
    }

    private Path updateViaDiff(Path essentialFile, ModJarMetadata currentMeta, ModJarMetadata latestMeta) throws IOException {
        FileMeta meta = fetchDiffUrl(latestMeta.getMod(), currentMeta.getVersion(), latestMeta.getVersion());
        if (meta == null) {
            return null; // no diff available
        }

        Path downloadedFile = Files.createTempFile("essential-download-", "");
        if (!downloadFile(meta.url, downloadedFile, meta.checksum)) {
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

    private Path updateViaDownload(ModJarMetadata latestMeta) throws IOException {
        FileMeta meta = fetchDownloadUrl(latestMeta.getMod(), latestMeta.getVersion());
        if (meta == null) {
            return null; // no download available, this is bad
        }

        Path downloadedFile = Files.createTempFile("essential-download-", "");
        if (!downloadFile(meta.url, downloadedFile, meta.checksum)) {
            return null; // failed to download file
        }

        return downloadedFile; // success
    }

    private JsonObject fetchJsonObject(String endpoint, boolean allowEmpty) {
        URLConnection connection = null;
        try {
            connection = this.prepareConnection(endpoint);

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

    private ModJarMetadata fetchLatestVersion(ModId modId, String branch) {
        JsonObject responseObject = fetchJsonObject(String.format(VERSION_URL, branch, this.apiGameVersion), true);

        if (responseObject == null) {
            LOGGER.warn("Essential does not support the following game version: {}", this.gameVersion);
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

        return new ModJarMetadata(modId, new ModVersion(id, version), this.apiGameVersion, checksum);
    }

    private FileMeta fetchDownloadUrl(ModId modId, ModVersion modVersion) {
        return fetchFileMeta(String.format(DOWNLOAD_URL, modVersion.getVersion(), this.apiGameVersion));
    }

    private FileMeta fetchDiffUrl(ModId modId, ModVersion oldVersion, ModVersion modVersion) {
        return fetchFileMeta(String.format(DIFF_URL, oldVersion.getVersion(), modVersion.getVersion(), this.apiGameVersion));
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

        return new FileMeta(url, checksum);
    }

    private URLConnection prepareConnection(final String url) throws IOException {
        final URLConnection urlConnection = new URL(url).openConnection();

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

    protected Path getExtractedJarsRoot() {
        return gameDir
            .resolve("essential")
            .resolve("libraries")
            .resolve(gameVersion);
    }

    private List<Path> extractJarsInJar(Path outerJar) throws IOException {
        final Path extractedJarsRoot = getExtractedJarsRoot();
        Files.createDirectories(extractedJarsRoot);

        final List<Path> extractedJars = new ArrayList<>();

        try (FileSystem fileSystem = FileSystems.newFileSystem(outerJar, (ClassLoader) null)) {
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

    protected void addToClasspath(Path mainJar, final List<Path> innerJars) {
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

    private boolean downloadFile(final String url, final Path target, String expectedHash) throws IOException {
        if (!this.attemptDownload(url, target)) {
            LOGGER.warn("Unable to download Essential, please check your internet connection. If the problem persists, please contact Support.");

            // Do not keep the file they downloaded if the download failed half way through
            Files.deleteIfExists(target);

            return false;
        }

        final String downloadedChecksum = getChecksum(target);

        if (downloadedChecksum.equals(expectedHash)) {
            return true;
        }

        LOGGER.warn(
            "Downloaded Essential file checksum did not match what we expected (downloaded={}, expected={}",
            downloadedChecksum, expectedHash
        );

        // Do not keep the file they downloaded if validation failed.
        Files.deleteIfExists(target);

        return false;
    }

    private boolean attemptDownload(final String url, final Path target) {
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
        String url;
        String checksum;

        public FileMeta(String url, String checksum) {
            this.url = url;
            this.checksum = checksum;
        }
    }
}