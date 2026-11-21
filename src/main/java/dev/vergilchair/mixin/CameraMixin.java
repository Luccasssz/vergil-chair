package dev.vergilchair.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vergilchair.ChairMotion;
import dev.vergilchair.ChairSeat;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
abstract class CameraMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
    private float vergilchair$seatedEyeHeight(Entity entity, Operation<Float> original) {
        float eyeHeight = original.call(entity);
        if (entity instanceof Player player && entity.getVehicle() instanceof ChairSeat seat) {
            ChairMotion.Frame frame = seat.frame(1.0F);
            float upright = Mth.cos(frame.pitch() * Mth.DEG_TO_RAD) * Mth.cos(frame.roll() * Mth.DEG_TO_RAD);
            float scale = player.getScale();
            return frame.hipHeight() * scale + (eyeHeight - ChairMotion.STANDING_HIP * scale) * Math.max(.06F, upright);
        }
        return eyeHeight;
    }
}
