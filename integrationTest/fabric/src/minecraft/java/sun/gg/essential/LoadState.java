package sun.gg.essential;

// This is here so it can be on the system/isolated class loader.
// Cause unlike forge, fabric does not add mod jars to the system class loader.
// Being in the sun package excludes this class from the launch class loader, so it can be safely accessed from everywhere
public class LoadState {
    public static boolean tweaker = false;
    public static boolean mod = false;

    public static boolean dummyStage2Loaded = false;
    public static boolean dummyStage2Initialized = false;
}
