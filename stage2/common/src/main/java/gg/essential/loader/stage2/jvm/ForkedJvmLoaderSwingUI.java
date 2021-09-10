package gg.essential.loader.stage2.jvm;

import gg.essential.loader.stage2.LoaderSwingUI;

import java.io.IOException;

public class ForkedJvmLoaderSwingUI extends ForkedJvmLoaderUI {
    public static void main(String[] args) throws IOException {
        forked(new LoaderSwingUI());
    }
}
