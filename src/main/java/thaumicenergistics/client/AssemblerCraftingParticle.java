/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Adapted from Applied Energistics 2 RV3 CraftingFx by AlgorithmX2 and contributors.
 * Copyright (c) 2013 - 2014, AlgorithmX2. Licensed under LGPL-3.0-or-later.
 * This adaptation is distributed without warranty. See licenses/AE2-LGPL-3.0.txt
 * and licenses/GPL-3.0.txt. Original source: AE2 commit ed54f03e7022e1509a0d722a7014fd7a5e6cdbf8.
 */
package thaumicenergistics.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/** Legacy CraftingFx movement, block clipping, color and per-tick exponential shrink. */
final class AssemblerCraftingParticle extends TextureSheetParticle {
    private final BlockPos start;
    AssemblerCraftingParticle(ClientLevel level, double x, double y, double z,
                             double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        start = BlockPos.containing(x, y, z); pickSprite(sprites);
        xd = vx; yd = vy; zd = vz; gravity = 0; hasPhysics = false;
        rCol = 1; gCol = .9F; bCol = 1; alpha = 1.3F; quadSize = .15F;
        // Original EntityFX constructor, then CraftingFx's compound division by1.2.
        lifetime = (int) ((int) (4F / (random.nextFloat() * .9F + .1F)) / 1.2);
    }
    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) remove();
        move(xd, yd, zd);
        xd *= .98F; yd *= .98F; zd *= .98F;
        quadSize *= .51F; alpha *= .51F;
    }
    @Override public void render(VertexConsumer vertices, Camera camera, float partial) {
        if (partial < 0 || partial > 1) return;
        // The old effect clips to the block in world float coordinates, before camera subtraction.
        float px = (float) (xo + (x - xo) * partial), py = (float) (yo + (y - yo) * partial), pz = (float) (zo + (z - zo) * partial);
        if (Mth.floor(px) != start.getX() || Mth.floor(py) != start.getY() || Mth.floor(pz) != start.getZ()) return;
        float originalAlpha = alpha;
        // Legacy OpenGL clamps 1.3 to1; byte color conversion must not wrap that initial alpha.
        alpha = Math.min(1, originalAlpha);
        try { super.render(vertices, camera, partial); } finally { alpha = originalAlpha; }
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
}
