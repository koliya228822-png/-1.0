package com.heroicpaths.village;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Наставники гильдий (и распорядитель с глашатаем в зале) живут при своём рабочем месте: дом — это
 * их верстак, из дома они не выходят, на сборы у колокола не ходят, спят только в кровати рядом.
 * Вышедшего за дверь или ушедшего далеко (толпой вытолкнуло, заманили) наставника сразу ставит обратно в дом. Погибшего через пять игровых дней сменяет новый наставник
 * той же профессии: он появляется у того же рабочего места, когда там загружен мир.
 */
public final class MentorHomes {
    static final String HOME = HeroicPaths.MODID + ".home";
    private static final String AWAY = HeroicPaths.MODID + ".away";
    /** Радиус, в котором наставник свободно бродит вокруг рабочего места. */
    public static final int LEASH = 8;
    /** Кровать и прочие точки дальше этого от дома наставник отпускает. */
    private static final int POI_RANGE = 14;
    /** Как часто проверяется, что наставник дома: за полсекунды он не успеет отойти от двери. */
    private static final int CHECK_TICKS = 10;
    /** Как часто торговцы за прилавком проверяют свой товар. */
    private static final int STOCK_TICKS = 40;
    public static final long RESPAWN_TICKS = 5 * 24000L;

    private MentorHomes() {}

    public static boolean isKeeper(Villager villager) {
        VillagerProfession p = villager.getVillagerData().getProfession();
        return Set.of(ModVillagers.SWORDMASTER.get(), ModVillagers.MASTER_ARCHER.get(), ModVillagers.ARCHMAGE.get(),
                ModVillagers.GUILDMASTER.get(), ModVillagers.BOUNTY_HERALD.get()).contains(p) || MerchantPosts.isPost(p);
    }

    @Nullable
    public static BlockPos home(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        return data.contains(HOME, Tag.TAG_LONG) ? BlockPos.of(data.getLong(HOME)) : null;
    }

    public static void setHome(Villager villager, BlockPos home) {
        villager.getPersistentData().putLong(HOME, home.asLong());
        villager.restrictTo(home, LEASH);
    }

