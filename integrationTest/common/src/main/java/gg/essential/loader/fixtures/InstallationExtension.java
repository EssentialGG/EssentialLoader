package gg.essential.loader.fixtures;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.io.IOException;
import java.util.ArrayList;

public class InstallationExtension implements ParameterResolver {
    private static final Namespace NAMESPACE = Namespace.create(InstallationExtension.class);
    private static final String KEY = "installation";

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return BaseInstallation.class.isAssignableFrom(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        try {
            BaseInstallation installation = parameterContext.getParameter().getType()
                .asSubclass(BaseInstallation.class)
                .getDeclaredConstructor()
                .newInstance();

            extensionContext.getStore(NAMESPACE)
                .getOrComputeIfAbsent(KEY, __ -> new Installations(), Installations.class)
                .add(installation);

            installation.setup();

            return installation;
        } catch (IOException | ReflectiveOperationException e) {
            throw new ParameterResolutionException("Failed to setup installation:", e);
        }
    }

    private static class Installations extends ArrayList<BaseInstallation> implements ExtensionContext.Store.CloseableResource {
        @Override
        public void close() throws Throwable {
            IOException exception = null;
            for (BaseInstallation installation : this) {
                try {
                    installation.close();
                } catch (IOException e) {
                    if (exception == null) {
                        exception = e;
                    } else {
                        exception.addSuppressed(e);
                    }
                }
            }
            if (exception != null) {
                throw exception;
            }
        }
    }
}
