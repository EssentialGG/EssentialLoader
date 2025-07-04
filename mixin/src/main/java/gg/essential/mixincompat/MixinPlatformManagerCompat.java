package gg.essential.mixincompat;

import gg.essential.CompatMixin;
import gg.essential.CompatShadow;
import org.spongepowered.asm.launch.platform.MixinContainer;
import org.spongepowered.asm.launch.platform.MixinPlatformManager;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;

import java.net.URI;

@CompatMixin(MixinPlatformManager.class)
public abstract class MixinPlatformManagerCompat {
    @CompatShadow
    public abstract MixinContainer addContainer(IContainerHandle handle);

    public final MixinContainer addContainer(URI uri) {
        return addContainer(new ContainerHandleURI(uri));
    }
}
