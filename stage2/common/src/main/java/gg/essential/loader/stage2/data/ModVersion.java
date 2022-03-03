package gg.essential.loader.stage2.data;

import java.util.Objects;

public class ModVersion {
    public static final ModVersion UNKNOWN = new ModVersion(null, null);

    private final String id;
    private final String version;

    public ModVersion(String id, String version) {
        this.id = id;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public boolean isUnknown() {
        return equals(UNKNOWN);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModVersion that = (ModVersion) o;
        return Objects.equals(id, that.id) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }
}
