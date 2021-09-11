package gg.essential.loader.stage2;

import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Utils {
    /**
     * Returns the file which has been written most recently and matches the given base name and extension in the given
     * folder. Recency is determined by a name suffix, not by file system dates (which may be unreliable).
     * <p>
     * Windows may prevent us from deleting files which are currently in use, preventing us from updating if the file
     * is still in use (likely by a zombie Minecraft instance). To work around that, we attach a numerical suffix to the
     * file (right before the ".jar" extension) as necessary and always load the largest one. This method will also try
     * to delete (tolerating errors) any files older than the most recent one to eventually clean them up.
     * When writing, we write to the file which has a suffix larger than any remaining files (so that next time we boot,
     * that's the one we read from).
     * <p>
     * The suffix 0 is special in that it is not actually written and instead denotes the file without any extra suffix.
     *
     * @return Pair of most recent file and its numerical suffix
     */
    public static Pair<Path, Integer> findMostRecentFile(Path dir, String baseName, String ext) throws IOException {
        String dotExt = "." + ext;
        // List all files
        List<Pair<Path, Integer>> files = Files.list(dir)
            // and determine their eligibility and numerical suffix
            .map(it -> {
                // Essential (version).1.jar
                String name = it.getFileName().toString();
                if (!name.startsWith(baseName)) {
                    return null; // invalid
                }
                // .1.jar
                String ending = name.substring(baseName.length());
                if (ending.equals(dotExt)) {
                    return Pair.of(it, 0); // default
                }
                // .1
                ending = ending.substring(0, ending.length() - dotExt.length());
                if (!ending.startsWith(".")) {
                    return null; // invalid
                }
                // 1
                ending = ending.substring(1);
                try {
                    return Pair.of(it, Integer.parseInt(ending));
                } catch (NumberFormatException e) {
                    return null; // invalid
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        // Find the most recent one
        Pair<Path, Integer> mostRecent = files.stream().max(Comparator.comparing(Pair::getRight)).orElse(null);
        if (mostRecent == null) {
            // No file exists, fall back to default name
            return Pair.of(dir.resolve(baseName + dotExt), 0);
        }

        // try to delete all others
        files.stream().filter(it -> it != mostRecent).forEach(it -> {
            try {
                Files.delete(it.getKey());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // return the most recent one
        return mostRecent;
    }

    public static Path findNextMostRecentFile(Path dir, String baseName, String ext) throws IOException {
        Pair<Path, Integer> mostRecent = findMostRecentFile(dir, baseName, ext);
        if (!Files.exists(mostRecent.getLeft())) {
            return mostRecent.getLeft();
        }
        return dir.resolve(baseName + "." + (mostRecent.getRight() + 1) + "." + ext);
    }
}
