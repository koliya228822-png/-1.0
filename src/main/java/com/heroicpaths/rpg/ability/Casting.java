package com.heroicpaths.rpg.ability;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.network.CastBarPayload;
import com.heroicpaths.registry.ModParticles;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Произнесение сильных умений: эффект срабатывает не сразу, а через время каста (AbilityDef.castTicks).
 * Пока идёт каст, персонаж замедлен вдвое, вокруг него кружат частицы стихии, на экране — полоска каста;
 * у заклинаний по площади точка удара выбирается в начале и подсвечивается. Ресурс и перезарядка тратятся в конце.
 * Каст прерывается, если убрать нужное оружие, умереть или уйти в другой мир.
 */
public final class Casting {
    private static final ResourceLocation SLOW_ID = HeroicPaths.id("casting");
    private static final double CAST_SLOW = 0.5;

    public record Cast(ServerPlayer player, ServerLevel level, AbilityDef def, long start, long end, @Nullable Vec3 target) {}

    private static final Map<UUID, Cast> CASTS = new HashMap<>();
    /** Точка удара каста, который сейчас завершается (её берёт AbilityLogic.groundTarget). */
    private static final Map<UUID, Vec3> FINISHING = new HashMap<>();

    private Casting() {}

    public static boolean isCasting(Player player) {
        return CASTS.containsKey(player.getUUID());
    }

    /** Начать произнесение: умение сработает через ticks тиков. */
    public static void begin(ServerPlayer player, AbilityDef def, int ticks) {
        ServerLevel level = player.serverLevel();
        Vec3 target = AbilityLogic.groundRange(def) > 0 ? AbilityLogic.pickGround(player, AbilityLogic.groundRange(def)) : null;
        long now = level.getGameTime();
        CASTS.put(player.getUUID(), new Cast(player, level, def, now, now + ticks, target));
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(SLOW_ID))
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, -CAST_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        bar(player, def.id(), ticks);
        Fx.sound(level, player.position(), def.kind() == AbilityDef.DamageKind.SPELL || def.kind() == AbilityDef.DamageKind.MINION
                ? SoundEvents.EVOKER_PREPARE_ATTACK : SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.8f, 1.2f);
    }

    /** Точка удара, выбранная в начале каста (null — не каст или умение не по площади). */
    @Nullable
    static Vec3 finishingTarget(ServerPlayer player) {
        return FINISHING.get(player.getUUID());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (Cast cast : new ArrayList<>(CASTS.values())) {
            ServerPlayer player = cast.player;
            if (player.isRemoved() || !player.isAlive() || player.level() != cast.level) {
                stop(player, null);
                continue;
            }
            if (!cast.def.weapon().isMet(player)) {
                stop(player, "message.heroicpaths.cast_interrupted");
                continue;
            }
            long now = cast.level.getGameTime();
            effects(cast, now);
            if (now >= cast.end) finish(player);
        }
    }

    /** Мгновенно завершить текущий каст (для тестов и команд). */
    public static void finishNow(ServerPlayer player) {
        if (isCasting(player)) finish(player);
    }

    private static void finish(ServerPlayer player) {
        Cast cast = CASTS.get(player.getUUID());
        if (cast == null) return;
        stop(player, null);
        // Сила срывается с руки: сноп искр (без вспышки — у самой камеры она закрыла бы обзор).
        Vec3 hand = hand(player);
        Palette palette = castPalette(cast.def, player);
        Fx.embers(cast.level, palette, hand, 14, 0.05, 0.15);
        Fx.wisps(cast.level, palette, player.getEyePosition().add(player.getLookAngle().scale(2.2)), 3, 0.1, 0.03);
        if (cast.target != null) FINISHING.put(player.getUUID(), cast.target);
        try {
            RpgManager.finishCast(player, cast.def);
        } finally {
            FINISHING.remove(player.getUUID());
        }
    }

    /** Убрать каст без эффекта; messageKey — что сказать игроку (null — молча). */
    public static void stop(ServerPlayer player, @Nullable String messageKey) {
        Cast cast = CASTS.remove(player.getUUID());
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SLOW_ID);
        if (cast == null) return;
        bar(player, cast.def.id(), 0);
        if (messageKey != null)
            player.displayClientMessage(Component.translatable(messageKey, cast.def.name()).withStyle(ChatFormatting.RED), true);
    }

    private static void bar(ServerPlayer player, String ability, int ticks) {
        if (player.connection != null && player.connection.hasChannel(CastBarPayload.TYPE))
            PacketDistributor.sendToPlayer(player, new CastBarPayload(ability, ticks));
    }

    private static Vec3 hand(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        return player.getEyePosition().add(look.scale(0.7)).add(right.scale(0.35)).add(0, -0.35, 0);
    }

    /** Гамма каста: по стихии умения, у мага крови и призывателя — своя. */
    public static Palette castPalette(AbilityDef def, ServerPlayer player) {
        var subclass = RpgManager.get(player).getSubclass();
        if (subclass == com.heroicpaths.rpg.Subclass.BLOOD_MAGE) return Palette.BLOOD;
        if (def.kind() == AbilityDef.DamageKind.MINION && subclass == com.heroicpaths.rpg.Subclass.SUMMONER) return Palette.SHADOW;
        if (def.kind() == AbilityDef.DamageKind.MINION) return Palette.NATURE;
        return Palette.of(def.element());
    }

    /** Эффекты каста: круг под ногами, искры стягиваются к руке, метка точки удара. */
    private static void effects(Cast cast, long now) {
        ServerPlayer player = cast.player;
        ServerLevel level = cast.level;
        Palette palette = castPalette(cast.def, player);
        double progress = (double) (now - cast.start) / Math.max(1, cast.end - cast.start);
        Vec3 feet = player.position().add(0, 0.1, 0);
        double phase = now * 0.3;
        // Круг под ногами: три бегущие точки и контур, к концу каста ярче.
        for (int i = 0; i < 3; i++) {
            double angle = phase + i * Math.PI * 2 / 3;
            Fx.one(level, ModParticles.ember(palette), feet.add(Math.cos(angle) * 1.1, 0, Math.sin(angle) * 1.1), new Vec3(0, 0.04, 0));
        }
        if (now % 3 == 0) Fx.ring(level, ModParticles.mark(palette), feet, 1.1, 14, 0, 0);
        // Сила стягивается к руке.
        Vec3 hand = hand(player);
        for (int i = 0; i < 2; i++) {
            Vec3 from = hand.add(level.random.nextGaussian() * 0.6, level.random.nextGaussian() * 0.6, level.random.nextGaussian() * 0.6);
            Fx.one(level, ModParticles.ember(palette), from, hand.subtract(from).scale(0.25));
        }
        if (progress > 0.5) level.sendParticles(ModParticles.ember(palette), hand.x, hand.y, hand.z, 2, 0.05, 0.05, 0.05, 0.03);
        // Где ударит.
        if (cast.target != null && now % 4 == 0)
            Fx.ring(level, ModParticles.mark(palette), cast.target.add(0, 0.12, 0), AbilityLogic.areaRadius(player, cast.def), 20, 0, 0);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        CASTS.clear();
        FINISHING.clear();
    }
}
