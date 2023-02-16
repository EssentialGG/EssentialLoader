package gg.essential.loader.stage2.data;

import java.util.Objects;

public class ModId {
    public static final ModId UNKNOWN = new ModId(null, null, null, null);
    public static final ModId ESSENTIAL = new ModId("essential", null, "essential", null);

    private final String publisherSlug;
    private final String publisherId;
    private final String modSlug;
    private final String modId;

    public ModId(String publisherSlug, String publisherId, String modSlug, String modId) {
        this.publisherSlug = publisherSlug;
        this.publisherId = publisherId;
        this.modSlug = modSlug;
        this.modId = modId;
    }

    public String getPublisherSlug() {
        return publisherSlug;
    }

    public String getPublisherId() {
        return publisherId;
    }

    public String getModSlug() {
        return modSlug;
    }

    public String getModId() {
        return modId;
    }

    public String getFullSlug() {
        return publisherSlug + ":" + modSlug;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModId modId1 = (ModId) o;
        return Objects.equals(publisherSlug, modId1.publisherSlug) && Objects.equals(publisherId, modId1.publisherId) && Objects.equals(modSlug, modId1.modSlug) && Objects.equals(modId, modId1.modId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publisherSlug, publisherId, modSlug, modId);
    }
}
