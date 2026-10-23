package thaumicenergistics;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.StorageCells;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.storage.CellContents;
import thaumicenergistics.storage.CellTier;
import thaumicenergistics.storage.EssentiaCell;
import thaumicenergistics.storage.EssentiaCellItem;
import thaumicenergistics.storage.EssentiaKeyType;
import thaumicenergistics.api.ContainerPermissions;
import thaumicenergistics.parts.EssentiaBusItem;
import appeng.api.parts.PartModels;
import appeng.api.upgrades.Upgrades;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;

@Mod(ThaumicEnergistics.ID)
public final class ThaumicEnergistics {
    public static final String ID = "thaumicenergistics";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final net.neoforged.neoforge.registries.DeferredBlock<thaumicenergistics.blocks.CellWorkbenchBlock> CELL_WORKBENCH =
            BLOCKS.register("essentia_cell_workbench", thaumicenergistics.blocks.CellWorkbenchBlock::new);
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> CELL_WORKBENCH_ITEM = ITEMS.registerSimpleBlockItem(CELL_WORKBENCH);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<thaumicenergistics.blocks.CellWorkbenchBlockEntity>> CELL_WORKBENCH_ENTITY =
            BLOCK_ENTITIES.register("essentia_cell_workbench", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                    thaumicenergistics.blocks.CellWorkbenchBlockEntity::new, CELL_WORKBENCH.get()).build(null));
    public static final net.neoforged.neoforge.registries.DeferredBlock<thaumicenergistics.blocks.VibrationChamberBlock> VIBRATION_CHAMBER=
            BLOCKS.register("essentia_vibration_chamber",thaumicenergistics.blocks.VibrationChamberBlock::new);
    public static final net.neoforged.neoforge.registries.DeferredItem<thaumicenergistics.items.VibrationChamberItem> VIBRATION_ITEM=
            ITEMS.register("essentia_vibration_chamber",()->new thaumicenergistics.items.VibrationChamberItem(VIBRATION_CHAMBER.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<thaumicenergistics.blocks.VibrationChamberBlockEntity>> VIBRATION_ENTITY=
            BLOCK_ENTITIES.register("essentia_vibration_chamber",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(thaumicenergistics.blocks.VibrationChamberBlockEntity::new,VIBRATION_CHAMBER.get()).build(null));
    public static final net.neoforged.neoforge.registries.DeferredBlock<thaumicenergistics.blocks.ProviderBlock<thaumicenergistics.blocks.EssentiaProviderBlockEntity>> ESSENTIA_PROVIDER=
            BLOCKS.register("essentia_provider",()->new thaumicenergistics.blocks.ProviderBlock<thaumicenergistics.blocks.EssentiaProviderBlockEntity>());
    public static final net.neoforged.neoforge.registries.DeferredBlock<thaumicenergistics.blocks.ProviderBlock<thaumicenergistics.blocks.InfusionProviderBlockEntity>> INFUSION_PROVIDER=
            BLOCKS.register("infusion_provider",()->new thaumicenergistics.blocks.ProviderBlock<thaumicenergistics.blocks.InfusionProviderBlockEntity>());
    public static final net.neoforged.neoforge.registries.DeferredItem<thaumicenergistics.items.ProviderItem> ESSENTIA_PROVIDER_ITEM=
            ITEMS.register("essentia_provider",()->new thaumicenergistics.items.ProviderItem(ESSENTIA_PROVIDER.get()));
    public static final net.neoforged.neoforge.registries.DeferredItem<thaumicenergistics.items.ProviderItem> INFUSION_PROVIDER_ITEM=
            ITEMS.register("infusion_provider",()->new thaumicenergistics.items.ProviderItem(INFUSION_PROVIDER.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<thaumicenergistics.blocks.EssentiaProviderBlockEntity>> ESSENTIA_PROVIDER_ENTITY=
            BLOCK_ENTITIES.register("essentia_provider",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(thaumicenergistics.blocks.EssentiaProviderBlockEntity::new,ESSENTIA_PROVIDER.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<thaumicenergistics.blocks.InfusionProviderBlockEntity>> INFUSION_PROVIDER_ENTITY=
            BLOCK_ENTITIES.register("infusion_provider",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(thaumicenergistics.blocks.InfusionProviderBlockEntity::new,INFUSION_PROVIDER.get()).build(null));
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<thaumicenergistics.menu.StorageBusMenu>> STORAGE_BUS_MENU =
            MENUS.register("essentia_storage_bus", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(thaumicenergistics.menu.StorageBusMenu::fromNetwork));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<thaumicenergistics.menu.StoragePriorityMenu>> PRIORITY_MENU =
            MENUS.register("storage_priority", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,data) -> new thaumicenergistics.menu.StoragePriorityMenu(id,inventory,null)));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<thaumicenergistics.menu.EssentiaBusMenu>> BUS_MENU =
            MENUS.register("essentia_bus", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(thaumicenergistics.menu.EssentiaBusMenu::fromNetwork));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<thaumicenergistics.menu.CellWorkbenchMenu>> WORKBENCH_MENU =
            MENUS.register("essentia_cell_workbench", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (id, inventory, data) -> new thaumicenergistics.menu.CellWorkbenchMenu(id, inventory, null)));
    private static final DeferredRegister<AEKeyType> KEY_TYPES = DeferredRegister.create(AEKeyType.REGISTRY_KEY, ID);
    private static final DeferredRegister<net.neoforged.neoforge.common.crafting.IngredientType<?>> INGREDIENT_TYPES=DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.INGREDIENT_TYPES,ID);
    public static final DeferredHolder<net.neoforged.neoforge.common.crafting.IngredientType<?>,net.neoforged.neoforge.common.crafting.IngredientType<thaumicenergistics.crafting.EmptyJarIngredient>> EMPTY_JAR_INGREDIENT=
            INGREDIENT_TYPES.register("empty_jar",()->new net.neoforged.neoforge.common.crafting.IngredientType<>(com.mojang.serialization.MapCodec.unit(new thaumicenergistics.crafting.EmptyJarIngredient())));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<thaumicenergistics.menu.EssentiaLevelEmitterMenu>> EMITTER_MENU =
            MENUS.register("essentia_level_emitter", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (id, inventory, data) -> new thaumicenergistics.menu.EssentiaLevelEmitterMenu(id,inventory,null)));
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<thaumicenergistics.menu.EssentiaTerminalMenu>> TERMINAL_MENU =
            MENUS.register("essentia_terminal",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,data)->new thaumicenergistics.menu.EssentiaTerminalMenu(id,inventory,null)));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<thaumicenergistics.menu.ArcaneTerminalMenu>> ARCANE_TERMINAL_MENU=
            MENUS.register("arcane_crafting_terminal",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,data)->new thaumicenergistics.menu.ArcaneTerminalMenu(id,inventory,null)));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<thaumicenergistics.menu.VibrationChamberMenu>> VIBRATION_MENU=
            MENUS.register("essentia_vibration_chamber",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,data)->new thaumicenergistics.menu.VibrationChamberMenu(id,inventory,null)));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CellContents>> CELL_CONTENTS =
            COMPONENTS.register("cell_contents", () -> DataComponentType.<CellContents>builder()
                    .persistent(CellContents.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(CellContents.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>,DataComponentType<thaumicenergistics.storage.TerminalSettings>> TERMINAL_SETTINGS =
            COMPONENTS.register("terminal_settings",()->DataComponentType.<thaumicenergistics.storage.TerminalSettings>builder()
                    .persistent(thaumicenergistics.storage.TerminalSettings.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(thaumicenergistics.storage.TerminalSettings.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>,DataComponentType<thaumicenergistics.storage.ChamberContents>> CHAMBER_CONTENTS=
            COMPONENTS.register("chamber_contents",()->DataComponentType.<thaumicenergistics.storage.ChamberContents>builder().persistent(thaumicenergistics.storage.ChamberContents.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(thaumicenergistics.storage.ChamberContents.CODEC)).build());
    public static final Map<CellTier, DeferredHolder<Item, EssentiaCellItem>> CELLS = new EnumMap<>(CellTier.class);
    public static final Map<CellTier, DeferredHolder<Item, Item>> COMPONENT_ITEMS = new EnumMap<>(CellTier.class);
    public static final DeferredHolder<Item, Item> STORAGE_CASING = ITEMS.register("storage_casing", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> DIFFUSION_CORE = ITEMS.register("diffusion_core", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> COALESCENCE_CORE = ITEMS.register("coalescence_core", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, EssentiaBusItem> IMPORT_BUS = ITEMS.register("essentia_import_bus", () -> new EssentiaBusItem(true));
    public static final DeferredHolder<Item, EssentiaBusItem> EXPORT_BUS = ITEMS.register("essentia_export_bus", () -> new EssentiaBusItem(false));
    public static final DeferredHolder<Item, thaumicenergistics.parts.EssentiaStorageBusItem> STORAGE_BUS = ITEMS.register("essentia_storage_bus", thaumicenergistics.parts.EssentiaStorageBusItem::new);
    public static final DeferredHolder<Item, thaumicenergistics.parts.VisInterfaceItem> VIS_INTERFACE = ITEMS.register("vis_interface", thaumicenergistics.parts.VisInterfaceItem::new);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredHolder<Item, thaumicenergistics.parts.EssentiaLevelEmitterItem> LEVEL_EMITTER = ITEMS.register("essentia_level_emitter",thaumicenergistics.parts.EssentiaLevelEmitterItem::new);
    public static final DeferredHolder<Item, thaumicenergistics.parts.EssentiaStorageMonitorItem> STORAGE_MONITOR = ITEMS.register("essentia_storage_monitor",thaumicenergistics.parts.EssentiaStorageMonitorItem::new);
    public static final DeferredHolder<Item, thaumicenergistics.parts.EssentiaConversionMonitorItem> CONVERSION_MONITOR = ITEMS.register("essentia_conversion_monitor",thaumicenergistics.parts.EssentiaConversionMonitorItem::new);
    public static final DeferredHolder<Item, thaumicenergistics.parts.EssentiaTerminalItem> ESSENTIA_TERMINAL = ITEMS.register("essentia_terminal",thaumicenergistics.parts.EssentiaTerminalItem::new);
    public static final DeferredHolder<Item,thaumicenergistics.items.WirelessEssentiaTerminalItem> WIRELESS_TERMINAL=ITEMS.register("wireless_essentia_terminal",thaumicenergistics.items.WirelessEssentiaTerminalItem::new);
    public static final DeferredHolder<Item,thaumicenergistics.parts.ArcaneTerminalItem> ARCANE_TERMINAL=ITEMS.register("arcane_crafting_terminal",thaumicenergistics.parts.ArcaneTerminalItem::new);

    static {
        KEY_TYPES.register("essentia", () -> EssentiaKeyType.INSTANCE);
        for (var tier : CellTier.values()) {
            CELLS.put(tier, ITEMS.register("essentia_cell_" + tier.suffix, () -> new EssentiaCellItem(tier)));
            if (!tier.creative()) COMPONENT_ITEMS.put(tier, ITEMS.register("storage_component_" + tier.suffix, () -> new Item(new Item.Properties())));
        }
        TABS.register("main", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.thaumicenergistics"))
                .icon(() -> new ItemStack(CELLS.get(CellTier.K1).get()))
                .displayItems((parameters, output) -> {
                    output.accept(STORAGE_CASING.get());
                    output.accept(DIFFUSION_CORE.get()); output.accept(COALESCENCE_CORE.get());
                    output.accept(CELL_WORKBENCH_ITEM.get());
                    output.accept(IMPORT_BUS.get()); output.accept(EXPORT_BUS.get());
                    output.accept(STORAGE_BUS.get());
                    output.accept(VIS_INTERFACE.get());
                    output.accept(LEVEL_EMITTER.get());
                    output.accept(STORAGE_MONITOR.get());
                    output.accept(CONVERSION_MONITOR.get());
                    output.accept(ESSENTIA_TERMINAL.get());
                    output.accept(ARCANE_TERMINAL.get());
                    output.accept(WIRELESS_TERMINAL.get());
                    output.accept(VIBRATION_ITEM.get());
                    output.accept(ESSENTIA_PROVIDER_ITEM.get());output.accept(INFUSION_PROVIDER_ITEM.get());
                    for (var tier : CellTier.values()) {
                        if (!tier.creative()) output.accept(COMPONENT_ITEMS.get(tier).get());
                        output.accept(CELLS.get(tier).get());
                    }
                }).build());
    }

    public ThaumicEnergistics(IEventBus modBus) {
        thaumicenergistics.crafting.ProviderResearchRegistration.initialize();
        thaumicenergistics.extras.ExtrasRegistrar.initialize(modBus);
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        INGREDIENT_TYPES.register(modBus);
        TABS.register(modBus);
        // AE2 creates its registry after mod constructors; register on the NeoForge registry event.
        KEY_TYPES.register(modBus);
        PartModels.registerModels(id("part/essentia_import_bus_off"), id("part/essentia_import_bus_on"), id("part/essentia_export_bus_off"), id("part/essentia_export_bus_on"));
        PartModels.registerModels(id("part/essentia_storage_bus_off"),id("part/essentia_storage_bus_on"));
        PartModels.registerModels(id("part/vis_interface_off"),id("part/vis_interface_on"));
        modBus.addListener(ThaumicEnergistics::setup);
        PartModels.registerModels(id("part/essentia_level_emitter_off"),id("part/essentia_level_emitter_on"));
        PartModels.registerModels(id("part/essentia_storage_monitor_off"),id("part/essentia_storage_monitor_on"),id("part/essentia_storage_monitor_empty_off"),id("part/essentia_storage_monitor_empty_on"));
        PartModels.registerModels(id("part/essentia_conversion_monitor_off"),id("part/essentia_conversion_monitor_on"),id("part/essentia_conversion_monitor_empty_off"),id("part/essentia_conversion_monitor_empty_on"));
        for(int rotation=0;rotation<4;rotation++)PartModels.registerModels(id("part/essentia_terminal_"+rotation+"_off"),id("part/essentia_terminal_"+rotation+"_on"));
        for(int rotation=0;rotation<4;rotation++)PartModels.registerModels(id("part/arcane_crafting_terminal_"+rotation+"_off"),id("part/arcane_crafting_terminal_"+rotation+"_on"));
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
                event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                        CELL_WORKBENCH_ENTITY.get(), (workbench, side) -> new net.neoforged.neoforge.items.wrapper.InvWrapper(workbench.inventory()));
                event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,VIBRATION_ENTITY.get(),(chamber,context)->chamber);
                event.registerBlockEntity(thaumcraft.api.aspects.EssentiaCapabilities.TRANSPORT,VIBRATION_ENTITY.get(),(chamber,side)->chamber);
                event.registerBlockEntity(thaumcraft.api.aspects.EssentiaCapabilities.CONTAINER,VIBRATION_ENTITY.get(),(chamber,context)->chamber);
                event.registerBlockEntity(thaumcraft.api.aspects.EssentiaCapabilities.SOURCE,VIBRATION_ENTITY.get(),(chamber,context)->chamber);
                event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,ESSENTIA_PROVIDER_ENTITY.get(),(provider,context)->provider);
                event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,INFUSION_PROVIDER_ENTITY.get(),(provider,context)->provider);
                event.registerBlockEntity(thaumcraft.api.aspects.EssentiaCapabilities.TRANSPORT,ESSENTIA_PROVIDER_ENTITY.get(),(provider,side)->provider);
                event.registerBlockEntity(thaumcraft.api.aspects.EssentiaCapabilities.CONTAINER,INFUSION_PROVIDER_ENTITY.get(),(provider,context)->provider);
                event.registerBlockEntity(thaumcraft.api.aspects.EssentiaCapabilities.SOURCE,INFUSION_PROVIDER_ENTITY.get(),(provider,context)->provider);
        });
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            StorageCells.addCellHandler(EssentiaCell.HANDLER);
            ContainerPermissions.registerDefaults();
            ContainerPermissions.register(id("essentia_vibration_chamber"),false,true,64);
            VIBRATION_CHAMBER.get().setBlockEntity(thaumicenergistics.blocks.VibrationChamberBlockEntity.class,VIBRATION_ENTITY.get(),null,null);
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(VIBRATION_ENTITY.get(),VIBRATION_ITEM.get());
            ESSENTIA_PROVIDER.get().setBlockEntity(thaumicenergistics.blocks.EssentiaProviderBlockEntity.class,ESSENTIA_PROVIDER_ENTITY.get(),(level,pos,state,provider)->provider.serverTick(),null);
            INFUSION_PROVIDER.get().setBlockEntity(thaumicenergistics.blocks.InfusionProviderBlockEntity.class,INFUSION_PROVIDER_ENTITY.get(),null,null);
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(ESSENTIA_PROVIDER_ENTITY.get(),ESSENTIA_PROVIDER_ITEM.get());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(INFUSION_PROVIDER_ENTITY.get(),INFUSION_PROVIDER_ITEM.get());
            appeng.api.features.GridLinkables.register(WIRELESS_TERMINAL.get(),appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER);
            for (var item : List.of(IMPORT_BUS.get(), EXPORT_BUS.get())) {
                Upgrades.add(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:speed_card")), item, 4);
                Upgrades.add(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:capacity_card")), item, 2);
                Upgrades.add(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:redstone_card")), item, 1);
            }
            Upgrades.add(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:inverter_card")),STORAGE_BUS.get(),1);
        });
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
}
