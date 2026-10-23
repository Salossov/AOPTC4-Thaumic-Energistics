package thaumicenergistics.golems;

import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * BlockItem for Iron and Thaumium Gearbox blocks with custom 3D TESR inventory rendering.
 */
public class GearboxBlockItem extends BlockItem {
    private final boolean isThaumium;

    public GearboxBlockItem(Block block, Properties properties, boolean isThaumium) {
        super(block, properties);
        this.isThaumium = isThaumium;
    }

    public boolean isThaumium() {
        return isThaumium;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GearboxItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new GearboxItemRenderer(isThaumium);
                }
                return renderer;
            }
        });
    }
}
