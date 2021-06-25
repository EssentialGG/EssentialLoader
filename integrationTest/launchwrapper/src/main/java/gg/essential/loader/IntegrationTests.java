package gg.essential.loader;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntegrationTests {
    @Test
    public void testFirstLaunch() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testSecondLaunch() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        installation.launchFML();

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testNoAutoUpdateLaunch() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        installation.launchFML();

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
            System.setProperty("essential.download.url", "http://127.0.0.1:" + socket.getLocalPort());
            System.setProperty("essential.autoUpdate", "false");
            isolatedLaunch = installation.launchFML();
        } finally {
            System.setProperty("essential.autoUpdate", "true");
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
