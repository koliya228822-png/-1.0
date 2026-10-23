package com.heroicpaths.entity;

import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.registry.ModVillagers;
import com.heroicpaths.village.GuardPosts;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Стражник деревни — мечник со щитом или лучник. Живёт в казарме, но не спит: днём и ночью обходит деревню
 * от зала гильдии до стен, бьёт любую нечисть, что подберётся, и вступается за раненых товарищей.
 * Часовой (лучник на воротах) не уходит со своего поста. Погибшего через сутки сменяет новый — см. {@link GuardPosts}.
 */
public class VillageGuard extends PathfinderMob implements RangedAttackMob {
    /** Насколько далеко от центра деревни (зала гильдии) уходит патруль. */
    public static final int PATROL_RADIUS = 72;

    @Nullable
    private BlockPos home;
    @Nullable
    private BlockPos center;
    @Nullable
    private BlockPos post;
    /** Стена своей деревни (ищется один раз): патруль не выходит за неё. */
    @Nullable
    private com.heroicpaths.village.VillageWallPiece wall;
    private boolean wallLooked;

    public VillageGuard(EntityType<? extends VillageGuard> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        if (getNavigation() instanceof GroundPathNavigation nav) nav.setCanOpenDoors(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 34.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    public boolean isArcher() {
        return getType() == ModEntities.GUARD_ARCHER.get();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        if (isArcher()) goalSelector.addGoal(2, new BowGoal(this));
        else goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        goalSelector.addGoal(5, new PatrolGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, VillageGuard.class, AbstractVillager.class).setAlertOthers());
        // Мечники на криперов не бросаются — их снимают лучники.
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
                e -> isFoe(e) && (isArcher() || !(e instanceof Creeper))));
    }

    /** Враг стражи: нечисть, кроме призванных игроком слуг и нейтральных эндерменов и пиглинов. */
    public static boolean isFoe(LivingEntity e) {
        return e instanceof Enemy && !(e instanceof com.heroicpaths.minion.Minion) && !(e instanceof net.minecraft.world.entity.monster.EnderMan)
                && !(e instanceof net.minecraft.world.entity.monster.ZombifiedPiglin) && !(e instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        equip();
        return result;
    }

    public void equip() {
        if (isArcher()) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        } else {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.STEEL_SWORD.get()));
            setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.IRON_HEATER_SHIELD.get()));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && tickCount % 20 == 0) {
            if (getMainHandItem().isEmpty()) equip();
            if (home == null) home = blockPosition();
            if (center == null && tickCount % 200 == 20) center = findCenter(level);
        }
    }

    /** Центр деревни — доска заказов распорядителя в зале гильдии; иначе — казарма. */
    private BlockPos findCenter(ServerLevel level) {
        Optional<BlockPos> board = level.getPoiManager().findClosest(p -> p.is(ModVillagers.GUILDMASTER_POI.getKey()),
                blockPosition(), 128, PoiManager.Occupancy.ANY);
        return board.orElse(home != null ? home : blockPosition());
    }

    /** Точка внутри стен своей деревни (без стены — в пределах {@link #PATROL_RADIUS} от центра). */
    public boolean insideVillage(int x, int z) {
        if (!wallLooked && level() instanceof ServerLevel level) {
            wallLooked = true;
            net.minecraft.world.level.levelgen.structure.StructureStart start = level.structureManager()
                    .getStructureWithPieceAt(blockPosition(), net.minecraft.tags.StructureTags.VILLAGE);
            if (start.isValid()) {
                for (var piece : start.getPieces()) if (piece instanceof com.heroicpaths.village.VillageWallPiece w) wall = w;
            }
        }
        if (wall != null) return wall.outside(x + 0.5, z + 0.5) < -4;
        BlockPos c = patrolCenter();
        return Math.hypot(x - c.getX(), z - c.getZ()) <= PATROL_RADIUS;
    }

    public BlockPos patrolCenter() {
        return center != null ? center : home != null ? home : blockPosition();
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    @Nullable
    public BlockPos post() {
        return post;
    }

    public void setPost(BlockPos post) {
        this.post = post;
        this.home = post;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        if (center != null) tag.put("Center", NbtUtils.writeBlockPos(center));
        if (post != null) tag.put("Post", NbtUtils.writeBlockPos(post));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home = NbtUtils.readBlockPos(tag, "Home").orElse(null);
        center = NbtUtils.readBlockPos(tag, "Center").orElse(null);
        post = NbtUtils.readBlockPos(tag, "Post").orElse(null);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Свои стрелы и удары своих не ранят.
        if (source.getEntity() instanceof VillageGuard) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level && !isRemoved()) GuardPosts.onGuardDied(level, this);
        super.die(source);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack bow = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, power, bow);
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        double dy = target.getY(0.3333) - arrow.getY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2, dz, 1.7f, 4f);
        arrow.setBaseDamage(arrow.getBaseDamage() + 1.5);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        playSound(SoundEvents.SKELETON_SHOOT, 1f, 1f / (getRandom().nextFloat() * 0.4f + 0.8f));
        level().addFreshEntity(arrow);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VINDICATOR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VINDICATOR_DEATH;
    }

    // ---------------------------------------------------------------- поведение

    /** Обход деревни: точка в пределах ~28 блоков от текущей, но не дальше {@link #PATROL_RADIUS} от центра. Часовой стоит на посту. */
    static class PatrolGoal extends Goal {
        private final VillageGuard guard;
        private int pause;

        PatrolGoal(VillageGuard guard) {
            this.guard = guard;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (guard.getTarget() != null) return false;
            if (pause > 0) {
                pause--;
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return guard.getTarget() == null && !guard.getNavigation().isDone();
        }

        @Override
        public void start() {
            BlockPos post = guard.post();
            if (post != null) {
                if (!guard.blockPosition().closerThan(post, 2)) guard.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 0.7);
                pause = 40;
                return;
            }
            BlockPos target = nextPoint();
            if (target == null || !guard.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.62)) {
                pause = 10;
            }
        }

        @Override
        public void stop() {
            if (guard.post() == null) pause = 40 + guard.getRandom().nextInt(120);
        }

        @Nullable
        private BlockPos nextPoint() {
            BlockPos center = guard.patrolCenter();
            for (int i = 0; i < 10; i++) {
                double a = guard.getRandom().nextDouble() * Math.PI * 2;
                double d = 10 + guard.getRandom().nextDouble() * 18;
                int x = (int) (guard.getX() + Math.cos(a) * d), z = (int) (guard.getZ() + Math.sin(a) * d);
                if (!guard.insideVillage(x, z)) {
                    // Слишком далеко — шаг в сторону центра.
                    x = (int) (guard.getX() + (center.getX() - guard.getX()) * 0.4);
                    z = (int) (guard.getZ() + (center.getZ() - guard.getZ()) * 0.4);
                }
                int y = guard.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(y - guard.getY()) > 12) continue;
                BlockPos pos = new BlockPos(x, y, z);
                if (!guard.level().getFluidState(pos.below()).isEmpty()) continue;
                return pos;
            }
            return null;
        }
    }

    /** Лучник: держит дистанцию 10–18 блоков, натягивает лук 20 тиков и стреляет. */
    static class BowGoal extends Goal {
        private final VillageGuard guard;
        private int seen;
        private int cooldown;

        BowGoal(VillageGuard guard) {
            this.guard = guard;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = guard.getTarget();
            return target != null && target.isAlive() && guard.getMainHandItem().getItem() instanceof BowItem;
        }

        @Override
        public void start() {
            guard.setAggressive(true);
        }

        @Override
        public void stop() {
            guard.setAggressive(false);
            seen = 0;
            guard.stopUsingItem();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = guard.getTarget();
            if (target == null) return;
            double dist = guard.distanceToSqr(target);
            boolean visible = guard.getSensing().hasLineOfSight(target);
            seen = visible ? Math.max(0, seen) + 1 : Math.min(0, seen) - 1;
            boolean onPost = guard.post() != null && guard.blockPosition().closerThan(guard.post(), 3);
            if (!onPost && (dist > 18 * 18 || seen < 5)) guard.getNavigation().moveTo(target, 1.0);
            else if (!onPost && dist < 7 * 7) {
                Vec3 away = guard.position().subtract(target.position()).normalize().scale(6);
                guard.getNavigation().moveTo(guard.getX() + away.x, guard.getY(), guard.getZ() + away.z, 1.1);
            } else guard.getNavigation().stop();
            guard.getLookControl().setLookAt(target, 30f, 30f);
            if (cooldown > 0) cooldown--;
            if (guard.isUsingItem()) {
                if (!visible && seen < -60) guard.stopUsingItem();
                else if (visible && guard.getTicksUsingItem() >= 20) {
                    guard.stopUsingItem();
                    guard.performRangedAttack(target, BowItem.getPowerForTime(guard.getTicksUsingItem()));
                    cooldown = 15;
                }
            } else if (cooldown <= 0 && seen >= -60 && dist < 26 * 26) {
                guard.startUsingItem(ProjectileUtil.getWeaponHoldingHand(guard, Items.BOW));
            }
        }
    }
}
