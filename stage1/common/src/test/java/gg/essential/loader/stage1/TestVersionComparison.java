package gg.essential.loader.stage1;

import org.junit.jupiter.api.Test;

import static gg.essential.loader.stage1.VersionComparison.compareVersions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestVersionComparison {
    @Test
    public void testCompareVersions() {
        assertEqual("1.0.0", "1.0.0");
        assertEqual("1.0.1", "1.0.1");
        assertEqual("1.0", "1.0.0");
        assertEqual("1", "1.0.0");
        assertEqual("1", "1.0.0+test");
        assertEqual("1", "1.0.0+test.1.2-3+4");
        assertEqual("1+a", "1.0.0+b");
        assertGreaterThan("1.0.1", "1.0.0");
        assertGreaterThan("1.1.0", "1.0.1");
        assertGreaterThan("1.2.0", "1.1.0");
        assertGreaterThan("2.0.0", "1.1.1");
        assertGreaterThan("1.0.1", "1.0");
        assertGreaterThan("1.1.1", "1.0");
        assertGreaterThan("1.2.3", "1.2.3-rc.1");
        assertGreaterThan("1.2.3.4", "1.2.3-rc.1");
        assertGreaterThan("1.2.3.4", "1.2.3");
        assertGreaterThan("1-b", "1-a");
        assertGreaterThan("1-rc.1", "1-pre.1");
        assertGreaterThan("1-rc.2", "1-rc.1");
    }

    private void assertEqual(String left, String right) {
        assertEquals(0, compareVersions(left, right));
        assertEquals(0, compareVersions(right, left));
    }

    private void assertGreaterThan(String left, String right) {
        assertTrue(compareVersions(left, right) > 0);
        assertTrue(compareVersions(right, left) < 0);
    }
}
