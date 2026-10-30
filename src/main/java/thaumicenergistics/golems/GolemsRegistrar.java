package thaumicenergistics.golems;

import appeng.api.features.GridLinkables;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Autonomous NeoForge EventBusSubscriber registrar for Gears, Gearboxes, and Golem Backpack.
 * Registers blocks, items, block entities, payloads, AE2 GridLinkables, and creative tab entries.
 */
@EventBusSubscriber(modid = ThaumicEnergistics.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class GolemsRegistrar {
    public static final ResourceLocation IRON_GEAR_ID = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "iron_gear");
    public static final ResourceLocation IRON_GEARBOX_ID = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "iron_gear_box");
    public static final ResourceLocation THAUMIUM_GEARBOX_ID = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "thaumium_gear_box");
    public static final ResourceLocation GEARBOX_BE_ID = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "gear_box");
    public static final ResourceLocation GOLEM_BACKPACK_ID = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "golem_wireless_backpack");

    private static IronGearItem ironGearInstance;
    private static GearboxBlock ironGearboxBlockInstance;
    private static GearboxBlock thaumiumGearboxBlockInstance;
    private static GearboxBlockItem ironGearboxItemInstance;
    private static GearboxBlockItem thaumiumGearboxItemInstance;
    private static BlockEntityType<GearboxBlockEntity> gearboxBeTypeInstance;
    private static WirelessGolemBackpackItem golemBackpackInstance;

    public static final Supplier<IronGearItem> IRON_GEAR_ITEM = () -> ironGearInstance;
    public static final Supplier<GearboxBlock> IRON_GEARBOX_BLOCK = () -> ironGearboxBlockInstance;
    public static final Supplier<GearboxBlock> THAUMIUM_GEARBOX_BLOCK = () -> thaumiumGearboxBlockInstance;
    public static final Supplier<GearboxBlockItem> IRON_GEARBOX_ITEM = () -> ironGearboxItemInstance;
    public static final Supplier<GearboxBlockItem> THAUMIUM_GEARBOX_ITEM = () -> thaumiumGearboxItemInstance;
    public static final Supplier<BlockEntityType<GearboxBlockEntity>> GEARBOX_BE = () -> gearboxBeTypeInstance;
    public static final Supplier<WirelessGolemBackpackItem> GOLEM_BACKPACK_ITEM = () -> golemBackpackInstance;

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            ironGearboxBlockInstance = new GearboxBlock(false);
            thaumiumGearboxBlockInstance = new GearboxBlock(true);
            event.register(Registries.BLOCK, IRON_GEARBOX_ID, () -> ironGearboxBlockInstance);
            event.register(Registries.BLOCK, THAUMIUM_GEARBOX_ID, () -> thaumiumGearboxBlockInstance);
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            ironGearInstance = new IronGearItem();
            ironGearboxItemInstance = new GearboxBlockItem(ironGearboxBlockInstance, new Item.Properties(), false);
            thaumiumGearboxItemInstance = new GearboxBlockItem(thaumiumGearboxBlockInstance, new Item.Properties(), true);
            golemBackpackInstance = new WirelessGolemBackpackItem();

            event.register(Registries.ITEM, IRON_GEAR_ID, () -> ironGearInstance);
            event.register(Registries.ITEM, IRON_GEARBOX_ID, () -> ironGearboxItemInstance);
            event.register(Registries.ITEM, THAUMIUM_GEARBOX_ID, () -> thaumiumGearboxItemInstance);
            event.register(Registries.ITEM, GOLEM_BACKPACK_ID, () -> golemBackpackInstance);
        } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            gearboxBeTypeInstance = BlockEntityType.Builder.of(
                    GearboxBlockEntity::new,
                    ironGearboxBlockInstance,
                    thaumiumGearboxBlockInstance
            ).build(null);
            event.register(Registries.BLOCK_ENTITY_TYPE, GEARBOX_BE_ID, () -> gearboxBeTypeInstance);
        }
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (golemBackpackInstance != null) {
                GridLinkables.register(golemBackpackInstance, WirelessGolemBackpackItem.LINKABLE_HANDLER);
            }
        });
    }

    @SubscribeEvent
    public static void onPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(GolemWirelessStatusPayload.TYPE, GolemWirelessStatusPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> GolemWirelessStatusPayload.handle(payload, context.player())));
        registrar.playToClient(
                GearboxSyncPayload.TYPE,
                GearboxSyncPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> GearboxSyncPayload.handle(payload, context.player()))
        );
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "main"))) {
            if (ironGearInstance != null) {
                event.accept(ironGearInstance);
            }
            if (ironGearboxItemInstance != null) {
                event.accept(ironGearboxItemInstance);
            }
            if (thaumiumGearboxItemInstance != null) {
                event.accept(thaumiumGearboxItemInstance);
            }
            if (golemBackpackInstance != null) {
                event.accept(golemBackpackInstance);
            }
        }
    }
}
