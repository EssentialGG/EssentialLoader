package gg.essential.loader.stage2.relaunch;

import com.google.common.collect.Iterators;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A class loader which strongly prefers loading its own instance of a class rather than using the one from its parent.
 * This allows us to re-order the class path such that more recent versions of libraries can be used even when the old
 * one has already been loaded into the system class loader before we get to run.
 * The only exception are JRE internal classes, lwjgl and logging as defined in {@link #exclusions}.
 */
class IsolatedClassLoader extends URLClassLoader {
    static { registerAsParallelCapable(); }

    // These should only contain things which need to be on the system class loader because the whole point of
    // relaunching is to get our versions of libraries loaded and anything in here, we cannot replace.
    private final List<String> exclusions = Arrays.asList(
        "java.", // JRE cannot be loaded twice
        "javax.", // JRE cannot be loaded twice
        "sun.", // JRE internals cannot be loaded twice
        "org.apache.logging.", // Continue to use the logging set up by any pre-launch code
        "org.lwjgl." // Natives cannot be loaded twice
    );

    private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();

    /**
     * The conceptual (but not actual) parent of this class loader.
     *
     * It is not the actual class loader because there is no way in Java 8 to re-define packages if they are already
     * defined in your parent. To work around that, our actual parent is an empty class loader, which has no packages
     * loaded at all, and we manually delegate to the conceptual parent as required.
     */
    private final ClassLoader delegateParent;

    public IsolatedClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, new EmptyClassLoader());

        this.delegateParent = parent;
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // Fast path
        Class<?> cls = classes.get(name);
        if (cls != null) {
            return cls;
        }

        // For excluded classes, use the parent class loader
        for (String exclusion : exclusions) {
            if (name.startsWith(exclusion)) {
                cls = delegateParent.loadClass(name);
                classes.put(name, cls);
                return cls;
            }
        }

        // Class is not excluded, so we define it in this loader regardless of whether it's already loaded in
        // the parent (cause that's the point of re-launching).
        synchronized (getClassLoadingLock(name)) {
            // Check if we have previously loaded this class. May be the case because we do not synchronize on
            // the lock for the fast path, so it may initiate loading multiple times.
            cls = findLoadedClass(name);

            // If the have not yet defined the class, let's do that
            if (cls == null) {
                cls = findClass(name);
            }

            // Class loaded successfully, store it in our map so we can take the fast path in the future
            classes.put(name, cls);

            return cls;
        }
    }

    @Override
    public URL getResource(String name) {
        // Try our classpath first because the order of our entries may be different from our parent.
        URL url = findResource(name);
        if (url != null) {
            return url;
        }

        return delegateParent.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return Iterators.asEnumeration(Iterators.concat(
            Iterators.forEnumeration(super.getResources(name)),
            Iterators.forEnumeration(delegateParent.getResources(name))
        ));
    }

    /**
     * We use an empty class loader as the actual parent because using null will use the system class loader and there
     * is plenty of stuff in there.
     */
    private static class EmptyClassLoader extends ClassLoader {
        @Override
        protected Package getPackage(String name) {
            return null;
        }

        @Override
        protected Package[] getPackages() {
            return null;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            throw new ClassNotFoundException();
        }

        @Override
        public URL getResource(String name) {
            return null;
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            return Collections.emptyEnumeration();
        }
    }
}
