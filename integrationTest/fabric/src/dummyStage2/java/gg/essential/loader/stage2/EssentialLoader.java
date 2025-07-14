package gg.essential.loader.stage2;

import sun.gg.essential.LoadState;

import java.nio.file.Path;

@SuppressWarnings("unused")
public class EssentialLoader {
    public EssentialLoader(Path gameDir, String gameVersion) {
    }

    public void load() {
        LoadState.dummyStage2Loaded = true;
    }

    public void initialize() {
        LoadState.dummyStage2Initialized = true;
    }
}
