package gg.essential.loader.stage2.relaunch.args;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

class DevLaunchInjector {
    public static final String MAIN = "net.fabricmc.devlaunchinjector.Main";

    static List<String> getLaunchArgs(List<String> javaArgs) {
        resetSystemProperties("fabric.dli.env", "fabric.dli.main","fabric.dli.config");
        ArrayList<String> result = new ArrayList<>();
        result.add(MAIN);
        result.addAll(javaArgs);
        return result;
    }

    /**
     * Resets all given system properties to the value they had at the very beginning of the JVM (before any user code
     * might have changed them).
     */
    private static void resetSystemProperties(String...properties) {
        Properties bootProperties;
        Properties currentProperties = System.getProperties();
        try {
            System.setProperties(null); // this will re-initialize all properties just like they were at boot
            bootProperties = System.getProperties();
        } finally {
            System.setProperties(currentProperties);
        }

        // for tests we cannot actually set the boot properties, so we just look everything up with a special prefix
        String testPrefix = System.getProperty("test.boot-prefix");

        for (String property : properties) {
            String value = testPrefix == null
                // production reads from the actual boot properties
                ? bootProperties.getProperty(property)
                // tests read from namespaced normal properties
                : currentProperties.getProperty(testPrefix + property);

            if (value != null) {
                System.setProperty(property, value);
            } else {
                System.clearProperty(property);
            }
        }
    }
}
