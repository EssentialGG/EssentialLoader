package com.example.mod;

// This is here so it can be on the system/isolated class loader.
// Cause unlike forge, fabric does not add mod jars to the system class loader.
public class LoadState {
    public static boolean tweaker = true; // does not apply to fabric
    public static boolean coreMod = false; // set from MixinMain (so not technically a coremod)
    public static boolean mod = false;
    public static boolean mixin = false;
}
