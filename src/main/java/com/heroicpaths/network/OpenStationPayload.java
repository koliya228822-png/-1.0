package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент: открыть окно станка ремесла. */
public record OpenStationPayload(int station, BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenStationPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("open_station"));
    public static final StreamCodec<ByteBuf, OpenStationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenStationPayload::station,
            BlockPos.STREAM_CODEC, OpenStationPayload::pos,
            OpenStationPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
