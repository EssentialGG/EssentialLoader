package gg.essential.loader.stage2.relaunch;

import com.google.common.io.ByteStreams;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.function.BiFunction;

class RelaunchClassLoader extends IsolatedClassLoader {
    static { registerAsParallelCapable(); }

    private final BiFunction<String, byte[], byte[]> transformer;

    public RelaunchClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);

        this.transformer = new LegacyRelaunchTransformer();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        URL jarUrl;
        byte[] bytes;
        try {
            URL url = getResource(name.replace('.', '/') + ".class");
            if (url == null) {
                throw new ClassNotFoundException(name);
            }
            URLConnection urlConnection = url.openConnection();
            if (urlConnection instanceof JarURLConnection) {
                // usually the case
                jarUrl = ((JarURLConnection) urlConnection).getJarFileURL();
            } else {
                // only in strange setups (like our integration tests), just use some url as fallback
                jarUrl = url;
            }
            try (InputStream in = urlConnection.getInputStream()) {
                bytes = ByteStreams.toByteArray(in);
            }
        } catch (Exception e) {
            throw new ClassNotFoundException(name, e);
        }

        bytes = transformer.apply(name, bytes);

        return defineClass(name, bytes, 0, bytes.length, new CodeSource(jarUrl, (CodeSigner[]) null));
    }
}
