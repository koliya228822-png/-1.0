package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент: начался каст умения ability на ticks тиков (0 — каст закончился или прерван). */
public record CastBarPayload(String ability, int ticks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CastBarPayload> TYPE = new CustomPacketPayload.Type<>(HeroicPaths.id("cast_bar"));
    public static final StreamCodec<ByteBuf, CastBarPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CastBarPayload::ability,
            ByteBufCodecs.VAR_INT, CastBarPayload::ticks,
            CastBarPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
