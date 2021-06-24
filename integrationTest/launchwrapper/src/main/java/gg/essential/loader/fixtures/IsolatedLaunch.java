package gg.essential.loader.fixtures;

import com.google.common.io.ByteStreams;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IsolatedLaunch {
    private final IsolatedClassLoader loader = new IsolatedClassLoader();

    public void launch(Path gameDir, String tweaker) throws Exception {
        System.out.println("Launching " + tweaker + " in " + gameDir);
        Class<?> cls = getClass("gg.essential.loader.fixtures.TestableLaunch");
        Object obj = cls.newInstance();
        cls.getDeclaredMethod("launch", File.class, String.class).invoke(obj, gameDir.toFile(), tweaker);
    }

    public boolean getModLoadState(String field) throws Exception {
        return getClass("com.example.mod.LoadState")
            .getDeclaredField(field)
            .getBoolean(null);
    }

    public boolean isEssentialLoaded() throws Exception {
        try {
            return getClass("gg.essential.api.tweaker.EssentialTweaker")
                .getDeclaredField("loaded")
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
                    if (name.startsWith("net.minecraftforge.fml.")) {
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
                        if (name.equals("net.minecraftforge.fml.relauncher.FMLSecurityManager")) {
                            ClassNode classNode = new ClassNode(Opcodes.ASM5);
                            new ClassReader(bytes).accept(classNode, 0);
                            for (MethodNode method : classNode.methods) {
                                InsnList instructions = method.instructions;
                                for (int i = 0; i < instructions.size(); i++) {
                                    AbstractInsnNode insn = instructions.get(i);
                                    if (insn instanceof LdcInsnNode) {
                                        LdcInsnNode ldcInsn = (LdcInsnNode) insn;
                                        if ("setSecurityManager".equals(ldcInsn.cst)) {
                                            ldcInsn.cst = "ignored";
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
}
