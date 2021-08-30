package gg.essential.loader.stage2;

public interface LoaderUI {
    void start();
    void setDownloadSize(int bytes);
    void setDownloaded(int bytes);
    void complete();
}
