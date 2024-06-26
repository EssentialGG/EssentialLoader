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

        IsolatedLaunch isolatedLaunch = installation.launchFabric();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testSecondLaunch(Installation installation) throws Exception {
        installation.addExampleMod();

        installation.launchFabric();

        IsolatedLaunch isolatedLaunch = installation.launchFabric();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testOfflineLaunch(Installation installation) throws Exception {
        installation.addExampleMod();

        installation.launchFabric();

        Delete.recursively(installation.apiDir);

        IsolatedLaunch isolatedLaunch = installation.launchFabric();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testNoAutoUpdateLaunch(Installation installation) throws Exception {
        installation.addExampleMod();

        installation.launchFabric();

        ServerSocket socket = new ServerSocket(0);
        AtomicInteger accessCount = new AtomicInteger();
        Thread thread = new Thread(() -> {
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
        thread.setDaemon(true);
        thread.start();
        IsolatedLaunch isolatedLaunch;
        try {
            isolatedLaunch = installation.newLaunchFabric();
            isolatedLaunch.setProperty("essential.download.url", "http://127.0.0.1:" + socket.getLocalPort());
            isolatedLaunch.setProperty("essential.autoUpdate", "false");
            isolatedLaunch.launch();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        thread.join();
        assertEquals(0, accessCount.get(), "Server connections");

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }
}
