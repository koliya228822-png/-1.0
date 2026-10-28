package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Сервер → клиент: известные игроку камни перемещения. {@code mode}: {@link #SYNC} — только обновить миникарту,
 * {@link #FROM_BLOCK} — открыть выбор точки у камня {@code source}, {@link #FROM_SCROLL} — выбор точки по свитку.
 */
public record WaystoneListPayload(int mode, BlockPos source, List<Point> points) implements CustomPacketPayload {
    public static final int SYNC = 0, FROM_BLOCK = 1, FROM_SCROLL = 2;

    /** Точка: измерение, позиция камня, название и своя ли она. */
    public record Point(ResourceLocation dimension, BlockPos pos, String name, boolean own) {
        public static final StreamCodec<ByteBuf, Point> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Point::dimension,
                BlockPos.STREAM_CODEC, Point::pos,
                ByteBufCodecs.stringUtf8(64), Point::name,
                ByteBufCodecs.BOOL, Point::own,
                Point::new);
    }

    public static final CustomPacketPayload.Type<WaystoneListPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("waystone_list"));
    public static final StreamCodec<ByteBuf, WaystoneListPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WaystoneListPayload::mode,
            BlockPos.STREAM_CODEC, WaystoneListPayload::source,
            Point.STREAM_CODEC.apply(ByteBufCodecs.list(256)), WaystoneListPayload::points,
            WaystoneListPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
