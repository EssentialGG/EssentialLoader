package gg.essential;

// This is here so it can be on the system/isolated class loader.
// Cause unlike forge, fabric does not add mod jars to the system class loader.
public class LoadState {
    public static boolean tweaker = false;
    public static boolean mod = false;
}
