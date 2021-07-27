package kotlin;

public class EarlyLoadedKotlin {
    public static void assertCorrectVersionPresent() {
        // This does not do anything, it's just a hypothetical method that also exists in the stage3 version.
    }

    public static void removedMethod() {
        // Dummy method not present in the stage3 version, so we get loader to emit its warning.
    }
}
