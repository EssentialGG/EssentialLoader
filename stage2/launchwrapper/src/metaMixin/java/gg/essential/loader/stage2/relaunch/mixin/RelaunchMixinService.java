package gg.essential.loader.stage2.relaunch.mixin;

import com.google.common.io.ByteStreams;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;

/**
 * Mixin service responsible for managing "meta" mixins applied to classes which are loaded in the fake system class
 * loader when re-launching.
 */
public class RelaunchMixinService extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider {
    static ClassLoader loader;

    /**
     * Initializes the Mixin subsystem for the given relaunch class loader.
     * The given class loader should be the one which will load classes ordinarily loaded by the system class loader and
     * which will be transformed by this Mixin subsystem (e.g. Launch, LaunchClassLoader, FMLTweaker, etc.).
     *
     * Care must be taken that this method is called from within its own independent/isolated class loader, such that
     * the Mixin classes used by this subsystem can operate independently of the Mixin potentially already present on
     * the real system class loader as well as the Mixin which will be loaded on the fake system class loader.
     *
     * Each instance of this subsystem can only be used for one relaunch class loader and calling this method more than
     * once will throw an error.
     */
    @SuppressWarnings("unused") // accessed via reflection from within the IsolatedClassLoader
    public static BiFunction<String, byte[], byte[]> boot(ClassLoader loader) throws ReflectiveOperationException {
        if (RelaunchMixinService.loader != null) {
            throw new IllegalStateException("Subsystem already bound to " + RelaunchMixinService.loader);
        }
        RelaunchMixinService.loader = loader;

        MixinBootstrap.init();

        Method gotoPhase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        gotoPhase.setAccessible(true);
        gotoPhase.invoke(null, MixinEnvironment.Phase.DEFAULT);

        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);

        Mixins.addConfiguration("essential-loader.launch.mixins.json");

        return new RelaunchTransformer();
    }

    @Override
    public String getName() {
        return "Essential/Relaunch";
    }

    @Override
    public boolean isValid() {
        return loader != null;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return null;
    }

    @Override
    public IClassTracker getClassTracker() {
        return null;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.emptyList();
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return new ContainerHandleVirtual(getName());
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return loader.getResourceAsStream(name);
    }

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, true);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        InputStream inputStream = getResourceAsStream(name.replace('.', '/') + ".class");
        if (inputStream == null) {
            throw new ClassNotFoundException(name);
        }
        byte[] bytes;
        try (InputStream in = inputStream) {
            bytes = ByteStreams.toByteArray(in);
        }
        ClassNode classNode = new ClassNode(Opcodes.ASM5);
        new ClassReader(bytes).accept(classNode, 0);
        return classNode;
    }

    @Override
    public URL[] getClassPath() {
        return null;
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return loader.loadClass(name);
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, loader);
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, getClass().getClassLoader());
    }
}
