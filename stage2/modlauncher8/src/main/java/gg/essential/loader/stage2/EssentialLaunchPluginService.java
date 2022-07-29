package gg.essential.loader.stage2;

import cpw.mods.modlauncher.EnumerationHelper;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.function.Function;

public class EssentialLaunchPluginService implements ILaunchPluginService {
    @Override
    public String name() {
        return "essential-loader";
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return EnumSet.noneOf(Phase.class);
    }

    @Override
    public void initializeLaunch(ITransformerLoader transformerLoader, Path[] specialPaths) {
        try {
            invertResourcePriorityForKotlinPackage();
        } catch (Exception e) {
            System.err.println("Failed to invert class loading priority for Kotlin classes.");
            System.err.println("Issues may occur if mods bundle an outdated, non-relocated Kotlin.");
            e.printStackTrace();
        }
    }

    /**
     * Replaces the builtin {@code resourceFinder} of the given class loader to prioritize {@code specialJars} (such as
     * language loaders like KotlinForForge) over the dynamic resource enumerator locator which supplies url from mod
     * jars.
     * Without this, ModLauncher will load Kotlin from mods if they bundle it, usually ending up with an older version
     * than what we ship via KotlinForForge.
     */
    @SuppressWarnings("unchecked")
    private void invertResourcePriorityForKotlinPackage() throws Exception {
        Function<String, Enumeration<URL>> resourceLocator, first, second, invertedLocator, newLocator;

        // The TransformingClassLoader is set as the context class loader before this method is called in [Launcher.run]
        TransformingClassLoader classLoader = (TransformingClassLoader) Thread.currentThread().getContextClassLoader();
        Field resourceFinderField = TransformingClassLoader.class.getDeclaredField("resourceFinder");
        resourceFinderField.setAccessible(true);
        resourceLocator = (Function<String, Enumeration<URL>>) resourceFinderField.get(classLoader);

        // The resource finder is constructed as follows:
        //   this.resourceFinder = EnumerationHelper.mergeFunctors(builder.getResourceEnumeratorLocator(), this::locateResource);
        // where `mergeFunctors` is simply:
        //   input -> merge(first.apply(input), second.apply(input))
        // So to get access to the original locators (to swap them around), we need to deconstruct the lambda.
        Class<?> lambdaClass = resourceLocator.getClass();
        Field[] lambdaFields = lambdaClass.getDeclaredFields();
        Arrays.stream(lambdaFields).forEach(it -> it.setAccessible(true));
        first = (Function<String, Enumeration<URL>>) lambdaFields[0].get(resourceLocator);
        second = (Function<String, Enumeration<URL>>) lambdaFields[1].get(resourceLocator);

        invertedLocator = EnumerationHelper.mergeFunctors(second, first);

        newLocator = path -> (path.startsWith("kotlin") ? invertedLocator : resourceLocator).apply(path);

        resourceFinderField.set(classLoader, newLocator);
    }
}
