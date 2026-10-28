package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Клиент → сервер: {@link #TRAVEL} — переместиться к камню {@code target} в измерении {@code dimension} от камня
 * {@code source} (или свитком, если {@code scroll}); {@link #RENAME} — назвать камень {@code target} строкой {@code name}.
 */
public record WaystoneActionPayload(int action, ResourceLocation dimension, BlockPos target, BlockPos source, boolean scroll, String name)
        implements CustomPacketPayload {
    public static final int TRAVEL = 0, RENAME = 1;

    public static final CustomPacketPayload.Type<WaystoneActionPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("waystone_action"));
    public static final StreamCodec<ByteBuf, WaystoneActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WaystoneActionPayload::action,
            ResourceLocation.STREAM_CODEC, WaystoneActionPayload::dimension,
            BlockPos.STREAM_CODEC, WaystoneActionPayload::target,
            BlockPos.STREAM_CODEC, WaystoneActionPayload::source,
            ByteBufCodecs.BOOL, WaystoneActionPayload::scroll,
            ByteBufCodecs.stringUtf8(64), WaystoneActionPayload::name,
            WaystoneActionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
