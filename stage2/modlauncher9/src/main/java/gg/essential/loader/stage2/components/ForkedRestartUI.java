package gg.essential.loader.stage2.components;

import gg.essential.loader.stage2.jvm.ForkedJvm;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ForkedRestartUI {
    private final Logger LOGGER = LogManager.getLogger();
    private final List<String> mods;
    private ForkedJvm jvm;

    public ForkedRestartUI(List<String> mods) {
        this.mods = mods;
    }

    public void show() {
        try {
            this.jvm = new ForkedJvm(getClass());

            DataOutputStream out = new DataOutputStream(this.jvm.process.getOutputStream());
            for (String name : this.mods) {
                out.writeBoolean(true); // signal more entries
                out.writeUTF(name);
            }
            out.writeBoolean(false); // signal end of list
            out.flush();
        } catch (IOException e) {
            LOGGER.warn("Failed to fork JVM for RestartUI:", e);
        }
    }

    public Boolean waitForClose() {
        if (this.jvm != null) return null;

        try {
            int verdict = this.jvm.process.getInputStream().read();
            return verdict == 1 ? Boolean.TRUE : verdict == 2 ? Boolean.FALSE : null;
        } catch (IOException e) {
            LOGGER.warn("Failed to wait for RestartUI to close:", e);
            return null;
        } finally {
            this.jvm.close();
            this.jvm = null;
        }
    }

    public static void main(String[] args) throws IOException {
        DataInputStream in = new DataInputStream(System.in);

        List<String> mods = new ArrayList<>();
        while(in.readBoolean()) {
            mods.add(in.readUTF());
        }

        Boolean verdict = null;
        try {
            RestartUI ui = new RestartUI(mods);
            ui.show();

            verdict = ui.waitForClose();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        if (verdict == Boolean.TRUE) {
            System.out.write(1);
        } else if (verdict == Boolean.FALSE) {
            System.out.write(2);
        } else {
            System.out.write(0);
        }
        System.out.flush();
    }
}
