package dev.vergilchair.mixin;

import dev.vergilchair.ChairMotion;
import dev.vergilchair.ChairSeat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void vergilchair$poseRider(LivingEntity player, float limbSwing, float swingAmount, float age,
                                       float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!player.isAlive() || !(player.getVehicle() instanceof ChairSeat seat)) {
            return;
        }
        float partialTick = Mth.clamp(age - player.tickCount, 0.0F, 1.0F);
        ChairMotion.Frame frame = seat.frame(partialTick);
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        float viewYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float relativeYaw = Mth.wrapDegrees(viewYaw - seat.ridingYaw(viewYaw - netHeadYaw, partialTick));

        model.body.resetPose();
        model.head.resetPose();
        model.head.yRot = Mth.clamp(relativeYaw, -65, 65) * frame.look() * Mth.DEG_TO_RAD;
        model.head.xRot = (frame.headPitch() + Mth.clamp(headPitch, -35, 45) * frame.look()) * Mth.DEG_TO_RAD;
        model.head.zRot = frame.headRoll() * Mth.DEG_TO_RAD;
        vergilchair$poseLimb(model.rightArm, frame.rightArm());
        vergilchair$poseLimb(model.leftArm, frame.leftArm());
        vergilchair$poseLimb(model.rightLeg, frame.rightLeg());
        vergilchair$poseLimb(model.leftLeg, frame.leftLeg());

        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    @Unique
    private static void vergilchair$poseLimb(ModelPart part, ChairMotion.Limb pose) {
        part.resetPose();
        part.xRot = pose.pitch() * Mth.DEG_TO_RAD;
        part.zRot = pose.spread() * Mth.DEG_TO_RAD;
    }
}
