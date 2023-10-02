package gg.essential.loader.stage2.jvm;

import gg.essential.loader.stage2.LoaderSwingUI;

import java.awt.*;
import java.io.IOException;

public class ForkedJvmLoaderSwingUI extends ForkedJvmLoaderUI {
    @Override
    public void start() {
        // Skip actual GUI for integration tests if we're in a headless environment
        if (System.getProperty("essential.integration_testing") != null && GraphicsEnvironment.isHeadless()) {
            return;
        }
        super.start();
    }

    public static void main(String[] args) throws IOException {
        forked(new LoaderSwingUI());
    }
}
