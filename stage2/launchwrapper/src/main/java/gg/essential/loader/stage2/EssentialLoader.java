package gg.essential.loader.stage2;

import gg.essential.loader.stage2.relaunch.Relaunch;
import gg.essential.loader.stage2.util.MixinTweakerInjector;
import gg.essential.loader.stage2.util.Stage0Tracker;
import net.minecraft.launchwrapper.Launch;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EssentialLoader extends EssentialLoaderBase {

    private Path ourEssentialPath;
    private URL ourEssentialUrl;
    private URL ourMixinUrl;

    public EssentialLoader(Path gameDir, String gameVersion) {
        super(gameDir, gameVersion);
    }

    @Override
    protected void loadPlatform() {
        if (ourEssentialPath == null || ourEssentialUrl == null || ourMixinUrl == null) {
            URL url = Launch.classLoader.findResource(CLASS_NAME.replace('.', '/') + ".class");
            if (url == null) {
                throw new RuntimeException("Failed to find Essential jar on classpath.");
            }
            if (!"jar".equals(url.getProtocol())) {
                throw new RuntimeException("Failed to find Essential jar on classpath, found URL with unexpected protocol: " + url);
            }
            try {
                ourEssentialUrl = new URL(url.getFile().substring(0, url.getFile().lastIndexOf('!')));
            } catch (MalformedURLException e) {
                throw new RuntimeException("Failed to find Essential jar on classpath, found URL with unexpected file: " + url, e);
            }
            try {
                ourEssentialPath = Paths.get(ourEssentialUrl.toURI());
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert Essential jar URL to Path: " + url, e);
            }
            ourMixinUrl = ourEssentialUrl;
        }

        if (Relaunch.checkEnabled()) {
            Relaunch.relaunch(ourMixinUrl);
        }

        MixinTweakerInjector.injectMixinTweaker();
    }

    @Override
    protected ClassLoader getModClassLoader() {
        // FIXME we should ideally be using the launch class loader to load our bootstrap class but currently that
        //  causes our bootstrap code to break because the launch class loader creates a separate code source for
        //  each class rather than for the whole jar.
        //  We should switch this (because it allows us to use preloadLibrary on our api package) once our bootstrap
        //  loads fine under the launch class loader (the required change has been committed to master but needs to be
        //  deployed before we can switch here).
        // return Launch.classLoader;
        return Launch.classLoader.getClass().getClassLoader();
    }

    @Override
    protected void addToClasspath(Path path) {
        URL url;
        try {
            // Add to launch class loader
            url = path.toUri().toURL();
            Launch.classLoader.addURL(url);

            // FIXME only if jar has a tweaker. and if so, we need to chain-load that tweaker; maybe also the AT?
            // And its parent (for those classes that are excluded from the launch class loader)
            final ClassLoader classLoader = Launch.classLoader.getClass().getClassLoader();
            final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(classLoader, url);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }

        // FIXME not everything that goes through here is necessarily Essential anymore
        ourEssentialPath = path;
        ourEssentialUrl = url;
        ourMixinUrl = url;
    }

    @Override
    protected void doInitialize() {
        Stage0Tracker.registerStage0Tweaker();

        super.doInitialize();
    }
}
