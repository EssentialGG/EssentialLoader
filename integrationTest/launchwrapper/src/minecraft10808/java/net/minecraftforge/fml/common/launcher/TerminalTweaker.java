package net.minecraftforge.fml.common.launcher;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.util.List;

/**
 * We use {@link System#exit} once the test code has reached the point where normally Minecraft code would run, to signal
 * a successful exit, much like Minecraft would after you click the Quit button (it'll be intercepted and handled by our SecurityManager).
 * <p></p>
 * Forge however, in addition to installing its own SecurityManager (which we already deal with elsewhere), also uses this
 * class (specifically the transformer it registers) to prevent all code that isn't Forge from directly referencing
 * {@link System#exit} (or equivalent).
 * <p></p>
 * As explained, we want to call that though, so we'll overwrite this tweaker to prevent that transformer from being applied.
 */
public class TerminalTweaker implements ITweaker {
    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
