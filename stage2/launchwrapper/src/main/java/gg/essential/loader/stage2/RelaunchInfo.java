package gg.essential.loader.stage2;

import com.google.gson.Gson;

import java.util.List;
import java.util.Set;

class RelaunchInfo {
    public Set<String> loadedIds;
    public List<String> extraMods;

    private static String PROPERTY = "gg.essential.loader.stage2.relaunch-info";

    public static RelaunchInfo get() {
        String relaunchInfoJson = System.getProperty(PROPERTY);
        if (relaunchInfoJson == null) return null;
        return new Gson().fromJson(relaunchInfoJson, RelaunchInfo.class);
    }

    public static void put(RelaunchInfo value) {
        System.setProperty(PROPERTY, new Gson().toJson(value));
    }
}
