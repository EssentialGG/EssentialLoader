package gg.essential.loader.stage0.util;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;

import java.util.List;

public interface IDelegatingTransformationService extends IDelegatingTransformationServiceBase {

    @Override
    default List<Resource> beginScanning(IEnvironment environment) {
        return this.delegate().beginScanning(environment);
    }

    @Override
    default List<Resource> completeScan(IModuleLayerManager layerManager) {
        return this.delegate().completeScan(layerManager);
    }
}
