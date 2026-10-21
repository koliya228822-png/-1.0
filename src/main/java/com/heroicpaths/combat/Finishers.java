package com.heroicpaths.combat;

import com.heroicpaths.entity.DungeonBoss;
import com.heroicpaths.entity.SpellProjectile;
import com.heroicpaths.guild.GuildOrders;
import com.heroicpaths.minion.Minion;
import com.heroicpaths.registry.ModDamageTypes;
import com.heroicpaths.rpg.ability.AbilityLogic;
import com.heroicpaths.rpg.ability.DelayedTasks;
import com.heroicpaths.rpg.stat.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Добивания: с шансом 10% обычный моб, убитый игроком (или его зверем), гибнет зрелищно — по стихии смертельного удара.
 * Огонь сжигает дотла, лёд превращает в ледяную статую и раскалывает, земля погребает, гроза бьёт молнией,
 * свет забирает в столб сияния, яд растворяет. Удары оружием: режущее — фонтан крови, дробящее — дробит кости,
 * колющее — пронзает. Боссов, игроков и чужих зверей добивания не касаются.
 */
public final class Finishers {
    public static final float CHANCE = 0.10f;
    public static final String STATUE_TAG = "heroicpaths.finisher";

    public enum Style {
        FIRE(ChatFormatting.GOLD), FROST(ChatFormatting.AQUA), EARTH(ChatFormatting.DARK_GREEN), STORM(ChatFormatting.YELLOW),
        HOLY(ChatFormatting.WHITE), POISON(ChatFormatting.GREEN), BLOOD(ChatFormatting.RED), SHATTER(ChatFormatting.GRAY),
        PIERCE(ChatFormatting.BLUE);

        private final ChatFormatting color;

        Style(ChatFormatting color) {
            this.color = color;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private Finishers() {}

    /** Обычный моб: не босс, не зверь и не слуга игрока. */
    public static boolean canFinish(LivingEntity victim) {
        return victim instanceof Mob && !(victim instanceof DungeonBoss) && !(victim instanceof Minion)
                && !victim.getType().is(Tags.EntityTypes.BOSSES);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.isCanceled() || !(victim.level() instanceof ServerLevel level) || !canFinish(victim)) return;
        ServerPlayer killer = GuildOrders.creditedPlayer(event.getSource().getEntity());
        if (killer == null || victim.getRandom().nextFloat() >= CHANCE) return;
        play(level, victim, styleFor(event.getSource(), killer), killer);
    }

    /** Стиль добивания по смертельному удару. */
    public static Style styleFor(DamageSource source, @Nullable ServerPlayer killer) {
        Element element = AbilityLogic.abilityElement();
        if (element != null && element != Element.PHYSICAL) return byElement(element);
        if (source.getDirectEntity() instanceof SpellProjectile spell) return spell.getItem().is(Items.PACKED_ICE) ? Style.FROST : Style.FIRE;
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) return Style.FIRE;
        if (source.is(DamageTypeTags.IS_FREEZING)) return Style.FROST;
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return Style.STORM;
        if (source.is(ModDamageTypes.BLEEDING)) return Style.BLOOD;
        if (source.getDirectEntity() instanceof AbstractArrow arrow) return arrow.isOnFire() ? Style.FIRE : Style.PIERCE;
        if (killer != null && source.getDirectEntity() == killer) {
            WeaponType weapon = WeaponType.of(killer.getMainHandItem());
            if (weapon == WeaponType.CRUSHING) return Style.SHATTER;
            if (weapon == WeaponType.PIERCING) return Style.PIERCE;
        }
        return Style.BLOOD;
    }

    public static Style byElement(Element element) {
        return switch (element) {
            case FIRE -> Style.FIRE;
            case WATER -> Style.FROST;
            case EARTH -> Style.EARTH;
            case AIR -> Style.STORM;
            case HOLY -> Style.HOLY;
            case POISON -> Style.POISON;
            case PHYSICAL -> Style.BLOOD;
        };
    }

