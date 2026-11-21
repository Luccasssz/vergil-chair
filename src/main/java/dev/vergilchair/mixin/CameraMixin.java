package dev.vergilchair.mixin;

import dev.vergilchair.ChairMotion;
import dev.vergilchair.ChairSeat;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
    private float vergilchair$seatedEyeHeight(Entity entity) {
        float vanilla = entity.getEyeHeight();
        if (entity instanceof Player player && entity.getVehicle() instanceof ChairSeat seat) {
            ChairMotion.Frame frame = seat.frame(1.0F);
            float upright = Mth.cos(frame.pitch() * Mth.DEG_TO_RAD) * Mth.cos(frame.roll() * Mth.DEG_TO_RAD);
            float scale = player.getScale();
            return frame.hipHeight() * scale + (vanilla - ChairMotion.STANDING_HIP * scale) * Math.max(.06F, upright);
        }
        return vanilla;
    }
}
