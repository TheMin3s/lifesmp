package com.schecks.lifesmp;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client packet carrying a player's own lives count.
 *
 * Only clients that have the LifeSMP mod installed register the receiver for
 * this channel; vanilla clients never do, which is exactly how the server
 * decides (via ServerPlayNetworking.canSend) whether to send the packet or
 * fall back to an action-bar message.
 */
public record LivesPayload(int lives, int maxLives) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LivesPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.parse("lifesmp:lives"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LivesPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LivesPayload::lives,
            ByteBufCodecs.VAR_INT, LivesPayload::maxLives,
            LivesPayload::new
        );

    @Override
    public CustomPacketPayload.Type<LivesPayload> type() {
        return TYPE;
    }
}
