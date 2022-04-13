package gg.essential.loader.stage2.jvm;

import gg.essential.loader.stage2.LoaderUI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public abstract class ForkedJvmLoaderUI implements LoaderUI {
    private final Logger LOGGER = LogManager.getLogger(getClass());
    private ForkedJvm jvm;
    private DataOutputStream out;

    @Override
    public void start() {
        try {
            this.jvm = new ForkedJvm(getClass());
            this.out = new DataOutputStream(this.jvm.process.getOutputStream());
        } catch (IOException e) {
            LOGGER.warn("Failed to fork JVM for loader UI:", e);
        }
    }

    @Override
    public void complete() {
        if (this.jvm == null) return;
        try {
            this.out.write(0);
            this.out.flush();
        } catch (IOException e) {
            LOGGER.warn("Failed to run `complete()` for forked JVM UI:", e);
        }

        ForkedJvm jvm = this.jvm;
        new Thread(() -> {
            try {
                jvm.process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}

            jvm.close();
        }, "forked-jvm-cleanup").start();

        this.jvm = null;
    }

    @Override
    public void setDownloadSize(int bytes) {
        if (this.jvm == null) return;
        try {
            this.out.write(1);
            this.out.writeInt(bytes);
            this.out.flush();
        } catch (IOException e) {
            LOGGER.warn("Failed to run `setDownloadSize(" + bytes + ")` for forked JVM UI:", e);
            this.jvm.close();
            this.jvm = null;
        }
    }

    @Override
    public void setDownloaded(int bytes) {
        if (this.jvm == null) return;
        try {
            this.out.write(2);
            this.out.writeInt(bytes);
            this.out.flush();
        } catch (IOException e) {
            LOGGER.warn("Failed to run `setDownloaded(" + bytes + ")` for forked JVM UI:", e);
            this.jvm.close();
            this.jvm = null;
        }
    }

    protected static void forked(LoaderUI loaderUI) throws IOException {
        loaderUI.start();

        DataInputStream in = new DataInputStream(System.in);
        while (true) {
            switch (in.read()) {
                case -1:
                case 0:
                    loaderUI.complete();
                    return;
                case 1:
                    loaderUI.setDownloadSize(in.readInt());
                    break;
                case 2:
                    loaderUI.setDownloaded(in.readInt());
                    break;
            }
        }
    }
}
