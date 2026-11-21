package dev.vergilchair.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.vergilchair.ChairMotion;
import dev.vergilchair.ChairSeat;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
abstract class PlayerRendererMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void vergilchair$rotateAroundHips(AbstractClientPlayer player, PoseStack stack, float age,
                                              float bodyYaw, float partialTick, float scale, CallbackInfo ci) {
        if (player.isAlive() && player.getVehicle() instanceof ChairSeat seat) {
            ChairMotion.Frame frame = seat.frame(partialTick);
            stack.mulPose(Axis.YP.rotationDegrees(180.0F - seat.ridingYaw(bodyYaw, partialTick)));
            stack.translate(0.0F, frame.hipHeight(), -frame.forward());
            stack.mulPose(Axis.ZP.rotationDegrees(frame.roll()));
            stack.mulPose(Axis.XP.rotationDegrees(frame.pitch()));
            stack.translate(0.0F, -ChairMotion.STANDING_HIP, 0.0F);
            ci.cancel();
        }
    }
}
