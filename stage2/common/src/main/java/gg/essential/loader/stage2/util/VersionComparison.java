package gg.essential.loader.stage2.util;

// Note: Keep in sync between stage1 and stage2; tests are in stage1 project
public class VersionComparison {
    public static int compareVersions(String a, String b) {
        int aPlusIndex = a.indexOf('+');
        int bPlusIndex = b.indexOf('+');
        if (aPlusIndex == -1) aPlusIndex = a.length();
        if (bPlusIndex == -1) bPlusIndex = b.length();
        String[] aParts = a.substring(0, aPlusIndex).replace('-', '.').split("\\.");
        String[] bParts = b.substring(0, bPlusIndex).replace('-', '.').split("\\.");
        for (int i = 0; i < Math.max(aParts.length, bParts.length); i++) {
            String aPart = i < aParts.length ? aParts[i] : "0";
            String bPart = i < bParts.length ? bParts[i] : "0";
            Integer aInt = toIntOrNull(aPart);
            Integer bInt = toIntOrNull(bPart);
            int compare;
            if (aInt != null && bInt != null) {
                // Both numbers, compare numerically
                compare = Integer.compare(aInt, bInt);
            } else if (aInt == null && bInt == null) {
                // Both non-numbers, compare lexicographically
                compare = aPart.compareTo(bPart);
            } else {
                // Mixed, number wins over str, e.g. 1.0.0.1 is greater than 1.0.0-rc.1
                if (aInt != null) {
                    compare = 1;
                } else {
                    compare = -1;
                }
            }
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }

    private static Integer toIntOrNull(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
