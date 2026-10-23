package thaumicenergistics.knowledge;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Autonomous NeoForge EventBusSubscriber registrar for Knowledge Inscriber components.
 * Modifies no shared entrypoints, registers block, items, BE, menu, payloads, capabilities, and creative tab.
 */
@EventBusSubscriber(modid = ThaumicEnergistics.ID, bus = EventBusSubscriber.Bus.MOD)
public final class KnowledgeRegistrar {
    public static final ResourceLocation INSCRIBER_ID = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "knowledge_inscriber");
    public static final ResourceLocation CORE_ID = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "knowledge_core");

    private static KnowledgeInscriberBlock blockInstance;
    private static ItemKnowledgeCore coreItemInstance;
    private static BlockItem inscriberItemInstance;
    private static BlockEntityType<KnowledgeInscriberBlockEntity> beTypeInstance;
    private static MenuType<KnowledgeInscriberMenu> menuTypeInstance;

    public static final Supplier<KnowledgeInscriberBlock> KNOWLEDGE_INSCRIBER_BLOCK = () -> blockInstance;
    public static final Supplier<ItemKnowledgeCore> KNOWLEDGE_CORE_ITEM = () -> coreItemInstance;
    public static final Supplier<BlockItem> KNOWLEDGE_INSCRIBER_ITEM = () -> inscriberItemInstance;
    public static final Supplier<BlockEntityType<KnowledgeInscriberBlockEntity>> KNOWLEDGE_INSCRIBER_BE = () -> beTypeInstance;
    public static final Supplier<MenuType<KnowledgeInscriberMenu>> KNOWLEDGE_INSCRIBER_MENU = () -> menuTypeInstance;

    @SubscribeEvent
    public static void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> appeng.api.crafting.PatternDetailsHelper.registerDecoder(new ArcanePatternDecoder()));
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            blockInstance = new KnowledgeInscriberBlock();
            event.register(Registries.BLOCK, INSCRIBER_ID, () -> blockInstance);
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            coreItemInstance = new ItemKnowledgeCore();
            inscriberItemInstance = new BlockItem(blockInstance, new Item.Properties());
            event.register(Registries.ITEM, CORE_ID, () -> coreItemInstance);
            event.register(Registries.ITEM, INSCRIBER_ID, () -> inscriberItemInstance);
        } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            beTypeInstance = BlockEntityType.Builder.of(KnowledgeInscriberBlockEntity::new, blockInstance).build(null);
            event.register(Registries.BLOCK_ENTITY_TYPE, INSCRIBER_ID, () -> beTypeInstance);
        } else if (event.getRegistryKey().equals(Registries.MENU)) {
            menuTypeInstance = IMenuTypeExtension.create((id, inv, data) -> new KnowledgeInscriberMenu(id, inv, null));
            event.register(Registries.MENU, INSCRIBER_ID, () -> menuTypeInstance);
        }
    }

    @SubscribeEvent
    public static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                beTypeInstance,
                (inscriber, side) -> new InvWrapper(inscriber)
        );
    }

    @SubscribeEvent
    public static void onPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(
                KnowledgeInscriberActionPayload.TYPE,
                KnowledgeInscriberActionPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> KnowledgeInscriberActionPayload.handle(payload, (net.minecraft.server.level.ServerPlayer) context.player()))
        );
        registrar.playToClient(
                KnowledgeInscriberStatePayload.TYPE,
                KnowledgeInscriberStatePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> KnowledgeInscriberStatePayload.handle(payload, context.player()))
        );
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "main"))) {
            if (coreItemInstance != null) {
                event.accept(coreItemInstance);
            }
            if (inscriberItemInstance != null) {
                event.accept(inscriberItemInstance);
            }
        }
    }

    private KnowledgeRegistrar() {}
}
