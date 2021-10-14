package gg.essential.loader.stage2.relaunch;

import com.google.common.io.ByteStreams;
import gg.essential.loader.stage2.utils.Versions;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.function.BiFunction;
import java.util.jar.Manifest;

class RelaunchClassLoader extends IsolatedClassLoader {
    static { registerAsParallelCapable(); }

    private final BiFunction<String, byte[], byte[]> transformer;

    public RelaunchClassLoader(URL[] urls, ClassLoader parent, URL mixinUrl) throws ReflectiveOperationException {
        super(urls, parent);

        String mixinVersion = Versions.getMixinVersion(mixinUrl);
        if (mixinVersion == null || mixinVersion.startsWith("0.7.")) {
            this.transformer = new LegacyRelaunchTransformer();
        } else {
            // We spin up our own "meta" Mixin instance, able to transform anything which would normally be loaded on
            // the system class loader (but with relaunch is loaded in this RelaunchClassLoader). This allows us to
            // effectively mixin into everything from coremods to FMLTweaker, even Launch and (the inner, normal) Mixin.
            IsolatedClassLoader mixinLoader = new IsolatedClassLoader(urls, parent);
            // Add our Mixin services. These are stored separately to prevent them being loaded by the regular Mixin.
            mixinLoader.addURL(getClass().getResource("mixin-services/"));
            // Boot our mixin services in the newly created, dedicated class loader.
            //noinspection unchecked
            this.transformer = (BiFunction<String, byte[], byte[]>) mixinLoader
                .loadClass("gg.essential.loader.stage2.relaunch.mixin.RelaunchMixinService")
                .getDeclaredMethod("boot", ClassLoader.class)
                .invoke(null, this);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        URL jarUrl;
        Manifest jarManifest;
        byte[] bytes;
        try {
            URL url = getResource(name.replace('.', '/') + ".class");
            if (url == null) {
                throw new ClassNotFoundException(name);
            }
            URLConnection urlConnection = url.openConnection();
            if (urlConnection instanceof JarURLConnection) {
                // usually the case
                JarURLConnection jarConnection = (JarURLConnection) urlConnection;
                jarUrl = jarConnection.getJarFileURL();
                jarManifest = jarConnection.getManifest();
            } else {
                // only in strange setups (like our integration tests), just use some url as fallback
                jarUrl = url;
                jarManifest = null;
            }
            try (InputStream in = urlConnection.getInputStream()) {
                bytes = ByteStreams.toByteArray(in);
            }
        } catch (Exception e) {
            throw new ClassNotFoundException(name, e);
        }

        // If the class has a package, define that based on the manifest
        int pkgIndex = name.lastIndexOf('.');
        if (pkgIndex > 0) {
            String pkgName = name.substring(0, pkgIndex);
            if (getPackage(pkgName) == null) {
                try {
                    if (jarManifest != null) {
                        definePackage(pkgName, jarManifest, jarUrl);
                    } else {
                        definePackage(pkgName, null, null, null, null, null, null, jarUrl);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        bytes = transformer.apply(name, bytes);

        return defineClass(name, bytes, 0, bytes.length, new CodeSource(jarUrl, (CodeSigner[]) null));
    }
}