    /** Проигрывает добивание над телом. */
    public static void play(ServerLevel level, LivingEntity victim, Style style, @Nullable ServerPlayer killer) {
        Vec3 at = victim.position();
        float width = Math.max(0.6f, victim.getBbWidth());
        float height = Math.max(0.6f, victim.getBbHeight());
        double mid = at.y + height / 2;
        switch (style) {
            case FIRE -> {
                DelayedTasks.repeat(0, 3, 5, () -> {
                    level.sendParticles(ParticleTypes.FLAME, at.x, mid, at.z, 25, width / 2, height / 2, width / 2, 0.04);
                    level.sendParticles(ParticleTypes.LAVA, at.x, mid, at.z, 3, width / 3, height / 3, width / 3, 0);
                });
                hide(victim, 8);
                DelayedTasks.schedule(10, () -> {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, mid, at.z, 20, width / 2, height / 2, width / 2, 0.02);
                    level.sendParticles(ParticleTypes.WHITE_ASH, at.x, mid, at.z, 60, width, height / 2, width, 0.02);
                    sound(level, at, SoundEvents.FIRE_EXTINGUISH, 1f, 0.8f);
                });
                sound(level, at, SoundEvents.FIRECHARGE_USE, 1f, 0.7f);
            }
            case FROST -> {
                Entity statue = statue(level, at, Blocks.PACKED_ICE.defaultBlockState(), width * 1.1f, height);
                hide(victim, 0);
                level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, mid, at.z, 40, width / 2, height / 2, width / 2, 0.05);
                sound(level, at, SoundEvents.GLASS_PLACE, 1f, 0.6f);
                DelayedTasks.schedule(30, () -> {
                    if (statue != null) statue.discard();
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                            at.x, mid, at.z, 60, width / 2, height / 2, width / 2, 0.1);
                    sound(level, at, SoundEvents.GLASS_BREAK, 1.2f, 0.8f);
                });
            }
            case EARTH -> {
                Entity mound = statue(level, at, Blocks.ROOTED_DIRT.defaultBlockState(), width * 1.3f, height * 0.7f);
                hide(victim, 4);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                        at.x, at.y + 0.2, at.z, 50, width, 0.2, width, 0.1);
                sound(level, at, SoundEvents.ROOTED_DIRT_PLACE, 1.2f, 0.6f);
                DelayedTasks.schedule(30, () -> {
                    if (mound != null) mound.discard();
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                            at.x, at.y + 0.3, at.z, 40, width, 0.3, width, 0.1);
                    sound(level, at, SoundEvents.GRAVEL_BREAK, 1f, 0.6f);
                });
            }
            case STORM -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(at);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, mid, at.z, 50, width / 2, height / 2, width / 2, 0.3);
                hide(victim, 3);
            }
            case HOLY -> {
                DelayedTasks.repeat(0, 2, 6, () -> level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 4, at.z, 30, width / 3, 4, width / 3, 0.01));
                hide(victim, 10);
                DelayedTasks.schedule(10, () -> level.sendParticles(ParticleTypes.FLASH, at.x, mid, at.z, 1, 0, 0, 0, 0));
                sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.4f);
            }
            case POISON -> {
                ParticleOptions green = new DustParticleOptions(new Vector3f(0.25f, 0.75f, 0.15f), 1.4f);
                DelayedTasks.repeat(0, 3, 5, () -> {
                    level.sendParticles(green, at.x, mid, at.z, 20, width / 2, height / 2, width / 2, 0);
                    level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y + 0.3, at.z, 6, width / 2, 0.2, width / 2, 0.02);
                });
                hide(victim, 12);
                sound(level, at, SoundEvents.SLIME_SQUISH, 1f, 0.6f);
            }
            case BLOOD -> {
                ParticleOptions blood = new DustParticleOptions(new Vector3f(0.6f, 0.02f, 0.02f), 1.6f);
                DelayedTasks.repeat(0, 2, 4, () -> level.sendParticles(blood, at.x, mid, at.z, 30, width / 2, height / 2, width / 2, 0.15));
                sound(level, at, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.6f);
            }
            case SHATTER -> {
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE)), at.x, mid, at.z, 40, width / 2, height / 2, width / 2, 0.15);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), at.x, at.y + 0.1, at.z, 30, width, 0.1, width, 0.1);
                hide(victim, 0);
                sound(level, at, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.8f, 1.2f);
            }
            case PIERCE -> {
                Vec3 look = killer != null ? killer.getLookAngle() : new Vec3(1, 0, 0);
                for (int i = -4; i <= 4; i++) {
                    Vec3 p = at.add(0, height / 2, 0).add(look.scale(i * 0.4));
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 3, 0.05, 0.05, 0.05, 0);
                }
                level.sendParticles(ParticleTypes.CRIT, at.x, mid, at.z, 30, width / 2, height / 2, width / 2, 0.4);
                sound(level, at, SoundEvents.TRIDENT_HIT, 1f, 0.8f);
            }
        }
        if (killer != null) {
            killer.displayClientMessage(Component.translatable("finisher.heroicpaths." + style.id()).withStyle(style.color, ChatFormatting.BOLD), true);
        }
    }

    /** Тело исчезает (остаются эффекты добивания). */
    private static void hide(LivingEntity victim, int delay) {
        if (delay <= 0) victim.setInvisible(true);
        else DelayedTasks.schedule(delay, () -> victim.setInvisible(true));
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** Временная «статуя» из блока на месте тела: ледяная глыба или земляной холм. */
    @Nullable
    public static Entity statue(ServerLevel level, Vec3 at, BlockState state, float width, float height) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        tag.put("block_state", NbtUtils.writeBlockState(state));
        CompoundTag transformation = new CompoundTag();
        transformation.put("left_rotation", floats(0, 0, 0, 1));
        transformation.put("right_rotation", floats(0, 0, 0, 1));
        transformation.put("translation", floats(-width / 2, 0, -width / 2));
        transformation.put("scale", floats(width, height, width));
        tag.put("transformation", transformation);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(STATUE_TAG));
        tag.put("Tags", tags);
        Entity statue = EntityType.loadEntityRecursive(tag, level, entity -> {
            entity.moveTo(at.x, at.y, at.z, 0f, 0f);
            return entity;
        });
        if (statue != null) level.addFreshEntity(statue);
        return statue;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) list.add(FloatTag.valueOf(value));
        return list;
    }

    /** Статуи живут секунды; если мир сохранили раньше, при загрузке они не возвращаются. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(STATUE_TAG)) event.setCanceled(true);
    }
}
