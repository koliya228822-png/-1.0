package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент: текущая стамина игрока (для полоски на экране). */
public record StaminaPayload(float stamina) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StaminaPayload> TYPE = new CustomPacketPayload.Type<>(HeroicPaths.id("stamina"));
    public static final StreamCodec<ByteBuf, StaminaPayload> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(StaminaPayload::new, StaminaPayload::stamina);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
