package gg.essential.loader.stage2.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardOpenOption.*;

public class FabricModJson {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(VersionRange.class, new VersionRange.JsonAdapter())
        .create();

    private final int schemaVersion;
    private final @NotNull String id;
    private final @NotNull String version;
    private final @Nullable String name;
    private final @Nullable Map<String, VersionRange> depends;
    private final @Nullable List<Jar> jars;

    public FabricModJson(
        int schemaVersion,
        @NotNull String id,
        @NotNull String version,
        @Nullable String name,
        @Nullable Map<String, VersionRange> depends,
        @Nullable List<Jar> jars
    ) {
        this.schemaVersion = schemaVersion;
        this.id = id;
        this.version = version;
        this.name = name;
        this.depends = depends;
        this.jars = jars;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getVersion() {
        return version;
    }

    public @Nullable String getName() {
        return name;
    }

    public Map<String, VersionRange> getDepends() {
        return depends == null ? Collections.emptyMap() : depends;
    }

    public List<Jar> getJars() {
        return jars == null ? Collections.emptyList() : jars;
    }

    // https://github.com/google/gson/issues/61
    @SuppressWarnings("ConstantConditions")
    private FabricModJson validate() throws IOException {
        if (id == null) throw new IOException("Missing \"id\" in fabric.mod.json.");
        if (version == null) throw new IOException("Missing \"version\" in fabric.mod.json.");
        return this;
    }

    public static void write(Path jsonFile, FabricModJson json) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(jsonFile, CREATE)) {
            GSON.toJson(json, out);
        }
    }

    public static FabricModJson read(Path jsonFile) throws IOException {
        try (BufferedReader in = Files.newBufferedReader(jsonFile)) {
            return GSON.fromJson(in, FabricModJson.class).validate();
        }
    }

    public static FabricModJson readFromJar(Path jarFile) throws IOException {
        try (FileSystem fileSystem = FileSystems.newFileSystem(jarFile, (ClassLoader) null)) {
            return read(fileSystem.getPath("fabric.mod.json"));
        }
    }

    public static class Jar {
        private final String file;

        public Jar(String file) {
            this.file = file;
        }

        public String getFile() {
            return file;
        }
    }

    public static class VersionRange extends ArrayList<String> {
        public VersionRange() {
        }

        public VersionRange(String version) {
            add(version);
        }

        private static class JsonAdapter extends TypeAdapter<VersionRange> {
            @Override
            public void write(JsonWriter out, VersionRange values) throws IOException {
                if (values.size() == 1) {
                    out.value(values.get(0));
                } else {
                    out.beginArray();
                    for (String value : values) {
                        out.value(value);
                    }
                    out.endArray();
                }
            }

            @Override
            public VersionRange read(JsonReader in) throws IOException {
                VersionRange values = new VersionRange();
                if (in.peek() != JsonToken.BEGIN_ARRAY) {
                    values.add(in.nextString());
                } else {
                    in.beginArray();
                    while (in.peek() != JsonToken.END_ARRAY) {
                        values.add(in.nextString());
                    }
                    in.endArray();
                }
                return values;
            }
        }
    }
}
