package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент: уровень моба (обычный уровень вида и сопротивление, −1 — нет) для подсказки под прицелом. */
public record MobLevelPayload(int entityId, int level, int base, int resist) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MobLevelPayload> TYPE = new CustomPacketPayload.Type<>(HeroicPaths.id("mob_level"));
    public static final StreamCodec<ByteBuf, MobLevelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MobLevelPayload::entityId,
            ByteBufCodecs.VAR_INT, MobLevelPayload::level,
            ByteBufCodecs.VAR_INT, MobLevelPayload::base,
            ByteBufCodecs.VAR_INT, MobLevelPayload::resist,
            MobLevelPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
