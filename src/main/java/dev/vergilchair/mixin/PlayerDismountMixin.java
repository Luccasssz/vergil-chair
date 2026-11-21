package dev.vergilchair.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vergilchair.ChairSeat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class PlayerDismountMixin {
    @WrapOperation(method = "rideTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;stopRiding()V"))
    private void vergilchair$leaveChairSafely(Player player, Operation<Void> original) {
        if (player instanceof ServerPlayer serverPlayer
                && player.getVehicle() instanceof ChairSeat seat
                && !seat.allowDismount(serverPlayer)) {
            return;
        }
        original.call(player);
    }
}
