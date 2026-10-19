package thaumicenergistics;

import appeng.api.stacks.AEKeyTypes;
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
import java.util.EnumMap;
import java.util.Map;

@Mod(ThaumicEnergistics.ID)
public final class ThaumicEnergistics {
    public static final String ID = "thaumicenergistics";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CellContents>> CELL_CONTENTS =
            COMPONENTS.register("cell_contents", () -> DataComponentType.<CellContents>builder()
                    .persistent(CellContents.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(CellContents.CODEC)).build());
    public static final Map<CellTier, DeferredHolder<Item, EssentiaCellItem>> CELLS = new EnumMap<>(CellTier.class);
    public static final Map<CellTier, DeferredHolder<Item, Item>> COMPONENT_ITEMS = new EnumMap<>(CellTier.class);
    public static final DeferredHolder<Item, Item> STORAGE_CASING = ITEMS.register("storage_casing", () -> new Item(new Item.Properties()));
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

    static {
        for (var tier : CellTier.values()) {
            CELLS.put(tier, ITEMS.register("essentia_cell_" + tier.suffix, () -> new EssentiaCellItem(tier)));
            if (!tier.creative()) COMPONENT_ITEMS.put(tier, ITEMS.register("storage_component_" + tier.suffix, () -> new Item(new Item.Properties())));
        }
        TABS.register("main", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.thaumicenergistics"))
                .icon(() -> new ItemStack(CELLS.get(CellTier.K1).get()))
                .displayItems((parameters, output) -> {
                    output.accept(STORAGE_CASING.get());
                    for (var tier : CellTier.values()) {
                        if (!tier.creative()) output.accept(COMPONENT_ITEMS.get(tier).get());
                        output.accept(CELLS.get(tier).get());
                    }
                }).build());
    }

    public ThaumicEnergistics(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        AEKeyTypes.register(EssentiaKeyType.INSTANCE);
        modBus.addListener(ThaumicEnergistics::setup);
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> StorageCells.addCellHandler(EssentiaCell.HANDLER));
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
}
