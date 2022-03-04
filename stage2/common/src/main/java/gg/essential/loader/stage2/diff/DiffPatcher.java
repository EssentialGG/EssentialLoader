package gg.essential.loader.stage2.diff;

import gg.essential.loader.stage2.util.Delete;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

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

    @FunctionalInterface
    private interface PatchingFunction {
        /**
         * Applies a given patch file from the diff jar to the given corresponding target file in the target archive.
         */
        void apply(Path patch, Path target) throws IOException;
    }
}
