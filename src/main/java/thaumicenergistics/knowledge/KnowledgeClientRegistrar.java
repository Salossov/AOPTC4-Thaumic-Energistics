package thaumicenergistics.knowledge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Client-only event subscriber for registering Knowledge Inscriber screens.
 */
@EventBusSubscriber(modid = ThaumicEnergistics.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KnowledgeClientRegistrar {
    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(KnowledgeRegistrar.KNOWLEDGE_INSCRIBER_MENU.get(), KnowledgeInscriberScreen::new);
    }

    private KnowledgeClientRegistrar() {}
}
