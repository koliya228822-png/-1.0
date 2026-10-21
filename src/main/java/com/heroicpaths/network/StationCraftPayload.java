package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент → сервер: создать предмет по рецепту станка (all — сколько хватит материалов). */
public record StationCraftPayload(ResourceLocation recipe, BlockPos pos, boolean all) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StationCraftPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("station_craft"));
    public static final StreamCodec<ByteBuf, StationCraftPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, StationCraftPayload::recipe,
            BlockPos.STREAM_CODEC, StationCraftPayload::pos,
            ByteBufCodecs.BOOL, StationCraftPayload::all,
            StationCraftPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
