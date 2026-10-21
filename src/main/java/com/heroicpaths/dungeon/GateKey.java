package com.heroicpaths.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Данные Камня врат: в какой данж ведёт и на сколько игроков зарегистрирована группа. */
public record GateKey(String dungeon, int partySize) {
    public static final Codec<GateKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dungeon").forGetter(GateKey::dungeon),
            Codec.intRange(1, DungeonType.MAX_PARTY).fieldOf("party").forGetter(GateKey::partySize)
    ).apply(instance, GateKey::new));

    public static final StreamCodec<ByteBuf, GateKey> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GateKey::dungeon,
            ByteBufCodecs.VAR_INT, GateKey::partySize,
            GateKey::new);
}
