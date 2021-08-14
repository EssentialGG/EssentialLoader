package gg.essential.loader.fixtures;

import com.google.common.io.ByteStreams;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permission;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IsolatedLaunch {
    private static final String LAUNCH_CLASS_NAME = "net.minecraft.launchwrapper.Launch";
    private final IsolatedClassLoader loader = new IsolatedClassLoader();

    public void launch(Path gameDir, String tweaker) throws Exception {
        System.out.println("Launching " + tweaker + " in " + gameDir);

        System.setSecurityManager(new NoExitAllowed());
        try {
            Class<?> cls = getClass(LAUNCH_CLASS_NAME);

            Constructor<?> constructor = cls.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object obj = constructor.newInstance();

            Object launchClassLoader = cls.getDeclaredField("classLoader").get(null);
            Method addExclusion = launchClassLoader.getClass().getDeclaredMethod("addClassLoaderExclusion", String.class);
            addExclusion.invoke(launchClassLoader, "com.example.mod.LoadState");
            addExclusion.invoke(launchClassLoader, "com.example.mod2.LoadState");
            addExclusion.invoke(launchClassLoader, "gg.essential.LoadState");
            addExclusion.invoke(launchClassLoader, "com.google.common.jimfs.");

            Method launchMethod = cls.getDeclaredMethod("launch", String[].class);
            launchMethod.setAccessible(true);
            launchMethod.invoke(obj, (Object) new String[] {
                "--gameDir", gameDir.toString(),
                "--tweakClass", tweaker,
            });
        } finally {
            System.setSecurityManager(new EverythingIsAllowedSecurityManager());
        }
    }

    public boolean getModLoadState(String field) throws Exception {
        return getClass("com.example.mod.LoadState")
            .getDeclaredField(field)
            .getBoolean(null);
    }

    public boolean getMod2LoadState(String field) throws Exception {
        return getClass("com.example.mod2.LoadState")
            .getDeclaredField(field)
            .getBoolean(null);
    }

    public boolean isEssentialLoaded() throws Exception {
        try {
            return getClass("gg.essential.LoadState")
                .getDeclaredField("mod")
                .getBoolean(null);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public Class<?> getClass(String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    private static class IsolatedClassLoader extends URLClassLoader {
        private final List<String> exclusions = Arrays.asList(
            "java.",
            "javax.",
            "sun.",
            "com.google.common.jimfs.",
            "org.apache.logging."
        );

        private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();

        public IsolatedClassLoader() {
            super(((URLClassLoader) getSystemClassLoader()).getURLs());
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            Class<?> cls = classes.get(name);
            if (cls != null) {
                return cls;
            }

            for (String exclusion : exclusions) {
                if (name.startsWith(exclusion)) {
                    return super.loadClass(name);
                }
            }

            synchronized (getClassLoadingLock(name)) {
                cls = findLoadedClass(name);
                if (cls == null) {
                    // Fuck forge
                    // We need to remove the SecurityManager lock down.. and it refuses to boot if we reject its manager
                    // (re-defining the whole package so we can ignore the signature)
                    // Unrelated to forge, we also want to remove the System.exit(1) from Launch and just re-throw any
                    // exceptions so we get the error directly in the test result rather than having to look at the log.
                    if (name.startsWith("net.minecraftforge.fml.") || name.equals(LAUNCH_CLASS_NAME)) {
                        URL jarUrl;
                        byte[] bytes;
                        try {
                            JarURLConnection urlConnection = (JarURLConnection) getResource(name.replace('.', '/') + ".class").openConnection();
                            jarUrl = urlConnection.getJarFileURL();
                            try (InputStream in = urlConnection.getInputStream()) {
                                bytes = ByteStreams.toByteArray(in);
                            }
                        } catch (Exception e) {
                            throw new ClassNotFoundException(name, e);
                        }
                        boolean isFMLTweaker = name.equals("net.minecraftforge.fml.common.launcher.FMLTweaker");
                        boolean isLaunch = name.equals(LAUNCH_CLASS_NAME);
                        if (isFMLTweaker || isLaunch) {
                            ClassNode classNode = new ClassNode(Opcodes.ASM5);
                            new ClassReader(bytes).accept(classNode, 0);
                            for (MethodNode method : classNode.methods) {
                                InsnList instructions = method.instructions;
                                for (int i = 0; i < instructions.size(); i++) {
                                    AbstractInsnNode insn = instructions.get(i);
                                    if (isFMLTweaker) {
                                        // This removes any call to setSecurityManager, instead dropping the security manager
                                        if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).name.equals("setSecurityManager")) {
                                            instructions.set(insn, new InsnNode(Opcodes.POP));
                                        }
                                    }
                                    if (isLaunch) {
                                        // This inserts a (re-)throw before the error is handed to the logger
                                        if (insn instanceof LdcInsnNode && "Unable to launch".equals(((LdcInsnNode) insn).cst)) {
                                            instructions.insertBefore(insn, new InsnNode(Opcodes.ATHROW));
                                            i++;
                                        }
                                    }
                                }
                            }
                            ClassWriter classWriter = new ClassWriter(Opcodes.ASM5);
                            classNode.accept(classWriter);
                            bytes = classWriter.toByteArray();
                        }
                        cls = defineClass(name, bytes, 0, bytes.length, new CodeSource(jarUrl, (CodeSigner[]) null));
                    } else {
                        cls = findClass(name);
                    }
                }
                classes.put(name, cls);
                return cls;
            }
        }
    }

    private static class EverythingIsAllowedSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission perm) {
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
        }
    }

    private static class NoExitAllowed extends SecurityManager {
        @Override
        public void checkPermission(Permission perm) {
            String name = perm.getName();
            if (name != null && name.startsWith("exitVM.")) {
                throw new SecurityException("No exit allowed in tests");
            }
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            this.checkPermission(perm);
        }
    }
}
