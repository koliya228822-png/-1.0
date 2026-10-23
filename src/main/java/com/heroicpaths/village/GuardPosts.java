package com.heroicpaths.village;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.VillageGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Стража деревни как служба: погибшего стражника через сутки сменяет новый на том же месте (у казармы
 * или на посту у ворот), нечисть замечает стражу и нападает на неё, а стрелы стражи не ранят жителей,
 * игроков и големов.
 */
public final class GuardPosts {
    public static final long RESPAWN_TICKS = 24000L;

    private GuardPosts() {}

    public static void onGuardDied(ServerLevel level, VillageGuard guard) {
        BlockPos home = guard.home() != null ? guard.home() : guard.blockPosition();
        Vacancies.get(level.getServer()).add(new Vacancy(level.dimension(), home, BuiltInRegistries.ENTITY_TYPE.getKey(guard.getType()),
                guard.post() != null, level.getGameTime()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 == 0) fillVacancies(event.getServer(), 0);
    }

    /** Ставит новых стражников на места погибших сутки назад. @return сколько появилось. */
    public static int fillVacancies(MinecraftServer server, long skipTicks) {
        Vacancies data = Vacancies.get(server);
        if (data.vacancies.isEmpty()) return 0;
        int spawned = 0;
        for (Vacancy v : new ArrayList<>(data.vacancies)) {
            ServerLevel level = server.getLevel(v.dimension());
            if (level == null) {
                data.remove(v);
                continue;
            }
            if (level.getGameTime() + skipTicks - v.diedTick() < RESPAWN_TICKS || !level.isPositionEntityTicking(v.home())) continue;
            data.remove(v);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(v.type());
            Entity entity = type.create(level);
            if (!(entity instanceof VillageGuard guard)) continue;
            BlockPos spot = MentorHomes.standingSpot(level, v.home());
            if (spot == null) spot = v.home();
            guard.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
            guard.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.STRUCTURE, null);
            if (v.sentry()) guard.setPost(v.home());
            level.addFreshEntity(guard);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, guard.getX(), guard.getY() + 1, guard.getZ(), 10, 0.4, 0.6, 0.4, 0);
            spawned++;
        }
        return spawned;
    }

    /** Нечисть видит в страже врага, как в железных големах. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Monster monster) || !VillageGuard.isFoe(monster)) return;
        monster.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(monster, VillageGuard.class, true));
    }

    /** Стрелы стражи пролетают сквозь своих. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile.getOwner() instanceof VillageGuard) || !(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        Entity target = hit.getEntity();
        if (target instanceof VillageGuard || target instanceof Villager || target instanceof Player || target instanceof IronGolem) {
            event.setCanceled(true);
        }
    }

    public record Vacancy(ResourceKey<Level> dimension, BlockPos home, ResourceLocation type, boolean sentry, long diedTick) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", dimension.location().toString());
            tag.putLong("home", home.asLong());
            tag.putString("type", type.toString());
            tag.putBoolean("sentry", sentry);
            tag.putLong("tick", diedTick);
            return tag;
        }

        @Nullable
        static Vacancy load(CompoundTag tag) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
            ResourceLocation type = ResourceLocation.tryParse(tag.getString("type"));
            if (dim == null || type == null) return null;
            return new Vacancy(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(tag.getLong("home")), type,
                    tag.getBoolean("sentry"), tag.getLong("tick"));
        }
    }

    public static final class Vacancies extends SavedData {
        private static final String NAME = HeroicPaths.MODID + "_guard_vacancies";
        final List<Vacancy> vacancies = new ArrayList<>();

        public static Vacancies get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Vacancies::new, Vacancies::load), NAME);
        }

        public List<Vacancy> all() {
            return List.copyOf(vacancies);
        }

        void add(Vacancy v) {
            vacancies.add(v);
            setDirty();
        }

        void remove(Vacancy v) {
            vacancies.remove(v);
            setDirty();
        }

        private static Vacancies load(CompoundTag tag, HolderLookup.Provider registries) {
            Vacancies data = new Vacancies();
            for (Tag entry : tag.getList("vacancies", Tag.TAG_COMPOUND)) {
                Vacancy v = Vacancy.load((CompoundTag) entry);
                if (v != null) data.vacancies.add(v);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Vacancy v : vacancies) list.add(v.save());
            tag.put("vacancies", list);
            return tag;
        }
    }
}
