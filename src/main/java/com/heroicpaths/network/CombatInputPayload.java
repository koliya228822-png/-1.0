package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Клиент → сервер: боевой ввод, который игра сама не передаёт.
 * <ul>
 *     <li>POWER_ATTACK — отпущена заряженная правая кнопка: силовой удар (value — сколько тиков её держали);</li>
 *     <li>CANCEL_CAST — двойной пробел: отменить текущий каст;</li>
 *     <li>QUICK_SHOT — щелчок ПКМ с луком под «Охотничьим азартом».</li>
 * </ul>
 */
public record CombatInputPayload(Kind kind, int value) implements CustomPacketPayload {
    public enum Kind { POWER_ATTACK, CANCEL_CAST, QUICK_SHOT }

    public static final CustomPacketPayload.Type<CombatInputPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("combat_input"));
    public static final StreamCodec<ByteBuf, CombatInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[Math.floorMod(i, Kind.values().length)], Kind::ordinal),
            CombatInputPayload::kind,
            ByteBufCodecs.VAR_INT, CombatInputPayload::value,
            CombatInputPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
