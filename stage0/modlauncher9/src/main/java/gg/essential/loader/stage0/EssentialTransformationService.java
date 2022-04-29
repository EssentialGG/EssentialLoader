package gg.essential.loader.stage0;

import gg.essential.loader.stage0.util.IDelegatingTransformationService;

public class EssentialTransformationService extends EssentialTransformationServiceBase implements IDelegatingTransformationService {
    public EssentialTransformationService() {
        super("modlauncher"); // note: not using `modlauncher9` to stay compatible with old stage0
    }
}
