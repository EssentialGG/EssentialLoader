package gg.essential.loader.stage0.util;

import cpw.mods.modlauncher.api.IEnvironment;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface IDelegatingTransformationService extends IDelegatingTransformationServiceBase {

    @Override
    default void beginScanning(IEnvironment environment) {
        this.delegate().beginScanning(environment);
    }

    @Override
    default List<Map.Entry<String, Path>> runScan(IEnvironment environment) {
        return this.delegate().runScan(environment);
    }
}
