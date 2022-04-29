package gg.essential.loader.stage1;

import java.net.URL;
import java.net.URLClassLoader;

public final class EssentialLoader extends EssentialLoaderBase {

    private static EssentialLoader instance;
    public static synchronized EssentialLoader getInstance(String variant, String gameVersion) {
        if (instance == null) {
            instance = new EssentialLoader(variant, gameVersion);
        }
        return instance;
    }

    private EssentialLoader(final String variant, final String gameVersion) {
        super(variant, gameVersion);
    }

    @Override
    protected ClassLoader addToClassLoader(URL stage2Url) {
        // Create a class loader with which to load stage2
        return new URLClassLoader(new URL[]{ stage2Url }, getClass().getClassLoader());
    }
}
