/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Adapted from Applied Energistics 2 RV3 AssemblerFX / EntityFloatingItem / RenderFloatingItem.
 * Copyright (c) 2013 - 2014, AlgorithmX2 and contributors. LGPL-3.0-or-later.
 * This adaptation is distributed without warranty. See licenses/AE2-LGPL-3.0.txt
 * and licenses/GPL-3.0.txt. Original source: AE2 commit ed54f03e7022e1509a0d722a7014fd7a5e6cdbf8.
 */
package thaumicenergistics.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import appeng.core.AEConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.assembler.AssemblerAnimationPayload;

/** Cosmetic-only client particles. No item entity, world inventory or server output is created. */
@EventBusSubscriber(modid=ThaumicEnergistics.MOD_ID, value=Dist.CLIENT, bus=EventBusSubscriber.Bus.GAME)
public final class AssemblerAnimationEffects {
    private static final List<Animation> ACTIVE = new ArrayList<>();
    private static ClientLevel currentLevel;
    private static SpriteSet sprites;
    private static int itemAge;

    public static void install() { AssemblerAnimationPayload.installClient(AssemblerAnimationEffects::receive); }
    public static void sprites(SpriteSet value) { sprites = value; }
    public static void clear() {
        for (var animation : ACTIVE) animation.remove();
        ACTIVE.clear(); currentLevel = null;
    }
    private static void useLevel(ClientLevel level) {
        if (currentLevel != level) { clear(); currentLevel = level; }
    }
    private static void receive(AssemblerAnimationPayload payload) {
        var minecraft = Minecraft.getInstance(); var level = minecraft.level;
        if (level == null || !level.dimension().equals(payload.dimension()) || !AEConfig.instance().isEnableEffects()) return;
        useLevel(level);
        var pos = payload.position();
        var animation = new Animation(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                payload.rate(), payload.lifetime(), payload.product());
        ACTIVE.add(animation); minecraft.particleEngine.add(animation);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        // Original TickHandler increments its global floating-item age even across worlds/pauses.
        itemAge = (itemAge + 1) % 60000;
        useLevel(Minecraft.getInstance().level);
        ACTIVE.removeIf(animation -> !animation.isAlive());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var minecraft = Minecraft.getInstance(); useLevel(minecraft.level);
        if (currentLevel == null || ACTIVE.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var buffers = minecraft.renderBuffers().bufferSource();
        for (var animation : List.copyOf(ACTIVE)) if (animation.isAlive())
            animation.render(event.getPoseStack(), buffers, event.getCamera().getPosition(), partial);
        buffers.endBatch();
    }

    private static final class Animation extends NoRenderParticle {
        private final int rate;
        private final ItemStack product;
        private float particleTime;
        Animation(ClientLevel level, double x, double y, double z, int rate, int lifetime, ItemStack product) {
            super(level, x, y, z);
            this.rate = rate; this.lifetime = lifetime; this.product = product.copy(); hasPhysics = false;
        }
        @Override public void tick() { if (age++ >= lifetime) remove(); }
        void render(PoseStack poses, MultiBufferSource buffers, Vec3 camera, float partial) {
            // Quirk kept: source AssemblerFX accumulates render partials, not elapsed tick deltas.
            particleTime += partial;
            if (particleTime > 4) {
                particleTime -= 4;
                if (sprites != null && AEConfig.instance().isEnableEffects()) for (int i = 0; i < (int) Math.ceil(rate / 5F); i++) {
                    float dx = (float) (((Math.abs(random.nextInt()) % 100) * .01) - .5) * .7F;
                    float dy = (float) (((Math.abs(random.nextInt()) % 100) * .01) - .5) * .7F;
                    float dz = (float) (((Math.abs(random.nextInt()) % 100) * .01) - .5) * .7F;
                    Minecraft.getInstance().particleEngine.add(new AssemblerCraftingParticle(level,
                            x + dx, y + dy, z + dz, -dx * .2, -dy * .2, -dz * .2, sprites));
                }
            }
            if (age <= 0 || (float) age / lifetime > .99F || !level.hasChunkAt(BlockPos.containing(x, y, z))) return;
            var renderer = Minecraft.getInstance().getItemRenderer();
            var model = renderer.getModel(product, level, null, 0);
            var spread = new Random(187);
            int count = product.getCount();
            int copies = count > 40 ? 5 : count > 20 ? 4 : count > 5 ? 3 : count > 1 ? 2 : 1;
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
            poses.pushPose();
            try {
                poses.translate(x - camera.x, y - camera.y - (product.getItem() instanceof BlockItem ? 0 : .15)
                        + .25F * model.getTransforms().ground.scale.y(), z - camera.z);
                poses.mulPose(Axis.YP.rotation((itemAge + partial) / 20F + .5F));
                // Modern baked GROUND models replace old sprite/block display lists. Preserve
                // source copy thresholds, deterministic spread and absence of vertical bobbing.
                for (int i = 0; i < copies; i++) {
                    poses.pushPose();
                    try {
                        if (i > 0) {
                            if (model.isGui3d()) poses.translate((spread.nextFloat() * 2 - 1) * .2F,
                                    (spread.nextFloat() * 2 - 1) * .2F, (spread.nextFloat() * 2 - 1) * .2F);
                            else poses.translate(0, 0, i * .0625F);
                        }
                        renderer.renderStatic(product, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                                poses, buffers, level, 0);
                    } finally { poses.popPose(); }
                }
            } finally { poses.popPose(); }
        }
    }
    private AssemblerAnimationEffects() {}
}
