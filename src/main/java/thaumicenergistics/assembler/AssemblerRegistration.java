package thaumicenergistics.assembler;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThaumicEnergistics;

/** Own registration group; knowledge-core registrations remain with the separate knowledge unit. */
public final class AssemblerRegistration {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ThaumicEnergistics.ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ThaumicEnergistics.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ThaumicEnergistics.ID);
    public static final net.neoforged.neoforge.registries.DeferredBlock<ArcaneAssemblerBlock> BLOCK=BLOCKS.register("arcane_assembler",ArcaneAssemblerBlock::new);
    public static final net.neoforged.neoforge.registries.DeferredItem<ArcaneAssemblerItem> ITEM=ITEMS.register("arcane_assembler",()->new ArcaneAssemblerItem(BLOCK.get()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ArcaneAssemblerBlockEntity>> BLOCK_ENTITY=ENTITIES.register("arcane_assembler",()->BlockEntityType.Builder.of(ArcaneAssemblerBlockEntity::new,BLOCK.get()).build(null));
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,ThaumicEnergistics.ID);
    public static final DeferredHolder<MenuType<?>,MenuType<ArcaneAssemblerMenu>> MENU=MENUS.register("arcane_assembler",()->IMenuTypeExtension.create(ArcaneAssemblerMenu::new));
    private static final DeferredRegister<net.neoforged.neoforge.common.crafting.IngredientType<?>> INGREDIENTS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.INGREDIENT_TYPES,ThaumicEnergistics.ID);
    public static final DeferredHolder<net.neoforged.neoforge.common.crafting.IngredientType<?>,
            net.neoforged.neoforge.common.crafting.IngredientType<AssemblerSceptreIngredient>> SCEPTRE_INGREDIENT =
            INGREDIENTS.register("assembler_sceptre",()->new net.neoforged.neoforge.common.crafting.IngredientType<>(AssemblerSceptreIngredient.CODEC));
    public static void initialize(IEventBus bus){
        AssemblerParticles.initialize(bus);
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);INGREDIENTS.register(bus);
        bus.addListener((FMLCommonSetupEvent event)->event.enqueueWork(()->{
            BLOCK.get().setBlockEntity(ArcaneAssemblerBlockEntity.class,BLOCK_ENTITY.get(),null,(level,pos,state,assembler)->assembler.serverTick());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(BLOCK_ENTITY.get(),ITEM.get());
            appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.SPEED_CARD,ITEM.get(),4);
        }));
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event)->
                event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,BLOCK_ENTITY.get(),(assembler,context)->assembler));
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event)->{
            if(event.getTabKey().location().equals(ThaumicEnergistics.id("main")))event.accept(ITEM.get());
        });
    }
    private AssemblerRegistration(){}
}
