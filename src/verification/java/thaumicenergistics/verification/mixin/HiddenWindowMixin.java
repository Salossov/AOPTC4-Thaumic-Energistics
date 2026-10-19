package thaumicenergistics.verification.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loaded only by the explicit development-run --mixin argument; never packaged. */
@Mixin(Window.class)
public abstract class HiddenWindowMixin {
    @Inject(method="<init>",at=@At(value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V",shift=At.Shift.AFTER,remap=false))
    private void hiddenCreation(CallbackInfo ci) {
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);
        System.setProperty("thaumicenergistics.hiddenWindowHook","true");
    }
}
