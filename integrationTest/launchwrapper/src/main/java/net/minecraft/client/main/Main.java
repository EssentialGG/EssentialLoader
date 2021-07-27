package net.minecraft.client.main;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.eventbus.EventBus;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.FMLModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModClassLoader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ModContainerFactory;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.ModDiscoverer;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import org.objectweb.asm.Type;

import java.io.File;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        ModClassLoader modClassLoader = new ModClassLoader(Main.class.getClassLoader());
        ModDiscoverer modDiscoverer = new ModDiscoverer();
        modDiscoverer.findClasspathMods(modClassLoader);
        modDiscoverer.findModDirMods(new File(Launch.minecraftHome, "mods"));
        LoadController loadController = new LoadController(Loader.instance());
        EventBus eventBus = new EventBus();
        for (ModContainer modContainer : modDiscoverer.identifyMods()) {
            if (modContainer instanceof ModContainerHack) {
                modContainer.registerBus(eventBus, loadController);
                ((ModContainerHack) modContainer).constructMod(new FMLConstructionEvent(
                    modClassLoader,
                    modDiscoverer.getASMTable(),
                    ArrayListMultimap.create()
                ));
            }
        }
    }

    static {
        ModContainerFactory.instance().registerContainerType(Type.getType(Mod.class), ModContainerHack.class);
    }

    public static class ModContainerHack extends FMLModContainer {
        public ModContainerHack(String className, ModCandidate container, Map<String, Object> modDescriptor) {
            super(className, container, modDescriptor);
        }

        // Original method depends on FMLCommonHandler, which tries to load Minecraft classes
        @Override
        public boolean shouldLoadInEnvironment() {
            return true;
        }
    }
}
