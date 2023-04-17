package gg.essential.loader.stage2.components;

import gg.essential.loader.stage2.jvm.ForkedJvm;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ForkedUpdatePromptUI {
    private final Logger LOGGER = LogManager.getLogger();
    private final String title;
    private final String description;
    private ForkedJvm jvm;

    public ForkedUpdatePromptUI(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public void show() {
        try {
            this.jvm = new ForkedJvm(getClass());

            DataOutputStream out = new DataOutputStream(this.jvm.process.getOutputStream());
            out.writeUTF(this.title);
            out.writeUTF(this.description);
            out.writeBoolean(Boolean.getBoolean("essential.integration_testing"));
            out.flush();
        } catch (IOException e) {
            LOGGER.warn("Failed to fork JVM for UpdatePromptUI:", e);
        }
    }

    public Boolean waitForClose() {
        if (this.jvm == null) return null;

        try {
            int verdict = this.jvm.process.getInputStream().read();
            return verdict == 1 ? Boolean.TRUE : verdict == 2 ? Boolean.FALSE : null;
        } catch (IOException e) {
            LOGGER.warn("Failed to wait for UpdatePromptUI to close:", e);
            return null;
        } finally {
            this.jvm.close();
            this.jvm = null;
        }
    }

    public static void main(String[] args) throws IOException {
        DataInputStream in = new DataInputStream(System.in);

        String title = in.readUTF();
        String description = in.readUTF();
        boolean isIntegrationTest = in.readBoolean();

        Boolean verdict = null;
        try {
            UpdatePromptUI ui = new UpdatePromptUI(title, description);
            ui.show();
            if (isIntegrationTest) {
                ui.close();
            } else {
                verdict = ui.waitForClose();
            }
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
