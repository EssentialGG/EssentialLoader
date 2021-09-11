package gg.essential.loader.stage2;

import gg.essential.loader.stage2.util.Delete;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilsTest {

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    void findMostRecentFile() throws IOException {
        Path tmpDir = Files.createTempDirectory("test");
        try {
            Path file0 = tmpDir.resolve("test.txt");
            Path file1 = tmpDir.resolve("test.1.txt");
            Path file2 = tmpDir.resolve("test.2.txt");

            assertEquals(Pair.of(file0, 0), Utils.findMostRecentFile(tmpDir, "test", "txt"));

            Files.createFile(file0);
            assertEquals(Pair.of(file0, 0), Utils.findMostRecentFile(tmpDir, "test", "txt"));
            assertTrue(Files.exists(file0));

            Files.createFile(file1);
            assertEquals(Pair.of(file1, 1), Utils.findMostRecentFile(tmpDir, "test", "txt"));
            assertTrue(Files.notExists(file0));
            assertTrue(Files.exists(file1));

            Files.createFile(file2);
            assertEquals(Pair.of(file2, 2), Utils.findMostRecentFile(tmpDir, "test", "txt"));
            assertTrue(Files.notExists(file1));
            assertTrue(Files.exists(file2));
            Files.delete(file2);

            Files.createFile(file0);
            Files.createFile(file1);
            Files.createFile(file2);
            tmpDir.toFile().setWritable(false);
            assertEquals(Pair.of(file2, 2), Utils.findMostRecentFile(tmpDir, "test", "txt"));
            assertTrue(Files.exists(file0));
            assertTrue(Files.exists(file1));
            assertTrue(Files.exists(file2));
        } finally {
            tmpDir.toFile().setWritable(true);
            Delete.recursively(tmpDir);
        }
    }
}