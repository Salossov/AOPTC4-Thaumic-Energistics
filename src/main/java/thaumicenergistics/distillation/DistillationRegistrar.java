package thaumicenergistics.distillation;

import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.api.ContainerPermissions;

/**
 * Autonomous registrar for Distillation Pattern Encoder.
 * Automatically registers Block, Item, BlockEntityType, MenuType, Client Screen,
 * Permissions and Creative Tab entry via NeoForge Mod Event Bus.
 * The shared content-config registrar owns its conditional TC4 research definition.
 */
@EventBusSubscriber(modid = ThaumicEnergistics.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DistillationRegistrar {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "distillation_pattern_encoder");

    public static final Supplier<DistillationPatternEncoderBlock> BLOCK =
            Suppliers.memoize(DistillationPatternEncoderBlock::new);

    public static final Supplier<BlockItem> ITEM =
            Suppliers.memoize(() -> new BlockItem(BLOCK.get(), new Item.Properties()));

    public static final Supplier<BlockEntityType<DistillationPatternEncoderBlockEntity>> BLOCK_ENTITY_TYPE =
            Suppliers.memoize(() -> BlockEntityType.Builder.of(DistillationPatternEncoderBlockEntity::new, BLOCK.get()).build(null));

    public static final Supplier<MenuType<DistillationPatternEncoderMenu>> MENU_TYPE =
            Suppliers.memoize(() -> IMenuTypeExtension.create(DistillationPatternEncoderMenu::new));

    private DistillationRegistrar() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            event.register(Registries.BLOCK, ID, BLOCK::get);
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            event.register(Registries.ITEM, ID, ITEM::get);
        } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            event.register(Registries.BLOCK_ENTITY_TYPE, ID, BLOCK_ENTITY_TYPE::get);
        } else if (event.getRegistryKey().equals(Registries.MENU)) {
            event.register(Registries.MENU, ID, MENU_TYPE::get);
        }
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "main"))) {
            event.accept(ITEM.get());
        }
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ContainerPermissions.register(ID, false, false, 64);
        });
    }

    @EventBusSubscriber(modid = ThaumicEnergistics.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientRegistration {
        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(MENU_TYPE.get(), DistillationPatternEncoderScreen::new);
        }
    }
}
