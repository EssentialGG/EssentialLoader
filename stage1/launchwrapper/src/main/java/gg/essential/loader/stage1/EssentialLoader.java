package gg.essential.loader.stage1;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
        super("launchwrapper", gameVersion);
    }

    @Override
    protected ClassLoader addToClassLoader(URL stage2Url) throws Exception {
        // Add stage2 file to launch class loader (with an exception) and its parent (which will end up load it)
        LaunchClassLoader classLoader = Launch.classLoader;
        classLoader.addURL(stage2Url);
        classLoader.addClassLoaderExclusion(STAGE2_PKG);
        addUrlHack(classLoader.getClass().getClassLoader(), stage2Url);
        return classLoader;
    }

    private static void addUrlHack(ClassLoader loader, URL url) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // This breaks if the parent class loader is not a URLClassLoader, but so does Forge, so we should be fine.
        final ClassLoader classLoader = Launch.classLoader.getClass().getClassLoader();
        final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        method.setAccessible(true);
        method.invoke(classLoader, url);
    }
}