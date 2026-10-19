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
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<thaumicenergistics.menu.EssentiaBusMenu>> BUS_MENU =
            MENUS.register("essentia_bus", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(thaumicenergistics.menu.EssentiaBusMenu::fromNetwork));
    private static final DeferredRegister<AEKeyType> KEY_TYPES = DeferredRegister.create(AEKeyType.REGISTRY_KEY, ID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CellContents>> CELL_CONTENTS =
            COMPONENTS.register("cell_contents", () -> DataComponentType.<CellContents>builder()
                    .persistent(CellContents.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(CellContents.CODEC)).build());
    public static final Map<CellTier, DeferredHolder<Item, EssentiaCellItem>> CELLS = new EnumMap<>(CellTier.class);
    public static final Map<CellTier, DeferredHolder<Item, Item>> COMPONENT_ITEMS = new EnumMap<>(CellTier.class);
    public static final DeferredHolder<Item, Item> STORAGE_CASING = ITEMS.register("storage_casing", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, EssentiaBusItem> IMPORT_BUS = ITEMS.register("essentia_import_bus", () -> new EssentiaBusItem(true));
    public static final DeferredHolder<Item, EssentiaBusItem> EXPORT_BUS = ITEMS.register("essentia_export_bus", () -> new EssentiaBusItem(false));
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

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
                    output.accept(IMPORT_BUS.get()); output.accept(EXPORT_BUS.get());
                    for (var tier : CellTier.values()) {
                        if (!tier.creative()) output.accept(COMPONENT_ITEMS.get(tier).get());
                        output.accept(CELLS.get(tier).get());
                    }
                }).build());
    }

    public ThaumicEnergistics(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        // AE2 creates its registry after mod constructors; register on the NeoForge registry event.
        KEY_TYPES.register(modBus);
        PartModels.registerModels(id("part/essentia_import_bus_off"), id("part/essentia_import_bus_on"), id("part/essentia_export_bus_off"), id("part/essentia_export_bus_on"));
        modBus.addListener(ThaumicEnergistics::setup);
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            StorageCells.addCellHandler(EssentiaCell.HANDLER);
            ContainerPermissions.registerDefaults();
            for (var item : List.of(IMPORT_BUS.get(), EXPORT_BUS.get())) {
                Upgrades.add(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:speed_card")), item, 4);
                Upgrades.add(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:capacity_card")), item, 2);
                Upgrades.add(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:redstone_card")), item, 1);
            }
        });
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
}
