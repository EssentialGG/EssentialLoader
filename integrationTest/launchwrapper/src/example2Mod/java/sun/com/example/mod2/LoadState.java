package sun.com.example.mod2;

// Being in the sun package excludes this class from the launch class loader, so it can be safely accessed from everywhere
public class LoadState {
    public static boolean tweaker = false;
    public static boolean coreMod = false;
    public static boolean mod = false;
    public static boolean mixin = false;
}
