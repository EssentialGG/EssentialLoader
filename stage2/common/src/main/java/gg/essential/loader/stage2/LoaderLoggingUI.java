package gg.essential.loader.stage2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoaderLoggingUI implements LoaderUI {
    private static final Logger LOGGER = LogManager.getLogger(LoaderLoggingUI.class);
    private int size;

    @Override
    public void start() {
        LOGGER.info("Preparing download...");
    }

    @Override
    public void setDownloadSize(int bytes) {
        LOGGER.info("Downloading {}KB of updates...", bytes / 1024);
        this.size = bytes;
    }

    @Override
    public void setDownloaded(int bytes) {
        LOGGER.info("{}KB / {}KB ({}%)", bytes / 1024, size / 1024, bytes * 100 / size);
    }

    @Override
    public void complete() {
        LOGGER.info("End of download.");
    }
}
