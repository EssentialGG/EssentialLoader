package gg.essential.loader.stage2.relaunch;

import com.google.common.io.ByteStreams;
import gg.essential.loader.stage2.EssentialLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static gg.essential.loader.stage2.relaunch.Relaunch.FML_TWEAKER;

class RelaunchClassLoader extends URLClassLoader {
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

    public RelaunchClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // Fast path
        Class<?> cls = classes.get(name);
        if (cls != null) {
            return cls;
        }

        // For excluded classes, use the default loadClass behavior which delegates the parent class loader first
        for (String exclusion : exclusions) {
            if (name.startsWith(exclusion)) {
                cls = super.loadClass(name);
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
                // Fuck forge
                // It installs a SecurityManager which locks itself down by rejecting any future managers and forge
                // itself refuses to boot if its manager is rejected (e.g. by a manager previously installed by it).
                // (re-defining the whole package so we can ignore the signature).
                if (name.startsWith("net.minecraftforge.fml.")) {
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
                    if (name.equals(FML_TWEAKER)) {
                        ClassNode classNode = new ClassNode(Opcodes.ASM5);
                        new ClassReader(bytes).accept(classNode, 0);
                        for (MethodNode method : classNode.methods) {
                            InsnList instructions = method.instructions;
                            for (int i = 0; i < instructions.size(); i++) {
                                AbstractInsnNode insn = instructions.get(i);
                                // This removes any call to setSecurityManager, instead dropping the security manager
                                if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).name.equals("setSecurityManager")) {
                                    instructions.set(insn, new InsnNode(Opcodes.POP));
                                }
                            }
                        }
                        ClassWriter classWriter = new ClassWriter(Opcodes.ASM5);
                        classNode.accept(classWriter);
                        bytes = classWriter.toByteArray();
                    }
                    cls = defineClass(name, bytes, 0, bytes.length, new CodeSource(jarUrl, (CodeSigner[]) null));
                } else {
                    // Lets the URLClassLoader do the hard work (and do it properly)
                    cls = findClass(name);
                }
            }

            // Class loaded successfully, store it in our map so we can take the fast path in the future
            classes.put(name, cls);

            return cls;
        }
    }
}
