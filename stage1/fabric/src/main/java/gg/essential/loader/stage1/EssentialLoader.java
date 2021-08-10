package gg.essential.loader.stage1;

import java.net.URL;
import java.net.URLClassLoader;

public final class EssentialLoader extends EssentialLoaderBase {

    private static EssentialLoader instance;
    public static synchronized EssentialLoader getInstance(String gameVersion) {
        if (instance == null) {
            instance = new EssentialLoader(gameVersion);
        }
        return instance;
    }

    private EssentialLoader(final String gameVersion) {
        super("fabric", gameVersion);
    }

    @Override
    protected ClassLoader addToClassLoader(URL stage2Url) {
        // Create a class loader with which to load stage1
        return new URLClassLoader(new URL[]{ stage2Url }, getClass().getClassLoader());
    }
}