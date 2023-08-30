package gg.essential.loader.stage2.diff;

import gg.essential.loader.stage2.util.Delete;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Applies diffs in the form of a zip file to a given target archive file.
 *
 * The layout of the zip file is fairly simple and determines how patching works:
 * - Files from the "+" directory are added to the target archive (overwriting any existing entries)
 * - Files from the "~" directory replace the corresponding entry in the target archive
 * - Files from the "-" directory are removed from the target archive
 *
 * The differences between "+" and "~" is purely for aesthetics, they are actually applied identically.
 */
public class DiffPatcher {
    public static void apply(Path targetFile, Path diffFile) throws IOException {
        try (FileSystem targetFileSystem = FileSystems.newFileSystem(targetFile, (ClassLoader) null)) {
            try (FileSystem diffFileSystem = FileSystems.newFileSystem(diffFile, (ClassLoader) null)) {
                walk(diffFileSystem.getPath("/-"), targetFileSystem, DiffPatcher::remove);
                walk(diffFileSystem.getPath("/~"), targetFileSystem, DiffPatcher::add);
                walk(diffFileSystem.getPath("/+"), targetFileSystem, DiffPatcher::add);
            }
        }
        stripNonDeterminism(targetFile);
    }

    private static void add(Path diffPath, Path targetPath) throws IOException {
        if (Files.exists(targetPath)) {
            Delete.recursively(targetPath);
        }
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(diffPath, targetPath);
    }

    private static void remove(Path diffPath, Path targetPath) throws IOException {
        if (Files.exists(targetPath)) {
            Delete.recursively(targetPath);

            // Now that we've deleted this entry, check if the parent has any other children
            Path parent = targetPath.getParent();
            if (parent != null && !hasChildren(parent)) {
                // if it doesn't (and isn't the root), then it can be cleaned up as well
                remove(diffPath, parent);
            }
        }
    }

    /**
     * Traverses the give diff directory tree calling the patcher function for every regular file entry in the tree.
     */
    private static void walk(Path diffRoot, FileSystem targetFileSystem, PatchingFunction patcher) throws IOException {
        if (!Files.exists(diffRoot)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(diffRoot)) {
            for (Path diffPath : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(diffPath)) {
                    continue;
                }
                String relativePath = diffRoot.relativize(diffPath).toString();
                Path targetPath = targetFileSystem.getPath(relativePath);
                patcher.apply(diffPath, targetPath);
            }
        }
    }

    private static boolean hasChildren(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.findAny().isPresent();
        }
    }

    private static void stripNonDeterminism(Path path) throws IOException {
        Path tmpPath = Files.createTempFile(path.getParent(), "tmp", ".jar");
        try {
            try (OutputStream fileOut = Files.newOutputStream(tmpPath, StandardOpenOption.TRUNCATE_EXISTING);
                 ZipOutputStream zipOut = new ZipOutputStream(fileOut);
                 ZipFile zipIn = new ZipFile(path.toFile())) {
                List<? extends ZipEntry> entries = Collections.list(zipIn.entries());
                entries.sort(Comparator.comparing(ZipEntry::getName));
                for (ZipEntry entry : entries) {
                    ZipEntry newEntry = new ZipEntry(entry.getName());
                    newEntry.setTime(CONSTANT_TIME_FOR_ZIP_ENTRIES);
                    zipOut.putNextEntry(newEntry);
                    IOUtils.copy(zipIn.getInputStream(entry), zipOut);
                }
            }
            Files.move(tmpPath, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmpPath);
        }
    }

    // A safe, constant value for creating consistent zip entries
    // From: https://github.com/gradle/gradle/blob/d6c7fd470449a59fc57a26b4ebc0ad83c64af50a/subprojects/core/src/main/java/org/gradle/api/internal/file/archive/ZipCopyAction.java#L42-L57
    private static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).getTimeInMillis();

    @FunctionalInterface
    private interface PatchingFunction {
        /**
         * Applies a given patch file from the diff jar to the given corresponding target file in the target archive.
         */
        void apply(Path patch, Path target) throws IOException;
    }
}
