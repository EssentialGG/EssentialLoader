package gg.essential.loader.stage2.restart;

import gg.essential.loader.stage2.jvm.ForkedJvm;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ForkedNeedsRestartUI {
    private final Logger LOGGER = LogManager.getLogger();
    private final List<String> updatedModNames;
    private final List<Path> modsToDisable;
    private ForkedJvm jvm;

    public ForkedNeedsRestartUI(List<String> updatedModNames, List<Path> modsToDisable) {
        this.updatedModNames = updatedModNames;
        this.modsToDisable = modsToDisable;
    }

    public void show() {
        try {
            this.jvm = new ForkedJvm(getClass());

            DataOutputStream out = new DataOutputStream(this.jvm.process.getOutputStream());
            for (String name : this.updatedModNames) {
                out.writeBoolean(true); // signal more entries
                out.writeUTF(name);
            }
            out.writeBoolean(false); // signal end of list
            for (Path path : this.modsToDisable) {
                out.writeBoolean(true); // signal more entries
                out.writeUTF(path.toAbsolutePath().toString());
            }
            out.writeBoolean(false); // signal end of list
            out.writeBoolean(Boolean.getBoolean("essential.integration_testing"));
            out.flush();
        } catch (IOException e) {
            LOGGER.warn("Failed to fork JVM for NeedsRestartUI:", e);
        }
    }

    public void waitForClose() {
        if (this.jvm == null) return;

        try {
            //noinspection ResultOfMethodCallIgnored
            this.jvm.process.getInputStream().read();
        } catch (IOException e) {
            LOGGER.warn("Failed to wait for NeedsRestartUI to close:", e);
            this.jvm.close();
        }
    }

    public void exit() {
        if (this.jvm != null) {
            try {
                this.jvm.process.getOutputStream().close();
            } catch (IOException e) {
                LOGGER.warn("Failed to signal exit to forked NeedsRestartUI JVM:", e);
                this.jvm.close();
                this.jvm = null;
            }
        }

        if (this.jvm != null && Boolean.getBoolean("essential.integration_testing")) {
            // In integration tests we need to wait until the forked JVM is done, before we can exist and then launch
            // again.
            // This also means that they won't work on Windows, but I've got no clue how to fix that cause we can't
            // truly exit our process anyway. Just use WSL or something.
            try {
                this.jvm.process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // And finally we exit, so the forked JVM can disable the mods we need disabled
        System.exit(0);
    }

    public static void main(String[] args) throws IOException {
        DataInputStream in = new DataInputStream(System.in);

        List<String> updatedModNames = new ArrayList<>();
        while (in.readBoolean()) {
            updatedModNames.add(in.readUTF());
        }
        List<Path> modsToDisable = new ArrayList<>();
        while (in.readBoolean()) {
            modsToDisable.add(Paths.get(in.readUTF()));
        }
        boolean isIntegrationTest = in.readBoolean();

        try {
            NeedsRestartUI ui = new NeedsRestartUI(updatedModNames);
            ui.show();
            if (isIntegrationTest) {
                ui.close();
            } else {
                ui.waitForClose();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        System.out.write(0);
        System.out.flush();

        //noinspection ResultOfMethodCallIgnored
        in.read();

        disableMods(modsToDisable);
    }

    private static void disableMods(List<Path> modsToDisable) {
        boolean interrupted = false;

        for (Path path : modsToDisable) {
            int attempt = 0;
            while (true) {
                try {
                    Files.move(path, path.resolveSibling(path.getFileName() + ".disabled"));
                    break;
                } catch (IOException ioException) {
                    // wait up to one second before giving up
                    if (attempt++ < 100) {
                        try {
                            //noinspection BusyWait
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            interrupted = true;
                            break;
                        }
                    } else {
                        // we tried, give up
                        ioException.printStackTrace();
                        break;
                    }
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
