package thaumicenergistics.client;

import aop.ui.api.SettingsRegistry;
import net.neoforged.fml.ModList;

/** Resolves an aspect's stable texture namespace to its current loader identity. */
final class AspectModNames {
    static String owner(String namespace) {
        String modId = SettingsRegistry.providers().stream()
                .filter(provider -> provider.namespace().equals(namespace))
                .map(SettingsRegistry.Provider::modId).findFirst().orElse(namespace);
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName()).orElse("Unknown");
    }
    private AspectModNames() { }
}
