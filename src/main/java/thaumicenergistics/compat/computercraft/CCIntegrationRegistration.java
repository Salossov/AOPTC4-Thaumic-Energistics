package thaumicenergistics.compat.computercraft;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Isolated Mod-bus event subscriber for ComputerCraft capability registration.
 * Guarantees zero classloading of CC:Tweaked classes when CC is absent.
 */
@EventBusSubscriber(modid = "thaumicenergistics", bus = EventBusSubscriber.Bus.MOD)
public final class CCIntegrationRegistration {
    public static final String MOD_ID = "computercraft";

    private CCIntegrationRegistration() {}

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        if (ModList.get().isLoaded(MOD_ID)) {
            CCCapabilityRegistrar.register(event);
        }
    }
}
