package sun.com.example.mod;

// Being in the sun package excludes this class from the launch class loader, so it can be safely accessed from everywhere
public class LoadState {
    public static String[] args;
    public static boolean tweaker = false;
    public static boolean secondTweaker;
    public static boolean coreMod = false;
    public static boolean mod = false;
    public static boolean mixin = false;

    public static boolean relaunched;

    public static void checkForRelaunch() {
        // By virtue of being in the sun package, this class is also excluded from the re-launch class loader.
        // We do want to reset all our state though when we re-launch to ensure it actually gets to run inside the
        // relaunch as well, so we'll just do that manually.
        if (!relaunched && Boolean.getBoolean("essential.loader.relaunched")) {
            relaunched = true;

            args = null;
            tweaker = false;
            secondTweaker = false;
            coreMod = false;
            mod = false;
            mixin = false;
        }
    }
}
