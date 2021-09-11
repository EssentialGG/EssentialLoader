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

    default LoaderUI updatesEveryMillis(int msPerUpdate) {
        return new Adapter(this) {
            private long lastUpdate = 0;
            private int lastSize;

            @Override
            public void setDownloaded(int bytes) {
                this.lastSize = bytes;

                long now = System.currentTimeMillis();
                if (now - this.lastUpdate <= msPerUpdate) {
                    return;
                }
                this.lastUpdate = now;

                super.setDownloaded(bytes);
            }

            @Override
            public void complete() {
                super.setDownloaded(this.lastSize);
                super.complete();
            }
        };
    }

    class Adapter implements LoaderUI {
        private final LoaderUI inner;

        public Adapter(LoaderUI inner) {
            this.inner = inner;
        }

        @Override
        public void start() {
            inner.start();
        }

        @Override
        public void setDownloadSize(int bytes) {
            inner.setDownloadSize(bytes);
        }

        @Override
        public void setDownloaded(int bytes) {
            inner.setDownloaded(bytes);
        }

        @Override
        public void complete() {
            inner.complete();
        }
    }
}
