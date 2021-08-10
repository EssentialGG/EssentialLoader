package gg.essential.loader.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

// How this does not exist in the standard library is beyond me.
public class Copy extends SimpleFileVisitor<Path> {
    private final Path sourcePath;
    private final Path targetPath;

    public Copy(Path sourcePath, Path targetPath) {
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir)));
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.copy(file, targetPath.resolve(sourcePath.relativize(file)));
        return FileVisitResult.CONTINUE;
    }

    public static void recursively(Path sourcePath, Path targetPath) throws IOException {
        Files.walkFileTree(sourcePath, new Copy(sourcePath, targetPath));
    }
}
