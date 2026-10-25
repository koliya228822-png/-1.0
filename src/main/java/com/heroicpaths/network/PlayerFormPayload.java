package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Сервер → клиент: игрок entityId на ticks тиков принял облик form (werewolf — волколак, spin — вихрь клинка,
 * camera — свой вид от третьего лица). ticks = 0 снимает облик.
 */
public record PlayerFormPayload(int entityId, String form, int ticks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlayerFormPayload> TYPE = new CustomPacketPayload.Type<>(HeroicPaths.id("player_form"));
    public static final StreamCodec<ByteBuf, PlayerFormPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlayerFormPayload::entityId,
            ByteBufCodecs.STRING_UTF8, PlayerFormPayload::form,
            ByteBufCodecs.VAR_INT, PlayerFormPayload::ticks,
            PlayerFormPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
