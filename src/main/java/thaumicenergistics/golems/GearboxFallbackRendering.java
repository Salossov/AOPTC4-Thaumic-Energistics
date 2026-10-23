package thaumicenergistics.golems;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.block.state.BlockState;

/** Render the existing fallback block model directly, avoiding the BEWLR dispatch recursion. */
final class GearboxFallbackRendering {
    static void render(BlockState state, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        dispatcher.getModelRenderer().renderModel(poses.last(),
                buffers.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS)), state,
                dispatcher.getBlockModel(state), 1, 1, 1, light, overlay);
    }

    private GearboxFallbackRendering() { }
}
