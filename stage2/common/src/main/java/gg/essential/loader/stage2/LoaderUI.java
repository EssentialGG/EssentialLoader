package gg.essential.loader.stage2;

public interface LoaderUI {
    void start();
    void setDownloadSize(int bytes);
    void setDownloaded(int bytes);
    void complete();

    static LoaderUI all(final LoaderUI... uis) {
        return new LoaderUI() {
            @Override
            public void start() {
                for (LoaderUI ui : uis) {
                    ui.start();
                }
            }

            @Override
            public void setDownloadSize(int bytes) {
                for (LoaderUI ui : uis) {
                    ui.setDownloadSize(bytes);
                }
            }

            @Override
            public void setDownloaded(int bytes) {
                for (LoaderUI ui : uis) {
                    ui.setDownloaded(bytes);
                }
            }

            @Override
            public void complete() {
                for (LoaderUI ui : uis) {
                    ui.complete();
                }
            }
        };
    }
}
