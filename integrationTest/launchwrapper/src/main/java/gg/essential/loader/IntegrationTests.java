package gg.essential.loader;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import gg.essential.loader.util.Delete;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntegrationTests {
    @Test
    public void testFirstLaunch(Installation installation) throws Exception {
        installation.addExampleMod();

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testSecondLaunch(Installation installation) throws Exception {
        installation.addExampleMod();

        installation.launchFML();

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testOfflineLaunch(Installation installation) throws Exception {
        installation.addExampleMod();

        installation.launchFML();

        Delete.recursively(installation.apiDir.resolve("v1/mods/essential"));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testNoAutoUpdateLaunch(Installation installation) throws Exception {
        installation.addExampleMod();

        installation.launchFML();

        IsolatedLaunch isolatedLaunch;
        try (ForbiddenApiServer server = new ForbiddenApiServer()) {
            isolatedLaunch = installation.newLaunchFML();
            server.configure(isolatedLaunch);
            isolatedLaunch.setProperty("essential.autoUpdate", "false");
            isolatedLaunch.launch();
        }

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void test11202Launch(Installation installation) throws Exception {
        installation.addExampleMod();

        IsolatedLaunch isolatedLaunch = installation.newLaunchFML11202();
        isolatedLaunch.launch();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    private static class ForbiddenApiServer implements AutoCloseable {
        private final ServerSocket socket = new ServerSocket(0);
        private final AtomicInteger accessCount = new AtomicInteger();
        private final Thread thread = new Thread(() -> {
            while (!socket.isClosed()) {
                try {
                    socket.accept().close();
                    accessCount.incrementAndGet();
                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        e.printStackTrace();
                    }
                }
            }
        });

        ForbiddenApiServer() throws IOException {
            thread.setDaemon(true);
            thread.start();
        }

        public void configure(IsolatedLaunch launch) {
            launch.setProperty("essential.download.url", "http://127.0.0.1:" + socket.getLocalPort());
        }

        @Override
        public void close() throws Exception {
            socket.close();
            thread.join();

            assertNeverAccessed();
        }

        public void assertNeverAccessed() {
            assertEquals(0, accessCount.get(), "Server connections");
        }
    }
}
