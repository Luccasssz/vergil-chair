package dev.vergilchair;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChairAction(boolean fall) implements CustomPacketPayload {
    public static final Type<ChairAction> TYPE = new Type<>(VergilChair.id("pose"));
    public static final StreamCodec<FriendlyByteBuf, ChairAction> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, ChairAction::fall, ChairAction::new);

    @Override
    public Type<ChairAction> type() {
        return TYPE;
    }
}
