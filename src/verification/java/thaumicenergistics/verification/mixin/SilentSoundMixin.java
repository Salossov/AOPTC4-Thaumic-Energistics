package thaumicenergistics.verification.mixin;

import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public abstract class SilentSoundMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void markSilentPlayback(CallbackInfo ci) { System.setProperty("thaumicenergistics.silentSoundHook","true"); }
    @Inject(method="play",at=@At("HEAD"),cancellable=true)
    private void suppressPlayback(CallbackInfo ci) { ci.cancel(); }
}