    @SubscribeEvent
    public static void onVillagerTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        // Банкир, оружейник, книжник и скупщик стоят на посту строже наставников (см. MerchantPosts).
        if (!villager.isBaby() && MerchantPosts.isPost(villager)) {
            if (villager.tickCount % MerchantPosts.CHECK_TICKS == 0) MerchantPosts.keepPost(level, villager);
            if (villager.tickCount % STOCK_TICKS == 0) MerchantPosts.stock(level, villager);
            return;
        }
        if (villager.tickCount % CHECK_TICKS != 0 || villager.isBaby() || !isKeeper(villager)) return;
        keepHome(level, villager);
    }

    /** Одна проверка «поводка»: запомнить дом, отпустить дальние точки, вернуть домой ушедшего. */
    public static void keepHome(ServerLevel level, Villager villager) {
        BlockPos home = home(villager);
        if (home == null) {
            home = findHome(level, villager);
            setHome(villager, home);
        } else if (!villager.hasRestriction()) {
            villager.restrictTo(home, LEASH);
        }
        Brain<Villager> brain = villager.getBrain();
        // Сборы у колокола: без точки сбора распорядок «встреча» не включается, и наставник остаётся дома.
        releaseIf(villager, MemoryModuleType.MEETING_POINT, level, home, -1);
        releaseIf(villager, MemoryModuleType.HOME, level, home, POI_RANGE);
        releaseIf(villager, MemoryModuleType.JOB_SITE, level, home, POI_RANGE);
        releaseIf(villager, MemoryModuleType.POTENTIAL_JOB_SITE, level, home, POI_RANGE);

        // Дом под крышей — значит, выходить под открытое небо нельзя: ни идти к двери, ни стоять за ней.
        boolean roofed = !level.canSeeSky(home.above());
        BlockPos center = home;
        brain.getMemory(MemoryModuleType.WALK_TARGET).ifPresent(target -> {
            if (!inside(level, center, roofed, target.getTarget().currentBlockPosition())) {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                brain.eraseMemory(MemoryModuleType.PATH);
            }
        });
        CompoundTag data = villager.getPersistentData();
        data.remove(AWAY);
        if (villager.isSleeping() || inside(level, home, roofed, villager.blockPosition())) return;
        // Вышел (вытолкали, заманили) — сразу обратно в дом.
        BlockPos spot = level.isPositionEntityTicking(home) ? standingSpot(level, home) : null;
        if (spot == null) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(home, 0.6f, 2));
            return;
        }
        villager.stopRiding();
        villager.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        villager.setDeltaMovement(0, 0, 0);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
    }

    /** Клетка в доме наставника: недалеко от рабочего места и, если дом под крышей, не под открытым небом. */
    static boolean inside(ServerLevel level, BlockPos home, boolean roofed, BlockPos pos) {
        if (!pos.closerThan(home, LEASH)) return false;
        return !roofed || !level.canSeeSky(pos);
    }

    /** Дом — рабочее место наставника; если он его ещё не занял — ближайшее подходящее рядом с ним. */
    private static BlockPos findHome(ServerLevel level, Villager villager) {
        Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (site.isPresent() && site.get().dimension() == level.dimension()) return site.get().pos();
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return level.getPoiManager()
                .findClosest(profession.acquirableJobSite(), villager.blockPosition(), 24, PoiManager.Occupancy.ANY)
                .orElse(villager.blockPosition());
    }

    /** Отпускает точку из памяти, если она дальше {@code range} от дома (range < 0 — всегда). */
    static void releaseIf(Villager villager, MemoryModuleType<GlobalPos> memory, ServerLevel level, BlockPos home, int range) {
        Optional<GlobalPos> pos = villager.getBrain().getMemory(memory);
        if (pos.isEmpty()) return;
        if (range >= 0 && pos.get().dimension() == level.dimension() && pos.get().pos().closerThan(home, range)) return;
        villager.releasePoi(memory);
        villager.getBrain().eraseMemory(memory);
    }

    /** Свободная клетка с опорой под ногами рядом с рабочим местом. */
    @Nullable
    static BlockPos standingSpot(ServerLevel level, BlockPos home) {
        for (int r = 1; r <= 3; r++) {
            for (int dy = 0; dy >= -1; dy--) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos pos = home.offset(dx, dy, dz);
                        if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                                && level.getFluidState(pos).isEmpty()
                                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- смена погибших

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        if (!isKeeper(villager) || villager.isBaby()) return;
        BlockPos home = home(villager);
        if (home == null) home = findHome(level, villager);
        Graves.get(level.getServer()).add(new Grave(level.dimension(), home,
                BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()),
                BuiltInRegistries.VILLAGER_TYPE.getKey(villager.getVillagerData().getType()),
                level.getServer().overworld().getDayTime(), level.getGameTime()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 100 != 0) return;
        replaceFallen(server, 0);
    }

    /**
     * Ставит новых наставников вместо погибших пять дней назад. {@code skipTicks} сдвигает «сейчас» вперёд (для тестов).
     * @return сколько наставников появилось
     */
    public static int replaceFallen(MinecraftServer server, long skipTicks) {
        Graves graves = Graves.get(server);
        if (graves.graves.isEmpty()) return 0;
        long day = server.overworld().getDayTime() + skipTicks;
        int spawned = 0;
        for (Grave grave : new ArrayList<>(graves.graves)) {
            ServerLevel level = server.getLevel(grave.dimension());
            if (level == null) {
                graves.remove(grave);
                continue;
            }
            long now = level.getGameTime() + skipTicks;
            // Время суток могли перевести назад командой — тогда считаем по общему времени мира.
            boolean due = (day >= grave.diedDay() && day - grave.diedDay() >= RESPAWN_TICKS) || now - grave.diedTick() >= RESPAWN_TICKS;
            if (!due || !level.isPositionEntityTicking(grave.home())) continue;
            Optional<VillagerProfession> profession = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(grave.profession());
            if (profession.isEmpty()) {
                graves.remove(grave);
                continue;
            }
            graves.remove(grave);
            // Кто-то уже занял место (вылечили зомби-жителя, поставили нового) — второго не нужно.
            boolean taken = !level.getEntitiesOfClass(Villager.class, new AABB(grave.home()).inflate(LEASH * 2),
                    v -> v.isAlive() && v.getVillagerData().getProfession() == profession.get()).isEmpty();
            if (taken) continue;
            if (spawn(level, grave, profession.get()) != null) spawned++;
        }
        return spawned;
    }

    @Nullable
    private static Villager spawn(ServerLevel level, Grave grave, VillagerProfession profession) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return null;
        boolean post = MerchantPosts.isPost(profession);
        // Банкир, оружейник, книжник и скупщик встают ровно на пост за прилавком, наставники — рядом с рабочим местом.
        BlockPos spot = post ? grave.home() : standingSpot(level, grave.home());
        if (spot == null) spot = grave.home().above();
        VillagerType type = BuiltInRegistries.VILLAGER_TYPE.getOptional(grave.type()).orElse(VillagerType.PLAINS);
        villager.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.STRUCTURE, null);
        villager.setVillagerData(villager.getVillagerData().setType(type).setProfession(profession).setLevel(1));
        // Опыт больше нуля: новый наставник не теряет профессию, пока не занял рабочее место.
        villager.setVillagerXp(1);
        villager.setPersistenceRequired();
        setHome(villager, grave.home());
        level.addFreshEntity(villager);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1, villager.getZ(), 12, 0.4, 0.6, 0.4, 0);
        HeroicPaths.LOGGER.info("A new {} took the fallen mentor's place at {}", grave.profession(), grave.home());
        return villager;
    }

    public record Grave(ResourceKey<Level> dimension, BlockPos home, ResourceLocation profession, ResourceLocation type,
                        long diedDay, long diedTick) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", dimension.location().toString());
            tag.putLong("home", home.asLong());
            tag.putString("profession", profession.toString());
            tag.putString("type", type.toString());
            tag.putLong("day", diedDay);
            tag.putLong("tick", diedTick);
            return tag;
        }

        @Nullable
        static Grave load(CompoundTag tag) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
            ResourceLocation profession = ResourceLocation.tryParse(tag.getString("profession"));
            ResourceLocation type = ResourceLocation.tryParse(tag.getString("type"));
            if (dim == null || profession == null || type == null) return null;
            return new Grave(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(tag.getLong("home")), profession, type,
                    tag.getLong("day"), tag.getLong("tick"));
        }
    }

    /** Погибшие наставники, ждущие смены. Хранится в данных верхнего мира. */
    public static final class Graves extends SavedData {
        private static final String NAME = "heroicpaths_mentor_graves";
        final List<Grave> graves = new ArrayList<>();

        public static Graves get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Graves::new, Graves::load), NAME);
        }

        public List<Grave> all() {
            return List.copyOf(graves);
        }

        void add(Grave grave) {
            graves.add(grave);
            setDirty();
        }

        void remove(Grave grave) {
            graves.remove(grave);
            setDirty();
        }

        private static Graves load(CompoundTag tag, HolderLookup.Provider registries) {
            Graves data = new Graves();
            for (Tag entry : tag.getList("graves", Tag.TAG_COMPOUND)) {
                Grave grave = Grave.load((CompoundTag) entry);
                if (grave != null) data.graves.add(grave);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Grave grave : graves) list.add(grave.save());
            tag.put("graves", list);
            return tag;
        }
    }
}
