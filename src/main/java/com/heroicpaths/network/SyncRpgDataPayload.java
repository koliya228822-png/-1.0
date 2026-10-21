package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент: полное состояние RPG-прогресса игрока. */
public record SyncRpgDataPayload(CompoundTag data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncRpgDataPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("sync_rpg_data"));
    public static final StreamCodec<ByteBuf, SyncRpgDataPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(SyncRpgDataPayload::new, SyncRpgDataPayload::data);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
