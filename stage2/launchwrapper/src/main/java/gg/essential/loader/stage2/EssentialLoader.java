package gg.essential.loader.stage2;

// This class name is reserved for the `launchwrapper-legacy` stage2 shim and must not be used here as it will
// always resolve to the shim class because the old stage1 puts that jar on the system class loader where we have no way
// to update it.
@Deprecated
public class EssentialLoader {}
