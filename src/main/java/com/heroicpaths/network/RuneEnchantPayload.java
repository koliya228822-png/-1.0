package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент → сервер: наложить рунные чары на вещь в основной руке у рунного стола. */
public record RuneEnchantPayload(ResourceLocation enchantment, BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RuneEnchantPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("rune_enchant"));
    public static final StreamCodec<ByteBuf, RuneEnchantPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, RuneEnchantPayload::enchantment,
            BlockPos.STREAM_CODEC, RuneEnchantPayload::pos,
            RuneEnchantPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
