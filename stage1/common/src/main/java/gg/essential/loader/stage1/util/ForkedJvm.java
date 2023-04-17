package gg.essential.loader.stage1.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Forks a separate JVM process and executes the main method of the given class.
 * This is particularly useful on 1.14+ where AWT cannot be used because it is incompatible with LWJGL3/GLFW.
 *
 * Some care must be taken when implementing the main method because the only jar file on the classpath will be the one
 * which also contains this class.
 */
public class ForkedJvm implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger(ForkedJvm.class);

    public final Process process;

    public ForkedJvm(Class<?> main) throws IOException {
        CodeSource codeSource = getClass().getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            throw new UnsupportedOperationException("Failed to get CodeSource for Essential stage1 loader");
        }
        URL essentialJarUrl = codeSource.getLocation();
        if (essentialJarUrl == null) {
            throw new UnsupportedOperationException("Failed to get location of Essential stage1 loader jar");
        }

        // Try to convert the URL to a real path
        String classpath;
        try {
            classpath = Paths.get(essentialJarUrl.toURI()).toAbsolutePath().toString();
        } catch (URISyntaxException e) {
            throw new UnsupportedOperationException("Failed to parse " + essentialJarUrl + " as file path:", e);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(Paths.get(System.getProperty("java.home"))
            .resolve("bin")
            .resolve("java")
            .toAbsolutePath()
            .toString());
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(main.getName());

        LOGGER.debug("Starting forked JVM: " + String.join(" ", cmd));

        this.process = new ProcessBuilder(cmd)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .start();

        Thread loggerThread = new Thread(() -> {
            Logger logger = LogManager.getLogger(main);
            BufferedReader reader = new BufferedReader(new InputStreamReader(this.process.getErrorStream(), StandardCharsets.UTF_8));
            try {
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    logger.error(line);
                }
            } catch (IOException e) {
                LOGGER.warn("Error in forked jvm log forwarding:", e);
            }
        }, "ForkedJvm Log Forwarder");
        loggerThread.setDaemon(true);
        loggerThread.start();
    }

    @Override
    public void close() {
        this.process.destroy();
    }
}
